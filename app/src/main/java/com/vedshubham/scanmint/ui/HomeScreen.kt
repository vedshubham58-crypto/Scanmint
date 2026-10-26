package com.vedshubham.scanmint.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedshubham.scanmint.AppViewModel
import com.vedshubham.scanmint.DocActions
import com.vedshubham.scanmint.ads.Ads
import com.vedshubham.scanmint.ads.BannerAd
import com.vedshubham.scanmint.data.Doc
import com.vedshubham.scanmint.data.formatAge
import com.vedshubham.scanmint.data.formatSize

enum class Tool(val label: String, val icon: ImageVector) {
    SCAN("Scan", Icons.Outlined.DocumentScanner),
    ID_CARD("ID card", Icons.Outlined.Badge),
    TO_TEXT("To text", Icons.Outlined.TextSnippet),
    PHOTOS("Photos", Icons.Outlined.PhotoLibrary),
    IMPORT_PDF("Import PDF", Icons.Outlined.PictureAsPdf),
    MERGE("Merge", Icons.Outlined.CallMerge),
    COMPRESS("Compress", Icons.Outlined.Compress),
    PRO("Pro", Icons.Filled.Star),
}

@Composable
fun HomeScreen(
    vm: AppViewModel,
    actions: DocActions,
    onTool: (Tool) -> Unit,
    onWatchVideo: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val docs by vm.store.docs.collectAsState()
    val adsReady by Ads.ready.collectAsState()
    val privacyNeeded by Ads.privacyOptionsRequired.collectAsState()
    var query by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf<Tool?>(null) }
    var showPro by remember { mutableStateOf(false) }
    var compressing by remember { mutableStateOf<Doc?>(null) }

    LaunchedEffect(Unit) { vm.recheckAdFree() }

    val shown = remember(docs, query) {
        if (query.isBlank()) docs else docs.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item { TopRow(onPro = { showPro = true }) }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("Search your documents") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                    )
                }
                item {
                    ToolGrid { tool ->
                        when (tool) {
                            Tool.MERGE, Tool.COMPRESS -> picking = tool
                            Tool.PRO -> showPro = true
                            else -> onTool(tool)
                        }
                    }
                }
                item {
                    Text(
                        if (query.isBlank()) "Recent" else "Results",
                        Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                if (shown.isEmpty()) {
                    item { EmptyState(searching = query.isNotBlank()) }
                }
                items(shown, key = { it.id }) { doc -> DocRow(vm, doc, actions, onCompress = { compressing = it }) }
            }

            ScanButton(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                onClick = { onTool(Tool.SCAN) },
            )
        }

        Box(Modifier.navigationBarsPadding()) {
            if (adsReady && !vm.adFree) BannerAd()
        }
    }

    picking?.let { tool ->
        PickDocsDialog(
            title = if (tool == Tool.MERGE) "Merge documents" else "Compress a document",
            docs = docs,
            store = vm.store,
            multiple = tool == Tool.MERGE,
            confirm = if (tool == Tool.MERGE) "Merge" else "Next",
            onDismiss = { picking = null },
            onPick = { picked ->
                if (tool == Tool.MERGE) vm.merge(picked) else compressing = picked.firstOrNull()
            },
        )
    }
    compressing?.let { doc ->
        CompressDialog(doc, onDismiss = { compressing = null }, onPick = { actions.compress(doc, it) })
    }
    if (showPro) {
        ProDialog(
            adFree = vm.adFree,
            privacyNeeded = privacyNeeded,
            onDismiss = { showPro = false },
            onWatchVideo = { showPro = false; onWatchVideo() },
            onPrivacy = onPrivacy,
        )
    }
}

@Composable
private fun TopRow(onPro: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Mint.brush),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.size(10.dp))
        Text(
            "ScanMint",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            "GO PRO",
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Mint.Gold)
                .clickable(onClick = onPro)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            color = Mint.GoldInk,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ToolGrid(onTool: (Tool) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Tool.entries.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tool ->
                    SoftCard(Modifier.weight(1f).clickable { onTool(tool) }) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                tool.icon,
                                null,
                                tint = if (tool == Tool.PRO) Color(0xFFE0A800) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(tool.label, fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocRow(vm: AppViewModel, doc: Doc, actions: DocActions, onCompress: (Doc) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    SoftCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { actions.open(doc) }) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            DocThumb(vm.store, doc, Modifier.size(44.dp, 56.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(doc.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${doc.pages} ${if (doc.pages == 1) "page" else "pages"} · ${formatSize(doc.bytes)} · ${formatAge(doc.created)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; actions.share(doc) })
                    DropdownMenuItem(text = { Text("Save to phone") }, onClick = { menu = false; actions.saveToPhone(doc) })
                    DropdownMenuItem(text = { Text("Copy text") }, onClick = { menu = false; actions.readText(doc) })
                    DropdownMenuItem(text = { Text("Compress") }, onClick = { menu = false; onCompress(doc) })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = true })
                }
            }
        }
    }
    if (renaming) RenameDialog(doc, onDismiss = { renaming = false }, onRename = { actions.rename(doc, it) })
    if (deleting) DeleteDialog(doc, onDismiss = { deleting = false }, onDelete = { actions.delete(doc) })
}

@Composable
private fun EmptyState(searching: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.DocumentScanner,
            null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (searching) "No documents with that name." else "No documents yet.\nTap the camera to scan your first page.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScanButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(78.dp)
            .shadow(14.dp, CircleShape, ambientColor = Mint.Deep, spotColor = Mint.Deep)
            .clip(CircleShape)
            .background(Mint.brush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PhotoCamera, "Scan", tint = Color.White, modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun ProDialog(
    adFree: Boolean,
    privacyNeeded: Boolean,
    onDismiss: () -> Unit,
    onWatchVideo: () -> Unit,
    onPrivacy: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Star, null, tint = Color(0xFFE0A800)) },
        title = { Text("ScanMint Pro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("• No ads, ever")
                Text("• Password-locked PDFs")
                Text("• Sign documents")
                Text("• Unlimited text copy")
                Spacer(Modifier.height(4.dp))
                Text("₹99 a month or ₹499 a year, with a 3-day free trial. Coming in the next update.", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (adFree) "Ads are off for now. Thanks for watching!" else "For now, watch one short video to turn ads off for 24 hours.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (privacyNeeded) {
                    TextButton(onClick = onPrivacy) { Text("Privacy settings") }
                }
            }
        },
        confirmButton = {
            if (!adFree) TextButton(onClick = onWatchVideo) { Text("Watch video") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
