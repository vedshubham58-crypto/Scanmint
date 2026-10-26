package com.vedshubham.scanmint.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Reading, rendering and rebuilding PDFs on the phone. Call these off the main thread. */
object PdfTools {

    /** A4 width in points; pictures are fitted to this width. */
    private const val A4_WIDTH = 595f

    fun pageCount(file: File): Int = open(file) { it.pageCount }

    /** Draws one page as a picture [widthPx] wide, on white. */
    fun render(file: File, index: Int, widthPx: Int): Bitmap = open(file) { renderer ->
        renderPage(renderer, index, widthPx)
    }

    private fun renderPage(renderer: PdfRenderer, index: Int, widthPx: Int): Bitmap {
        renderer.openPage(index).use { page ->
            val w = widthPx.coerceAtLeast(1)
            val h = (w.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            return bitmap
        }
    }

    /** Page size in points (width, height). */
    private fun pageSize(renderer: PdfRenderer, index: Int): Pair<Float, Float> =
        renderer.openPage(index).use { it.width.toFloat() to it.height.toFloat() }

    private fun <T> open(file: File, block: (PdfRenderer) -> T): T {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { return block(it) }
        }
    }

    /** Settings for rebuilding pages: how sharp (pixels per point) and the JPEG quality. */
    enum class Quality(val pixelsPerPoint: Float, val jpeg: Int, val label: String) {
        HIGH(2.2f, 88, "High"),
        MEDIUM(1.6f, 75, "Medium"),
        SMALL(1.15f, 60, "Small"),
    }

    /**
     * Joins [sources] into one PDF at [target], page by page, re-drawing every page as a picture.
     * Also used with a single source to make a file smaller.
     */
    fun rebuild(sources: List<File>, target: File, quality: Quality, onProgress: (Float) -> Unit = {}) {
        val total = sources.sumOf { pageCount(it) }.coerceAtLeast(1)
        var done = 0
        val pages = mutableListOf<JpegPage>()
        for (source in sources) {
            open(source) { renderer ->
                for (i in 0 until renderer.pageCount) {
                    val (w, h) = pageSize(renderer, i)
                    val px = (w * quality.pixelsPerPoint).roundToInt().coerceIn(200, 2600)
                    val bitmap = renderPage(renderer, i, px)
                    pages += toPage(bitmap, quality.jpeg, w, h)
                    bitmap.recycle()
                    done++
                    onProgress(done.toFloat() / total)
                }
            }
        }
        writeAtomically(target) { JpegPdfWriter.write(pages, it) }
    }

    /** Makes a PDF from gallery pictures, one picture per A4-wide page. */
    fun fromImages(context: Context, uris: List<Uri>, target: File, onProgress: (Float) -> Unit = {}) {
        val pages = uris.mapIndexed { i, uri ->
            val bitmap = loadImage(context, uri, maxSide = 2400)
            val pageHeight = A4_WIDTH * bitmap.height / bitmap.width
            toPage(bitmap, 85, A4_WIDTH, pageHeight).also {
                bitmap.recycle()
                onProgress((i + 1f) / uris.size)
            }
        }
        writeAtomically(target) { JpegPdfWriter.write(pages, it) }
    }

    private fun toPage(bitmap: Bitmap, jpegQuality: Int, pageWidth: Float, pageHeight: Float): JpegPage {
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, it) }
        return JpegPage(bytes.toByteArray(), bitmap.width, bitmap.height, pageWidth, pageHeight)
    }

    /** Loads a picture, shrunk so its longest side is at most [maxSide], turned the right way up. */
    fun loadImage(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Could not read this picture")

        val rotation = runCatching {
            resolver.openInputStream(uri)!!.use { ExifInterface(it).rotationDegrees }
        }.getOrDefault(0)

        val longest = max(decoded.width, decoded.height)
        val scale = if (longest > maxSide) maxSide.toFloat() / longest else 1f
        if (rotation == 0 && scale == 1f) return flattenOnWhite(decoded)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        val turned = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (turned !== decoded) decoded.recycle()
        return flattenOnWhite(turned)
    }

    /** Transparent PNGs would turn black as JPEG, so paint them onto white first. */
    private fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.WHITE)
        android.graphics.Canvas(out).drawBitmap(bitmap, 0f, 0f, null)
        bitmap.recycle()
        return out
    }

    /** Writes to a temporary file first so a crash never leaves half a PDF behind. */
    private fun writeAtomically(target: File, write: (java.io.OutputStream) -> Unit) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.outputStream().buffered().use(write)
        if (target.exists()) target.delete()
        check(tmp.renameTo(target)) { "Could not save the file" }
    }
}
