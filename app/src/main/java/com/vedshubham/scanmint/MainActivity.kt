package com.vedshubham.scanmint

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.vedshubham.scanmint.ads.Ads
import com.vedshubham.scanmint.data.Doc
import com.vedshubham.scanmint.ui.BusyCard
import com.vedshubham.scanmint.ui.HomeScreen
import com.vedshubham.scanmint.ui.ScanMintTheme
import com.vedshubham.scanmint.ui.TextScreen
import com.vedshubham.scanmint.ui.Tool
import com.vedshubham.scanmint.ui.ViewerScreen

/** What the scanner was opened for, so the result goes to the right place. */
enum class ScanKind(val label: String, val pageLimit: Int) {
    DOCUMENT("Scan", 50),
    ID_CARD("ID card", 2),
    TEXT("Text", 10),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Ads.setUp(this)
        setContent {
            ScanMintTheme { App(this) }
        }
    }
}

@Composable
private fun App(activity: Activity) {
    val vm: AppViewModel = viewModel()
    var scanKind by remember { mutableStateOf(ScanKind.DOCUMENT) }
    var exporting by remember { mutableStateOf<Doc?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data) ?: return@rememberLauncherForActivityResult
        if (scanKind == ScanKind.TEXT) {
            vm.readScan(scan.pages.orEmpty().map { it.imageUri })
        } else {
            val pdf = scan.pdf ?: return@rememberLauncherForActivityResult
            vm.saveScan(pdf.uri, pdf.pageCount, scanKind.label)
        }
    }

    fun startScan(kind: ScanKind) {
        scanKind = kind
        val format = if (kind == ScanKind.TEXT) {
            GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
        } else {
            GmsDocumentScannerOptions.RESULT_FORMAT_PDF
        }
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setPageLimit(kind.pageLimit)
            .setResultFormats(format)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener {
                vm.toast = "The scanner needs Google Play services. Please update it and try again."
            }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        vm.importImages(uris)
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPdf(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val doc = exporting
        exporting = null
        if (uri != null && doc != null) vm.exportTo(doc, uri)
    }

    val actions = remember(vm) {
        object : DocActions {
            override fun open(doc: Doc) { vm.route = Route.Viewer(doc.id) }
            // A full-screen ad runs first, then the share sheet or file picker opens.
            override fun share(doc: Doc) = withAd(activity, vm) {
                runCatching { activity.startActivity(vm.shareIntent(doc)) }
                    .onFailure { vm.toast = "Could not share this file" }
            }
            override fun saveToPhone(doc: Doc) = withAd(activity, vm) {
                exporting = doc
                exportLauncher.launch(AppViewModel.safeFileName(doc.name) + ".pdf")
            }
            override fun rename(doc: Doc, name: String) { vm.rename(doc, name) }
            override fun delete(doc: Doc) { vm.delete(doc) }
            override fun readText(doc: Doc) { vm.readDoc(doc) }
            override fun compress(doc: Doc, quality: com.vedshubham.scanmint.pdf.PdfTools.Quality) { vm.compress(doc, quality) }
        }
    }

    // A new document opens right away; the ad comes when the user saves or shares it.
    LaunchedEffect(vm.justSaved) {
        val doc = vm.justSaved ?: return@LaunchedEffect
        vm.justSaved = null
        vm.route = Route.Viewer(doc.id)
    }

    LaunchedEffect(vm.toast) {
        val message = vm.toast ?: return@LaunchedEffect
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        vm.toast = null
    }

    BackHandler(enabled = vm.route != Route.Home) { vm.route = Route.Home }

    when (val route = vm.route) {
        Route.Home -> HomeScreen(
            vm = vm,
            actions = actions,
            onTool = { tool ->
                when (tool) {
                    Tool.SCAN -> startScan(ScanKind.DOCUMENT)
                    Tool.ID_CARD -> startScan(ScanKind.ID_CARD)
                    Tool.TO_TEXT -> startScan(ScanKind.TEXT)
                    Tool.PHOTOS -> photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                    Tool.IMPORT_PDF -> pdfPicker.launch(arrayOf("application/pdf"))
                    Tool.MERGE, Tool.COMPRESS, Tool.PRO -> Unit // handled inside the home screen
                }
            },
            onWatchVideo = {
                val shown = Ads.showRewarded(activity) { vm.grantAdFreeDay() }
                if (!shown) vm.toast = "No video ready yet. Try again in a moment."
            },
            onPrivacy = { Ads.showPrivacyOptions(activity) },
        )
        is Route.Viewer -> {
            val doc = vm.store.docs.value.firstOrNull { it.id == route.id }
            if (doc == null) {
                LaunchedEffect(Unit) { vm.route = Route.Home }
            } else {
                ViewerScreen(vm = vm, docId = doc.id, actions = actions, onBack = { vm.route = Route.Home })
            }
        }
        is Route.Text -> TextScreen(title = route.title, text = route.text, onBack = { vm.route = Route.Home })
    }

    vm.busy?.let { BusyCard(it) }
}

/** Runs [action] after a full-screen ad when one is due, otherwise straight away. */
private fun withAd(activity: Activity, vm: AppViewModel, action: () -> Unit) {
    if (!vm.adDue()) {
        action()
        return
    }
    if (Ads.showInterstitial(activity, action)) vm.adShown()
}

/** Things you can do with a saved document, shared by the home list and the viewer. */
interface DocActions {
    fun open(doc: Doc)
    fun share(doc: Doc)
    fun saveToPhone(doc: Doc)
    fun rename(doc: Doc, name: String)
    fun delete(doc: Doc)
    fun readText(doc: Doc)
    fun compress(doc: Doc, quality: com.vedshubham.scanmint.pdf.PdfTools.Quality)
}
