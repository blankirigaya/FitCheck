package com.fitcheck.app.ai

/**
 * Public-facing AI abstraction. The rest of the application talks to this
 * interface and never to the underlying Gemma runtime directly.
 *
 * Implementations are responsible for:
 *   - locating or downloading a compatible model file
 *   - negotiating the best available hardware accelerator
 *   - initializing / disposing the native engine
 *   - running inference (streaming tokens when possible)
 */
interface AiRuntime {

    /** Lightweight, non-blocking snapshot of the current runtime state. */
    fun snapshot(): RuntimeSnapshot

    /**
     * Idempotently initialize the underlying engine. Safe to call multiple
     * times; subsequent calls return immediately when already initialized.
     *
     * @throws AiException if the model cannot be located, loaded, or the
     *   runtime cannot be initialized on this device.
     */
    suspend fun initialize()

    /**
     * Send a single prompt and stream model output tokens as they arrive.
     *
     * The flow completes when the model signals end-of-turn. Failures are
     * surfaced as exceptions inside the flow.
     */
    fun generate(prompt: String): kotlinx.coroutines.flow.Flow<String>

    /** Release native resources. Safe to call when already closed. */
    suspend fun close()

    /**
     * Probe the filesystem / assets for a .litertlm model without initializing
     * the native engine. Implementations should update their runtime
     * snapshot so callers can observe model presence.
     */
    suspend fun probeModel()

    /** Analyze one local image using the multimodal Gemma conversation. */
    suspend fun analyzeImage(imagePath: String, prompt: String): String
}

/** Immutable snapshot of the runtime's current state for UI rendering. */
data class RuntimeSnapshot(
    val modelInfo: ModelInfo,
    val initState: InitState,
    val lastInitMs: Long?,
    val lastInferenceMs: Long?,
    val lastFirstTokenMs: Long?,
    val lastTokensEmitted: Int,
    val lastError: String?
)

/** Describes a Gemma model file detected on disk. */
data class ModelInfo(
    val displayName: String,
    val absolutePath: String,
    val sizeBytes: Long
) {
    companion object {
        val Missing = ModelInfo(
            displayName = "No .litertlm model found",
            absolutePath = "",
            sizeBytes = 0L
        )
    }
}

/** Initialization lifecycle for the underlying engine. */
sealed interface InitState {
    data object NotInitialized : InitState
    data object Initializing : InitState
    data class Ready(val accelerator: Accelerator) : InitState
    data class Failed(val reason: String) : InitState
}

/** Hardware acceleration backend actually selected by the runtime. */
enum class Accelerator(val label: String) {
    CPU("CPU"),
    GPU("GPU"),
    NPU("NPU");

    companion object {
        fun fromLabel(label: String?): Accelerator = when (label?.uppercase()) {
            "GPU" -> GPU
            "NPU" -> NPU
            else -> CPU
        }
    }
}

/** Thrown by [AiRuntime] implementations for user-actionable failures. */
class AiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
