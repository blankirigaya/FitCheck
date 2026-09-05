package com.fitcheck.app.ui.screens.dressme

import android.app.Application
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.location.Geocoder
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.net.URL
import org.json.JSONObject
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitcheck.app.ai.*
import com.fitcheck.app.data.DataGraph
import com.fitcheck.app.data.local.entity.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DressMeState(val loading: Boolean = false, val context: TodayContext? = null, val recommendation: OutfitRecommendation? = null, val items: List<WardrobeItemEntity> = emptyList(), val error: String? = null)

class DressMeViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = DataGraph.get(app)
    private val runtime = AiRuntimeProvider.get(app)
    private val engine = OutfitEngine(graph.wardrobeRepository, graph.wearRepository, graph.stylePreferenceRepository, runtime)
    private val _state = MutableStateFlow(DressMeState())
    val state = _state.asStateFlow()
    init { loadContext() }
    private fun loadContext() = viewModelScope.launch {
        val base = ContextBuilder().build()
        val weather = withContext(kotlinx.coroutines.Dispatchers.IO) { readWeather(base) }
        _state.value = _state.value.copy(context = weather)
    }
    private fun readWeather(base: TodayContext): TodayContext {
        val app = getApplication<Application>(); val fine = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; val coarse = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return base.copy(weather = "Location permission needed")
        return runCatching {
            val lm = app.getSystemService(LocationManager::class.java); val loc = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time } ?: return base.copy(weather = "Location unavailable")
            val json = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}&current=temperature_2m,weather_code&temperature_unit=celsius").readText()); val current = json.getJSONObject("current"); val temp = current.getDouble("temperature_2m").toInt(); val code = current.getInt("weather_code"); val city = runCatching { Geocoder(app).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.locality }.getOrNull() ?: "Current location"; base.copy(temperatureC = temp, weather = weatherLabel(code), location = city)
        }.getOrDefault(base.copy(weather = "Weather unavailable"))
    }
    private fun weatherLabel(code: Int) = when (code) { 0 -> "Clear"; 1, 2, 3 -> "Partly cloudy"; 45, 48 -> "Foggy"; in 51..67 -> "Rainy"; in 71..77 -> "Snowy"; in 80..99 -> "Showers"; else -> "Current weather" }
    fun recommend() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching {
            if (runtime.snapshot().initState !is InitState.Ready) {
                runtime.initialize()
            }
            val previous = _state.value.recommendation?.itemIds?.toSet().orEmpty()
            val (context, rec) = engine.recommend(previous)
            val items = rec.itemIds.mapNotNull { graph.wardrobeRepository.getItemById(it) }
            _state.value = DressMeState(context = context, recommendation = rec, items = items)
        }.onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Could not create an outfit") }
    }
    fun wear() = viewModelScope.launch {
        val rec = _state.value.recommendation ?: return@launch
        val outfitId = graph.outfitRepository.saveOutfit(OutfitEntity(name = "Today's Look", generatedByAI = true, explanation = rec.explanation, occasion = rec.occasion), rec.itemIds)
        rec.itemIds.forEach { graph.wearRepository.recordWear(it, outfitId = outfitId, occasion = rec.occasion) }
        _state.value = _state.value.copy(error = "Saved to wear history")
    }
}

@Composable
fun DressMeScreen(onToolClick: (String) -> Unit = {}, vm: DressMeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("YOUR ASSISTANT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Good morning, Alex", style = MaterialTheme.typography.headlineLarge); Text("◉", style = MaterialTheme.typography.headlineMedium) }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text("☼", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.width(10.dp)); Column { val context = state.context; Text("${context?.temperatureC?.let { "${it}°C" } ?: "—"} · ${context?.weather ?: "Loading weather…"} · ${context?.location ?: "Locating…"}", style = MaterialTheme.typography.labelLarge); Text("Live device context for your day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        if (state.items.isEmpty() && !state.loading) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Dress Me Today", style = MaterialTheme.typography.titleLarge); Text("Add a top, bottom, and shoes in Wardrobe to get a personal look."); Button(onClick = { vm.recommend() }) { Text("Make outfit for me") } } }
        } else {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Dress Me Today", style = MaterialTheme.typography.titleMedium); Text("✦ AI CHOICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }
                if (state.items.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { state.items.forEach { item -> LocalImage(item.imageUri, Modifier.width(110.dp).height(150.dp)) } } else Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                Text("Why this works:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary); Text(state.recommendation?.explanation ?: "Gemma is choosing a look from your wardrobe.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = vm::wear, enabled = state.recommendation != null, modifier = Modifier.weight(1f)) { Text("✓  Wear this") }; OutlinedButton(onClick = vm::recommend, enabled = !state.loading, modifier = Modifier.weight(1f)) { Text("Change outfit") } }
            } }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text("Quick tools", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { QuickTool("◎", "Scan clothes", Modifier.weight(1f)) { onToolClick("scan") }; QuickTool("✣", "What goes with this?", Modifier.weight(1f)) { onToolClick("stylist") } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { QuickTool("▣", "Should I buy this?", Modifier.weight(1f)) { onToolClick("stylist") }; QuickTool("⊘", "What am I missing?", Modifier.weight(1f)) { onToolClick("wardrobe") } }
    }
}

@Composable private fun QuickTool(icon: String, label: String, modifier: Modifier, onClick: () -> Unit) { Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(12.dp)) { Text(icon, color = MaterialTheme.colorScheme.secondary); Spacer(Modifier.height(6.dp)); Text(label, style = MaterialTheme.typography.labelMedium) } } }

@Composable private fun LocalImage(path: String?, modifier: Modifier) {
    val bitmap = remember(path) { path?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } }
    if (bitmap != null) Image(bitmap.asImageBitmap(), "Clothing photo", modifier, contentScale = ContentScale.Crop)
    else Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant) {}
}
