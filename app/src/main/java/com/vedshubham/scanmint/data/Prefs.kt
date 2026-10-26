package com.vedshubham.scanmint.data

import android.content.Context

/** Small settings: how many saves since the last full-screen ad, and ad-free time from videos. */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("scanmint", Context.MODE_PRIVATE)

    var savesSinceAd: Int
        get() = prefs.getInt("saves_since_ad", 0)
        set(v) = prefs.edit().putInt("saves_since_ad", v).apply()

    /** Time until which ads stay off because the user watched a video. */
    var adFreeUntil: Long
        get() = prefs.getLong("ad_free_until", 0L)
        set(v) = prefs.edit().putLong("ad_free_until", v).apply()

    val adFree: Boolean get() = System.currentTimeMillis() < adFreeUntil
}
