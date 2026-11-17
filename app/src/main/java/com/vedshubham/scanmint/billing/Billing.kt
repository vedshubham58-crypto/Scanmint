package com.vedshubham.scanmint.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ScanMint Pro, sold as a Google Play subscription.
 *
 * In Play Console make ONE subscription with the product ID [PRODUCT_ID] and two base plans:
 * [MONTHLY] (auto-renewing every month) and [YEARLY] (every year). A free-trial offer on a base
 * plan is picked up automatically. Prices come from Play, so each country sees its own price.
 */
object Billing {
    const val PRODUCT_ID = "scanmint_pro"
    const val MONTHLY = "monthly"
    const val YEARLY = "yearly"

    private const val TAG = "Billing"
    private const val PREFS = "scanmint_billing"

    /** One plan as shown on the Pro screen. */
    data class Plan(
        val basePlanId: String,
        val price: String,
        val priceMicros: Long,
        val currencyCode: String,
        /** Days of free trial, or 0. */
        val trialDays: Int,
        internal val offerToken: String,
        internal val details: ProductDetails,
    )

    private val _pro = MutableStateFlow(false)
    /** True while the user has an active Pro subscription. */
    val pro: StateFlow<Boolean> = _pro.asStateFlow()

    private val _plans = MutableStateFlow<List<Plan>>(emptyList())
    val plans: StateFlow<List<Plan>> = _plans.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    /** Something to tell the user after a purchase ("Welcome to Pro"). Clear with [messageShown]. */
    val message: StateFlow<String?> = _message.asStateFlow()

    private lateinit var appContext: Context
    private var client: BillingClient? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handle(purchases.orEmpty(), fromPurchase = true)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _message.value = "Payment didn't go through. Please try again."
        }
    }

    /** Call once at start. Uses the last known Pro state until Play answers, so it works offline. */
    fun setUp(context: Context) {
        if (client != null) return
        appContext = context.applicationContext
        _pro.value = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("pro", false)
        client = BillingClient.newBuilder(appContext)
            .setListener(purchasesListener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        connect()
    }

    private fun connect() {
        val c = client ?: return
        if (c.isReady) {
            loadPlans()
            refresh()
            return
        }
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadPlans()
                    refresh()
                } else {
                    Log.w(TAG, "Setup: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun loadPlans() {
        val c = client ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        c.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Plans: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            _plans.value = details.productDetailsList.flatMap(::plansOf)
                .sortedBy { if (it.basePlanId == YEARLY) 0 else 1 }
        }
    }

    /** One plan per base plan, preferring the offer with a free trial. */
    private fun plansOf(product: ProductDetails): List<Plan> {
        val offers = product.subscriptionOfferDetails.orEmpty()
        return offers.groupBy { it.basePlanId }.mapNotNull { (basePlan, list) ->
            val best = list.maxByOrNull { trialDays(it) } ?: return@mapNotNull null
            // The last phase is the normal price that applies after any trial.
            val phase = best.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
            Plan(basePlan, phase.formattedPrice, phase.priceAmountMicros, phase.priceCurrencyCode, trialDays(best), best.offerToken, product)
        }
    }

    private fun trialDays(offer: ProductDetails.SubscriptionOfferDetails): Int {
        val free = offer.pricingPhases.pricingPhaseList.firstOrNull { it.priceAmountMicros == 0L } ?: return 0
        return periodDays(free.billingPeriod) * free.billingCycleCount.coerceAtLeast(1)
    }

    /** "P3D" → 3, "P1W" → 7, "P1M" → 30. */
    internal fun periodDays(iso: String): Int {
        val m = Regex("P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?").matchEntire(iso) ?: return 0
        val (y, mo, w, d) = m.destructured
        return (y.toIntOrNull() ?: 0) * 365 + (mo.toIntOrNull() ?: 0) * 30 + (w.toIntOrNull() ?: 0) * 7 + (d.toIntOrNull() ?: 0)
    }

    /** Opens Google Play's payment sheet for [plan]. */
    fun buy(activity: Activity, plan: Plan) {
        val c = client
        if (c == null || !c.isReady) {
            _message.value = "Google Play isn't ready. Check your internet and try again."
            connect()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(plan.details)
                        .setOfferToken(plan.offerToken)
                        .build(),
                ),
            )
            .build()
        c.launchBillingFlow(activity, params)
    }

    /** Asks Play which subscriptions are active. Also the "Restore purchase" button. */
    fun refresh(userAsked: Boolean = false) {
        val c = client ?: return
        if (!c.isReady) {
            connect()
            return
        }
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            handle(purchases, fromPurchase = false)
            if (userAsked) {
                _message.value = if (_pro.value) "Pro is active on this phone" else "No Pro subscription found on this Google account"
            }
        }
    }

    private fun handle(purchases: List<Purchase>, fromPurchase: Boolean) {
        val active = purchases.filter {
            PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        // Play refunds a purchase that isn't acknowledged within 3 days.
        active.filterNot { it.isAcknowledged }.forEach { purchase ->
            client?.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            ) { r -> if (r.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "Ack: ${r.debugMessage}") }
        }
        val pending = purchases.any { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PENDING }
        val wasPro = _pro.value
        // A list from a purchase only holds the new purchase; only a full check can turn Pro off.
        val nowPro = active.isNotEmpty() || (fromPurchase && wasPro)
        setPro(nowPro)
        if (fromPurchase && nowPro && !wasPro) _message.value = "Welcome to ScanMint Pro! Ads are gone."
        if (fromPurchase && pending && !nowPro) _message.value = "Payment is pending. Pro turns on as soon as it's done."
    }

    private fun setPro(value: Boolean) {
        _pro.value = value
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("pro", value).apply()
    }

    /** Test builds only: lets you try the Pro features without paying (Play billing needs a store build). */
    fun setTestPro(value: Boolean) {
        if (!com.vedshubham.scanmint.BuildConfig.DEBUG) return
        setPro(value)
    }

    fun messageShown() {
        _message.value = null
    }

    /** Google Play's own page where people cancel or change their subscription. */
    fun manageIntent(packageName: String): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=$packageName"),
    )
}
