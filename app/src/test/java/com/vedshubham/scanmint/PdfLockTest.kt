package com.vedshubham.scanmint

import com.vedshubham.scanmint.billing.Billing
import com.vedshubham.scanmint.pdf.JpegPage
import com.vedshubham.scanmint.pdf.JpegPdfWriter
import com.vedshubham.scanmint.pdf.PdfLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLockTest {

    private val fixedId = ByteArray(16) { it.toByte() }

    @Test
    fun rc4MatchesKnownVector() {
        assertEquals("bbf316e8d940af0ad3", PdfLock.hex(PdfLock.rc4("Key".toByteArray(), "Plaintext".toByteArray())))
    }

    /** Values checked against poppler (pdftoppm) and pypdf, which open the file with this password. */
    @Test
    fun passwordCheckValuesStayTheSame() {
        val lock = PdfLock("secret 123", fixedId)
        assertEquals("022bcd88baee95db4d75bf97dd4f20c9b771845258db6cf8f661b33177863750", PdfLock.hex(lock.ownerKey))
        assertEquals("a867b02ce8c4c855995e5851f220e296", PdfLock.hex(lock.userKey).take(32))
    }

    @Test
    fun lockedPdfHasEncryptDictionaryAndHidesContent() {
        val jpeg = ByteArray(200) { (it * 7).toByte() }
        val page = JpegPage(jpeg, 10, 20, 595f, 842f)
        val pdf = String(JpegPdfWriter.toBytes(listOf(page), PdfLock("pass", fixedId)), Charsets.ISO_8859_1)
        assertTrue(pdf.startsWith("%PDF-1.6"))
        assertTrue(pdf.contains("/Filter /Standard /V 4 /R 4"))
        assertTrue(pdf.contains("/CFM /AESV2"))
        assertTrue(pdf.contains("/Encrypt 6 0 R"))
        assertTrue(pdf.contains("/ID [<000102030405060708090a0b0c0d0e0f>"))
        assertFalse("page drawing must be encrypted", pdf.contains("/Im0 Do"))
        // 16-byte IV + data padded up to the next 16 bytes.
        assertTrue(pdf.contains("/Length ${16 + (jpeg.size / 16 + 1) * 16}"))
    }

    @Test
    fun unlockedPdfIsUnchanged() {
        val page = JpegPage(ByteArray(10), 10, 20, 595f, 842f)
        val pdf = String(JpegPdfWriter.toBytes(listOf(page)), Charsets.ISO_8859_1)
        assertTrue(pdf.startsWith("%PDF-1.4"))
        assertFalse(pdf.contains("/Encrypt"))
        assertTrue(pdf.contains("/Im0 Do"))
    }

    @Test
    fun onlyPlainLettersAllowedInPasswords() {
        assertTrue(PdfLock.isSupported("Abc 123 !@#"))
        assertFalse(PdfLock.isSupported("पासवर्ड"))
    }

    @Test
    fun trialPeriodsReadAsDays() {
        assertEquals(3, Billing.periodDays("P3D"))
        assertEquals(7, Billing.periodDays("P1W"))
        assertEquals(30, Billing.periodDays("P1M"))
        assertEquals(365, Billing.periodDays("P1Y"))
    }
}
