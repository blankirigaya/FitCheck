package com.fitcheck.app.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Production [AiRuntime] for Phase 1. Wraps Google AI Edge LiteRT-LM.
 *
 * Keeps a single [Engine] and a single [Conversation] for the lifetime of
 * the process. Conversation history is preserved between calls, which is
 * appropriate for an interactive AI Lab / stylist chat.
 *
 * Thread-safety:
 *   - Initialization is guarded by [initMutex] so concurrent callers observe
 *     a consistent state.
 *   - [snapshotRef] is an atomic reference, updated after every state
 *     transition so the UI can poll it without locking.
 */
class LiteRtLmRuntime(
    private val appContext: Context,
    private val preferredAccelerator: Accelerator = Accelerator.GPU
) : AiRuntime {

    private val initMutex = Mutex()
    private val snapshotRef = AtomicReference(
        RuntimeSnapshot(
            modelInfo = ModelInfo.Missing,
            initState = InitState.NotInitialized,
            lastInitMs = null,
            lastInferenceMs = null,
            lastFirstTokenMs = null,
            lastTokensEmitted = 0,
            lastError = null
        )
    )

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    init {
        Engine.setNativeMinLogSeverity(LogSeverity.INFO)
    }

    override fun snapshot(): RuntimeSnapshot = snapshotRef.get()

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        initMutex.withLock {
            val current = snapshotRef.get().initState
            if (current is InitState.Ready || current is InitState.Initializing) return@withContext

            val info = locateOrStageModel()
            updateSnapshot(
                snapshotRef.get().copy(
                    modelInfo = info,
                    initState = InitState.Initializing,
                    lastError = null
                )
            )

            try {
                val modelFile = File(info.absolutePath)
                require(modelFile.exists() && modelFile.length() > 0L) {
                    "Model file not found at ${info.absolutePath}. " +
                        "Drop a .litertlm file into assets/models/ or push to the app's files dir."
                }

                val backend = chooseBackend()
                Log.i(TAG, "initializing Gemma with preferred backend=${labelForBackend(backend)}")
                val engineConfig = EngineConfig(
                    modelPath = info.absolutePath,
                    backend = backend,
                    // Multimodal Gemma bundles have a separate vision
                    // executor. Leaving this unset makes text initialization
                    // succeed but every first image message fail with
                    // "Vision executor should not be null".
                    visionBackend = backend,
                    cacheDir = appContext.cacheDir.absolutePath
                )

                val newEngine = Engine(engineConfig)
                val initStart = System.nanoTime()
                newEngine.initialize()
                val initElapsedMs = (System.nanoTime() - initStart) / 1_000_000L

                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(
                        "You are the offline AI engine inside Fit Check, a personal wardrobe " +
                            "operating system. Be concise, calm, and practical."
                    ),
                    samplerConfig = SamplerConfig(
                        topK = 40,
                        topP = 0.9,
                        temperature = 0.7,
                        seed = 0
                    )
                )

                val newConversation = newEngine.createConversation(conversationConfig)
                Log.i(TAG, "conversation CREATED backend=${labelForBackend(backend)}")

                engine = newEngine
                conversation = newConversation

                val accelerator = labelForBackend(backend)
                updateSnapshot(
                    snapshotRef.get().copy(
                        initState = InitState.Ready(accelerator),
                        lastInitMs = initElapsedMs,
                        lastError = null
                    )
                )
            } catch (t: Throwable) {
                val reason = t.message ?: t::class.java.simpleName
                // Surface the failure reason in the runtime snapshot so the UI
                // can display an actionable error to the user instead of just
                // showing "Failed" with no detail.
                updateSnapshot(
                    snapshotRef.get().copy(
                        initState = InitState.Failed(reason),
                        lastError = reason
                    )
                )
                runCatching { engine?.close() }
                engine = null
                conversation = null
                throw AiException("Failed to initialize local Gemma runtime", t)
            }
        }
    }

    override fun generate(prompt: String): Flow<String> = flow {
        val convo = conversation ?: error("Engine not initialized. Call initialize() first.")
        Log.i(TAG, "inference START promptBytes=${prompt.length}")
        val started = System.nanoTime()
        var firstChunkNs: Long? = null
        var tokens = 0
        var emittedChars = 0L
        try {
            convo.sendMessageAsync(prompt)
                .map { message -> extractText(message) }
                .collect { chunk ->
                    if (chunk.isNotEmpty()) {
                        if (firstChunkNs == null) {
                            firstChunkNs = System.nanoTime()
                            Log.i(TAG, "token RECEIVED first chunkLen=${chunk.length}")
                        }
                        tokens += 1
                        emittedChars += chunk.length
                        if (tokens % 50 == 0) {
                            Log.i(TAG, "token COUNT tokens=$tokens emittedChars=$emittedChars")
                        }
                        emit(chunk)
                    }
                }
            Log.i(TAG, "inference STREAM COMPLETE tokens=$tokens emittedChars=$emittedChars")
        } catch (t: Throwable) {
            Log.e(TAG, "inference FAILED tokens=$tokens emittedChars=$emittedChars: ${t.message}", t)
            updateSnapshot(
                snapshotRef.get().copy(
                    lastTokensEmitted = tokens,
                    lastError = t.message ?: t::class.java.simpleName
                )
            )
            throw t
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        val firstTokenMs = firstChunkNs?.let { (it - started) / 1_000_000L }
        Log.i(TAG, "final RESPONSE BUILT tokens=$tokens totalMs=$elapsedMs firstTokenMs=$firstTokenMs")
        updateSnapshot(
            snapshotRef.get().copy(
                lastInferenceMs = elapsedMs,
                lastFirstTokenMs = firstTokenMs,
                lastTokensEmitted = tokens,
                lastError = null
            )
        )
    }.flowOn(Dispatchers.IO)

    override suspend fun analyzeImage(imagePath: String, prompt: String): String = withContext(Dispatchers.IO) {
        val convo = conversation ?: error("Engine not initialized. Call initialize() first.")
        val imageFile = File(imagePath)
        require(imageFile.exists() && imageFile.length() > 0L) { "Selected clothing photo could not be opened." }
        convo.sendMessageAsync(
            Contents.of(Content.ImageFile(imageFile.absolutePath), Content.Text(prompt))
        ).map { message -> extractText(message) }.foldToText()
    }

    /**
     * Walk a streamed [Message] and concatenate any text fragments. The
     * LiteRT-LM API exposes a Message as a list of typed [Content] blocks;
     * generated responses arrive as one or more `Content.Text` entries.
     */
    private fun extractText(message: Message): String {
        return message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        initMutex.withLock {
            Log.i(TAG, "conversation CLOSE start")
            runCatching { conversation?.close() }
            runCatching { engine?.close() }
            conversation = null
            engine = null
            Log.i(TAG, "conversation CLOSE done")
            updateSnapshot(
                snapshotRef.get().copy(
                    initState = InitState.NotInitialized,
                    lastInferenceMs = null
                )
            )
        }
    }

    override suspend fun probeModel() = withContext(Dispatchers.IO) {
        // Locate a model (but don't initialize native engine). Update snapshot
        // so the UI can enable/disable Initialize and display the model name.
        val info = locateOrStageModel()
        val next = if (info == ModelInfo.Missing) {
            val extModels = appContext.getExternalFilesDir("models")
            snapshotRef.get().copy(
                modelInfo = info,
                lastError = "No .litertlm model found. Expected $EXPECTED_MODEL_NAME in API path $extModels (pkg=${appContext.packageName}). See logcat FitCheck.LiteRtLm for resolve/open-test details."
            )
        } else {
            snapshotRef.get().copy(
                modelInfo = info,
                lastError = null
            )
        }
        updateSnapshot(next)
    }

    /**
     * NPU requires SoC-specific dispatch libraries that ship outside the
     * standard LiteRT-LM AAR. We try the preferred accelerator first, then
     * fall back through GPU → CPU on construction failure.
     */
    private fun chooseBackend(): Backend {
        val candidates: List<() -> Backend> = when (preferredAccelerator) {
            Accelerator.NPU -> listOf(
                { Backend.NPU(nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir) },
                { Backend.GPU() },
                { Backend.CPU() }
            )
            Accelerator.GPU -> listOf(
                { Backend.GPU() },
                { Backend.CPU() }
            )
            Accelerator.CPU -> listOf({ Backend.CPU() })
        }
        var lastError: Throwable? = null
        candidates.forEach { factory ->
            try {
                return factory()
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("No available backend")
    }

    private fun labelForBackend(backend: Backend): Accelerator = when (backend) {
        is Backend.NPU -> Accelerator.NPU
        is Backend.GPU -> Accelerator.GPU
        is Backend.CPU -> Accelerator.CPU
    }

    /**
     * Locate a `.litertlm` model using ONLY Android APIs (no hardcoded
     * `/sdcard` paths). Root-cause fix for "no .litertlm model found" when
     * the verified model already exists in the app-specific external dir but
     * `File.canRead()` reports false and `listFiles()` returns null.
     *
     * Order of preference (all API-derived):
     *   1. `context.getExternalFilesDir("models")` — direct API path
     *   2. `context.getExternalFilesDir(null)/models` — legacy equivalent
     *   3. `context.filesDir/models` (private internal storage, used in place)
     *   4. `assets/models` — bundled in APK as last resort (only case that copies)
     *
     * No copy of the 4.58 GB external file is made: external/internal hits are
     * used in place. `File.canRead()` is logged but NOT authoritative; the
     * authoritative check is actually opening the file with FileInputStream.
     */
    private fun locateOrStageModel(): ModelInfo {
        val pkg = appContext.packageName
        val extNull = appContext.getExternalFilesDir(null)
        val extModels = appContext.getExternalFilesDir("models")
        val internalDir = File(appContext.filesDir, "models").apply { mkdirs() }
        Log.i(TAG, "resolve pkg=$pkg filesDir=${appContext.filesDir.absolutePath} extNull=$extNull extModels=$extModels internalDir=${internalDir.absolutePath}")

        val apiDirs = listOfNotNull(
            extModels,
            extNull?.let { File(it, "models") }
        ).distinctBy { it.absolutePath }

        for (dir in apiDirs) {
            Log.i(TAG, "probe api-dir: ${dir.absolutePath} exists=${dir.exists()} isDir=${dir.isDirectory} canRead=${dir.canRead()} canWrite=${dir.canWrite()}")
            if (!dir.exists()) continue
            // 1) Direct expected-file check: does not depend on listFiles().
            val direct = File(dir, EXPECTED_MODEL_NAME)
            Log.i(TAG, "direct-check: ${direct.absolutePath} exists=${direct.exists()} len=${runCatching { direct.length() }.getOrNull()}")
            if (direct.exists() && direct.length() > 0L && testReadable(direct)) {
                Log.i(TAG, "found via direct-check: ${direct.absolutePath} size=${direct.length()}")
                return ModelInfo(
                    displayName = direct.name,
                    absolutePath = direct.absolutePath,
                    sizeBytes = direct.length()
                )
            }
            // 2) Directory listing check (may return null under scoped storage).
            val listed: Array<File>? = try {
                dir.listFiles()
            } catch (t: Throwable) {
                Log.w(TAG, "listFiles threw for ${dir.absolutePath}: ${t.message}")
                null
            }
            Log.i(TAG, "listFiles: dir=${dir.absolutePath} result=${if (listed == null) "null" else "${listed.size} entries"}")
            val best = pickBest(listed)
            if (best != null) {
                Log.i(TAG, "listed candidate: ${best.absolutePath} size=${best.length()}")
                if (testReadable(best)) {
                    return ModelInfo(
                        displayName = best.name,
                        absolutePath = best.absolutePath,
                        sizeBytes = best.length()
                    )
                }
                Log.w(TAG, "listed candidate not readable, continuing: ${best.absolutePath}")
            }
        }

        Log.i(TAG, "probe internal: ${internalDir.absolutePath} exists=${internalDir.exists()} canRead=${internalDir.canRead()}")
        val internalDirect = File(internalDir, EXPECTED_MODEL_NAME)
        if (internalDirect.exists() && internalDirect.length() > 0L && testReadable(internalDirect)) {
            Log.i(TAG, "found via internal direct-check: ${internalDirect.absolutePath} size=${internalDirect.length()}")
            return ModelInfo(
                displayName = internalDirect.name,
                absolutePath = internalDirect.absolutePath,
                sizeBytes = internalDirect.length()
            )
        }
        val internalListed = try { internalDir.listFiles() } catch (t: Throwable) {
            Log.w(TAG, "listFiles threw for internal: ${t.message}")
            null
        }
        val internalBest = pickBest(internalListed)
        if (internalBest != null && testReadable(internalBest)) {
            Log.i(TAG, "found at internal: ${internalBest.absolutePath} size=${internalBest.length()}")
            return ModelInfo(
                displayName = internalBest.name,
                absolutePath = internalBest.absolutePath,
                sizeBytes = internalBest.length()
            )
        }

        val assetNames = appContext.assets.list("models").orEmpty()
            .filter { it.endsWith(".litertlm") }
        val picked = assetNames.firstOrNull()
        if (picked != null) {
            val target = File(internalDir, picked)
            appContext.assets.open("models/$picked").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            Log.i(TAG, "staged from assets: ${target.absolutePath} size=${target.length()}")
            return ModelInfo(
                displayName = picked,
                absolutePath = target.absolutePath,
                sizeBytes = target.length()
            )
        }

        Log.w(TAG, "no .litertlm model found in any probed location")
        return ModelInfo.Missing
    }

    private fun pickBest(files: Array<File>?): File? {
        if (files == null) return null
        val litertlm = files.filter { it.isFile && it.name.endsWith(".litertlm") }
        if (litertlm.isEmpty()) return null
        // Prefer the verified E4B model if present, else largest file.
        litertlm.firstOrNull { it.name == EXPECTED_MODEL_NAME }?.let { return it }
        return litertlm.maxByOrNull { it.length() }
    }

    /**
     * Authoritative readability check: actually open the file with
     * FileInputStream and read one byte. Returns true only if open+read
     * succeeds. Logs size so logcat shows the real on-device size.
     */
    private fun testReadable(file: File): Boolean {
        return try {
            java.io.FileInputStream(file).use { fis ->
                val b = fis.read()
                Log.i(TAG, "open-test OK: ${file.absolutePath} size=${file.length()} firstByte=$b")
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "open-test FAILED: ${file.absolutePath}: ${t::class.java.simpleName}: ${t.message}")
            false
        }
    }

    private companion object {
        private const val TAG = "FitCheck.LiteRtLm"
        private const val EXPECTED_MODEL_NAME = "gemma-3n-E4B-it-int4.litertlm"
    }

    private fun updateSnapshot(next: RuntimeSnapshot) {
        snapshotRef.set(next)
    }

    private suspend fun Flow<String>.foldToText(): String {
        val out = StringBuilder()
        collect { out.append(it) }
        return out.toString()
    }
}
