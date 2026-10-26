package com.vedshubham.scanmint.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vedshubham.scanmint.Busy
import com.vedshubham.scanmint.data.Doc
import com.vedshubham.scanmint.data.DocStore
import com.vedshubham.scanmint.data.formatSize
import com.vedshubham.scanmint.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small picture of a document's first page, cached on disk. */
@Composable
fun DocThumb(store: DocStore, doc: Doc, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, doc.id, doc.bytes) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val cached = store.thumbOf(doc)
                if (cached.exists()) {
                    BitmapFactory.decodeFile(cached.path)
                } else {
                    PdfTools.render(store.fileOf(doc), 0, 180).also { bmp ->
                        cached.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                    }
                }
            }.getOrNull()
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Outlined.Description, null, tint = Color(0xFFB8C7C1))
        }
    }
}

/** Card over the screen while a long job runs. */
@Composable
fun BusyCard(busy: Busy) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp).width(240.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val p = busy.progress
                if (p == null) {
                    CircularProgressIndicator()
                } else {
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(16.dp))
                Text(busy.message, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun RenameDialog(doc: Doc, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(doc.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onRename(name); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DeleteDialog(doc: Doc, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete “${doc.name}”?") },
        text = { Text("This removes it from your phone. It cannot be undone.") },
        confirmButton = { TextButton(onClick = { onDelete(); onDismiss() }) { Text("Delete", color = Color(0xFFD03A3A)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CompressDialog(doc: Doc, onDismiss: () -> Unit, onPick: (PdfTools.Quality) -> Unit) {
    var choice by remember { mutableStateOf(PdfTools.Quality.MEDIUM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make smaller") },
        text = {
            Column {
                Text("Now ${formatSize(doc.bytes)}. Smaller files share faster on WhatsApp and email.")
                Spacer(Modifier.height(8.dp))
                PdfTools.Quality.entries.forEach { q ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == q, onClick = { choice = q })
                        Text(
                            when (q) {
                                PdfTools.Quality.HIGH -> "High quality"
                                PdfTools.Quality.MEDIUM -> "Medium (recommended)"
                                PdfTools.Quality.SMALL -> "Smallest file"
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(choice); onDismiss() }) { Text("Compress") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Choose documents: several for merging, or one for compressing. */
@Composable
fun PickDocsDialog(
    title: String,
    docs: List<Doc>,
    store: DocStore,
    multiple: Boolean,
    confirm: String,
    onDismiss: () -> Unit,
    onPick: (List<Doc>) -> Unit,
) {
    val picked = remember { mutableStateListOf<Doc>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (docs.isEmpty()) {
                Text("Scan or import a document first.")
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (multiple) Text("Pages are joined in the order you tick them.", style = MaterialTheme.typography.bodySmall)
                    docs.forEach { doc ->
                        val index = picked.indexOf(doc)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Checkbox(checked = index >= 0, onCheckedChange = { on ->
                                if (!multiple) picked.clear()
                                if (on) picked.add(doc) else picked.remove(doc)
                            })
                            DocThumb(store, doc, Modifier.size(32.dp, 40.dp))
                            Column(Modifier.weight(1f)) {
                                Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${doc.pages} pages · ${formatSize(doc.bytes)}" + if (multiple && index >= 0) " · #${index + 1}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = if (multiple) picked.size >= 2 else picked.size == 1,
                onClick = { onPick(picked.toList()); onDismiss() },
            ) { Text(if (multiple && picked.size >= 2) "$confirm (${picked.size})" else confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Rounded white card used on the light screens. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) { content() }
}
