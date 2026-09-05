package com.fitcheck.app.ui.screens.stylist

import android.app.Application
import android.graphics.BitmapFactory
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitcheck.app.ai.AiRuntimeProvider
import com.fitcheck.app.data.DataGraph
import com.fitcheck.app.data.UserProfilePreferences
import com.fitcheck.app.data.local.entity.WardrobeItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class StylistMessage(val text: String, val fromUser: Boolean, val itemIds: List<Long> = emptyList(), val attachment: String? = null)

class AiStylistViewModel(app: Application) : AndroidViewModel(app) {
    private val runtime = AiRuntimeProvider.get(app)
    private val wardrobe = DataGraph.get(app).wardrobeRepository
    private val _messages = MutableStateFlow(listOf(StylistMessage("Tell me what you are dressing for and I’ll help you build a look from your wardrobe.", false)))
    val messages = _messages.asStateFlow()
    var isThinking by mutableStateOf(false); private set
    fun send(text: String, attachment: String? = null) {
        val prompt = text.trim(); if (prompt.isEmpty() || isThinking) return
        _messages.value = _messages.value + StylistMessage(if (attachment == null) prompt else prompt, true, attachment = attachment); isThinking = true
        viewModelScope.launch {
            runCatching {
                if (runtime.snapshot().initState !is com.fitcheck.app.ai.InitState.Ready) runtime.initialize()
                val availableItems = wardrobe.getAvailableItems()
                val items = availableItems.take(24).joinToString { "${it.name} (${it.category}, ${it.color ?: "unknown color"})" }
                val profile = UserProfilePreferences.read(getApplication())
                val photoContext = attachment?.let { runCatching { runtime.analyzeImage(it, "Identify the clothing item in this photo. Return concise plain text with name, category, color, material, style, and outfit pairing notes.") }.getOrDefault("Photo attached; describe it from the visible image if possible.") } ?: "No photo attached."
                val answer = runtime.generate("You are Fit Check AI Stylist. Be warm, concise, and practical. Reply in readable Markdown only, never JSON. Use short headings with **bold**, and bullet points with '-'. User says: $prompt. Wardrobe: $items. Attached photo analysis: $photoContext. User context: age=${profile?.age ?: "unknown"}, gender=${profile?.gender ?: "unknown"}, profession=${profile?.profession ?: "unknown"}. Use it respectfully and do not stereotype. Suggest choices and customization; never invent wardrobe item IDs.").foldToString()
                val recommendationPhotos = availableItems.filter { it.imageUri != null }.sortedWith(compareBy { it.category.ordinal }).take(4).map { it.id }
                _messages.value = _messages.value + StylistMessage(normalizeStylistResponse(answer), false, recommendationPhotos)
            }.onFailure { _messages.value = _messages.value + StylistMessage(it.message ?: "I couldn’t reach the local stylist engine.", false) }
            isThinking = false
        }
    }
}

private fun normalizeStylistResponse(raw: String): String {
    val cleaned = raw.replace("```json", "").replace("```", "").trim()
    runCatching {
        val json = JSONObject(cleaned)
        val out = StringBuilder()
        json.optString("explanation").takeIf { it.isNotBlank() }?.let { out.append("**Stylist suggestion**\n\n").append(it).append("\n") }
        json.optJSONArray("criteria")?.let { array -> out.append("\n**What works**\n"); for (i in 0 until array.length()) out.append("- ").append(array.optString(i)).append("\n") }
        json.optString("advice").takeIf { it.isNotBlank() }?.let { out.append("\n**Customization**\n\n").append(it) }
        if (out.isNotBlank()) return out.toString().trim()
    }
    return cleaned.replace("{", "").replace("}", "").replace("\\\"", "").trim()
        .ifBlank { "I couldn’t format that suggestion. Try asking about a specific occasion." }
}

