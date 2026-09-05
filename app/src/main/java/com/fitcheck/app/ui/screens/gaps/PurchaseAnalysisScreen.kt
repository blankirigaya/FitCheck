package com.fitcheck.app.ui.screens.gaps

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import java.net.URL
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitcheck.app.data.local.entity.Category
import com.fitcheck.app.data.local.entity.WardrobeItemEntity
import com.fitcheck.app.data.DataGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PurchaseAnalysisScreen(
    categoryName: String,
    suggestedTitle: String? = null,
    calculatedNewOutfits: Int? = null,
    calculatedCompatible: Int? = null,
    calculatedItemsUsed: Int? = null,
    calculatedReason: String? = null,
    onBack: () -> Unit,
    vm: WardrobeGapsViewModel = viewModel()
) {
    val gap = vm.gaps.value.firstOrNull { it.category.name == categoryName }
    val title = suggestedTitle?.takeIf { it.isNotBlank() } ?: gap?.title ?: "AI wardrobe recommendation"
    val newOutfits = calculatedNewOutfits ?: gap?.newOutfits ?: 0
    val compatible = calculatedCompatible ?: gap?.compatible ?: 0
    val itemsUsed = calculatedItemsUsed ?: gap?.wardrobeItemsUsed ?: 0
    val reason = calculatedReason?.takeIf { it.isNotBlank() } ?: gap?.reason ?: "Calculated from the available items in your wardrobe."
    val price = "Price unavailable"
    val category = runCatching { Category.valueOf(categoryName) }.getOrDefault(Category.OUTERWEAR)
    var wardrobeItems by remember { mutableStateOf<List<WardrobeItemEntity>>(emptyList()) }
    val appContext = LocalContext.current
    LaunchedEffect(Unit) { wardrobeItems = DataGraph.get(appContext).wardrobeRepository.getAvailableItems() }
    val combinations = buildCombinations(wardrobeItems, category, title).take(6)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("‹  Back", modifier = Modifier.weight(1f).clickable { onBack() }); Text("Purchase Analysis", style = MaterialTheme.typography.titleLarge); Text("⋯", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End) } }
        item { Column { Text("AI suggestion", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${title} for your wardrobe", style = MaterialTheme.typography.headlineMedium) } }
        item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Column {
            RemoteProductImage(category, title, Modifier.fillMaxWidth().height(190.dp), when (category) { Category.SHOES -> "👞"; Category.BOTTOM -> "👖"; Category.ETHNIC_WEAR -> "🥻"; else -> "🧥" })
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(title, style = MaterialTheme.typography.titleLarge); Text("AI wardrobe recommendation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text(price, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } } }
        item { Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) { Text("✓  AI VERDICT: BUY", Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge) } }
        item { AnalysisCard("WARDROBE COMPATIBILITY", "Works with $compatible available items", "Those matches are based on $itemsUsed available wardrobe items analyzed for this recommendation.") }
        item { AnalysisCard("OUTFIT PAYOFF", "+$newOutfits outfit combinations", "This is the number of complete outfit combinations this item can add using your current wardrobe.") }
        item { Text("Outfit combinations from your wardrobe", style = MaterialTheme.typography.titleMedium) }
        if (combinations.isEmpty()) item { Text("Add clothing photos to see real outfit combinations here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else combinations.forEachIndexed { index, combination -> item { CombinationCard("Look ${index + 1}", combination) } }
        item { AnalysisCard("PRICE STATUS", "Price not verified", "Add a product link or connect a retailer source to compare a live price. No estimate is shown.") }
        item { AnalysisCard("WHY BUY THIS", if (newOutfits > 0) "Fills a real wardrobe gap" else "Does not create a complete outfit yet", reason) }
        item { Text("Similar alternatives", style = MaterialTheme.typography.titleMedium) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AlternativeCard("${compatible} wardrobe matches", "+$newOutfits outfits", Modifier.weight(1f))
            AlternativeCard("${itemsUsed} items analyzed", "Live price needed", Modifier.weight(1f))
        } }
        item { Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("+  Add to plan") } }
    }
}

private fun buildCombinations(items: List<WardrobeItemEntity>, gapCategory: Category, title: String): List<List<WardrobeItemEntity>> {
    val photographed = items.filter { it.isAvailable && !it.imageUri.isNullOrBlank() }
    val tops = photographed.filter { it.category == Category.TOP || (gapCategory == Category.ETHNIC_WEAR && it.category == Category.ETHNIC_WEAR) }
    val bottoms = photographed.filter { it.category == Category.BOTTOM }
    val shoes = photographed.filter { it.category == Category.SHOES }
    val layers = photographed.filter { it.category == Category.OUTERWEAR }
    if (tops.isEmpty() || bottoms.isEmpty() || shoes.isEmpty()) return emptyList()
    return tops.flatMap { top -> bottoms.flatMap { bottom -> shoes.flatMap { shoe ->
        val base = listOf(top, bottom, shoe)
        if (gapCategory == Category.OUTERWEAR && layers.isNotEmpty()) layers.map { base + it } else listOf(base)
    } } }.sortedByDescending { combo -> combo.sumOf { matchScore(it, title, gapCategory) } }
        .distinctBy { combo -> combo.joinToString("|") { it.id.toString() } }
}

private fun matchScore(item: WardrobeItemEntity, title: String, gapCategory: Category): Int {
    val words = title.lowercase().split(Regex("[^a-z0-9]+" )).filter { it.length > 2 }
    val searchable = listOfNotNull(item.name, item.subcategory, item.color, item.style, item.material).joinToString(" ").lowercase()
    return words.count { searchable.contains(it) } * 4 + when {
        gapCategory == item.category -> 3
        gapCategory == Category.ETHNIC_WEAR && item.category == Category.TOP -> 1
        else -> 0
    }
}

@Composable private fun CombinationCard(title: String, items: List<WardrobeItemEntity>) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleSmall); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { items.forEach { item -> Column(Modifier.weight(1f)) { GapImage(item.imageUri, Modifier.fillMaxWidth().height(105.dp)); Text(item.name, maxLines = 1, style = MaterialTheme.typography.labelSmall) } } } } } }

