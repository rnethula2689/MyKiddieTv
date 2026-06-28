package com.mykiddietv.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parent-visible log of what the child watched (title + time). Recorded when a kid-mode player opens;
 * shown to the parent on the Kid watch-history screen. Local only, newest first, capped.
 */
object KidHistory {
    private const val PREF = "kids"
    private const val MAX = 200

    data class H(val title: String, val ts: Long)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun all(ctx: Context): List<H> {
        val out = ArrayList<H>()
        try {
            val a = JSONArray(prefs(ctx).getString("history", "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(H(o.optString("title"), o.optLong("ts")))
            }
        } catch (_: Exception) {}
        return out
    }

    fun add(ctx: Context, title: String) {
        if (title.isBlank()) return
        val list = ArrayList(all(ctx))
        // Collapse consecutive duplicates (e.g. re-opening the same title).
        if (list.firstOrNull()?.title == title) return
        list.add(0, H(title, System.currentTimeMillis()))
        val a = JSONArray()
        for (h in list.take(MAX)) a.put(JSONObject().put("title", h.title).put("ts", h.ts))
        prefs(ctx).edit().putString("history", a.toString()).apply()
    }

    fun clear(ctx: Context) { prefs(ctx).edit().remove("history").apply() }
}
