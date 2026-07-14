package com.mykiddietv.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kid-mode store. Holds the parent name + passcode (global) and a LIST of kid profiles — each with
 * its own name, avatar, age band, content-filter mode, and whitelist of live channels / VOD titles /
 * episodes (+ which downloads were made for that kid). One kid is "active" at a time; the legacy
 * single-kid accessors (kidName, allowedVod, …) transparently operate on the active kid so the rest
 * of the app didn't need rewiring. Kept in its own SharedPreferences file ("kids").
 *
 * Migrates a pre-multi-kid install (flat kidName/channels/vod/episodes keys) into one Kid on first read.
 */
object Profiles {
    private const val PREF = "kids"
    private const val KEY_LIST = "kidsList"
    private const val KEY_ACTIVE = "activeKidId"

    /** Set true when a kid whitelist changes, so the kid home reloads on resume. */
    var dirty = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---- kid model ----
    data class Kid(
        val id: String,
        var name: String,
        var avatar: String = "",              // "" | "emoji:🦁" | "file:/path" (see Avatars)
        var manageContent: Boolean = true,    // true = kid sees only the parent-approved whitelist; false = full catalogue
        // For a FULL-ACCESS kid (manageContent = false): which folders they may browse. all* = every folder,
        // otherwise only the listed live-genre / vod-category ids. Lets a parent hide adult folders. (Ignored for a managed kid.)
        var allLiveFolders: Boolean = true,
        val liveFolders: MutableSet<String> = LinkedHashSet(),
        var allVodFolders: Boolean = true,
        val vodFolders: MutableSet<String> = LinkedHashSet(),
        val channels: MutableList<Portal.Channel> = ArrayList(),
        val vod: MutableList<Portal.VodItem> = ArrayList(),
        val episodes: MutableList<KidEpisode> = ArrayList(),
        val downloads: LinkedHashSet<String> = LinkedHashSet()
    )

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

    // ---- (de)serialization ----
    private fun kidToJson(k: Kid): JSONObject {
        val ch = JSONArray()
        for (c in k.channels) ch.put(JSONObject()
            .put("id", c.id).put("name", c.name).put("number", c.number)
            .put("cmd", c.cmd).put("logo", c.logoUrl).put("genreId", c.genreId))
        val vd = JSONArray()
        for (v in k.vod) vd.put(JSONObject()
            .put("id", v.id).put("name", v.name).put("cmd", v.cmd)
            .put("poster", v.posterUrl).put("isSeries", v.isSeries))
        val ep = JSONArray()
        for (e in k.episodes) ep.put(JSONObject()
            .put("seriesId", e.seriesId).put("seriesName", e.seriesName)
            .put("seasonId", e.seasonId).put("seasonName", e.seasonName).put("episodeId", e.episodeId)
            .put("name", e.name).put("poster", e.poster))
        val dl = JSONArray(); k.downloads.forEach { dl.put(it) }
        return JSONObject()
            .put("id", k.id).put("name", k.name).put("avatar", k.avatar)
            .put("manageContent", k.manageContent)
            .put("allLiveFolders", k.allLiveFolders).put("liveFolders", JSONArray(k.liveFolders.toList()))
            .put("allVodFolders", k.allVodFolders).put("vodFolders", JSONArray(k.vodFolders.toList()))
            .put("channels", ch).put("vod", vd).put("episodes", ep).put("downloads", dl)
    }