@Composable private fun GapImage(path: String?, modifier: Modifier) { val bitmap = remember(path) { path?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } }; if (bitmap != null) Image(bitmap.asImageBitmap(), "Wardrobe item", modifier, contentScale = ContentScale.Crop) else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) }

@Composable private fun AnalysisCard(label: String, title: String, body: String) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(12.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodySmall) } } }
@Composable private fun AlternativeCard(name: String, detail: String, modifier: Modifier) { Card(modifier) { Column(Modifier.padding(10.dp)) { Box(Modifier.fillMaxWidth().height(58.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("✓", style = MaterialTheme.typography.titleLarge) }; Spacer(Modifier.height(6.dp)); Text(name, style = MaterialTheme.typography.labelMedium); Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable private fun RemoteProductImage(category: Category, title: String, modifier: Modifier, fallback: String) {
    val normalizedTitle = title.lowercase()
    val url = when (category) {
        Category.SHOES -> if (normalizedTitle.contains("loafer")) "https://mir-s3-cdn-cf.behance.net/project_modules/2800_opt_1/b9470a68365529.60d4c7e6a66f1.jpg" else "https://fixthephoto.com/blog/images/uikit_slider/how-to-take-photos-for-poshmark-fixthephoto-product-editing-before_1664717961_wh960.jpg"
        Category.BOTTOM -> "https://images.squarespace-cdn.com/content/v1/5a402fd22aeba58d4a35a916/1659099686266-BH6CCAJYVDIZG7AU1WYP/Apparel-clothing-on-hanger-pants-folded.jpg"
        Category.ETHNIC_WEAR -> "https://i.pinimg.com/736x/8f/f3/96/8ff396c98ee40c32c4e4417708f40735.jpg"
        else -> "https://img01.ztat.net/article/spp-media-p1/a9cdedba8f714a5893805dd291906c8c/f21e4cc5f71b4b858d90f328749b7161.jpg?filter=packshot&imwidth=762"
    }
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) { bitmap = withContext(Dispatchers.IO) { runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull() } }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Example ${category.name.lowercase()} product photo", modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text(fallback, style = MaterialTheme.typography.displayLarge) }
}
