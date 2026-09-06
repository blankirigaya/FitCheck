package com.fitcheck.app.ui.screens.dressme

import android.app.Application
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.location.Geocoder
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.app.Activity
import java.util.Locale
import androidx.core.content.ContextCompat
import java.net.URL
import org.json.JSONObject
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitcheck.app.ai.*
import com.fitcheck.app.data.DataGraph
import com.fitcheck.app.data.UserProfilePreferences
import com.fitcheck.app.data.local.entity.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class DressMeState(val loading: Boolean = false, val context: TodayContext? = null, val recommendation: OutfitRecommendation? = null, val items: List<WardrobeItemEntity> = emptyList(), val error: String? = null)

class DressMeViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = DataGraph.get(app)
    private val runtime = AiRuntimeProvider.get(app)
    private val engine = OutfitEngine(graph.wardrobeRepository, graph.wearRepository, graph.stylePreferenceRepository, runtime)
    private val _state = MutableStateFlow(DressMeState())
    val state = _state.asStateFlow()
    private var recommendationCycle = 0
    init { refreshContext() }
    private fun refreshContext() = viewModelScope.launch { _state.value = _state.value.copy(context = fetchLiveContext()) }
    private suspend fun fetchLiveContext(): TodayContext = withContext(kotlinx.coroutines.Dispatchers.IO) { readWeather(ContextBuilder(UserProfilePreferences.read(getApplication())).build()) }
    private fun readWeather(base: TodayContext): TodayContext {
        val app = getApplication<Application>(); val fine = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; val coarse = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return base.copy(weather = "Location permission needed")
        return runCatching {
            val lm = app.getSystemService(LocationManager::class.java); val loc = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time } ?: return base.copy(weather = "Location unavailable")
            val json = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}&current=temperature_2m,weather_code&temperature_unit=celsius").readText()); val current = json.getJSONObject("current"); val temp = current.getDouble("temperature_2m").toInt(); val code = current.getInt("weather_code"); val city = runCatching { Geocoder(app).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.locality }.getOrNull() ?: "Current location"; base.copy(temperatureC = temp, weather = weatherLabel(code), location = city)
        }.getOrDefault(base.copy(weather = "Weather unavailable"))
    }
    private fun weatherLabel(code: Int) = when (code) { 0 -> "Clear"; 1, 2, 3 -> "Partly cloudy"; 45, 48 -> "Foggy"; in 51..67 -> "Rainy"; in 71..77 -> "Snowy"; in 80..99 -> "Showers"; else -> "Current weather" }
    fun recommend(requestedOccasion: String? = null) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        val liveContext = fetchLiveContext().let { context ->
            requestedOccasion?.trim()?.takeIf { it.isNotBlank() }?.let { context.copy(occasion = it) } ?: context
        }
        val available = graph.wardrobeRepository.getAvailableItems().filter { it.laundryStatus != LaundryStatus.IN_LAUNDRY }
        val cycle = recommendationCycle++
        val previousIds = _state.value.recommendation?.itemIds?.toSet().orEmpty()
        val coreFallback = listOf(Category.TOP, Category.BOTTOM, Category.SHOES).mapNotNull { category ->
            chooseForOccasion(available.filter { it.category == category }, liveContext.occasion, cycle)
        }
        if (coreFallback.size != 3) {
            val isGym = liveContext.occasion.lowercase().let { it.contains("gym") || it.contains("workout") || it.contains("training") || it.contains("running") }
            val message = if (isGym) "No complete gym-ready outfit was found. Add activewear with stretch or breathable details, plus athletic shoes." else "Add at least one top, bottom, and shoes suitable for ${liveContext.occasion}."
            _state.value = _state.value.copy(loading = false, context = liveContext, error = message)
            return@launch
        }
        val fallbackAccessory = available.filter { it.category == Category.ACCESSORY }
            .sortedBy { it.id }
            .let { candidates -> candidates.getOrNull(cycle.mod(candidates.size.coerceAtLeast(1))) }
        val fallback = coreFallback + listOfNotNull(fallbackAccessory)
        val fallbackRecommendation = OutfitRecommendation(fallback.map { it.id }, "This local wardrobe match uses available pieces and is ready to wear.", liveContext.occasion)
        _state.value = DressMeState(context = liveContext, recommendation = fallbackRecommendation.copy(explanation = explainLook(liveContext, fallback)), items = fallback, error = null)
        val aiResult = withTimeoutOrNull(30_000L) {
            runCatching {
                if (runtime.snapshot().initState !is InitState.Ready) runtime.initialize()
                engine.recommend(previousIds, liveContext)
            }.getOrNull()
        }
        if (aiResult != null) {
            val (context, rec) = aiResult
            val selected = rec.itemIds.mapNotNull { graph.wardrobeRepository.getItemById(it) }
            val matchingAccessory = available
                .filter { it.category == Category.ACCESSORY && it.id !in rec.itemIds }
                .maxByOrNull { accessoryMatchScore(it, selected) }
            val finalRecommendation = if (matchingAccessory != null && selected.size == 3) rec.copy(itemIds = rec.itemIds + matchingAccessory.id) else rec
            val items = finalRecommendation.itemIds.mapNotNull { graph.wardrobeRepository.getItemById(it) }
            if (items.size >= 3) _state.value = DressMeState(context = context, recommendation = finalRecommendation.copy(explanation = explainLook(context, items)), items = items)
        } else {
            // The local recommendation is already visible and usable. Do not show a
            // red failure message when the on-device AI is unavailable or offline.
            _state.value = _state.value.copy(error = null)
        }
        _state.value = _state.value.copy(loading = false)
    }

    private fun explainLook(context: TodayContext, items: List<WardrobeItemEntity>): String {
        val names = items.joinToString(", ") { it.name }
        val colors = items.mapNotNull { it.color?.takeIf(String::isNotBlank) }.distinct()
        val palette = if (colors.isEmpty()) "I do not have recorded color details for these pieces" else "The recorded palette is ${colors.joinToString(" and ")}" 
        val weather = when {
            context.temperatureC == null -> "Weather data is unavailable, so check the conditions before heading out"
            context.temperatureC <= 12 -> "The cooler ${context.temperatureC}°C weather favors the warmer layers in this look"
            context.temperatureC >= 28 -> "The warm ${context.temperatureC}°C weather favors the lighter pieces in this look"
            else -> "The mild ${context.temperatureC}°C weather suits this balanced combination"
        }
        val occasion = context.occasion.lowercase()
        val recordedDetails = items.mapNotNull { item ->
            val details = listOfNotNull(item.material, item.fit, item.style, item.subcategory)
                .filter { it.isNotBlank() }
            details.takeIf { it.isNotEmpty() }?.let { "${item.name}: ${it.joinToString(", ")}" }
        }
        val occasionReason = when {
            listOf("gym", "workout", "training", "running").any { occasion.contains(it) } ->
                if (recordedDetails.isEmpty()) "I can only verify the selected pieces; fabric and stretch details are not recorded yet." else "I prioritized the recorded fit, material, and style details that are available for movement and comfort: ${recordedDetails.joinToString("; ")}."
            listOf("professional", "office", "work", "presentation", "interview").any { occasion.contains(it) } ->
                if (recordedDetails.isEmpty()) "I selected the available outfit pieces, but structure and fabric details are not recorded yet." else "The recorded garment details support a more intentional, put-together look: ${recordedDetails.joinToString("; ")}."
            listOf("wedding", "party", "date", "formal").any { occasion.contains(it) } ->
                if (recordedDetails.isEmpty()) "I selected the available pieces, but formal details are not recorded yet." else "I used the recorded garment details to keep the look coordinated: ${recordedDetails.joinToString("; ")}."
            else ->
                if (recordedDetails.isEmpty()) "I selected the available pieces; add material, fit, or style details for a more specific explanation." else "The recorded garment details make the combination practical and versatile: ${recordedDetails.joinToString("; ")}."
        }
        val accessory = items.firstOrNull { it.category == Category.ACCESSORY }?.name?.let { " The $it adds a finishing touch without replacing the core outfit." }.orEmpty()
        return "For ${context.occasion}, I chose $names. $occasionReason $weather. $palette supports the overall combination.$accessory"
    }

    private fun chooseForOccasion(candidates: List<WardrobeItemEntity>, occasion: String, cycle: Int): WardrobeItemEntity? {
        if (candidates.isEmpty()) return null
        val normalized = occasion.lowercase()
        val eligible = candidates.filter { isSuitableForOccasion(it, occasion) }
        if (eligible.isEmpty()) return null
        val terms = when {
            listOf("gym", "workout", "training", "running").any { normalized.contains(it) } ->
                listOf("gym", "workout", "training", "running", "active", "athletic", "sport", "stretch", "breathable", "moisture", "flexible", "sneaker", "track")
            listOf("college", "campus", "university", "school").any { normalized.contains(it) } ->
                listOf("college", "campus", "casual", "everyday", "comfortable", "versatile", "sneaker", "relaxed")
            listOf("professional", "office", "work", "presentation", "interview").any { normalized.contains(it) } ->
                listOf("professional", "office", "work", "formal", "polished", "structured", "smart", "blazer", "loafer")
            else -> normalized.split(Regex("\\s+")).filter { it.length > 2 }
        }
        fun score(item: WardrobeItemEntity): Int {
            val text = listOfNotNull(item.name, item.subcategory, item.style, item.material, item.fit, item.styleTags.joinToString(" "))
                .joinToString(" ").lowercase()
            return terms.sumOf { term -> if (text.contains(term)) 1 else 0 }
        }
        val ranked = eligible.sortedWith(compareByDescending<WardrobeItemEntity> { score(it) }.thenBy { it.id })
        val bestScore = score(ranked.first())
        val best = ranked.filter { score(it) == bestScore }
        return best[(kotlin.math.abs(normalized.hashCode()) + cycle) % best.size]
    }
    fun wear() = viewModelScope.launch {
        val rec = _state.value.recommendation ?: return@launch
        val outfitId = graph.outfitRepository.saveOutfit(OutfitEntity(name = "Today's Look", generatedByAI = true, explanation = rec.explanation, occasion = rec.occasion), rec.itemIds)
        rec.itemIds.forEach { graph.wearRepository.recordWear(it, outfitId = outfitId, occasion = rec.occasion) }
        _state.value = _state.value.copy(error = "Saved to wear history")
    }

    private fun accessoryMatchScore(accessory: WardrobeItemEntity, outfit: List<WardrobeItemEntity>): Int {
        val palette = outfit.flatMap { listOfNotNull(it.color, it.style, it.material) }.joinToString(" ").lowercase()
        val accessoryText = listOfNotNull(accessory.name, accessory.color, accessory.style, accessory.material).joinToString(" ").lowercase()
        return listOfNotNull(accessory.color, accessory.style, accessory.material).count { palette.contains(it.lowercase()) } * 3 +
            if (accessoryText.contains("watch") || accessoryText.contains("belt") || accessoryText.contains("glasses") || accessoryText.contains("bag")) 1 else 0
    }
}

