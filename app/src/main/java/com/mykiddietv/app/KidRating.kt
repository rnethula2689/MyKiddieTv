package com.mykiddietv.app

import android.content.Context

/**
 * Resolves a title's content certification (e.g. "PG-13", "TV-14") for the age-cap filter, and
 * decides whether a given kid may see it. TMDB is the primary source; OMDb's "Rated" fills gaps.
 * Results are cached on disk so a browse only pays the lookup cost once per title.
 *
 * Network calls — call [cert]/[show] off the main thread.
 */
object KidRating {
    private const val PREF = "kid_ratings"
    private const val NONE = "?"   // sentinel: looked up but no certification found

    private fun key(title: String, year: String) = (Tmdb.cleanTitle(title) + "|" + year).lowercase()

    /** Cached certification for a title ("" = none found). */
    fun cert(ctx: Context, title: String, year: String): String {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val k = key(title, year)
        p.getString(k, null)?.let { return if (it == NONE) "" else it }
        var c = try { Tmdb.certification(BuildConfig.TMDB_KEY, title, year) } catch (_: Exception) { null }
        if (c.isNullOrBlank() && BuildConfig.OMDB_KEY.isNotBlank())
            c = try { Omdb.rated(BuildConfig.OMDB_KEY, title, year) } catch (_: Exception) { null }
        val v = c?.takeIf { it.isNotBlank() && it != "N/A" } ?: ""
        p.edit().putString(k, if (v.isBlank()) NONE else v).apply()
        return v
    }

    /** Whether [title] should be shown to a kid in [band], honoring [hideUnrated]. */
    fun show(ctx: Context, title: String, year: String, band: Int, hideUnrated: Boolean): Boolean =
        AgeBands.allows(cert(ctx, title, year).ifBlank { null }, band, hideUnrated)
}
