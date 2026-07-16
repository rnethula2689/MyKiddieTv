package com.mykiddietv.app

import android.content.Context
import java.io.File

/**
 * Remembers the subtitle a user picked for a title, so resuming the movie or
 * playing a downloaded copy can re-load it without another search.
 */
object SubStore {
    private const val PREF = "substore"

    private fun dir(ctx: Context) = File(ctx.filesDir, "subs").apply { mkdirs() }
    private fun key(id: String) = "sub:$id"

    fun saved(ctx: Context, id: String): File? {
        if (id.isBlank()) return null
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(key(id), null) ?: return null
        val f = File(p)
        return if (f.exists()) f else null
    }

    fun stats(ctx: Context): Pair<Int, Long> {
        val files = dir(ctx).listFiles()?.filter { it.isFile } ?: emptyList()
        return files.size to files.sumOf { it.length() }
    }

    fun clearAll(ctx: Context): Int {
        var n = 0
        dir(ctx).listFiles()?.forEach { if (it.isFile && it.delete()) n++ }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
        return n
    }

    fun forget(ctx: Context, id: String) {
        if (id.isBlank()) return
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.getString(key(id), null)?.let { path -> try { File(path).delete() } catch (_: Exception) {} }
        prefs.edit().remove(key(id)).apply()
    }

    fun remember(ctx: Context, id: String, src: File): File {
        if (id.isBlank() || !src.exists()) return src
        val safe = id.replace(Regex("[^A-Za-z0-9_]"), "_").take(80)
        val dest = File(dir(ctx), "$safe.srt")
        return try {
            src.copyTo(dest, overwrite = true)
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(key(id), dest.absolutePath).apply()
            dest
        } catch (_: Exception) { src }
    }
}
