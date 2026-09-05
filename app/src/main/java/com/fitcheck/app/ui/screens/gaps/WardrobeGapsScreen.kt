package com.fitcheck.app.ui.screens.gaps

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitcheck.app.data.DataGraph
import com.fitcheck.app.data.UserProfilePreferences
import com.fitcheck.app.data.local.entity.Category
import com.fitcheck.app.ai.AiRuntimeProvider
import com.fitcheck.app.ai.InitState
import com.fitcheck.app.data.local.entity.WardrobeItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray

data class WardrobeGap(
    val title: String,
    val category: Category,
    val priority: String,
    val newOutfits: Int,
    val compatible: Int,
    val wardrobeItemsUsed: Int,
    val reason: String
)

class WardrobeGapsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DataGraph.get(app).wardrobeRepository
    private val runtime = AiRuntimeProvider.get(app)
    private val _gaps = MutableStateFlow<List<WardrobeGap>>(emptyList())
    val gaps = _gaps.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init {
        cachedGaps?.let {
            _gaps.value = it
            _isLoading.value = false
        }
        viewModelScope.launch {
            repo.observeAllItems().collectLatest { items ->
                val signature = items.filter { it.isAvailable }
                    .joinToString("|") { "${it.id}:${it.updatedAt}:${it.isAvailable}" }
                if (signature == cachedSignature && cachedGaps != null) return@collectLatest
                _isLoading.value = true
                _gaps.value = buildAiRecommendations(items)
                cachedSignature = signature
                cachedGaps = _gaps.value
                _isLoading.value = false
            }
        }
    }

    companion object {
        private var cachedSignature: String? = null
        private var cachedGaps: List<WardrobeGap>? = null
    }

    private suspend fun buildAiRecommendations(items: List<WardrobeItemEntity>): List<WardrobeGap> {
        val calculated = calculateGaps(items)
        if (calculated.isEmpty()) return emptyList()
        val wardrobe = items.filter { it.isAvailable }.joinToString("\n") {
            "- ${it.name}; category=${it.category}; color=${it.color ?: "unknown"}; material=${it.material ?: "unknown"}; style=${it.style ?: "unknown"}"
        }
        val prompt = buildString {
            appendLine("You are FitCheck's wardrobe purchase planner.")
            appendLine("Look at the user's available wardrobe and suggest up to 3 specific clothing items to buy next.")
            appendLine("Return ONLY a JSON array, with no markdown: [{\"category\":\"TOP|BOTTOM|SHOES|OUTERWEAR|ACCESSORY\",\"item\":\"specific item\",\"reason\":\"short reason\"}]")
            appendLine("Do not repeat an item already owned. Choose varied, concrete items (for example: linen overshirt, brown loafers, knit tie), not category names alone. Do not include prices.")
            appendLine("Respect the user's gender context: never suggest women's-only pieces such as a women's kurta, lehenga, or dress for a male profile. Use men's or gender-neutral alternatives when appropriate. Do not output a generic category as the item name.")
            appendLine("Available wardrobe:\n$wardrobe")
            appendLine("Calculated gaps and outfit payoff:\n${calculated.joinToString("\n") { "${it.category}: +${it.newOutfits} outfits, ${it.compatible} compatible items" }}")
            val profile = UserProfilePreferences.read(getApplication())
            appendLine("User context: age=${profile?.age ?: "unknown"}, gender=${profile?.gender ?: "unknown"}, profession=${profile?.profession ?: "unknown"}. Use respectfully.")
        }
        return runCatching {
            if (runtime.snapshot().initState !is InitState.Ready) runtime.initialize()
            val answer = runtime.generate(prompt).foldToString()
            applyAiSuggestions(answer, calculated)
        }.getOrElse { calculated }
    }

    private fun calculateGaps(items: List<WardrobeItemEntity>): List<WardrobeGap> {
        val available = items.filter { it.isAvailable }
        val counts = available.groupingBy { it.category }.eachCount()
        return Category.entries.mapNotNull { category ->
            val count = counts[category] ?: 0
            if (count >= 2) null else {
                val tops = countsOf(available, Category.TOP)
                val bottoms = countsOf(available, Category.BOTTOM)
                val shoes = countsOf(available, Category.SHOES)
                val coreOutfits = tops * bottoms * shoes
                val newOutfits = when (category) {
                    Category.TOP -> bottoms * shoes
                    Category.BOTTOM -> tops * shoes
                    Category.SHOES -> tops * bottoms
                    Category.OUTERWEAR, Category.ACCESSORY, Category.ETHNIC_WEAR -> coreOutfits
                }
                val compatible = when (category) {
                    Category.TOP -> bottoms + shoes
                    Category.BOTTOM -> tops + shoes
                    Category.SHOES -> tops + bottoms
                    Category.OUTERWEAR, Category.ACCESSORY, Category.ETHNIC_WEAR -> tops + bottoms + shoes
                }
                val priority = when {
                    newOutfits >= 4 -> "High Priority"
                    newOutfits > 0 || compatible >= 2 -> "Medium Priority"
                    else -> "Low Priority"
                }
                val reason = when {
                    count == 0 && newOutfits > 0 -> "Missing category with the strongest outfit payoff."
                    count == 0 -> "Missing category; add core pieces before expecting new complete outfits."
                    else -> "Adding a second option would increase wardrobe variety."
                }
                WardrobeGap(category.label(), category, priority, newOutfits, compatible, available.size, reason)
            }
        }.sortedWith(compareByDescending<WardrobeGap> { it.newOutfits }
            .thenByDescending { it.compatible }
            .thenBy { it.category.ordinal })
            .take(3)
    }

    private fun applyAiSuggestions(raw: String, calculated: List<WardrobeGap>): List<WardrobeGap> {
        val jsonText = raw.substringAfter('[', "").substringBeforeLast(']', "").let { if (it.isBlank()) "[]" else "[$it]" }
        val suggestions = JSONArray(jsonText)
        val used = mutableSetOf<Category>()
        val ai = buildList {
            for (index in 0 until suggestions.length()) {
                val item = suggestions.optJSONObject(index) ?: continue
                val category = runCatching { Category.valueOf(item.optString("category").uppercase()) }.getOrNull() ?: continue
                val base = calculated.firstOrNull { it.category == category } ?: continue
                if (!used.add(category)) continue
                val name = item.optString("item").trim().takeIf { it.isNotBlank() } ?: continue
                val reason = item.optString("reason").trim().takeIf { it.isNotBlank() } ?: base.reason
                val profile = UserProfilePreferences.read(getApplication())
                if (profile?.gender.equals("Male", ignoreCase = true) && isWomenOnlySuggestion(name)) continue
                add(base.copy(title = name, reason = reason))
            }
        }
        return (ai + calculated.filterNot { candidate -> ai.any { it.category == candidate.category } }).take(3)
    }

    private fun isWomenOnlySuggestion(name: String): Boolean {
        val value = name.lowercase()
        return value.contains("women's") || value.contains("womens") || value.contains("women ") ||
            value.contains("female dress") || value.contains("lehenga")
    }
}

