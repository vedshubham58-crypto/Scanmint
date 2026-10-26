package com.vedshubham.scanmint.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.vedshubham.scanmint.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * All AdMob code lives here: the consent popup, banner, a full-screen (interstitial)
 * ad after saving a document, and rewarded ads that turn off ads for a while.
 */
object Ads {
    private const val TAG = "Ads"

    private val started = AtomicBoolean(false)
    private lateinit var consent: ConsentInformation

    private val _ready = MutableStateFlow(false)
    /** True once the user's consent allows ads and the SDK is running. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var loadingInterstitial = false
    private var loadingRewarded = false

    /** Call once from the main screen. Shows Google's consent form where the law requires it. */
    fun setUp(activity: Activity) {
        consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form: ${error.message}")
                    _privacyOptionsRequired.value = consent.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    if (consent.canRequestAds()) start(activity)
                }
            },
            { error -> Log.w(TAG, "Consent update: ${error.message}") },
        )
        // Consent from a previous launch lets ads start loading right away.
        if (consent.canRequestAds()) start(activity)
    }

    /** "Privacy settings" button: lets users in Europe change their ad choices. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options: ${error.message}")
        }
    }

    private fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        MobileAds.initialize(app) {
            _ready.value = true
            loadInterstitial(app)
            loadRewarded(app)
        }
    }

    private fun loadInterstitial(context: Context) {
        if (interstitial != null || loadingInterstitial || !_ready.value) return
        loadingInterstitial = true
        InterstitialAd.load(
            context,
            BuildConfig.INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    loadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Interstitial failed: ${error.message}")
                    loadingInterstitial = false
                }
            },
        )
    }

    private fun loadRewarded(context: Context) {
        if (rewarded != null || loadingRewarded || !_ready.value) return
        loadingRewarded = true
        RewardedAd.load(
            context,
            BuildConfig.REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded = ad
                    loadingRewarded = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Rewarded failed: ${error.message}")
                    loadingRewarded = false
                }
            },
        )
    }

    /** Shows a full-screen ad if one is loaded, then runs [onDone] either way. */
    fun showInterstitial(activity: Activity, onDone: () -> Unit) {
        val ad = interstitial
        if (ad == null) {
            loadInterstitial(activity)
            onDone()
            return
        }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadInterstitial(activity)
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadInterstitial(activity)
                onDone()
            }
        }
        ad.show(activity)
    }

    fun rewardedAvailable(): Boolean = rewarded != null

    /**
     * Shows a rewarded ad. [onReward] runs only if the user watched it to the end;
     * [onClosed] runs when the ad goes away, watched or not.
     * Returns false when no ad is ready yet.
     */
    fun showRewarded(activity: Activity, onClosed: () -> Unit = {}, onReward: () -> Unit): Boolean {
        val ad = rewarded
        if (ad == null) {
            loadRewarded(activity)
            return false
        }
        rewarded = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewarded(activity)
                onClosed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadRewarded(activity)
                onClosed()
            }
        }
        ad.show(activity) { onReward() }
        return true
    }
}

/** Banner that sits at the bottom of the screen and sizes itself to the screen width. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    var view by remember { mutableStateOf<AdView?>(null) }
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                adUnitId = BuildConfig.BANNER_ID
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
                loadAd(AdRequest.Builder().build())
                view = this
            }
        },
    )
    DisposableEffect(Unit) {
        onDispose { view?.destroy() }
    }
}
