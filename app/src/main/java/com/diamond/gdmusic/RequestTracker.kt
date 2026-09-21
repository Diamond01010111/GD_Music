package com.diamond.gdmusic

import android.content.Context
import android.content.SharedPreferences

object RequestTracker {
    private val limiter = RequestWindow()
    private var preferences: SharedPreferences? = null

    @JvmStatic @Synchronized
    fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences("gd_request_window", Context.MODE_PRIVATE)
        preferences!!.getString("timestamps", "").orEmpty().split(',')
            .mapNotNull(String::toLongOrNull).forEach { limiter.restore(it) }
    }

    /** Reserve atomically before dispatch. Rejected attempts are never counted. */
    @JvmStatic @Synchronized
    fun tryAcquire(): Boolean {
        val accepted = limiter.tryAcquire(System.currentTimeMillis())
        if (accepted) preferences?.edit()?.putString("timestamps", limiter.snapshot().joinToString(","))?.apply()
        return accepted
    }

    @JvmStatic @Synchronized
    fun countLastFiveMinutes(): Int = limiter.count(System.currentTimeMillis())
}
