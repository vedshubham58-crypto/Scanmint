package com.vedshubham.scanmint.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedshubham.scanmint.AppViewModel
import com.vedshubham.scanmint.DocActions
import com.vedshubham.scanmint.data.Doc
import com.vedshubham.scanmint.data.formatSize
import com.vedshubham.scanmint.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ViewerBg = Color(0xFF20262A)
private val BarBg = Color(0xFF151A1D)

@Composable
fun ViewerScreen(vm: AppViewModel, docId: String, actions: DocActions, onBack: () -> Unit) {
    val docs by vm.store.docs.collectAsState()
    val doc = docs.firstOrNull { it.id == docId } ?: return
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var compressing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(ViewerBg)) {
        Row(
            Modifier.fillMaxWidth().background(BarBg).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(doc.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${doc.pages} ${if (doc.pages == 1) "page" else "pages"} · ${formatSize(doc.bytes)}",
                    color = Color(0xFFAAB8B2),
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, "Rename", tint = Color.White) }
            IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.Delete, "Delete", tint = Color.White) }
        }

        Pages(vm, doc, Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth().background(BarBg).navigationBarsPadding().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BarAction(Icons.Outlined.Share, "Share") { actions.share(doc) }
            BarAction(Icons.Outlined.Download, "Save") { actions.saveToPhone(doc) }
            BarAction(Icons.Outlined.TextSnippet, "Copy text") { actions.readText(doc) }
            BarAction(Icons.Outlined.Compress, "Compress") { compressing = true }
        }
    }

    if (renaming) RenameDialog(doc, onDismiss = { renaming = false }, onRename = { actions.rename(doc, it) })
    if (deleting) DeleteDialog(doc, onDismiss = { deleting = false }, onDelete = { actions.delete(doc) })
    if (compressing) CompressDialog(doc, onDismiss = { compressing = false }, onPick = { actions.compress(doc, it) })
}

@Composable
private fun Pages(vm: AppViewModel, doc: Doc, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { (maxWidth - 32.dp).roundToPx() }.coerceIn(300, 1600)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(doc.pages) { index ->
                // Re-render when the file changes (for example after compressing).
                val page by produceState<Bitmap?>(null, doc.bytes, index, widthPx) {
                    value = withContext(Dispatchers.IO) {
                        runCatching { PdfTools.render(vm.store.fileOf(doc), index, widthPx) }.getOrNull()
                    }
                }
                val bitmap = page
                DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (bitmap != null) {
                                Modifier.aspectRatio(bitmap.width.toFloat() / bitmap.height)
                            } else {
                                Modifier.aspectRatio(1f / 1.414f)
                            },
                        )
                        .shadow(8.dp, RoundedCornerShape(2.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bitmap != null) {
                        Image(bitmap.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxSize())
                    } else {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Color.White)
            Text(label, color = Color(0xFFDDE6E2), fontSize = 11.sp)
        }
    }
}
