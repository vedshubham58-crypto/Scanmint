package com.vedshubham.scanmint

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vedshubham.scanmint.data.Doc
import com.vedshubham.scanmint.data.DocStore
import com.vedshubham.scanmint.data.Prefs
import com.vedshubham.scanmint.pdf.PdfTools
import com.vedshubham.scanmint.pdf.TextReader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Route {
    data object Home : Route
    data class Viewer(val id: String) : Route
    data class Text(val title: String, val text: String) : Route
    /** The Pro page. [reason] says which locked feature sent the user here; [back] is where Close goes. */
    data class Pro(val reason: String?, val back: Route) : Route
    data class Sign(val id: String) : Route
}

/** A password-locked copy is ready to share or save. */
data class LockedCopy(val file: File, val name: String, val target: com.vedshubham.scanmint.ui.LockedTarget)

/** A long job in progress, shown as a card over the screen. */
data class Busy(val message: String, val progress: Float? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = DocStore(app)
    val prefs = Prefs(app)
    val signatures = com.vedshubham.scanmint.data.Signatures(app)

    /** Set when a locked copy is ready; the activity opens the share sheet or file picker. */
    var lockedCopy by mutableStateOf<LockedCopy?>(null)

    /** Goes up after each successful save or share; the activity may then ask for a rating. */
    var successes by mutableStateOf(0)

    var route by mutableStateOf<Route>(Route.Home)
    var busy by mutableStateOf<Busy?>(null)
        private set
    var toast by mutableStateOf<String?>(null)
    var adFree by mutableStateOf(prefs.adFree)
        private set

    /** Set after a new document is saved; the activity opens it. */
    var justSaved by mutableStateOf<Doc?>(null)

    private val context get() = getApplication<Application>()

    private fun job(message: String, work: suspend () -> Unit) {
        if (busy != null) return
        busy = Busy(message)
        viewModelScope.launch {
            try {
                work()
            } catch (e: Exception) {
                toast = e.message ?: "Something went wrong"
            } finally {
                busy = null
            }
        }
    }

    private fun progress(message: String): (Float) -> Unit = { p -> busy = Busy(message, p) }

    /** A scan from Google's scanner: copy its PDF into our folder. */
    fun saveScan(pdf: Uri, pages: Int, kind: String) = job("Saving…") {
        val file = store.newFile()
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(pdf).use { input ->
                requireNotNull(input) { "The scan could not be read" }
                file.outputStream().use { input.copyTo(it) }
            }
        }
        val count = withContext(Dispatchers.IO) { runCatching { PdfTools.pageCount(file) }.getOrDefault(pages) }
        justSaved = store.add(file, DocStore.defaultName(kind), count)
    }

    /** "To text" scans: read the words off the pictures and show them. */
    fun readScan(images: List<Uri>) = job("Reading text…") {
        val message = "Reading text…"
        val parts = mutableListOf<String>()
        images.forEachIndexed { i, uri ->
            val bitmap = withContext(Dispatchers.IO) { PdfTools.loadImage(context, uri, 2000) }
            parts += TextReader.read(bitmap).trim()
            bitmap.recycle()
            busy = Busy(message, (i + 1f) / images.size)
        }
        showText("Scanned text", parts.filter { it.isNotEmpty() }.joinToString("\n\n"))
    }

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        job("Making PDF…") {
            val file = store.newFile()
            withContext(Dispatchers.IO) { PdfTools.fromImages(context, uris, file, progress("Making PDF…")) }
            justSaved = store.add(file, DocStore.defaultName("Photos"), uris.size)
        }
    }

    fun importPdf(uri: Uri) = job("Importing…") {
        val file = store.newFile()
        val name = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "The file could not be opened" }
                file.outputStream().use { input.copyTo(it) }
            }
            displayName(uri)?.removeSuffix(".pdf")?.removeSuffix(".PDF")
        }
        val pages = withContext(Dispatchers.IO) {
            runCatching { PdfTools.pageCount(file) }.getOrElse {
                file.delete()
                error("This PDF is locked or damaged")
            }
        }
        justSaved = store.add(file, name ?: DocStore.defaultName("PDF"), pages)
    }

    fun merge(docs: List<Doc>) {
        if (docs.size < 2) {
            toast = "Pick at least two documents"
            return
        }
        job("Merging…") {
            val file = store.newFile()
            withContext(Dispatchers.IO) {
                PdfTools.rebuild(docs.map(store::fileOf), file, PdfTools.Quality.HIGH, progress("Merging…"))
            }
            justSaved = store.add(file, "Merged ${docs.first().name}", docs.sumOf { it.pages })
        }
    }

    fun compress(doc: Doc, quality: PdfTools.Quality) = job("Compressing…") {
        val before = doc.bytes
        val tmp = File(store.fileOf(doc).parentFile, "${doc.id}.small")
        withContext(Dispatchers.IO) {
            PdfTools.rebuild(listOf(store.fileOf(doc)), tmp, quality, progress("Compressing…"))
        }
        val after = tmp.length()
        if (after >= before) {
            tmp.delete()
            toast = "This file is already as small as it gets"
            return@job
        }
        withContext(Dispatchers.IO) {
            store.fileOf(doc).delete()
            check(tmp.renameTo(store.fileOf(doc))) { "Could not save the file" }
        }
        store.refresh(doc, doc.pages)
        toast = "Now ${com.vedshubham.scanmint.data.formatSize(after)} (was ${com.vedshubham.scanmint.data.formatSize(before)})"
    }

    fun readDoc(doc: Doc) = job("Reading text…") {
        val text = TextReader.readPdf(store.fileOf(doc), progress("Reading text…"))
        showText(doc.name, text)
    }

    private fun showText(title: String, text: String) {
        if (text.isBlank()) {
            toast = "No text found. Try a clearer, brighter photo."
        } else {
            route = Route.Text(title, text)
        }
    }

    /** Saves a new copy of [doc] with the user's signature drawn where they placed it. */
    fun sign(doc: Doc, at: com.vedshubham.scanmint.ui.SignPlacement) = job("Signing…") {
        val signature = withContext(Dispatchers.IO) { signatures.load() } ?: error("Draw your signature first")
        val file = store.newFile()
        withContext(Dispatchers.IO) {
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
            PdfTools.rebuild(
                listOf(store.fileOf(doc)), file, PdfTools.Quality.HIGH, progress("Signing…"),
                overlay = { page, canvas, width, height ->
                    if (page == at.page) {
                        val w = width * at.width
                        val h = w * signature.height / signature.width
                        val left = width * at.centerX - w / 2
                        val top = height * at.centerY - h / 2
                        canvas.drawBitmap(signature, null, android.graphics.RectF(left, top, left + w, top + h), paint)
                    }
                },
            )
        }
        signature.recycle()
        justSaved = store.add(file, "${doc.name} (signed)", doc.pages)
        toast = "Signed copy saved"
    }

    /** Makes a password-locked copy of [doc] for sharing or saving. The copy in the app stays open. */
    fun lock(doc: Doc, password: String, target: com.vedshubham.scanmint.ui.LockedTarget) = job("Locking…") {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val out = withContext(Dispatchers.IO) {
            dir.listFiles()?.forEach { it.delete() }
            val name = safeFileName(doc.name) + " (locked).pdf"
            File(dir, name).also {
                PdfTools.rebuild(
                    listOf(store.fileOf(doc)), it, PdfTools.Quality.HIGH, progress("Locking…"),
                    lock = com.vedshubham.scanmint.pdf.PdfLock(password),
                )
            }
        }
        lockedCopy = LockedCopy(out, doc.name + " (locked)", target)
    }

    fun rename(doc: Doc, name: String) = viewModelScope.launch { store.rename(doc, name) }

    fun delete(doc: Doc) = viewModelScope.launch {
        store.delete(doc)
        if (route == Route.Viewer(doc.id)) route = Route.Home
        toast = "Deleted"
    }

    /** A share sheet for the PDF, named after the document. */
    fun shareIntent(doc: Doc): Intent {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val copy = File(dir, safeFileName(doc.name) + ".pdf")
        store.fileOf(doc).copyTo(copy, overwrite = true)
        return shareIntent(copy, doc.name)
    }

    /** A share sheet for a file already in the share folder. */
    fun shareIntent(copy: File, name: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", copy)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share $name")
    }

    /** Saves a copy where the user chose (Downloads, Drive, …). */
    fun exportTo(doc: Doc, target: Uri) = exportFile(store.fileOf(doc), target)

    fun exportFile(source: File, target: Uri) = job("Saving…") {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(target).use { out ->
                requireNotNull(out) { "Could not save there" }
                source.inputStream().use { it.copyTo(out) }
            }
        }
        toast = "Saved"
        successes++
    }

    /**
     * True when a full-screen ad may show before Save or Share (or after a new scan).
     * At most one per [AD_GAP_MS], so sharing to several chats doesn't mean an ad each time.
     */
    fun adDue(): Boolean {
        if (adFree || com.vedshubham.scanmint.billing.Billing.pro.value) return false
        return System.currentTimeMillis() - prefs.lastAdAt >= AD_GAP_MS
    }

    fun adShown() {
        prefs.lastAdAt = System.currentTimeMillis()
    }

    fun grantAdFreeDay() {
        prefs.adFreeUntil = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
        adFree = true
        toast = "No ads for the next 24 hours"
    }

    fun recheckAdFree() {
        adFree = prefs.adFree
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    companion object {
        const val AD_GAP_MS = 60_000L

        fun safeFileName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().take(80).ifEmpty { "ScanMint" }
    }
}
