package com.fitcheck.app.ai

import android.content.Context

/**
 * Factory and lightweight singleton holder for the [AiRuntime].
 *
 * Kept intentionally simple for Phase 1 — when multiple implementations
 * arrive (CPU-only fallback, NPU on Pixel, remote preview, etc.) the
 * selection logic will live here.
 */
object AiRuntimeProvider {

    @Volatile private var instance: AiRuntime? = null

    fun get(context: Context): AiRuntime {
        return instance ?: synchronized(this) {
            instance ?: LiteRtLmRuntime(
                appContext = context.applicationContext,
                preferredAccelerator = Accelerator.GPU
            ).also { instance = it }
        }
    }
}
