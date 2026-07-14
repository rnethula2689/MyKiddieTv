package com.mykiddietv.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Continue Watching" store (per provider). Remembers playback position for movies/episodes
 * (kind="vod") and the last-watched live channel (kind="live", a single rolling entry). Finished
 * items (~95%+) are dropped. Newest first; capped so it doesn't grow without bound.
 */
object Resume {
    const val LIVE_ID = "live_last"
    private const val CAP = 40
    private const val MIN_RESUME_MS = 30_000L

    data class Entry(
        val id: String, val kind: String, val title: String, val poster: String,
        val source: String, val position: Long, val duration: Long, val updated: Long,
        val year: String = "",
        val restricted: Boolean = false,  // came from an adult/censored (PIN-locked) channel → keep off home rails
        val kidId: String = ""            // blank = parent/legacy; non-blank = a specific child profile
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    private fun key(ctx: Context) = "resume:" + (Configs.active(ctx)?.sig() ?: "default") + ContentProfiles.scopeSuffix(ctx)

    fun all(ctx: Context): List<Entry> {
        val out = ArrayList<Entry>()
        try {
            val a = JSONArray(prefs(ctx).getString(key(ctx), "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(
                    Entry(
                        o.optString("id"), o.optString("kind"), o.optString("title"), o.optString("poster"),
                        o.optString("source"), o.optLong("position"), o.optLong("duration"), o.optLong("updated"),
                        o.optString("year"), o.optBoolean("restricted", false), o.optString("kidId", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return out.sortedByDescending { it.updated }
    }

    private fun saveList(ctx: Context, list: List<Entry>) {
        val a = JSONArray()
        for (e in list.sortedByDescending { it.updated }.take(CAP)) a.put(
            JSONObject().put("id", e.id).put("kind", e.kind).put("title", e.title).put("poster", e.poster)
                .put("source", e.source).put("position", e.position).put("duration", e.duration).put("updated", e.updated)
                .put("year", e.year).put("restricted", e.restricted).put("kidId", e.kidId)
        )
        prefs(ctx).edit().putString(key(ctx), a.toString()).apply()
    }

    private fun sameSlot(e: Entry, id: String, kidId: String) = e.id == id && e.kidId == kidId

    fun get(ctx: Context, id: String, kidId: String = ""): Entry? {
        val list = all(ctx)
        return if (kidId.isBlank()) {
            list.firstOrNull { it.id == id && it.kidId.isBlank() }
        } else {
            // Fall back to blank legacy entries so pre-upgrade kid progress can still resume.
            list.firstOrNull { sameSlot(it, id, kidId) } ?: list.firstOrNull { it.id == id && it.kidId.isBlank() }
        }
    }

    /** True if [e] has a meaningful position to resume from. */
    fun resumable(e: Entry?): Boolean =
        e != null && e.kind == "vod" && e.position > MIN_RESUME_MS &&
            (e.duration <= 0 || e.position < e.duration * 95 / 100)

    fun save(
        ctx: Context,
        id: String,
        kind: String,
        title: String,
        poster: String,
        source: String,
        position: Long,
        duration: Long,
        year: String = "",
        restricted: Boolean = false,
        kidId: String = ""
    ) {
        if (id.isBlank()) return
        // Preserve a previously-saved year if this call doesn't carry one (e.g. resumed from Continue Watching).
        val prevYear = get(ctx, id, kidId)?.year ?: ""
        val list = all(ctx).filterNot {
            sameSlot(it, id, kidId) || (kidId.isNotBlank() && it.id == id && it.kidId.isBlank())
        }.toMutableList()
        // Drop finished VOD instead of storing it.
        if (kind == "vod" && duration > 0 && position >= duration * 95 / 100) { saveList(ctx, list); return }
        list.add(Entry(id, kind, title, poster, source, position, duration, System.currentTimeMillis(), year.ifBlank { prevYear }, restricted, kidId))
        saveList(ctx, list)
    }

    fun remove(ctx: Context, id: String, kidId: String = "") {
        val next = all(ctx).filterNot {
            it.id == id && if (kidId.isBlank()) it.kidId.isBlank() else (it.kidId == kidId || it.kidId.isBlank())
        }
        saveList(ctx, next)
    }

    fun clearAll(ctx: Context) { prefs(ctx).edit().remove(key(ctx)).apply() }

    fun clearParent(ctx: Context): Int {
        val cur = all(ctx)
        val next = cur.filterNot { it.kidId.isBlank() }
        saveList(ctx, next)
        return cur.size - next.size
    }

    fun allForParent(ctx: Context): List<Entry> = all(ctx).filter { it.kidId.isBlank() }

    private fun visibleLegacyForKid(kid: Profiles.Kid, e: Entry): Boolean {
        if (!kid.manageContent) return e.kind == "vod"
        val p = e.source.split("|")
        return when (p.getOrNull(0)) {
            "vod" -> kid.vod.any { it.id == p.getOrElse(1) { "" } }
            "ep" -> {
                val seriesId = p.getOrElse(1) { "" }
                val key = "$seriesId|${p.getOrElse(2) { "" }}|${p.getOrElse(3) { "" }}"
                kid.episodes.any { it.key == key } || kid.vod.any { it.id == seriesId }
            }
            else -> false
        }
    }

    fun allForKid(ctx: Context, kid: Profiles.Kid): List<Entry> =
        all(ctx).filter { it.kidId == kid.id || (it.kidId.isBlank() && visibleLegacyForKid(kid, it)) }

    /** Clears this kid's tagged progress. Blank legacy entries are also removed when they are visible
     *  to the selected kid, because pre-upgrade progress was shared and has no child owner to inspect. */
    fun clearForKid(ctx: Context, kid: Profiles.Kid): Int {
        val cur = all(ctx)
        val remove = cur.filter { it.kidId == kid.id || (it.kidId.isBlank() && visibleLegacyForKid(kid, it)) }
        if (remove.isNotEmpty()) saveList(ctx, cur - remove.toSet())
        return remove.size
    }
}