private suspend fun kotlinx.coroutines.flow.Flow<String>.foldToString(): String {
    val output = StringBuilder()
    collect { output.append(it) }
    return output.toString()
}

private fun countsOf(items: List<com.fitcheck.app.data.local.entity.WardrobeItemEntity>, category: Category): Int =
    items.count { it.category == category }

private fun Category.label(): String = when (this) {
    Category.TOP -> "Tops"
    Category.BOTTOM -> "Bottoms"
    Category.SHOES -> "Shoes"
    Category.OUTERWEAR -> "Outerwear"
    Category.ACCESSORY -> "Accessories"
    Category.ETHNIC_WEAR -> "Ethnic Wear"
}

@Composable
fun WardrobeGapsScreen(onBack: () -> Unit = {}, onOpenAnalysis: (WardrobeGap) -> Unit = {}, vm: WardrobeGapsViewModel = viewModel()) {
    val gaps = vm.gaps.collectAsStateWithLifecycle().value
    val isLoading = vm.isLoading.collectAsStateWithLifecycle().value
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp)); Text("‹  Back", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { onBack() }); Text("Wardrobe Gaps", style = MaterialTheme.typography.headlineLarge); Text("Based on your wardrobe analysis, here are pieces that would unlock the most new outfits.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(10.dp))
        when {
            isLoading -> Text("AI engine is loading your recommendation…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            gaps.isEmpty() -> Text("Your wardrobe has a strong foundation. Add more variety to discover new gaps.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(gaps) { gap -> GapCard(gap) { onOpenAnalysis(gap) } } }
        }
    }
}

@Composable private fun GapCard(gap: WardrobeGap, onExpand: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(gap.title, style = MaterialTheme.typography.titleMedium); Text("Price unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; AssistChip(onClick = {}, label = { Text(gap.priority) }) }
        Text("↗  +${gap.newOutfits} outfit combinations  ·  ${gap.compatible} wardrobe items can pair  ·  ${gap.wardrobeItemsUsed} available items analyzed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(gap.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onExpand, modifier = Modifier.fillMaxWidth()) { Text("⌄  Expand details") }
    } }
}
