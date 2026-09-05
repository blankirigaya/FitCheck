package com.fitcheck.app.ui.screens.stylist

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitcheck.app.ai.AiRuntimeProvider
import com.fitcheck.app.data.DataGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class StylistMessage(val text: String, val fromUser: Boolean)

class AiStylistViewModel(app: Application) : AndroidViewModel(app) {
    private val runtime = AiRuntimeProvider.get(app)
    private val wardrobe = DataGraph.get(app).wardrobeRepository
    private val _messages = MutableStateFlow(listOf(StylistMessage("Tell me what you are dressing for and I’ll help you build a look from your wardrobe.", false)))
    val messages = _messages.asStateFlow()
    var isThinking by mutableStateOf(false); private set
    fun send(text: String) {
        val prompt = text.trim(); if (prompt.isEmpty() || isThinking) return
        _messages.value = _messages.value + StylistMessage(prompt, true); isThinking = true
        viewModelScope.launch {
            runCatching {
                if (runtime.snapshot().initState !is com.fitcheck.app.ai.InitState.Ready) runtime.initialize()
                val items = wardrobe.getAvailableItems().take(24).joinToString { "${it.name} (${it.category}, ${it.color ?: "unknown color"})" }
                val answer = runtime.generate("You are Fit Check AI Stylist. Be warm, concise, and practical. Reply in readable Markdown only, never JSON. Use short headings with **bold**, and bullet points with '-'. User says: $prompt. Wardrobe: $items. Suggest choices and customization; never invent wardrobe item IDs.").foldToString()
                _messages.value = _messages.value + StylistMessage(normalizeStylistResponse(answer), false)
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("✧ AI Stylist", style = MaterialTheme.typography.headlineLarge); Text("ACTIVE NOW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }
        Text("Your personal wardrobe assistant", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(messages) { message -> Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) { Surface(color = if (message.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 300.dp)) { MarkdownText(message.text, Modifier.padding(12.dp), if (message.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) } } }
            if (vm.isThinking) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Date night", "Make it casual", "What matches?").forEach { suggestion -> AssistChip(onClick = { input = suggestion }, label = { Text(suggestion) }) } }
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Message AI Stylist…") }, singleLine = true, shape = RoundedCornerShape(18.dp)); Button(onClick = { vm.send(input); input = "" }, enabled = input.isNotBlank() && !vm.isThinking, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("↑") } }
    }
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
