package com.fitcheck.app.ui.screens.wardrobe

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
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
import com.fitcheck.app.data.DataGraph
import com.fitcheck.app.data.local.entity.Category
import com.fitcheck.app.data.local.entity.WardrobeItemEntity
import com.fitcheck.app.ai.AiRuntimeProvider
import com.fitcheck.app.ai.ClothingVisionParser
import com.fitcheck.app.ai.InitState
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class WardrobeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DataGraph.get(app).wardrobeRepository
    private val runtime = AiRuntimeProvider.get(app)
    val items = repo.observeAllItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun add(item: WardrobeItemEntity) = viewModelScope.launch { repo.insertItem(item) }
    fun analyzeAndAdd(item: WardrobeItemEntity, onResult: (String?) -> Unit) = viewModelScope.launch {
        runCatching {
            val result = withContext(Dispatchers.IO) {
                if (runtime.snapshot().initState !is InitState.Ready) runtime.initialize()
                val source = item.imageUri ?: error("A clothing photo is required.")
                val localPath = copyToPrivateStorage(Uri.parse(source))
                val raw = runtime.analyzeImage(localPath, """
                    Look at this clothing photo. Return ONLY JSON with keys name, category (TOP, BOTTOM, SHOES, ACCESSORY), subcategory, color, material, fit, style, formality (1-5). Identify the single main clothing item.
                """.trimIndent())
                val attributes = ClothingVisionParser.parse(raw) ?: error("Gemma could not identify this clothing photo. Try a clearer photo.")
                repo.insertItem(item.copy(imageUri = localPath, name = attributes.name, category = item.category, subcategory = attributes.subcategory, color = attributes.color, material = attributes.material, fit = attributes.fit, style = attributes.style, formality = attributes.formality))
                true
            }
            if (result) onResult(null)
        }.onFailure { onResult(it.message ?: "Could not analyze clothing photo") }
    }
    private fun copyToPrivateStorage(uri: Uri): String {
        val target = File(getApplication<Application>().filesDir, "wardrobe/${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
        getApplication<Application>().contentResolver.openInputStream(uri).use { input -> requireNotNull(input) { "Selected photo cannot be read." }; target.outputStream().use { output -> input.copyTo(output) } }
        return target.absolutePath
    }
    fun delete(item: WardrobeItemEntity) = viewModelScope.launch { repo.deleteItem(item) }
    fun update(item: WardrobeItemEntity) = viewModelScope.launch { repo.updateItem(item) }
}

@Composable
fun WardrobeScreen(onItemClick: (Long) -> Unit = {}, vm: WardrobeViewModel = viewModel()) {
    val items by vm.items.collectAsStateWithLifecycle(); var showAdd by remember { mutableStateOf(false) }; var analyzing by remember { mutableStateOf(false) }; var analysisError by remember { mutableStateOf<String?>(null) }; var filter by remember { mutableStateOf("ALL") }
    val visible = items.filter { filter == "ALL" || it.category.name == filter }
    Scaffold(floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Outlined.Add, "Add item") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Wardrobe", style = MaterialTheme.typography.headlineLarge); IconButton(onClick = {}) { Text("⌕", style = MaterialTheme.typography.headlineMedium) } }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("ALL", "TOP", "BOTTOM", "SHOES", "OUTERWEAR", "ACCESSORY").forEach { value -> FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(value.lowercase().replaceFirstChar { it.uppercase() }) }) } }
            Text("${visible.size} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyVerticalGrid(columns = GridCells.Fixed(2), state = rememberLazyGridState(), contentPadding = PaddingValues(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                items(visible, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth().clickable { onItemClick(item.id) }, shape = MaterialTheme.shapes.large) { Column {
                        LocalImage(item.imageUri, Modifier.fillMaxWidth().height(150.dp)); Column(Modifier.padding(9.dp)) { Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1); Text("Worn ${item.wearCount}x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } }
                }
            }
        }
    }
    if (showAdd) AddItemDialog(onDismiss = { if (!analyzing) showAdd = false }, analyzing = analyzing, error = analysisError) { item -> analysisError = null; analyzing = true; vm.analyzeAndAdd(item) { error -> analyzing = false; analysisError = error; if (error == null) showAdd = false } }
}

@Composable
fun WardrobeItemDetailScreen(itemId: Long, onBack: () -> Unit, vm: WardrobeViewModel = viewModel()) {
    val items by vm.items.collectAsStateWithLifecycle(); val item = items.firstOrNull { it.id == itemId }; var editing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("‹  Back", modifier = Modifier.clickable { onBack() }); if (item != null) TextButton(onClick = { editing = true }) { Text("Edit") } }
        if (item == null) Text("Item not found") else { LocalImage(item.imageUri, Modifier.fillMaxWidth().height(250.dp)); Text(item.name, style = MaterialTheme.typography.headlineLarge); Text(item.brand ?: item.subcategory ?: item.category.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpecCard("COLOR", item.color, Modifier.weight(1f)); SpecCard("MATERIAL", item.material, Modifier.weight(1f)) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpecCard("FIT", item.fit, Modifier.weight(1f)); SpecCard("PRICE", item.purchasePrice?.let { "₹${"%.0f".format(it)}" }, Modifier.weight(1f)) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpecCard("SIZE", item.size, Modifier.weight(1f)); SpecCard("TIMES WORN", item.wearCount.toString(), Modifier.weight(1f)) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpecCard("LAST WORN", item.lastWorn?.let { "Recently" }, Modifier.weight(1f)); SpecCard("AVAILABILITY", if (item.isAvailable) "Clean" else "Unavailable", Modifier.weight(1f)) }
            Text("Outfit ideas with this", style = MaterialTheme.typography.titleMedium); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { IdeaCard("Campus Smart"); IdeaCard("Weekend Casual") }
        }
    }
    if (editing && item != null) EditItemDialog(item, onDismiss = { editing = false }) { vm.update(it); editing = false }
}

