package com.mykiddietv.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Parental screen-time controls for kid mode — PER KID: each kid profile has its own optional daily
 * watch-time limit and bedtime window, and its own daily usage tally. Kid activities call
 * [onResume]/[onPause] so foreground time accumulates for whoever is active; when that kid hits their
 * limit or it's their bedtime, [enforce] sends them to [KidLockActivity]. All local; resets each day.
 *
 * Config keys are suffixed with the kid id ("limitMin:<id>", …). Reads fall back to the legacy GLOBAL
 * key so a pre-per-kid install's single limit keeps applying to every kid until it's set individually.
 */
object KidLimits {
    private const val PREF = "kids"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    /** The kid currently watching (enforcement + usage are scoped to them). */
    private fun active(ctx: Context) = Profiles.activeKidId(ctx) ?: "default"

    // ---- config (per kid; legacy-global fallback on read) ----
    fun dailyLimitMin(ctx: Context, kid: String = active(ctx)): Int =
        prefs(ctx).getInt("limitMin:$kid", prefs(ctx).getInt("limitMin", 0)) // 0 = off
    fun setDailyLimitMin(ctx: Context, kid: String, m: Int) {
        prefs(ctx).edit().putInt("limitMin:$kid", m.coerceAtLeast(0)).apply()
    }

    fun bedStartMin(ctx: Context, kid: String = active(ctx)): Int =
        prefs(ctx).getInt("bedStart:$kid", prefs(ctx).getInt("bedStart", -1))
    fun bedEndMin(ctx: Context, kid: String = active(ctx)): Int =
        prefs(ctx).getInt("bedEnd:$kid", prefs(ctx).getInt("bedEnd", -1))
    fun setBedtime(ctx: Context, kid: String, startMin: Int, endMin: Int) {
        prefs(ctx).edit().putInt("bedStart:$kid", startMin).putInt("bedEnd:$kid", endMin).apply()
    }
    fun bedtimeOn(ctx: Context, kid: String = active(ctx)) =
        bedStartMin(ctx, kid) >= 0 && bedEndMin(ctx, kid) >= 0 && bedStartMin(ctx, kid) != bedEndMin(ctx, kid)

    // ---- usage (per kid, resets daily) ----
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date())
    fun usedMin(ctx: Context, kid: String = active(ctx)): Int {
        val p = prefs(ctx)
        if (p.getString("usedDate:$kid", "") != today()) return 0
        return (p.getLong("usedMs:$kid", 0L) / 60000L).toInt()
    }
    private fun addUsedMs(ctx: Context, ms: Long) {
        if (ms <= 0) return
        val kid = active(ctx)
        val p = prefs(ctx)
        val base = if (p.getString("usedDate:$kid", "") == today()) p.getLong("usedMs:$kid", 0L) else 0L
        p.edit().putString("usedDate:$kid", today()).putLong("usedMs:$kid", base + ms).apply()
    }
    fun remainingMin(ctx: Context): Int {
        val lim = dailyLimitMin(ctx); if (lim <= 0) return -1
        return (lim - usedMin(ctx)).coerceAtLeast(0)
    }

    // ---- checks (for the active kid) ----
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
    private var warned = false // one gentle "almost out of time" heads-up per foreground session

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
        warned = false
        resumeAt = SystemClock.elapsedRealtime()
        checker?.let { ui.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                flush(activity)
                if (!activity.isFinishing && enforce(activity)) return
                maybeWarnLowTime(activity)
                ui.postDelayed(this, 30_000)
            }
        }
        checker = r
        ui.postDelayed(r, 30_000)
    }

    /** One gentle heads-up in the last few minutes before the daily limit cuts the child off, so the
     *  lock screen isn't a jarring surprise mid-show. Shown once per foreground session. */
    private fun maybeWarnLowTime(activity: Activity) {
        if (warned || activity.isFinishing) return
        if (dailyLimitMin(activity) <= 0) return          // no limit set → nothing to warn about
        val left = remainingMin(activity)
        if (left in 1..5) {
            warned = true
            Toast.makeText(activity, "⏰  $left more minute${if (left == 1) "" else "s"} of watching today!", Toast.LENGTH_LONG).show()
        }
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
