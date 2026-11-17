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
import com.vedshubham.scanmint.billing.Billing
import com.vedshubham.scanmint.review.Rating
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.collectAsState
import com.vedshubham.scanmint.ui.LockDialog
import com.vedshubham.scanmint.ui.LockedTarget
import com.vedshubham.scanmint.ui.MenuItem
import com.vedshubham.scanmint.ui.PRIVACY_POLICY_URL
import com.vedshubham.scanmint.ui.ProScreen
import com.vedshubham.scanmint.ui.SignScreen
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
        Billing.setUp(this)
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
    var exporting by remember { mutableStateOf<java.io.File?>(null) }
    var locking by remember { mutableStateOf<Doc?>(null) }
    val pro by Billing.pro.collectAsState()
    val billingMessage by Billing.message.collectAsState()
    val privacyNeeded by Ads.privacyOptionsRequired.collectAsState()
    // True while the share sheet is open; coming back to the app counts as a successful share.
    var sharing by remember { mutableStateOf(false) }

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
        val file = exporting
        exporting = null
        if (uri != null && file != null) vm.exportFile(file, uri)
    }

    val actions = remember(vm) {
        object : DocActions {
            override fun open(doc: Doc) { vm.route = Route.Viewer(doc.id) }
            // A full-screen ad runs first, then the share sheet or file picker opens.
            override fun share(doc: Doc) = withAd(activity, vm) {
                runCatching { activity.startActivity(vm.shareIntent(doc)) }
                    .onSuccess { sharing = true }
                    .onFailure { vm.toast = "Could not share this file" }
            }
            override fun saveToPhone(doc: Doc) = withAd(activity, vm) {
                exporting = vm.store.fileOf(doc)
                exportLauncher.launch(AppViewModel.safeFileName(doc.name) + ".pdf")
            }
            override fun lock(doc: Doc) {
                if (Billing.pro.value) locking = doc
                else vm.route = Route.Pro("Password-locking PDFs is part of Pro.", vm.route)
            }
            override fun sign(doc: Doc) {
                if (Billing.pro.value) vm.route = Route.Sign(doc.id)
                else vm.route = Route.Pro("Signing documents is part of Pro.", vm.route)
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

    // A password-locked copy is ready: share it or let the user pick where to save it.
    LaunchedEffect(vm.lockedCopy) {
        val copy = vm.lockedCopy ?: return@LaunchedEffect
        vm.lockedCopy = null
        when (copy.target) {
            LockedTarget.SHARE -> runCatching { activity.startActivity(vm.shareIntent(copy.file, copy.name)) }
                .onSuccess { sharing = true }
                .onFailure { vm.toast = "Could not share this file" }
            LockedTarget.SAVE -> {
                exporting = copy.file
                exportLauncher.launch(copy.file.name)
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        if (sharing) {
            sharing = false
            vm.successes++
        }
        onPauseOrDispose { }
    }

    // After a few saves or shares, Google's review card may appear (Google limits how often).
    LaunchedEffect(vm.successes) {
        if (vm.successes > 0) Rating.onSuccess(activity)
    }

    LaunchedEffect(billingMessage) {
        val message = billingMessage ?: return@LaunchedEffect
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        Billing.messageShown()
        if (Billing.pro.value && vm.route is Route.Pro) vm.route = (vm.route as Route.Pro).back
    }

    LaunchedEffect(vm.toast) {
        val message = vm.toast ?: return@LaunchedEffect
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        vm.toast = null
    }

    BackHandler(enabled = vm.route != Route.Home) {
        vm.route = when (val r = vm.route) {
            is Route.Pro -> r.back
            is Route.Sign -> Route.Viewer(r.id)
            else -> Route.Home
        }
    }

    val watchVideo = {
        val shown = Ads.showRewarded(activity) { vm.grantAdFreeDay() }
        if (!shown) vm.toast = "No video ready yet. Try again in a moment."
    }

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
                    Tool.PRO -> vm.route = Route.Pro(null, Route.Home)
                    Tool.MERGE, Tool.COMPRESS -> Unit // handled inside the home screen
                }
            },
            onMenu = { item ->
                when (item) {
                    MenuItem.RATE -> runCatching { Rating.openStorePage(activity) }
                    MenuItem.PRIVACY_POLICY -> runCatching {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                    }
                    MenuItem.AD_CHOICES -> Ads.showPrivacyOptions(activity)
                }
            },
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
        is Route.Pro -> ProScreen(
            activity = activity,
            reason = route.reason,
            adFree = vm.adFree,
            privacyNeeded = privacyNeeded,
            onBack = { vm.route = route.back },
            onWatchVideo = {
                vm.route = route.back
                watchVideo()
            },
            onPrivacyChoices = { Ads.showPrivacyOptions(activity) },
            onOpenLink = { url ->
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            },
        )
        is Route.Sign -> {
            val doc = vm.store.docs.value.firstOrNull { it.id == route.id }
            if (doc == null || !pro) {
                LaunchedEffect(Unit) { vm.route = Route.Home }
            } else {
                SignScreen(vm = vm, doc = doc, onBack = { vm.route = Route.Viewer(doc.id) })
            }
        }
    }

    locking?.let { doc ->
        LockDialog(doc, onDismiss = { locking = null }, onLock = { password, target -> vm.lock(doc, password, target) })
    }

    vm.busy?.let { BusyCard(it) }
}

/** Runs [action] after a full-screen ad when one is due, otherwise straight away. */
private fun withAd(activity: Activity, vm: AppViewModel, action: () -> Unit) {
    // adDue() is always false for Pro users and during ad-free time.
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
    /** Pro: share or save a password-locked copy. */
    fun lock(doc: Doc)
    /** Pro: add your signature. */
    fun sign(doc: Doc)
}