    private fun kidFromJson(o: JSONObject): Kid {
        val k = Kid(
            id = o.optString("id"),
            name = o.optString("name"),
            avatar = o.optString("avatar", ""),
            // Legacy profiles (pre-age-removal): anything that used to browse everything ("auto") maps to
            // full access; everyone else defaults to managed (approved-only) — the safe default.
            manageContent = o.optBoolean("manageContent", o.optString("filterMode", "pick") != "auto"),
            allLiveFolders = o.optBoolean("allLiveFolders", true),
            allVodFolders = o.optBoolean("allVodFolders", true)
        )
        o.optJSONArray("liveFolders")?.let { a -> for (i in 0 until a.length()) k.liveFolders.add(a.optString(i)) }
        o.optJSONArray("vodFolders")?.let { a -> for (i in 0 until a.length()) k.vodFolders.add(a.optString(i)) }
        o.optJSONArray("channels")?.let { a ->
            for (i in 0 until a.length()) { val c = a.optJSONObject(i) ?: continue
                k.channels.add(Portal.Channel(c.optString("id"), c.optString("name"), c.optString("number"),
                    c.optString("cmd"), c.optString("logo"), c.optString("genreId"))) }
        }
        o.optJSONArray("vod")?.let { a ->
            for (i in 0 until a.length()) { val v = a.optJSONObject(i) ?: continue
                k.vod.add(Portal.VodItem(v.optString("id"), v.optString("name"), v.optString("cmd"),
                    v.optString("poster"), v.optBoolean("isSeries"))) }
        }
        o.optJSONArray("episodes")?.let { a ->
            for (i in 0 until a.length()) { val e = a.optJSONObject(i) ?: continue
                k.episodes.add(KidEpisode(e.optString("seriesId"), e.optString("seriesName"),
                    e.optString("seasonId"), e.optString("seasonName"), e.optString("episodeId"),
                    e.optString("name"), e.optString("poster"))) }
        }
        o.optJSONArray("downloads")?.let { a -> for (i in 0 until a.length()) k.downloads.add(a.optString(i)) }
        return k
    }

