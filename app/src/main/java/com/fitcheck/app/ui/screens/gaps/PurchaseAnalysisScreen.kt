package com.fitcheck.app.ui.screens.gaps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitcheck.app.data.local.entity.Category

@Composable
fun PurchaseAnalysisScreen(categoryName: String, onBack: () -> Unit, vm: WardrobeGapsViewModel = viewModel()) {
    val gap = vm.gaps.value.firstOrNull { it.category.name == categoryName }
    val title = gap?.title ?: when (categoryName) { "SHOES" -> "Everyday Shoes"; "BOTTOM" -> "Versatile Bottoms"; else -> "Navy Blazer" }
    val price = gap?.price ?: when (categoryName) { "SHOES" -> "₹4,200"; "BOTTOM" -> "₹3,500"; else -> "₹7,500" }
    val category = runCatching { Category.valueOf(categoryName) }.getOrDefault(Category.OUTERWEAR)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("‹  Back", modifier = Modifier.weight(1f).clickable { onBack() }); Text("Purchase Analysis", style = MaterialTheme.typography.titleLarge); Text("⋯", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End) } }
        item { Column { Text("AI suggestion", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${title} for your wardrobe", style = MaterialTheme.typography.headlineMedium) } }
        item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Column {
            Box(Modifier.fillMaxWidth().height(190.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text(when (category) { Category.SHOES -> "👞"; Category.BOTTOM -> "👖"; else -> "🧥" }, style = MaterialTheme.typography.displayLarge) }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(title, style = MaterialTheme.typography.titleLarge); Text("AI wardrobe recommendation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text(price, style = MaterialTheme.typography.titleLarge) }
        } } }
        item { Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) { Text("✓  AI VERDICT: BUY", Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge) } }
        item { AnalysisCard("WARDROBE COMPATIBILITY", "Works with ${gap?.compatible ?: 3} existing items", "Unlocks approximately ${gap?.newOutfits ?: 6} new outfit combinations.") }
        item { AnalysisCard("EXPECTED USAGE", "~${gap?.expected ?: 24} wears/year projected", "Estimated cost per wear: ${if (price.contains("7,500")) "₹250" else "₹175"} after 1 year.") }
        item { AnalysisCard("BUDGET STATUS", "Within monthly budget", "Based on your current wardrobe plan.") }
        item { Text("Similar alternatives", style = MaterialTheme.typography.titleMedium) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { AlternativeCard("Everyday option", Modifier.weight(1f)); AlternativeCard("Premium option", Modifier.weight(1f)) } }
        item { Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("+  Add to plan") } }
    }
}

@Composable private fun AnalysisCard(label: String, title: String, body: String) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(12.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodySmall) } } }
@Composable private fun AlternativeCard(name: String, modifier: Modifier) { Card(modifier) { Column(Modifier.padding(10.dp)) { Box(Modifier.fillMaxWidth().height(58.dp).background(MaterialTheme.colorScheme.surfaceVariant)); Spacer(Modifier.height(6.dp)); Text(name, style = MaterialTheme.typography.labelMedium) } } }
