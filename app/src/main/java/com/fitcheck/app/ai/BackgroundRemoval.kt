package com.fitcheck.app.ai

import android.graphics.BitmapFactory
import android.graphics.Bitmap.CompressFormat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Local foreground extraction for wardrobe photos. */
suspend fun removeBackground(sourcePath: String): String? = suspendCancellableCoroutine { continuation ->
    val source = BitmapFactory.decodeFile(sourcePath)
    if (source == null) {
        continuation.resume(null)
        return@suspendCancellableCoroutine
    }
    val segmenter = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
    )
    segmenter.process(InputImage.fromBitmap(source, 0))
        .addOnSuccessListener { result ->
            val foreground = result.foregroundBitmap
            val output = foreground?.let { File(sourcePath.substringBeforeLast('.') + "_cutout.png") }
            val saved = if (output != null) runCatching {
                output.outputStream().use { foreground!!.compress(CompressFormat.PNG, 100, it) }
                output.absolutePath
            }.getOrNull() else null
            source.recycle()
            segmenter.close()
            if (continuation.isActive) continuation.resume(saved)
        }
        .addOnFailureListener {
            source.recycle()
            segmenter.close()
            if (continuation.isActive) continuation.resume(null)
        }
}
