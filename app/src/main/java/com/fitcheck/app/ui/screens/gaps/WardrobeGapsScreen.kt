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
import com.fitcheck.app.data.local.entity.Category
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class WardrobeGap(val title: String, val category: Category, val priority: String, val price: String, val newOutfits: Int, val compatible: Int, val expected: Int)

class WardrobeGapsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DataGraph.get(app).wardrobeRepository
    val gaps = repo.observeAllItems().map { items ->
        val counts = items.groupingBy { it.category }.eachCount()
        listOf(
            Category.OUTERWEAR to Triple("Outerwear", "High Priority", "₹7,500"),
            Category.SHOES to Triple("Shoes", "Medium Priority", "₹4,200"),
            Category.BOTTOM to Triple("Bottoms", "Medium Priority", "₹3,500")
        ).mapNotNull { (category, info) ->
            val count = counts[category] ?: 0
            if (count >= 2) null else WardrobeGap(info.first, category, info.second, info.third, (counts[Category.TOP] ?: 0) * (counts[Category.BOTTOM] ?: 0).coerceAtLeast(1), (items.count { it.category != category }).coerceAtLeast(0), 12)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun WardrobeGapsScreen(onBack: () -> Unit = {}, onOpenAnalysis: (WardrobeGap) -> Unit = {}, vm: WardrobeGapsViewModel = viewModel()) {
    val gaps = vm.gaps.collectAsStateWithLifecycle().value
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp)); Text("‹  Back", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { onBack() }); Text("Wardrobe Gaps", style = MaterialTheme.typography.headlineLarge); Text("Based on your wardrobe analysis, here are pieces that would unlock the most new outfits.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(10.dp))
        if (gaps.isEmpty()) Text("Your wardrobe has a strong foundation. Add more variety to discover new gaps.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(gaps) { gap -> GapCard(gap) { onOpenAnalysis(gap) } } }
    }
}

@Composable private fun GapCard(gap: WardrobeGap, onExpand: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(gap.title, style = MaterialTheme.typography.titleMedium); Text(gap.price, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }; AssistChip(onClick = {}, label = { Text(gap.priority) }) }
        Text("↗  +${gap.newOutfits} new outfits  ·  ${gap.compatible} compatible items  ·  ~${gap.expected} expected impact", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onExpand, modifier = Modifier.fillMaxWidth()) { Text("⌄  Expand details") }
    } }
}