    // ---- kid list ----
    fun kids(ctx: Context): MutableList<Kid> {
        migrateIfNeeded(ctx)
        val raw = prefs(ctx).getString(KEY_LIST, "[]") ?: "[]"
        val out = ArrayList<Kid>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out.add(kidFromJson(it)) }
        } catch (_: Exception) {}
        return out
    }

    fun kid(ctx: Context, id: String?): Kid? = if (id == null) null else kids(ctx).firstOrNull { it.id == id }

    fun activeKidId(ctx: Context): String? = prefs(ctx).getString(KEY_ACTIVE, null)

    fun setActiveKid(ctx: Context, id: String?) {
        prefs(ctx).edit().apply { if (id == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, id) }.apply()
    }

    /** The active kid, falling back to the first one so the kid screens always have a subject. */
    fun activeKid(ctx: Context): Kid? = kid(ctx, activeKidId(ctx)) ?: kids(ctx).firstOrNull()

    private fun saveKids(ctx: Context, list: List<Kid>) {
        val arr = JSONArray(); for (k in list) arr.put(kidToJson(k))
        prefs(ctx).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    /** Insert or update a kid by id. */
    fun saveKid(ctx: Context, k: Kid) {
        val list = kids(ctx)
        val idx = list.indexOfFirst { it.id == k.id }
        if (idx >= 0) list[idx] = k else list.add(k)
        saveKids(ctx, list)
        if (activeKidId(ctx) == null) setActiveKid(ctx, k.id)
        dirty = true
    }

    fun deleteKid(ctx: Context, id: String) {
        saveKids(ctx, kids(ctx).filter { it.id != id })
        if (activeKidId(ctx) == id) setActiveKid(ctx, kids(ctx).firstOrNull()?.id)
        dirty = true
    }

    fun newKidId(): String = "k" + System.currentTimeMillis().toString(36)

    /** Whether the active kid is limited to the parent-approved whitelist (true) or sees everything (false). */
    fun activeManageContent(ctx: Context): Boolean = activeKid(ctx)?.manageContent ?: true

    /** For the active FULL-ACCESS kid: is this live-genre / vod-category folder allowed? (Managed kids ignore this.) */
    fun liveFolderAllowed(ctx: Context, genreId: String): Boolean {
        val k = activeKid(ctx) ?: return true
        return k.allLiveFolders || k.liveFolders.contains(genreId)
    }
    fun vodFolderAllowed(ctx: Context, catId: String): Boolean {
        val k = activeKid(ctx) ?: return true
        return k.allVodFolders || k.vodFolders.contains(catId)
    }

    /** Persist mutations made to the active kid object (its lists were changed in place). */
    private fun saveActive(ctx: Context, k: Kid) { saveKid(ctx, k) }

    // ---- migration from the old single-kid layout ----
    private var migrated = false
    private fun migrateIfNeeded(ctx: Context) {
        if (migrated) return
        migrated = true
        val p = prefs(ctx)
        if (p.contains(KEY_LIST)) return   // already multi-kid
        // Pull the legacy flat keys, if any.
        val oldName = p.getString("kidName", "") ?: ""
        val oldAvatar = p.getString("kidAvatar", "") ?: ""
        val hasOldContent = !p.getString("channels", "[]").isNullOrBlank() && p.getString("channels", "[]") != "[]" ||
            !p.getString("vod", "[]").isNullOrBlank() && p.getString("vod", "[]") != "[]" ||
            !p.getString("episodes", "[]").isNullOrBlank() && p.getString("episodes", "[]") != "[]"
        if (oldName.isBlank() && !hasOldContent) { return }   // fresh install → no kid yet
        val k = Kid(id = newKidId(), name = oldName.ifBlank { "Kids" }, avatar = oldAvatar)
        // migrate content lists
        try { JSONArray(p.getString("channels", "[]")).let { a -> for (i in 0 until a.length()) { val c = a.optJSONObject(i) ?: continue
            k.channels.add(Portal.Channel(c.optString("id"), c.optString("name"), c.optString("number"), c.optString("cmd"), c.optString("logo"), c.optString("genreId"))) } } } catch (_: Exception) {}
        try { JSONArray(p.getString("vod", "[]")).let { a -> for (i in 0 until a.length()) { val v = a.optJSONObject(i) ?: continue
            k.vod.add(Portal.VodItem(v.optString("id"), v.optString("name"), v.optString("cmd"), v.optString("poster"), v.optBoolean("isSeries"))) } } } catch (_: Exception) {}
        try { JSONArray(p.getString("episodes", "[]")).let { a -> for (i in 0 until a.length()) { val e = a.optJSONObject(i) ?: continue
            k.episodes.add(KidEpisode(e.optString("seriesId"), e.optString("seriesName"), e.optString("seasonId"), e.optString("seasonName"), e.optString("episodeId"), e.optString("name"), e.optString("poster"))) } } } catch (_: Exception) {}
        try { JSONArray(p.getString("kidDownloads", "[]")).let { a -> for (i in 0 until a.length()) k.downloads.add(a.optString(i)) } } catch (_: Exception) {}
        val arr = JSONArray().put(kidToJson(k))
        p.edit().putString(KEY_LIST, arr.toString()).putString(KEY_ACTIVE, k.id).apply()
    }

    // ---- parent name + passcode (global) ----
    fun parentName(ctx: Context): String = prefs(ctx).getString("parentName", "")?.ifBlank { "Parent" } ?: "Parent"

    /** Parent tile picture on "Who's watching?" — "" = default 👤, or "emoji:X" / "file:/path". */
    fun parentAvatar(ctx: Context): String = prefs(ctx).getString("parentAvatar", "") ?: ""
    fun setParentAvatar(ctx: Context, v: String) { prefs(ctx).edit().putString("parentAvatar", v).apply() }

    fun setNames(ctx: Context, parent: String, kid: String) {
        prefs(ctx).edit().putString("parentName", parent.trim()).apply()
        // Legacy Settings dialog: rename the active kid too (kid names are per-kid now).
        val k = activeKid(ctx)
        if (k != null && kid.isNotBlank()) { k.name = kid.trim(); saveActive(ctx, k) }
    }

    /** Stored parent passcode (a salted hash, or legacy plaintext for pre-hash installs). Empty = not set. */
    private fun passcodeStored(ctx: Context): String = prefs(ctx).getString("passcode", "") ?: ""
    fun hasPasscode(ctx: Context): Boolean = passcodeStored(ctx).isNotEmpty()
    fun setPasscode(ctx: Context, code: String) {
        val t = code.trim()
        prefs(ctx).edit().putString("passcode", if (t.isEmpty()) "" else Secret.hash(t)).apply()
    }
    /** True when [entered] matches the parent passcode. Rate-limited (lockout after repeated failures) and
     *  migrates a legacy plaintext code to a salted hash on the first correct entry. */
    fun verifyPasscode(ctx: Context, entered: String): Boolean {
        if (Secret.lockedMs(ctx, "kidpass") > 0L) return false
        val stored = passcodeStored(ctx)
        val ok = Secret.verify(entered.trim(), stored)
        if (ok) { Secret.recordSuccess(ctx, "kidpass"); if (!Secret.isHashed(stored)) setPasscode(ctx, entered.trim()) }
        else Secret.recordFail(ctx, "kidpass")
        return ok
    }
    /** Seconds the passcode entry is locked for after too many wrong tries (0 = not locked). */
    fun passcodeLockSecs(ctx: Context): Long = (Secret.lockedMs(ctx, "kidpass") + 999) / 1000

    // ==== legacy single-kid accessors — operate on the ACTIVE kid ====

    fun kidName(ctx: Context): String = activeKid(ctx)?.name?.ifBlank { "Kids" } ?: "Kids"
    fun kidAvatar(ctx: Context): String = activeKid(ctx)?.avatar ?: ""
    fun setKidAvatar(ctx: Context, a: String) { val k = activeKid(ctx) ?: return; k.avatar = a; saveActive(ctx, k) }

    fun allowedChannels(ctx: Context): MutableList<Portal.Channel> = activeKid(ctx)?.channels ?: ArrayList()
    fun allowedChannelIds(ctx: Context): Set<String> = allowedChannels(ctx).map { it.id }.toSet()
    fun saveChannels(ctx: Context, list: List<Portal.Channel>) {
        val k = activeKid(ctx) ?: return; k.channels.clear(); k.channels.addAll(list); saveActive(ctx, k)
    }

    fun allowedVod(ctx: Context): MutableList<Portal.VodItem> = activeKid(ctx)?.vod ?: ArrayList()
    fun allowedVodIds(ctx: Context): Set<String> = allowedVod(ctx).map { it.id }.toSet()
    fun saveVod(ctx: Context, list: List<Portal.VodItem>) {
        val k = activeKid(ctx) ?: return; k.vod.clear(); k.vod.addAll(list); saveActive(ctx, k)
    }

    fun allowedEpisodes(ctx: Context): MutableList<KidEpisode> = activeKid(ctx)?.episodes ?: ArrayList()
    fun allowedEpisodeKeys(ctx: Context): Set<String> = allowedEpisodes(ctx).map { it.key }.toSet()
    fun saveEpisodes(ctx: Context, list: List<KidEpisode>) {
        val k = activeKid(ctx) ?: return; k.episodes.clear(); k.episodes.addAll(list); saveActive(ctx, k)
    }

    // ---- which downloads were made FOR THE ACTIVE KID (vs the parent's own downloads) ----
    fun kidDownloadIds(ctx: Context): LinkedHashSet<String> = activeKid(ctx)?.downloads ?: LinkedHashSet()
    fun kidDownloadIds(ctx: Context, kidId: String): LinkedHashSet<String> = kid(ctx, kidId)?.downloads ?: LinkedHashSet()
    /** A download id belongs to *any* kid (parent Downloads screen hides all of them). */
    fun isKidDownload(ctx: Context, id: String): Boolean = kids(ctx).any { it.downloads.contains(id) }
    fun addKidDownload(ctx: Context, id: String) { val k = activeKid(ctx) ?: return; if (k.downloads.add(id)) saveActive(ctx, k) }
    fun removeKidDownload(ctx: Context, id: String) {
        // Remove from whichever kid owns it.
        val list = kids(ctx); var changed = false
        for (k in list) if (k.downloads.remove(id)) changed = true
        if (changed) saveKids(ctx, list)
    }
    fun clearKidDownloads(ctx: Context, kidId: String): Set<String> {
        val list = kids(ctx)
        val k = list.firstOrNull { it.id == kidId } ?: return emptySet()
        val removed = LinkedHashSet(k.downloads)
        if (removed.isNotEmpty()) {
            k.downloads.clear()
            saveKids(ctx, list)
        }
        return removed
    }
}
