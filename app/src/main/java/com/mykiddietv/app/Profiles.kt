package com.mykiddietv.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kid-mode store: profile names, the parent passcode, and the whitelist of
 * live channels + VOD titles the kid is allowed to watch. Kept in its own
 * SharedPreferences file ("kids") so it's independent of provider configs.
 */
object Profiles {
    private const val PREF = "kids"

    /** Set true when the kid whitelist changes, so the kid home reloads on resume. */
    var dirty = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---- profile names ----
    fun parentName(ctx: Context): String = prefs(ctx).getString("parentName", "")?.ifBlank { "Parent" } ?: "Parent"
    fun kidName(ctx: Context): String = prefs(ctx).getString("kidName", "")?.ifBlank { "Kids" } ?: "Kids"

    fun setNames(ctx: Context, parent: String, kid: String) {
        prefs(ctx).edit()
            .putString("parentName", parent.trim())
            .putString("kidName", kid.trim())
            .apply()
    }

    // ---- kid picture (avatar) ----
    /** "" = default (teddy bear) | "emoji:🦁" | "file:/path" (see Avatars). */
    fun kidAvatar(ctx: Context): String = prefs(ctx).getString("kidAvatar", "") ?: ""
    fun setKidAvatar(ctx: Context, a: String) { prefs(ctx).edit().putString("kidAvatar", a).apply() }

    // ---- passcode ----
    /** 4-digit parent passcode; empty string means "not set yet" (parent entry is open). */
    fun passcode(ctx: Context): String = prefs(ctx).getString("passcode", "") ?: ""
    fun hasPasscode(ctx: Context): Boolean = passcode(ctx).length == 4
    fun setPasscode(ctx: Context, code: String) { prefs(ctx).edit().putString("passcode", code.trim()).apply() }

    // ---- allowed live channels ----
    fun allowedChannels(ctx: Context): MutableList<Portal.Channel> {
        val out = ArrayList<Portal.Channel>()
        try {
            val arr = JSONArray(prefs(ctx).getString("channels", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Portal.Channel(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        number = o.optString("number"),
                        cmd = o.optString("cmd"),
                        logoUrl = o.optString("logo"),
                        genreId = o.optString("genreId")
                    )
                )
            }
        } catch (_: Exception) {}
        return out
    }

    fun allowedChannelIds(ctx: Context): Set<String> = allowedChannels(ctx).map { it.id }.toSet()

    fun saveChannels(ctx: Context, list: List<Portal.Channel>) {
        val arr = JSONArray()
        for (c in list) {
            arr.put(
                JSONObject()
                    .put("id", c.id).put("name", c.name).put("number", c.number)
                    .put("cmd", c.cmd).put("logo", c.logoUrl).put("genreId", c.genreId)
            )
        }
        prefs(ctx).edit().putString("channels", arr.toString()).apply()
        dirty = true
    }

    // ---- allowed VOD (movies & series) ----
    fun allowedVod(ctx: Context): MutableList<Portal.VodItem> {
        val out = ArrayList<Portal.VodItem>()
        try {
            val arr = JSONArray(prefs(ctx).getString("vod", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Portal.VodItem(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        cmd = o.optString("cmd"),
                        posterUrl = o.optString("poster"),
                        isSeries = o.optBoolean("isSeries")
                    )
                )
            }
        } catch (_: Exception) {}
        return out
    }

    fun allowedVodIds(ctx: Context): Set<String> = allowedVod(ctx).map { it.id }.toSet()

    fun saveVod(ctx: Context, list: List<Portal.VodItem>) {
        val arr = JSONArray()
        for (v in list) {
            arr.put(
                JSONObject()
                    .put("id", v.id).put("name", v.name).put("cmd", v.cmd)
                    .put("poster", v.posterUrl).put("isSeries", v.isSeries)
            )
        }
        prefs(ctx).edit().putString("vod", arr.toString()).apply()
        dirty = true
    }

    // ---- allowed individual episodes ----
    /** Enough to display and later resolve a play URL via Portal.playEpisodeUrl(series, season, episode). */
    data class KidEpisode(
        val seriesId: String, val seriesName: String,
        val seasonId: String, val seasonName: String, val episodeId: String,
        val name: String, val poster: String
    ) {
        val key get() = "$seriesId|$seasonId|$episodeId"
        /** Download descriptor understood by Downloads.resolveSource. */
        val source get() = "ep|$seriesId|$seasonId|$episodeId"
    }

    fun allowedEpisodes(ctx: Context): MutableList<KidEpisode> {
        val out = ArrayList<KidEpisode>()
        try {
            val arr = JSONArray(prefs(ctx).getString("episodes", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(KidEpisode(
                    o.optString("seriesId"), o.optString("seriesName"),
                    o.optString("seasonId"), o.optString("seasonName"), o.optString("episodeId"),
                    o.optString("name"), o.optString("poster")
                ))
            }
        } catch (_: Exception) {}
        return out
    }

    fun allowedEpisodeKeys(ctx: Context): Set<String> = allowedEpisodes(ctx).map { it.key }.toSet()

    fun saveEpisodes(ctx: Context, list: List<KidEpisode>) {
        val arr = JSONArray()
        for (e in list) {
            arr.put(JSONObject()
                .put("seriesId", e.seriesId).put("seriesName", e.seriesName)
                .put("seasonId", e.seasonId).put("seasonName", e.seasonName).put("episodeId", e.episodeId)
                .put("name", e.name).put("poster", e.poster))
        }
        prefs(ctx).edit().putString("episodes", arr.toString()).apply()
        dirty = true
    }

    // ---- which downloads were made FOR THE KID (vs the parent's own downloads) ----
    // Kid downloads show only in Approved Content → Downloads and the kid's Downloaded folder;
    // they're filtered out of the parent's own (Stalker-style) Downloads screen.
    fun kidDownloadIds(ctx: Context): LinkedHashSet<String> {
        val out = LinkedHashSet<String>()
        try {
            val arr = JSONArray(prefs(ctx).getString("kidDownloads", "[]") ?: "[]")
            for (i in 0 until arr.length()) out.add(arr.getString(i))
        } catch (_: Exception) {}
        return out
    }

    fun isKidDownload(ctx: Context, id: String): Boolean = kidDownloadIds(ctx).contains(id)

    fun addKidDownload(ctx: Context, id: String) {
        val s = kidDownloadIds(ctx); if (s.add(id)) saveKidDownloads(ctx, s)
    }

    fun removeKidDownload(ctx: Context, id: String) {
        val s = kidDownloadIds(ctx); if (s.remove(id)) saveKidDownloads(ctx, s)
    }

    private fun saveKidDownloads(ctx: Context, ids: Set<String>) {
        val arr = JSONArray(); ids.forEach { arr.put(it) }
        prefs(ctx).edit().putString("kidDownloads", arr.toString()).apply()
    }
}
