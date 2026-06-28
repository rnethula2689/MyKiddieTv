package com.mykiddietv.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Parental screen-time controls for kid mode: an optional daily watch-time limit and an optional
 * bedtime window. Kid activities call [onResume]/[onPause] so foreground time accumulates across the
 * whole kid session (home, browsing, and playback). When the limit is hit or it's bedtime, [enforce]
 * sends the child to [KidLockActivity] (passcode required to leave). All local; resets each day.
 */
object KidLimits {
    private const val PREF = "kids"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---- config ----
    fun dailyLimitMin(ctx: Context): Int = prefs(ctx).getInt("limitMin", 0) // 0 = off
    fun setDailyLimitMin(ctx: Context, m: Int) { prefs(ctx).edit().putInt("limitMin", m.coerceAtLeast(0)).apply() }

    /** Bedtime as minutes-of-day; both -1 = off. May wrap past midnight (start > end). */
    fun bedStartMin(ctx: Context): Int = prefs(ctx).getInt("bedStart", -1)
    fun bedEndMin(ctx: Context): Int = prefs(ctx).getInt("bedEnd", -1)
    fun setBedtime(ctx: Context, startMin: Int, endMin: Int) {
        prefs(ctx).edit().putInt("bedStart", startMin).putInt("bedEnd", endMin).apply()
    }
    fun bedtimeOn(ctx: Context) = bedStartMin(ctx) >= 0 && bedEndMin(ctx) >= 0 && bedStartMin(ctx) != bedEndMin(ctx)

    // ---- usage (resets daily) ----
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date())
    fun usedMin(ctx: Context): Int {
        val p = prefs(ctx)
        if (p.getString("usedDate", "") != today()) return 0
        return (p.getLong("usedMs", 0L) / 60000L).toInt()
    }
    private fun addUsedMs(ctx: Context, ms: Long) {
        if (ms <= 0) return
        val p = prefs(ctx)
        val base = if (p.getString("usedDate", "") == today()) p.getLong("usedMs", 0L) else 0L
        p.edit().putString("usedDate", today()).putLong("usedMs", base + ms).apply()
    }
    fun remainingMin(ctx: Context): Int {
        val lim = dailyLimitMin(ctx); if (lim <= 0) return -1
        return (lim - usedMin(ctx)).coerceAtLeast(0)
    }

    // ---- checks ----
    fun isBedtime(ctx: Context): Boolean {
        if (!bedtimeOn(ctx)) return false
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val s = bedStartMin(ctx); val e = bedEndMin(ctx)
        return if (s < e) now in s until e else (now >= s || now < e) // wraps midnight
    }
    fun overLimit(ctx: Context): Boolean {
        val lim = dailyLimitMin(ctx); return lim > 0 && usedMin(ctx) >= lim
    }
    /** "bedtime" | "limit" | null */
    fun blockReason(ctx: Context): String? = when {
        isBedtime(ctx) -> "bedtime"
        overLimit(ctx) -> "limit"
        else -> null
    }

    // ---- foreground accounting + enforcement ----
    private val ui = Handler(Looper.getMainLooper())
    private var resumeAt = 0L
    private var checker: Runnable? = null

    /** @return true if the child was sent to the lock screen (caller should stop setting up content). */
    fun enforce(activity: Activity): Boolean {
        val reason = blockReason(activity) ?: return false
        activity.startActivity(Intent(activity, KidLockActivity::class.java)
            .putExtra("reason", reason)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        activity.finish()
        return true
    }

    fun onResume(activity: Activity) {
        if (enforce(activity)) return
        resumeAt = SystemClock.elapsedRealtime()
        checker?.let { ui.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                flush(activity)
                if (!activity.isFinishing && enforce(activity)) return
                ui.postDelayed(this, 30_000)
            }
        }
        checker = r
        ui.postDelayed(r, 30_000)
    }

    fun onPause(activity: Activity) {
        flush(activity)
        checker?.let { ui.removeCallbacks(it) }
        checker = null
    }

    private fun flush(ctx: Context) {
        if (resumeAt > 0) {
            val now = SystemClock.elapsedRealtime()
            addUsedMs(ctx, now - resumeAt)
            resumeAt = now
        }
    }

    fun fmtMin(min: Int): String {
        if (min < 0) return "Off"
        val h = min / 60; val m = min % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
    fun fmtClock(min: Int): String {
        val h = min / 60; val m = min % 60
        val ampm = if (h < 12) "AM" else "PM"
        val hh = ((h + 11) % 12) + 1
        return String.format("%d:%02d %s", hh, m, ampm)
    }
}