@Composable private fun Detail(label: String, value: String?) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, style = MaterialTheme.typography.labelMedium); Text(value ?: "Not set") } }

@Composable private fun SpecCard(label: String, value: String?, modifier: Modifier) { Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(10.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value ?: "Not set", style = MaterialTheme.typography.bodyMedium) } } }

@Composable private fun IdeaCard(name: String) { Card(Modifier.width(145.dp)) { Column(Modifier.padding(10.dp)) { Surface(Modifier.fillMaxWidth().height(72.dp), color = MaterialTheme.colorScheme.surface) {}; Spacer(Modifier.height(6.dp)); Text(name, style = MaterialTheme.typography.labelMedium) } } }

@Composable
private fun EditItemDialog(item: WardrobeItemEntity, onDismiss: () -> Unit, onSave: (WardrobeItemEntity) -> Unit) {
    var name by remember(item.id) { mutableStateOf(item.name) }; var color by remember(item.id) { mutableStateOf(item.color.orEmpty()) }; var material by remember(item.id) { mutableStateOf(item.material.orEmpty()) }; var fit by remember(item.id) { mutableStateOf(item.fit.orEmpty()) }; var style by remember(item.id) { mutableStateOf(item.style.orEmpty()) }; var brand by remember(item.id) { mutableStateOf(item.brand.orEmpty()) }; var size by remember(item.id) { mutableStateOf(item.size.orEmpty()) }; var price by remember(item.id) { mutableStateOf(item.purchasePrice?.toString().orEmpty()) }; var category by remember(item.id) { mutableStateOf(item.category) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit clothing") }, text = { Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true); OutlinedTextField(color, { color = it }, Modifier.fillMaxWidth(), label = { Text("Color") }, singleLine = true); OutlinedTextField(material, { material = it }, Modifier.fillMaxWidth(), label = { Text("Material") }, singleLine = true); OutlinedTextField(fit, { fit = it }, Modifier.fillMaxWidth(), label = { Text("Fit") }, singleLine = true); OutlinedTextField(style, { style = it }, Modifier.fillMaxWidth(), label = { Text("Style") }, singleLine = true); OutlinedTextField(brand, { brand = it }, Modifier.fillMaxWidth(), label = { Text("Brand") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(size, { size = it }, Modifier.weight(1f), label = { Text("Size") }, singleLine = true); OutlinedTextField(price, { price = it }, Modifier.weight(1f), label = { Text("Cost") }, singleLine = true) }
        Text("Category", style = MaterialTheme.typography.labelMedium); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { Category.values().forEach { value -> Button(onClick = { category = value }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 1.dp), colors = ButtonDefaults.buttonColors(containerColor = if (category == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (category == value) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)) { Text(value.name.take(3)) } } }
    } }, confirmButton = { Button(onClick = { onSave(item.copy(name = name.trim().ifBlank { item.name }, category = category, color = color.trim().ifBlank { null }, material = material.trim().ifBlank { null }, fit = fit.trim().ifBlank { null }, style = style.trim().ifBlank { null }, brand = brand.trim().ifBlank { null }, size = size.trim().ifBlank { null }, purchasePrice = price.toDoubleOrNull())) }) { Text("Save changes") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun AddItemDialog(onDismiss: () -> Unit, analyzing: Boolean, error: String?, onAdd: (WardrobeItemEntity) -> Unit) {
    var name by remember { mutableStateOf("") }; var color by remember { mutableStateOf("") }; var size by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }; var imageUri by remember { mutableStateOf<String?>(null) }; var category by remember { mutableStateOf(Category.TOP) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imageUri = it?.toString() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add clothing") }, text = { Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (imageUri == null) "Choose clothing photo (required)" else "Photo selected ✓") }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name (optional — Gemma identifies it)") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(size, { size = it }, Modifier.weight(1f), label = { Text("Size") }, singleLine = true); OutlinedTextField(price, { price = it }, Modifier.weight(1f), label = { Text("Cost") }, singleLine = true) }
        Text("Category hint (Gemma will verify)", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { Category.values().forEach { value ->
            val selected = category == value
            val label = when (value) { Category.TOP -> "Top"; Category.BOTTOM -> "Bottom"; Category.SHOES -> "Shoes"; Category.OUTERWEAR -> "Outer"; Category.ACCESSORY -> "Access" }
            Button(onClick = { category = value }, modifier = Modifier.width(68.dp), contentPadding = PaddingValues(horizontal = 2.dp), colors = ButtonDefaults.buttonColors(containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
        } }
        if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { Button(enabled = imageUri != null && !analyzing, onClick = { onAdd(WardrobeItemEntity(name = name.ifBlank { "Analyzing…" }.trim(), category = category, size = size.ifBlank { null }, purchasePrice = price.toDoubleOrNull(), imageUri = imageUri)) }) { Text(if (analyzing) "Analyzing…" else "Analyze & add") } }, dismissButton = { TextButton(enabled = !analyzing, onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun LocalImage(uri: String?, modifier: Modifier) {
    val context = LocalContext.current; var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) { bitmap = uri?.let { runCatching { if (it.startsWith("content:")) context.contentResolver.openInputStream(Uri.parse(it)).use { input -> BitmapFactory.decodeStream(input) } else BitmapFactory.decodeFile(it) }.getOrNull() } }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Clothing photo", modifier, contentScale = ContentScale.Crop) else Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant) {}
}
