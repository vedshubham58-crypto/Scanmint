package com.vedshubham.scanmint.review

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * "Rate us": Google's own in-app review card, shown once after the user has saved or shared
 * a few documents (a good moment), plus a button that opens the Play Store page.
 * Google decides whether the card actually appears and limits how often; we never ask for
 * a particular star rating, as Play's rules require.
 */
object Rating {
    /** The store package (test builds add ".debug", which isn't on the Play Store). */
    const val STORE_PACKAGE = "com.vedshubham.scanmint"

    private const val PREFS = "scanmint_rating"
    private const val SUCCESSES_BEFORE_ASKING = 3
    private const val DAYS_BETWEEN_ASKS = 60L

    /** Call after a document was saved or shared. May show Google's review card. */
    fun onSuccess(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt("successes", 0) + 1
        prefs.edit().putInt("successes", count).apply()
        val lastAsked = prefs.getLong("last_asked", 0L)
        val now = System.currentTimeMillis()
        if (count < SUCCESSES_BEFORE_ASKING || now - lastAsked < DAYS_BETWEEN_ASKS * 86_400_000L) return
        prefs.edit().putLong("last_asked", now).apply()

        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful && !activity.isFinishing) {
                manager.launchReviewFlow(activity, request.result)
            }
        }
    }

    /** The "Rate ScanMint" button: opens the Play Store app, or the website if it's missing. */
    fun openStorePage(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$STORE_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$STORE_PACKAGE"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
