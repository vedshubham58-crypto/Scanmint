package com.vedshubham.scanmint.ui

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vedshubham.scanmint.AppViewModel
import com.vedshubham.scanmint.data.Doc
import com.vedshubham.scanmint.pdf.PdfTools
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where the signature goes on a page, as fractions of the page size (0..1). */
data class SignPlacement(val page: Int, val centerX: Float, val centerY: Float, val width: Float)

/** Ink colour for signatures: dark blue, like a pen. */
private val Ink = Color(0xFF1A2A80)

/**
 * Put your signature on a page: pick the page, drag the signature into place,
 * pinch to resize, then save a signed copy.
 */
@Composable
fun SignScreen(vm: AppViewModel, doc: Doc, onBack: () -> Unit) {
    var signature by remember { mutableStateOf(vm.signatures.load()) }
    var drawing by remember { mutableStateOf(signature == null) }
    var page by remember { mutableStateOf(doc.pages - 1) } // signatures usually go on the last page
    var cx by remember { mutableStateOf(0.7f) }
    var cy by remember { mutableStateOf(0.85f) }
    var w by remember { mutableStateOf(0.35f) }

    Column(Modifier.fillMaxSize().background(Color(0xFF20262A))) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF151A1D)).statusBarsPadding().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Sign", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Drag to move · pinch to resize", color = Color(0xFFAAB8B2), fontSize = 12.sp)
            }
            TextButton(onClick = { drawing = true }) { Text("New signature", color = Mint.Glow) }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceIn(300, 1400)
            val bitmap by produceState<Bitmap?>(null, doc.bytes, page, widthPx) {
                value = withContext(Dispatchers.IO) {
                    runCatching { PdfTools.render(vm.store.fileOf(doc), page, widthPx) }.getOrNull()
                }
            }
            val shown = bitmap
            DisposableEffect(shown) { onDispose { shown?.recycle() } }
            if (shown == null) {
                CircularProgressIndicator(color = Mint.Glow)
            } else {
                val ratio = shown.width.toFloat() / shown.height
                val fitWidth = if (maxWidth / maxHeight > ratio) maxHeight * ratio else maxWidth
                BoxWithConstraints(Modifier.width(fitWidth).aspectRatio(ratio).background(Color.White)) {
                    Image(shown.asImageBitmap(), "Page ${page + 1}", Modifier.fillMaxSize())
                    val sig = signature
                    if (sig != null) {
                        val density = LocalDensity.current
                        val boxW = with(density) { maxWidth.toPx() }
                        val boxH = with(density) { maxHeight.toPx() }
                        val sigW = boxW * w
                        val sigH = sigW * sig.height / sig.width
                        Image(
                            sig.asImageBitmap(),
                            "Your signature",
                            Modifier
                                .offset { IntOffset((cx * boxW - sigW / 2).roundToInt(), (cy * boxH - sigH / 2).roundToInt()) }
                                .size(with(density) { sigW.toDp() }, with(density) { sigH.toDp() })
                                .border(1.dp, Mint.Green.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                .pointerInput(boxW, boxH) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        w = (w * zoom).coerceIn(0.1f, 0.9f)
                                        cx = (cx + pan.x / boxW).coerceIn(0f, 1f)
                                        cy = (cy + pan.y / boxH).coerceIn(0f, 1f)
                                    }
                                },
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(Color(0xFF151A1D)).navigationBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(enabled = page > 0, onClick = { page-- }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous page", tint = Color.White)
            }
            Text("Page ${page + 1} of ${doc.pages}", color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
            IconButton(enabled = page < doc.pages - 1, onClick = { page++ }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next page", tint = Color.White)
            }
            Spacer(Modifier.size(8.dp))
            Button(
                enabled = signature != null,
                onClick = { vm.sign(doc, SignPlacement(page, cx, cy, w)) },
                colors = ButtonDefaults.buttonColors(containerColor = Mint.Green),
            ) { Text("Save signed copy", fontWeight = FontWeight.Bold) }
        }
    }

    if (drawing) {
        SignaturePad(
            onDismiss = {
                drawing = false
                if (signature == null) onBack()
            },
            onSave = { bmp ->
                vm.signatures.save(bmp)
                signature = bmp
                drawing = false
            },
        )
    }
}

/** A white pad to draw your signature with a finger. */
@Composable
private fun SignaturePad(onDismiss: () -> Unit, onSave: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Sign here", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Use your finger. Your signature stays on this phone.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFCCD8D3), RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { current = listOf(it) },
                                onDrag = { change, _ -> current = current + change.position },
                                onDragEnd = {
                                    if (current.size > 1) strokes.add(current)
                                    current = emptyList()
                                },
                            )
                        },
                ) {
                    padSize = size
                    (strokes + listOf(current)).forEach { pts ->
                        if (pts.size > 1) drawPath(pathOf(pts), Ink, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { strokes.clear() }, modifier = Modifier.weight(1f)) { Text("Clear") }
                    Button(
                        enabled = strokes.isNotEmpty(),
                        onClick = { signatureBitmap(strokes, padSize)?.let(onSave) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Use this") }
                }
            }
        }
    }
}

private fun pathOf(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val p = points[i]
        quadraticTo(prev.x, prev.y, (prev.x + p.x) / 2, (prev.y + p.y) / 2)
    }
    lineTo(points.last().x, points.last().y)
}

/** Draws the strokes at high resolution on a transparent picture, trimmed to the ink. */
private fun signatureBitmap(strokes: List<List<Offset>>, pad: androidx.compose.ui.geometry.Size): Bitmap? {
    val all = strokes.flatten()
    if (all.isEmpty() || pad.width <= 0f) return null
    val scale = 3f
    val margin = 12f
    val minX = all.minOf { it.x } - margin
    val minY = all.minOf { it.y } - margin
    val maxX = all.maxOf { it.x } + margin
    val maxY = all.maxOf { it.y } + margin
    val width = ((maxX - minX) * scale).roundToInt().coerceAtLeast(1)
    val height = ((maxY - minY) * scale).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(0x1A, 0x2A, 0x80)
        style = Paint.Style.STROKE
        strokeWidth = (pad.width / 90f).coerceAtLeast(4f) * scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { pts ->
        val path = android.graphics.Path()
        pts.forEachIndexed { i, p ->
            val x = (p.x - minX) * scale
            val y = (p.y - minY) * scale
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }
    return bitmap
}
