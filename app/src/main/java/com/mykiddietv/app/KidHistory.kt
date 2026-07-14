package com.mykiddietv.app

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parent-visible log of what each child watched: which kid, the title, whether it was a movie/show or
 * Live TV, when, and for how long. [start] is called when a kid-mode player opens (and on each resume);
 * [finish] on pause/close accumulates the watched duration onto that entry. Local only, newest first, capped.
 */
object KidHistory {
    private const val PREF = "kids"
    private const val KEY = "historyV2"
    private const val MAX = 300

    /** kind: "movie" (movie/show/episode) | "live" (Live TV). */
    data class H(
        val kidId: String, val kidName: String, val title: String,
        val kind: String, val ts: Long, var durationMs: Long
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // Open-session tracking for the duration tally (one active kid player at a time).
    private var curKey = ""
    private var curStart = 0L

    fun all(ctx: Context): List<H> {
        val out = ArrayList<H>()
        try {
            val a = JSONArray(prefs(ctx).getString(KEY, "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(H(o.optString("kidId"), o.optString("kidName"), o.optString("title"),
                    o.optString("kind", "movie"), o.optLong("ts"), o.optLong("dur")))
            }
        } catch (_: Exception) {}
        return out
    }

    fun forKid(ctx: Context, kidId: String): List<H> = all(ctx).filter { it.kidId == kidId }

    private fun save(ctx: Context, list: List<H>) {
        val a = JSONArray()
        for (h in list.take(MAX)) a.put(JSONObject()
            .put("kidId", h.kidId).put("kidName", h.kidName).put("title", h.title)
            .put("kind", h.kind).put("ts", h.ts).put("dur", h.durationMs))
        prefs(ctx).edit().putString(KEY, a.toString()).apply()
    }

    /** Begin (or resume) a watch session for the active kid. Adds the entry once; restarts the timer. */
    fun start(ctx: Context, title: String, kind: String) {
        if (title.isBlank()) return
        val k = Profiles.activeKid(ctx)
        val key = "${k?.id ?: ""}|$title"
        if (key != curKey) {
            finish(ctx) // close any previous session
            val list = ArrayList(all(ctx))
            val topKey = list.firstOrNull()?.let { "${it.kidId}|${it.title}" }
            if (topKey != key) { // collapse consecutive re-opens of the same title
                list.add(0, H(k?.id ?: "", k?.name ?: "Kid", title, kind, System.currentTimeMillis(), 0L))
                save(ctx, list)
            }
            curKey = key
        }
        curStart = SystemClock.elapsedRealtime()
    }

    /** End the current watch interval, adding its elapsed time to the open entry. */
    fun finish(ctx: Context) {
        if (curStart <= 0L) return
        val add = SystemClock.elapsedRealtime() - curStart
        curStart = 0L
        if (add <= 0L) return
        val list = ArrayList(all(ctx))
        list.firstOrNull { "${it.kidId}|${it.title}" == curKey }?.let { it.durationMs += add; save(ctx, list) }
    }

    fun clearForKid(ctx: Context, kidId: String): Int {
        val cur = all(ctx)
        val next = cur.filterNot { it.kidId == kidId }
        save(ctx, next)
        if (curKey.startsWith("$kidId|")) { curKey = ""; curStart = 0L }
        return cur.size - next.size
    }

    fun clear(ctx: Context) { prefs(ctx).edit().remove(KEY).apply(); curKey = ""; curStart = 0L }
}
