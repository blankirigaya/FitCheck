package com.fitcheck.app.ui.screens.ailab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitcheck.app.ai.Accelerator
import com.fitcheck.app.ai.InitState

@Composable
fun AiLabScreen(
    viewModel: AiLabViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "AI Lab",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Local Gemma · offline",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        StatusCard(
            initState = snapshot.initState,
            modelName = snapshot.modelInfo.displayName,
            modelPath = snapshot.modelInfo.absolutePath,
            modelSizeBytes = snapshot.modelInfo.sizeBytes,
            lastInitMs = snapshot.lastInitMs,
            lastInferenceMs = snapshot.lastInferenceMs,
            lastFirstTokenMs = snapshot.lastFirstTokenMs,
            lastTokensEmitted = snapshot.lastTokensEmitted,
            lastError = snapshot.lastError
        )

        Spacer(Modifier.height(16.dp))

        ControlRow(
            initState = snapshot.initState,
            isGenerating = state.isGenerating,
            modelSizeBytes = snapshot.modelInfo.sizeBytes,
            onInitialize = viewModel::initialize,
            onShutdown = viewModel::shutdown,
            onRefresh = viewModel::refreshSnapshot
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Prompt",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(AiLabUiState.DEFAULT_PROMPT) },
            enabled = !state.isGenerating,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = viewModel::sendPrompt,
                enabled = !state.isGenerating && snapshot.initState is InitState.Ready,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Send")
            }
            OutlinedButton(
                onClick = viewModel::cancel,
                enabled = state.isGenerating,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.Cancel, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Stop")
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Response",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        ResponseCard(text = state.response, isStreaming = state.isGenerating)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun StatusCard(
    initState: InitState,
    modelName: String,
    modelPath: String,
    modelSizeBytes: Long,
    lastInitMs: Long?,
    lastInferenceMs: Long?,
    lastFirstTokenMs: Long?,
    lastTokensEmitted: Int,
    lastError: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        StatusRow(label = "Engine", value = stateLabel(initState))
        StatusRow(label = "Accelerator", value = acceleratorLabel(initState))
        StatusRow(label = "Model", value = modelName)
        StatusRow(label = "Path", value = modelPath.ifEmpty { "—" }, monospace = true)
        StatusRow(label = "Size", value = humanSize(modelSizeBytes))
        StatusRow(label = "Init time", value = lastInitMs?.let { "${it} ms" } ?: "—")
        StatusRow(label = "First token", value = lastFirstTokenMs?.let { "${it} ms" } ?: "—")
        StatusRow(label = "Tokens emitted", value = lastTokensEmitted.takeIf { it > 0 }?.toString() ?: "—")
        StatusRow(label = "Total inference", value = lastInferenceMs?.let { "${it} ms" } ?: "—")
        if (lastInferenceMs != null && lastTokensEmitted > 0 && lastFirstTokenMs != null) {
            val decodeMs = (lastInferenceMs - lastFirstTokenMs).coerceAtLeast(1L)
            val tps = lastTokensEmitted.toDouble() * 1000.0 / decodeMs
            StatusRow(label = "Decode speed", value = String.format("%.2f tok/s", tps))
        }
        if (!lastError.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = lastError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, monospace: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontFamily = if (monospace) androidx.compose.ui.text.font.FontFamily.Monospace
                             else androidx.compose.ui.text.font.FontFamily.Default
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2
        )
    }
}

@Composable
private fun ControlRow(
    initState: InitState,
    isGenerating: Boolean,
    modelSizeBytes: Long,
    onInitialize: () -> Unit,
    onShutdown: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onInitialize,
            enabled = initState !is InitState.Initializing && initState !is InitState.Ready && !isGenerating && modelSizeBytes > 0L,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Outlined.Bolt, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Initialize")
        }
        OutlinedButton(
            onClick = onShutdown,
            enabled = initState is InitState.Ready && !isGenerating,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Outlined.Memory, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Shutdown")
        }
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Outlined.RestartAlt, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Refresh")
        }
    }
}

@Composable
private fun ResponseCard(text: String, isStreaming: Boolean) {
    val showEmpty = text.isEmpty() && !isStreaming
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.Top
    ) {
        when {
            showEmpty -> Text(
                text = "Response will stream here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            isStreaming && text.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "Thinking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> Box {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isStreaming) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }
}

private fun stateLabel(state: InitState): String = when (state) {
    InitState.NotInitialized -> "Not initialized"
    InitState.Initializing -> "Initializing…"
    is InitState.Ready -> "Ready"
    is InitState.Failed -> "Failed"
}

private fun acceleratorLabel(state: InitState): String = when (state) {
    is InitState.Ready -> when (state.accelerator) {
        Accelerator.CPU -> "CPU"
        Accelerator.GPU -> "GPU"
        Accelerator.NPU -> "NPU"
    }
    else -> "—"
}

private fun humanSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var idx = 0
    while (value >= 1024.0 && idx < units.lastIndex) {
        value /= 1024.0
        idx++
    }
    return String.format("%.1f %s", value, units[idx])
}
