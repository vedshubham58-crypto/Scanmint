package com.vedshubham.scanmint.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedshubham.scanmint.BuildConfig
import com.vedshubham.scanmint.billing.Billing
import java.text.NumberFormat
import java.util.Currency
import kotlin.math.roundToInt

/** The Pro page: what you get, the two plans with Play's prices, and the subscription terms. */
@Composable
fun ProScreen(
    activity: Activity,
    reason: String?,
    adFree: Boolean,
    privacyNeeded: Boolean,
    onBack: () -> Unit,
    onWatchVideo: () -> Unit,
    onPrivacyChoices: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val pro by Billing.pro.collectAsState()
    val plans by Billing.plans.collectAsState()
    var chosen by remember(plans) { mutableStateOf(plans.firstOrNull { it.basePlanId == Billing.YEARLY } ?: plans.firstOrNull()) }

    LaunchedEffect(Unit) { Billing.refresh() }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "Close") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(Mint.brush),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Star, null, tint = Mint.Gold, modifier = Modifier.size(40.dp)) }
            Spacer(Modifier.height(14.dp))
            Text("ScanMint Pro", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
            if (reason != null && !pro) {
                Spacer(Modifier.height(6.dp))
                Text(reason, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))
            Benefit("No ads, anywhere in the app")
            Benefit("Lock PDFs with a password")
            Benefit("Sign documents with your own signature")
            Benefit("Help a small independent app grow")
            Spacer(Modifier.height(22.dp))

            when {
                pro -> {
                    Text("You're Pro. Thank you!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { activity.startActivity(Billing.manageIntent(activity.packageName)) }) {
                        Text("Manage subscription")
                    }
                }
                plans.isEmpty() -> {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Loading prices from Google Play…\nMake sure you're online and signed in to the Play Store.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    val monthly = plans.firstOrNull { it.basePlanId == Billing.MONTHLY }
                    plans.forEach { plan ->
                        PlanCard(plan, monthly, selected = plan == chosen, onClick = { chosen = plan })
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            if (!pro && !adFree) {
                TextButton(onClick = onWatchVideo) { Text("Not now? Watch one video for 24 hours without ads") }
            }
            if (BuildConfig.DEBUG) {
                // Play billing only works in the store version, so test builds get a switch.
                TextButton(onClick = { Billing.setTestPro(!pro) }) {
                    Text(if (pro) "Test build: turn Pro off" else "Test build: turn Pro on (no payment)", color = Color(0xFFB06A00))
                }
            }
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { Billing.refresh(userAsked = true) }) { Text("Restore purchase", fontSize = 12.sp) }
                TextButton(onClick = { onOpenLink(PRIVACY_POLICY_URL) }) { Text("Privacy policy", fontSize = 12.sp) }
                if (privacyNeeded) TextButton(onClick = onPrivacyChoices) { Text("Ad choices", fontSize = 12.sp) }
            }
            Spacer(Modifier.height(8.dp))
        }

        val plan = chosen
        if (!pro && plan != null) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Button(
                    onClick = { Billing.buy(activity, plan) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(
                        if (plan.trialDays > 0) "Start ${plan.trialDays}-day free trial" else "Continue",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    termsText(plan),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

const val PRIVACY_POLICY_URL = "https://github.com/vedshubham58-crypto/Scanmint/blob/main/PRIVACY.md"

/** The plain-words subscription terms Google Play asks for next to the buy button. */
internal fun termsText(plan: Billing.Plan): String {
    val period = if (plan.basePlanId == Billing.YEARLY) "year" else "month"
    val start = if (plan.trialDays > 0) "Free for ${plan.trialDays} days, then ${plan.price} a $period. " else "${plan.price} a $period. "
    return start + "Renews automatically until you cancel. Cancel anytime in Google Play → Payments & subscriptions."
}

@Composable
private fun Benefit(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.size(12.dp))
        Text(text, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun PlanCard(plan: Billing.Plan, monthly: Billing.Plan?, selected: Boolean, onClick: () -> Unit) {
    val yearly = plan.basePlanId == Billing.YEARLY
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = if (yearly) 10.dp else 0.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (yearly) "Yearly" else "Monthly", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                val sub = when {
                    yearly -> perMonth(plan)?.let { "Just $it a month" }
                    else -> "Cancel anytime"
                }
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (plan.trialDays > 0) {
                    Text("${plan.trialDays} days free", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                plan.price + if (yearly) "/yr" else "/mo",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
            )
        }
        val saving = if (yearly && monthly != null) savingPercent(plan, monthly) else null
        if (saving != null) {
            Text(
                "BEST VALUE · SAVE $saving%",
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Mint.Gold)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                color = Mint.GoldInk,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
            )
        }
    }
}

private fun perMonth(plan: Billing.Plan): String? = runCatching {
    val format = NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(plan.currencyCode) }
    format.maximumFractionDigits = if (plan.priceMicros / 12 >= 100_000_000L) 0 else 2
    format.format(plan.priceMicros / 12 / 1_000_000.0)
}.getOrNull()

private fun savingPercent(yearly: Billing.Plan, monthly: Billing.Plan): Int? {
    if (monthly.priceMicros <= 0 || yearly.currencyCode != monthly.currencyCode) return null
    val full = monthly.priceMicros * 12.0
    val pct = ((1 - yearly.priceMicros / full) * 100).roundToInt()
    return pct.takeIf { it in 5..90 }
}
