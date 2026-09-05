package com.fitcheck.app.ui.screens.dressme

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

data class DressMeState(val loading: Boolean = false, val context: TodayContext? = null, val recommendation: OutfitRecommendation? = null, val items: List<WardrobeItemEntity> = emptyList(), val error: String? = null)

class DressMeViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = DataGraph.get(app)
    private val runtime = AiRuntimeProvider.get(app)
    private val engine = OutfitEngine(graph.wardrobeRepository, graph.wearRepository, graph.stylePreferenceRepository, runtime)
    private val _state = MutableStateFlow(DressMeState())
    val state = _state.asStateFlow()
    fun recommend() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching {
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
fun DressMeScreen(vm: DressMeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("DRESS ME TODAY", style = MaterialTheme.typography.displaySmall)
        Text("A local outfit from your wardrobe")
        state.context?.let { Text("${it.date} · ${it.time} · ${it.weather}") }
        if (state.loading) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.items.isNotEmpty()) {
            Text("TODAY'S LOOK", style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.items) { item -> Card { Column(Modifier.padding(16.dp)) { Text(item.category.name); Text(item.name) } } }
            }
            state.recommendation?.explanation?.let { Text("WHY THIS WORKS\n$it", style = MaterialTheme.typography.bodyLarge) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = vm::recommend) { Text("Try Another") }
                Button(onClick = vm::wear) { Text("Wear This") }
            }
        } else if (!state.loading) {
            Text("Add at least one top, bottom, and shoes in Wardrobe to get started.")
        }
        Button(onClick = vm::recommend, enabled = !state.loading) { Text("Create today's look") }
    }
}
