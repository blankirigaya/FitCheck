package com.fitcheck.app.ui.screens.wardrobe

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch

class WardrobeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DataGraph.get(app).wardrobeRepository
    private val runtime = AiRuntimeProvider.get(app)
    val items = repo.observeAllItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun add(item: WardrobeItemEntity) = viewModelScope.launch { repo.insertItem(item) }
    fun analyzeAndAdd(item: WardrobeItemEntity, onResult: (String?) -> Unit) = viewModelScope.launch {
        runCatching {
            if (runtime.snapshot().initState !is InitState.Ready) runtime.initialize()
            val source = item.imageUri ?: error("A clothing photo is required.")
            val localPath = copyToPrivateStorage(Uri.parse(source))
            val raw = runtime.analyzeImage(localPath, """
                Look at this clothing photo. Return ONLY JSON with keys name, category (TOP, BOTTOM, SHOES, ACCESSORY), subcategory, color, material, fit, style, formality (1-5). Identify the single main clothing item.
            """.trimIndent())
            val attributes = ClothingVisionParser.parse(raw) ?: error("Gemma could not identify this clothing photo. Try a clearer photo.")
            repo.insertItem(item.copy(imageUri = localPath, name = attributes.name, category = attributes.category, subcategory = attributes.subcategory, color = attributes.color, material = attributes.material, fit = attributes.fit, style = attributes.style, formality = attributes.formality))
            onResult(null)
        }.onFailure { onResult(it.message ?: "Could not analyze clothing photo") }
    }
    private fun copyToPrivateStorage(uri: Uri): String {
        val target = File(getApplication<Application>().filesDir, "wardrobe/${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
        getApplication<Application>().contentResolver.openInputStream(uri).use { input -> requireNotNull(input) { "Selected photo cannot be read." }; target.outputStream().use { output -> input.copyTo(output) } }
        return target.absolutePath
    }
    fun delete(item: WardrobeItemEntity) = viewModelScope.launch { repo.deleteItem(item) }
}

@Composable
fun WardrobeScreen(onItemClick: (Long) -> Unit = {}, vm: WardrobeViewModel = viewModel()) {
    val items by vm.items.collectAsStateWithLifecycle(); var showAdd by remember { mutableStateOf(false) }
    Scaffold(floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Outlined.Add, "Add item") } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("WARDROBE", style = MaterialTheme.typography.displaySmall); Text("${items.size} items · local only") }
            items(items, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().clickable { onItemClick(item.id) }) { Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LocalImage(item.imageUri, Modifier.size(82.dp)); Column(Modifier.weight(1f)) { Text(item.name, style = MaterialTheme.typography.titleMedium); Text(item.category.name); Text(item.color ?: "Color not set") }; IconButton(onClick = { vm.delete(item) }) { Icon(Icons.Outlined.Delete, "Delete") }
                } }
            }
        }
    }
    if (showAdd) AddItemDialog({ showAdd = false }) { item -> vm.analyzeAndAdd(item) { error -> if (error == null) showAdd = false } }
}

@Composable
fun WardrobeItemDetailScreen(itemId: Long, onBack: () -> Unit, vm: WardrobeViewModel = viewModel()) {
    val items by vm.items.collectAsStateWithLifecycle(); val item = items.firstOrNull { it.id == itemId }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("‹  Item details", style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable { onBack() })
        if (item == null) Text("Item not found") else { LocalImage(item.imageUri, Modifier.fillMaxWidth().height(240.dp)); Text(item.name, style = MaterialTheme.typography.headlineMedium); Text(item.category.name)
            Detail("COLOR", item.color); Detail("MATERIAL", item.material); Detail("FIT", item.fit); Detail("SIZE", item.size); Detail("PRICE", item.purchasePrice?.let { "₹${"%.0f".format(it)}" }); Detail("TIMES WORN", item.wearCount.toString()); Detail("LAUNDRY", item.laundryStatus.name) }
    }
}

@Composable private fun Detail(label: String, value: String?) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, style = MaterialTheme.typography.labelMedium); Text(value ?: "Not set") } }

@Composable private fun AddItemDialog(onDismiss: () -> Unit, onAdd: (WardrobeItemEntity) -> Unit) {
    var name by remember { mutableStateOf("") }; var color by remember { mutableStateOf("") }; var size by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }; var imageUri by remember { mutableStateOf<String?>(null) }; var category by remember { mutableStateOf(Category.TOP) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imageUri = it?.toString() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add clothing") }, text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Button(onClick = { picker.launch("image/*") }) { Text(if (imageUri == null) "Add clothing photo (required)" else "Photo selected") }; OutlinedTextField(name, { name = it }, label = { Text("Optional name — Gemma will identify it") }, singleLine = true); OutlinedTextField(size, { size = it }, label = { Text("Size") }, singleLine = true); OutlinedTextField(price, { price = it }, label = { Text("Cost") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Category.values().forEach { Button(onClick = { category = it }) { Text(it.name.take(3)) } } }
    } }, confirmButton = { Button(enabled = imageUri != null, onClick = { onAdd(WardrobeItemEntity(name = name.ifBlank { "Analyzing…" }.trim(), category = category, size = size.ifBlank { null }, purchasePrice = price.toDoubleOrNull(), imageUri = imageUri)) }) { Text("Analyze & add") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun LocalImage(uri: String?, modifier: Modifier) {
    val context = LocalContext.current; var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) { bitmap = uri?.let { runCatching { if (it.startsWith("content:")) context.contentResolver.openInputStream(Uri.parse(it)).use { input -> BitmapFactory.decodeStream(input) } else BitmapFactory.decodeFile(it) }.getOrNull() } }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Clothing photo", modifier, contentScale = ContentScale.Crop) else Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant) {}
}
