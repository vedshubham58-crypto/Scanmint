package com.vedshubham.scanmint.data

import android.content.Context

/** Small settings: when the last full-screen ad showed, and ad-free time from videos. */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("scanmint", Context.MODE_PRIVATE)

    var lastAdAt: Long
        get() = prefs.getLong("last_ad_at", 0L)
        set(v) = prefs.edit().putLong("last_ad_at", v).apply()

    /** Time until which ads stay off because the user watched a video. */
    var adFreeUntil: Long
        get() = prefs.getLong("ad_free_until", 0L)
        set(v) = prefs.edit().putLong("ad_free_until", v).apply()

    val adFree: Boolean get() = System.currentTimeMillis() < adFreeUntil
}