@Composable
fun DressMeScreen(onToolClick: (String) -> Unit = {}, vm: DressMeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var wardrobePreview by remember { mutableStateOf<List<WardrobeItemEntity>>(emptyList()) }
    var occasion by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    val appContext = LocalContext.current
    val textToSpeech = remember(appContext) {
        TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }
    DisposableEffect(textToSpeech) {
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { speaking = true }
            override fun onDone(utteranceId: String?) { speaking = false }
            override fun onError(utteranceId: String?) { speaking = false }
        })
        onDispose { textToSpeech.shutdown() }
    }
    val speech = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        if (result.resultCode == Activity.RESULT_OK) occasion = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
    }
    val requestMic = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            listening = true
            speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say today's occasion")
            })
        }
    }
    fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            listening = true
            speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say today's occasion")
            })
        } else requestMic.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) { wardrobePreview = DataGraph.get(appContext).wardrobeRepository.getAvailableItems() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("YOUR ASSISTANT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Good morning, ${UserProfilePreferences.read(LocalContext.current)?.name ?: "there"}", style = MaterialTheme.typography.headlineLarge); Text("◉", style = MaterialTheme.typography.headlineMedium) }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text("☼", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.width(10.dp)); Column { val context = state.context; Text("${context?.temperatureC?.let { "${it}°C" } ?: "—"} · ${context?.weather ?: "Loading weather…"} · ${context?.location ?: "Locating…"}", style = MaterialTheme.typography.labelLarge); Text("Live device context for your day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What’s today’s occasion?", style = MaterialTheme.typography.titleMedium)
                Text("Tell Gemma what you’re dressing for and it will use it in your outfit recommendation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = occasion, onValueChange = { occasion = it }, modifier = Modifier.weight(1f), placeholder = { Text("e.g. college, wedding, gym…") }, singleLine = true)
                    IconButton(onClick = { startVoiceInput() }, enabled = !listening) { Icon(Icons.Outlined.Mic, contentDescription = "Speak occasion") }
                }
                if (listening) Text("Listening…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (state.items.isEmpty() && !state.loading) {
            FeaturedWardrobe(wardrobePreview, onMakeLook = { vm.recommend(occasion) }, onToolClick = onToolClick)
        } else {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Dress Me Today", style = MaterialTheme.typography.titleMedium); Text("✦ AI CHOICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }
                if (state.items.isNotEmpty()) OutfitCollage(state.items) else Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Why this works:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    IconButton(onClick = {
                        if (speaking) {
                            textToSpeech.stop()
                            speaking = false
                            return@IconButton
                        }
                        val explanation = state.recommendation?.explanation ?: "Gemma is choosing a look from your wardrobe."
                        val selected = state.items.joinToString(", ") { it.name }
                        textToSpeech.speak("Today's occasion is ${state.context?.occasion ?: "everyday"}. I chose $selected. $explanation", TextToSpeech.QUEUE_FLUSH, null, "fitcheck-look")
                    }, enabled = ttsReady && state.recommendation != null) { Text(if (speaking) "⏸" else "🔊", style = MaterialTheme.typography.titleMedium) }
                }
                Text(state.recommendation?.explanation ?: "Gemma is choosing a look from your wardrobe.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = vm::wear, enabled = state.recommendation != null, modifier = Modifier.weight(1f)) { Text("✓  Wear this") }; OutlinedButton(onClick = { vm.recommend(occasion) }, enabled = !state.loading, modifier = Modifier.weight(1f)) { Text("Change outfit") } }
            } }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text("Quick tools", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { QuickTool("◎", "Scan clothes", Modifier.weight(1f)) { onToolClick("wardrobe") }; QuickTool("✣", "What goes with this?", Modifier.weight(1f)) { onToolClick("stylist") } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { QuickTool("▣", "Should I buy this?", Modifier.weight(1f)) { onToolClick("stylist") }; QuickTool("⊘", "What am I missing?", Modifier.weight(1f)) { onToolClick("gaps") } }
    }
}

