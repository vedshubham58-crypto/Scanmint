package com.vedshubham.scanmint

import com.vedshubham.scanmint.pdf.JpegPage
import com.vedshubham.scanmint.pdf.JpegPdfWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JpegPdfWriterTest {

    private fun page(bytes: Int) = JpegPage(ByteArray(bytes) { it.toByte() }, 10, 20, 595f, 841.89f)

    @Test
    fun writesHeaderAndEnd() {
        val pdf = String(JpegPdfWriter.toBytes(listOf(page(30))), Charsets.ISO_8859_1)
        assertTrue(pdf.startsWith("%PDF-1.4"))
        assertTrue(pdf.trimEnd().endsWith("%%EOF"))
        assertTrue(pdf.contains("/Count 1"))
        assertTrue(pdf.contains("/MediaBox [0 0 595 841.89]"))
    }

    @Test
    fun xrefOffsetsPointAtObjects() {
        val pdf = String(JpegPdfWriter.toBytes(listOf(page(50), page(70), page(5))), Charsets.ISO_8859_1)
        val startXref = pdf.substringAfter("startxref\n").substringBefore("\n").toInt()
        assertTrue(pdf.startsWith("xref", startXref))
        val lines = pdf.substring(startXref).lines()
        val size = lines[1].split(" ")[1].toInt()
        assertEquals(1 + 2 + 3 * 3, size)
        for (n in 1 until size) {
            val offset = lines[2 + n].substring(0, 10).toInt()
            assertTrue("object $n", pdf.startsWith("$n 0 obj", offset))
        }
    }

    @Test
    fun streamLengthMatchesJpeg() {
        val pdf = String(JpegPdfWriter.toBytes(listOf(page(123))), Charsets.ISO_8859_1)
        assertTrue(pdf.contains("/Length 123"))
    }
}
