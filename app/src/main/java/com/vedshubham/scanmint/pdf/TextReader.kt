package com.vedshubham.scanmint.pdf

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Reads the words on scanned pages, on the phone. English and Hindi (Devanagari)
 * are both tried, and whichever finds more text wins for that page.
 */
object TextReader {
    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val hindi by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }

    suspend fun read(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val english = runCatching { latin.process(image).await().text }.getOrDefault("")
        val devanagari = runCatching { hindi.process(image).await().text }.getOrDefault("")
        val hindiChars = devanagari.count { it in 'ऀ'..'ॿ' }
        return if (hindiChars > english.length / 4) devanagari else english
    }

    /** Reads every page of a PDF; pages are separated by a blank line. */
    suspend fun readPdf(file: File, onProgress: (Float) -> Unit = {}): String {
        val count = withContext(Dispatchers.IO) { PdfTools.pageCount(file) }
        val parts = mutableListOf<String>()
        for (i in 0 until count) {
            val bitmap = withContext(Dispatchers.IO) { PdfTools.render(file, i, 1800) }
            parts += read(bitmap).trim()
            bitmap.recycle()
            onProgress((i + 1f) / count)
        }
        return parts.filter { it.isNotEmpty() }.joinToString("\n\n")
    }
}
