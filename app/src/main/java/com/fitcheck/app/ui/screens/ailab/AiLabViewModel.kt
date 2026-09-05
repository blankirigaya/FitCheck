package com.fitcheck.app.ui.screens.ailab

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitcheck.app.ai.Accelerator
import com.fitcheck.app.ai.AiRuntime
import com.fitcheck.app.ai.AiRuntimeProvider
import com.fitcheck.app.ai.InitState
import com.fitcheck.app.ai.RuntimeSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiLabUiState(
    val snapshot: RuntimeSnapshot,
    val prompt: String = DEFAULT_PROMPT,
    val response: String = "",
    val isGenerating: Boolean = false
) {
    companion object {
        const val DEFAULT_PROMPT =
            "In one short sentence, what does a great everyday outfit look like?"
    }
}

class AiLabViewModel(application: Application) : AndroidViewModel(application) {

    private val runtime: AiRuntime = AiRuntimeProvider.get(application)

    companion object {
        private const val TAG = "FitCheck.AiLab"
    }

    private val _state = MutableStateFlow(AiLabUiState(snapshot = runtime.snapshot()))
    val state: StateFlow<AiLabUiState> = _state.asStateFlow()

    private var generateJob: Job? = null

    init {
        // Keep the UI state in sync with runtime state transitions.
        viewModelScope.launch {
            // Probe for a model file on startup so the UI can show whether
            // Initialize should be enabled and display the model name.
            runCatching { runtime.probeModel() }
            _state.update { it.copy(snapshot = runtime.snapshot()) }
        }
    }

    fun refreshSnapshot() {
        viewModelScope.launch {
            runCatching { runtime.probeModel() }
            _state.update { it.copy(snapshot = runtime.snapshot()) }
        }
    }

    fun initialize() {
        viewModelScope.launch {
            _state.update { it.copy(snapshot = runtime.snapshot().copy(initState = InitState.Initializing)) }
            try {
                runtime.initialize()
                val s = runtime.snapshot()
                Log.i(TAG, "initialize OK  accelerator=${(s.initState as? InitState.Ready)?.accelerator}  initMs=${s.lastInitMs}  model=${s.modelInfo.displayName}  size=${s.modelInfo.sizeBytes}")
            } catch (t: Throwable) {
                Log.e(TAG, "initialize FAILED: ${t.message}", t)
            } finally {
                _state.update { it.copy(snapshot = runtime.snapshot()) }
            }
        }
    }

    fun onPromptChange(value: String) {
        _state.update { it.copy(prompt = value) }
    }

    fun sendPrompt() {
        if (generateJob?.isActive == true) return
        val prompt = _state.value.prompt.trim()
        if (prompt.isEmpty()) return

        val snapshot = runtime.snapshot()
        if (snapshot.initState !is InitState.Ready) {
            _state.update {
                it.copy(
                    snapshot = snapshot.copy(
                        lastError = "Engine not ready. Tap Initialize first."
                    )
                )
            }
            return
        }

        _state.update { it.copy(response = "", isGenerating = true) }
        Log.i(TAG, "sendPrompt start  promptBytes=${prompt.length}")

        generateJob = viewModelScope.launch {
            runtime.generate(prompt)
                .catch { t ->
                    Log.e(TAG, "sendPrompt FAILED: ${t.message}", t)
                    _state.update {
                        it.copy(
                            isGenerating = false,
                            snapshot = runtime.snapshot().copy(
                                lastError = t.message ?: t::class.java.simpleName
                            )
                        )
                    }
                }
                .collect { chunk ->
                    _state.update { current ->
                        current.copy(response = current.response + chunk)
                    }
                }
            val s = runtime.snapshot()
            val decodeMs = ((s.lastInferenceMs ?: 0L) - (s.lastFirstTokenMs ?: 0L)).coerceAtLeast(1L)
            val tps = if (s.lastFirstTokenMs != null && s.lastTokensEmitted > 0) {
                s.lastTokensEmitted.toDouble() * 1000.0 / decodeMs
            } else 0.0
            Log.i(TAG, "sendPrompt done  totalMs=${s.lastInferenceMs}  firstTokenMs=${s.lastFirstTokenMs}  tokens=${s.lastTokensEmitted}  decodeTokPerSec=${"%.3f".format(tps)}")
            _state.update {
                it.copy(
                    isGenerating = false,
                    snapshot = runtime.snapshot()
                )
            }
        }
    }

    fun cancel() {
        generateJob?.cancel()
        generateJob = null
        _state.update { it.copy(isGenerating = false) }
    }

    fun shutdown() {
        viewModelScope.launch {
            runtime.close()
            _state.update { it.copy(snapshot = runtime.snapshot()) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        generateJob?.cancel()
    }

    @Suppress("unused")
    private val labelForState: String
        get() = when (val s = state.value.snapshot.initState) {
            InitState.NotInitialized -> "Not initialized"
            InitState.Initializing -> "Initializing…"
            is InitState.Ready -> "Ready · ${s.accelerator.label}"
            is InitState.Failed -> "Failed"
        }
}