@Composable private fun OutfitCollage(items: List<WardrobeItemEntity>) {
    Row(Modifier.fillMaxWidth().height(235.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutfitTile(items.first(), Modifier.weight(1.35f).fillMaxHeight())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.drop(1).take(2).forEach { OutfitTile(it, Modifier.weight(1f).fillMaxWidth()) }
        }
        if (items.size > 3) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.drop(3).take(2).forEach { OutfitTile(it, Modifier.weight(1f).fillMaxWidth()) }
        }
    }
}

@Composable private fun OutfitTile(item: WardrobeItemEntity, modifier: Modifier) {
    Column(modifier) {
        LocalImage(item.imageUri, Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)))
        Text(if (item.category == Category.ACCESSORY) "Accessory · ${item.name}" else item.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable private fun FeaturedWardrobe(items: List<WardrobeItemEntity>, onMakeLook: () -> Unit, onToolClick: (String) -> Unit) {
    val pictured = items.filter { !it.imageUri.isNullOrBlank() }.take(6)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (pictured.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { Text("Add clothing photos to build your look") }
            } else {
                Row(Modifier.fillMaxWidth().height(300.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LocalImage(pictured.first().imageUri, Modifier.weight(1.35f).fillMaxHeight().clip(RoundedCornerShape(14.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        pictured.drop(1).take(2).forEach { LocalImage(it.imageUri, Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp))) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        pictured.drop(3).take(3).forEach { LocalImage(it.imageUri, Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp))) }
                    }
                }
            }
            Text("Everyday", Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Button(onClick = onMakeLook, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Dress Me Today") }
        }
    }
}

@Composable private fun QuickTool(icon: String, label: String, modifier: Modifier, onClick: () -> Unit) { Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(12.dp)) { Text(icon, color = MaterialTheme.colorScheme.secondary); Spacer(Modifier.height(6.dp)); Text(label, style = MaterialTheme.typography.labelMedium) } } }

@Composable private fun LocalImage(path: String?, modifier: Modifier) {
    val bitmap = remember(path) { path?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } }
    if (bitmap != null) Image(bitmap.asImageBitmap(), "Clothing photo", modifier, contentScale = ContentScale.Crop)
    else Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant) {}
}
