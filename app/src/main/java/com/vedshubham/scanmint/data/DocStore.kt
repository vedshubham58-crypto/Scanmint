package com.vedshubham.scanmint.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A saved document. The PDF itself lives at [DocStore.fileOf]. */
data class Doc(
    val id: String,
    val name: String,
    val created: Long,
    val pages: Int,
    val bytes: Long,
)

/**
 * Keeps every document in the app's private folder, so nothing is uploaded anywhere.
 * The list (names, dates, page counts) is a small JSON file next to the PDFs.
 */
class DocStore(context: Context) {
    private val dir = File(context.filesDir, "docs").apply { mkdirs() }
    private val index = File(dir, "index.json")
    private val thumbs = File(context.cacheDir, "thumbs").apply { mkdirs() }

    private val _docs = MutableStateFlow(load())
    /** Newest first. */
    val docs: StateFlow<List<Doc>> = _docs.asStateFlow()

    fun fileOf(doc: Doc): File = File(dir, "${doc.id}.pdf")
    fun thumbOf(doc: Doc): File = File(thumbs, "${doc.id}_${doc.bytes}.jpg")

    /** A fresh file to write a new PDF into before calling [add]. */
    fun newFile(): File = File(dir, "${UUID.randomUUID()}.pdf")

    fun get(id: String): Doc? = _docs.value.firstOrNull { it.id == id }

    /** Registers a PDF already written to [file] (from [newFile]). */
    suspend fun add(file: File, name: String, pages: Int): Doc = withContext(Dispatchers.IO) {
        val doc = Doc(
            id = file.nameWithoutExtension,
            name = uniqueName(name.trim().ifEmpty { defaultName() }),
            created = System.currentTimeMillis(),
            pages = pages,
            bytes = file.length(),
        )
        update { listOf(doc) + it }
        doc
    }

    suspend fun rename(doc: Doc, name: String) = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isEmpty()) return@withContext
        update { list -> list.map { if (it.id == doc.id) it.copy(name = clean) else it } }
    }

    /** Call after the PDF behind [doc] was replaced, for example by compressing it. */
    suspend fun refresh(doc: Doc, pages: Int) = withContext(Dispatchers.IO) {
        val size = fileOf(doc).length()
        update { list -> list.map { if (it.id == doc.id) it.copy(pages = pages, bytes = size) else it } }
    }

    suspend fun delete(doc: Doc) = withContext(Dispatchers.IO) {
        fileOf(doc).delete()
        thumbs.listFiles { f -> f.name.startsWith(doc.id) }?.forEach { it.delete() }
        update { list -> list.filterNot { it.id == doc.id } }
    }

    private fun uniqueName(name: String): String {
        val taken = _docs.value.map { it.name.lowercase(Locale.ROOT) }.toSet()
        if (name.lowercase(Locale.ROOT) !in taken) return name
        var n = 2
        while ("$name ($n)".lowercase(Locale.ROOT) in taken) n++
        return "$name ($n)"
    }

    @Synchronized
    private fun update(change: (List<Doc>) -> List<Doc>) {
        val next = change(_docs.value)
        save(next)
        _docs.value = next
    }

    private fun load(): List<Doc> {
        if (!index.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(index.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Doc(o.getString("id"), o.getString("name"), o.getLong("created"), o.getInt("pages"), o.getLong("bytes"))
            }.filter { File(dir, "${it.id}.pdf").exists() }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<Doc>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("created", it.created)
                    .put("pages", it.pages)
                    .put("bytes", it.bytes),
            )
        }
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(array.toString())
        if (index.exists()) index.delete()
        tmp.renameTo(index)
    }

    companion object {
        fun defaultName(kind: String = "Scan"): String =
            "$kind " + SimpleDateFormat("d MMM yyyy, h.mm a", Locale.getDefault()).format(Date())
    }
}

/** "340 KB", "1.8 MB". */
fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

/** "today", "yesterday", "3 days ago", or a date. */
fun formatAge(time: Long, now: Long = System.currentTimeMillis()): String {
    val days = ((now - time) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 7 -> "$days days ago"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(time))
    }
}