@Composable
fun AiStylistScreen(vm: AiStylistViewModel = viewModel()) {
    val messages by vm.messages.collectAsStateWithLifecycle(); var input by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<String?>(null) }
    var attachmentLabel by remember { mutableStateOf<String?>(null) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showWardrobePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraFilePath by remember { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) { attachment = cameraFilePath; attachmentLabel = "Camera photo" } }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "stylist/${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
            cameraFilePath = file.absolutePath
            cameraUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); cameraUri?.let(camera::launch)
        } else Toast.makeText(context, "Camera permission is required", Toast.LENGTH_SHORT).show()
    }
    fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) requestCamera.launch(Manifest.permission.CAMERA)
        else requestCamera.launch(Manifest.permission.CAMERA)
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("✧ AI Stylist", style = MaterialTheme.typography.headlineLarge); Text("ACTIVE NOW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }
        Text("Your personal wardrobe assistant", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(messages) { message -> Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) { Surface(color = if (message.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 320.dp)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { if (message.attachment != null) StylistLocalImage(message.attachment, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))); MarkdownText(if (message.attachment != null) "📷  Clothing photo attached" else message.text, Modifier, if (message.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface); if (message.itemIds.isNotEmpty()) StylistRecommendationPhotos(message.itemIds) } } } }
            if (vm.isThinking) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Date night", "Make it casual", "What matches?").forEach { suggestion -> AssistChip(onClick = { input = suggestion }, label = { Text(suggestion) }) } }
        if (attachmentLabel != null) Text("Attached: $attachmentLabel  ×", modifier = Modifier.clickable { attachment = null; attachmentLabel = null }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                OutlinedButton(onClick = { showAttachMenu = true }, contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(18.dp)) { Text("+") }
                DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                    DropdownMenuItem(text = { Text("Choose from wardrobe") }, onClick = { showAttachMenu = false; showWardrobePicker = true })
                    DropdownMenuItem(text = { Text("Take a camera photo") }, onClick = { showAttachMenu = false; openCamera() })
                }
            }
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Message AI Stylist…") }, singleLine = true, shape = RoundedCornerShape(18.dp))
            Button(onClick = { vm.send(input, attachment); input = ""; attachment = null; attachmentLabel = null }, enabled = input.isNotBlank() && !vm.isThinking, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("↑") }
        }
    }
    if (showWardrobePicker) WardrobePhotoPicker(onDismiss = { showWardrobePicker = false }, onSelect = { item -> attachment = item.imageUri; attachmentLabel = item.name; showWardrobePicker = false })
}

@Composable
private fun StylistRecommendationPhotos(itemIds: List<Long>) {
    val items by DataGraph.get(LocalContext.current).wardrobeRepository.observeAvailableItems().collectAsStateWithLifecycle(initialValue = emptyList())
    val photos = itemIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
    if (photos.isNotEmpty()) {
        Text("From your wardrobe", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { photos.forEach { item -> Column(Modifier.weight(1f)) { StylistLocalImage(item.imageUri, Modifier.fillMaxWidth().height(82.dp)); Text(item.name, maxLines = 1, style = MaterialTheme.typography.labelSmall) } } }
    }
}

@Composable
private fun WardrobePhotoPicker(onDismiss: () -> Unit, onSelect: (WardrobeItemEntity) -> Unit) {
    val items by DataGraph.get(LocalContext.current).wardrobeRepository.observeAvailableItems().collectAsStateWithLifecycle(initialValue = emptyList())
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose wardrobe photo") }, text = {
        if (items.isEmpty()) Text("Your wardrobe has no available photos yet.")
        else LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.heightIn(max = 390.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().clickable { onSelect(item) }) { Column {
                    StylistLocalImage(item.imageUri, Modifier.fillMaxWidth().height(105.dp))
                    Column(Modifier.padding(7.dp)) { Text(item.name, maxLines = 1, style = MaterialTheme.typography.labelLarge); Text(item.category.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun StylistLocalImage(uri: String?, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) { bitmap = uri?.let { runCatching { if (it.startsWith("content:")) context.contentResolver.openInputStream(Uri.parse(it)).use { input -> BitmapFactory.decodeStream(input) } else BitmapFactory.decodeFile(it) }.getOrNull() } }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Clothing photo", modifier, contentScale = ContentScale.Crop) else Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant) {}
}

@Composable private fun MarkdownText(value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color) {
    val styled = buildAnnotatedString {
        value.lines().forEachIndexed { index, line ->
            if (index > 0) append("\n")
            val content = line.removePrefix("* ").removePrefix("- ")
            if (line.startsWith("* ") || line.startsWith("- ")) append("• ")
            var cursor = 0
            Regex("\\*\\*([^*]+)\\*\\*").findAll(content).forEach { match -> append(content.substring(cursor, match.range.first)); withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }; cursor = match.range.last + 1 }
            append(content.substring(cursor))
        }
    }
    Text(styled, modifier, color = color, style = MaterialTheme.typography.bodyLarge)
}

private suspend fun kotlinx.coroutines.flow.Flow<String>.foldToString(): String { val text = StringBuilder(); collect { text.append(it) }; return text.toString() }
