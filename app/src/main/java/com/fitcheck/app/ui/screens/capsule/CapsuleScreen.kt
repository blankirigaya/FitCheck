package com.fitcheck.app.ui.screens.capsule

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitcheck.app.ai.AiRuntimeProvider
import com.fitcheck.app.capsule.CapsuleAnalysis
import com.fitcheck.app.capsule.CapsuleAnalyzer
import com.fitcheck.app.data.DataGraph
import com.fitcheck.app.data.UserProfilePreferences
import com.fitcheck.app.data.local.entity.WardrobeItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CapsuleViewModel(app: Application) : AndroidViewModel(app) {
    private val analyzer = CapsuleAnalyzer()
    private val runtime = AiRuntimeProvider.get(app)
    private val _analysis = MutableStateFlow<CapsuleAnalysis?>(null)
    val analysis = _analysis.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()
    private val _gemmaLoading = MutableStateFlow(false)
    val gemmaLoading = _gemmaLoading.asStateFlow()

    init { viewModelScope.launch { DataGraph.get(app).wardrobeRepository.observeAllItems().collectLatest { items -> if (_analysis.value == null) analyze(items) } } }

    fun analyzeNow() = viewModelScope.launch { analyze(DataGraph.get(getApplication()).wardrobeRepository.getAllItems()) }

    private fun analyze(items: List<WardrobeItemEntity>) { _loading.value = true; _analysis.value = analyzer.analyze(items); _loading.value = false }

    fun askGemma() = viewModelScope.launch {
        val current = _analysis.value ?: return@launch
        _gemmaLoading.value = true
        val profile = UserProfilePreferences.read(getApplication())
        val prompt = """You are FitCheck's offline capsule wardrobe advisor. Explain these deterministic results in 2 concise sentences. Do not invent or change numbers. Score=${current.capsuleScore}/100, core pieces=${current.capsuleItems.size}, viable outfits=${current.outfitPotential}, coverage=${current.categoryCoverage}%, redundancy=${current.redundancyScore}%. Core: ${current.capsuleItems.joinToString { it.item.name }}. Gaps: ${current.gaps.joinToString { it.role }}. User context: age=${profile?.age ?: "unknown"}, gender=${profile?.gender ?: "unknown"}, profession=${profile?.profession ?: "unknown"}. Use it respectfully."""
        runCatching {
            if (runtime.snapshot().initState !is com.fitcheck.app.ai.InitState.Ready) runtime.initialize()
            val text = runtime.generate(prompt).foldToString().trim()
            if (text.isNotBlank()) _analysis.value = current.copy(insight = text.replace("**", ""))
        }
        _gemmaLoading.value = false
    }
}

@Composable
fun CapsuleScreen(onBack: () -> Unit = {}, onItemClick: (Long) -> Unit = {}, vm: CapsuleViewModel = viewModel()) {
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val gemmaLoading by vm.gemmaLoading.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(10.dp)); Text("‹  Plan", modifier = Modifier.clickable { onBack() }); Text("Capsule Wardrobe", style = MaterialTheme.typography.headlineLarge); Text("Your most versatile pieces, organized into a wardrobe that works harder.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (loading || analysis == null) Text("Analyzing your local wardrobe…", style = MaterialTheme.typography.titleMedium)
        else analysis?.let { data ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("CAPSULE SCORE", style = MaterialTheme.typography.labelMedium); Text("${data.capsuleScore} / 100", style = MaterialTheme.typography.displaySmall); Text("${data.capsuleItems.size} core pieces  ·  ${data.outfitPotential} strong combinations  ·  ${coverageLabel(data.categoryCoverage)} coverage", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            SectionTitle("CORE CAPSULE")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) { data.capsuleItems.forEach { scored -> CapsuleItemCard(scored.item, scored.versatilityScore, onItemClick) } }
            SectionTitle("OUTFIT POTENTIAL"); Text("${data.outfitPotential} strong combinations", style = MaterialTheme.typography.headlineSmall); Text("Calculated from compatible tops, bottoms, and shoes in your capsule.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (data.gaps.isNotEmpty()) { SectionTitle("WARDROBE GAPS"); data.gaps.forEach { gap -> InfoCard("${gap.role} · ${gap.severity} priority", gap.explanation) } }
            if (data.redundancyGroups.isNotEmpty()) { SectionTitle("REDUNDANCY"); data.redundancyGroups.forEach { group -> InfoCard("${group.items.size} overlapping pieces", group.explanation) } }
            SectionTitle("AI INSIGHT"); InfoCard(if (gemmaLoading) "Gemma is interpreting your results…" else "Based on your wardrobe", data.insight)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = vm::analyzeNow, modifier = Modifier.weight(1f)) { Text("Reanalyze") }; Button(onClick = vm::askGemma, enabled = !gemmaLoading, modifier = Modifier.weight(1f)) { Text("AI insight") } }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
@Composable private fun InfoCard(title: String, body: String) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun CapsuleItemCard(item: WardrobeItemEntity, score: Int, onClick: (Long) -> Unit) { Card(Modifier.width(150.dp).clickable { onClick(item.id) }) { Column { LocalImage(item.imageUri, Modifier.fillMaxWidth().height(120.dp)); Column(Modifier.padding(9.dp)) { Text(item.name, maxLines = 1, style = MaterialTheme.typography.titleSmall); Text("Versatility $score", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } } } }
@Composable private fun LocalImage(uri: String?, modifier: Modifier) { val context = LocalContext.current; var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }; LaunchedEffect(uri) { bitmap = uri?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } }; if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Wardrobe item", modifier, contentScale = ContentScale.Crop) else Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant) {} }
private fun coverageLabel(value: Int) = when { value >= 100 -> "Strong"; value >= 66 -> "Good"; value > 0 -> "Developing"; else -> "Missing" }
private suspend fun kotlinx.coroutines.flow.Flow<String>.foldToString(): String { val out = StringBuilder(); collect { out.append(it) }; return out.toString() }
