package com.vedshubham.scanmint.pdf

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** One page: a JPEG picture that fills a page of [pageWidth] x [pageHeight] points (1/72 inch). */
class JpegPage(
    val jpeg: ByteArray,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val pageWidth: Float,
    val pageHeight: Float,
)

/**
 * Writes a PDF whose pages are JPEG pictures, stored as-is (no re-encoding).
 * Android's own PdfDocument stores pictures uncompressed, which makes scans
 * several times bigger, so imports, merges and "compress" use this instead.
 */
object JpegPdfWriter {

    fun write(pages: List<JpegPage>, out: OutputStream) {
        require(pages.isNotEmpty()) { "A PDF needs at least one page" }
        val buf = CountingStream(out)
        val offsets = mutableListOf<Long>()

        fun obj(body: () -> Unit) {
            offsets += buf.count
            buf.ascii("${offsets.size} 0 obj\n")
            body()
            buf.ascii("\nendobj\n")
        }

        buf.ascii("%PDF-1.4\n")
        buf.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))

        // Objects 1 and 2 are the catalog and the page tree; each page then takes three objects.
        val pageIds = pages.indices.map { 3 + it * 3 }
        obj { buf.ascii("<< /Type /Catalog /Pages 2 0 R >>") }
        obj { buf.ascii("<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pages.size} >>") }

        pages.forEachIndexed { i, page ->
            val pageId = pageIds[i]
            val w = fmt(page.pageWidth)
            val h = fmt(page.pageHeight)
            obj {
                buf.ascii(
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $w $h] " +
                        "/Resources << /XObject << /Im$i ${pageId + 1} 0 R >> >> /Contents ${pageId + 2} 0 R >>",
                )
            }
            obj {
                buf.ascii(
                    "<< /Type /XObject /Subtype /Image /Width ${page.pixelWidth} /Height ${page.pixelHeight} " +
                        "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${page.jpeg.size} >>\nstream\n",
                )
                buf.write(page.jpeg)
                buf.ascii("\nendstream")
            }
            val content = "q $w 0 0 $h 0 0 cm /Im$i Do Q".toByteArray(Charsets.US_ASCII)
            obj {
                buf.ascii("<< /Length ${content.size} >>\nstream\n")
                buf.write(content)
                buf.ascii("\nendstream")
            }
        }

        val xref = buf.count
        buf.ascii("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { buf.ascii(String.format(java.util.Locale.US, "%010d 00000 n \n", it)) }
        buf.ascii("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        buf.flush()
    }

    fun toBytes(pages: List<JpegPage>): ByteArray =
        ByteArrayOutputStream().also { write(pages, it) }.toByteArray()

    private fun fmt(v: Float): String =
        if (v == v.toInt().toFloat()) v.toInt().toString() else "%.2f".format(java.util.Locale.US, v)

    private class CountingStream(private val out: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        override fun flush() = out.flush()

        fun ascii(s: String) = write(s.toByteArray(Charsets.US_ASCII))
    }
}
