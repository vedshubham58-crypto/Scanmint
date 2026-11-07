package com.vedshubham.scanmint.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** The user's saved signature, a transparent picture kept in the app's private folder. */
class Signatures(context: Context) {
    private val file = File(context.filesDir, "signature.png")

    fun load(): Bitmap? = if (file.exists()) BitmapFactory.decodeFile(file.path) else null

    fun save(bitmap: Bitmap) {
        val tmp = File(file.parentFile, "signature.png.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file.delete()
        tmp.renameTo(file)
    }
}
