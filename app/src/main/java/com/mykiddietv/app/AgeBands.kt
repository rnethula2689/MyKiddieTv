package com.mykiddietv.app

/**
 * Age bands for a kid profile (YouTube-Kids style). The band a kid is in decides how much
 * functionality their view exposes — the youngest just tap-and-watch; ratings, search, trailers
 * and the full info screen switch on progressively with age. Bands are defined by content rating.
 *
 * Stored on each [Profiles.Kid] as an Int id (0..3); default = YOUNGER.
 */
object AgeBands {
    const val PRESCHOOL = 0
    const val YOUNGER = 1
    const val OLDER = 2
    const val TEEN = 3

    data class Band(
        val id: Int,
        val emoji: String,
        val name: String,
        val ages: String,     // shown to the parent when choosing
        val rating: String    // the content rating this band maps to
    )

    val ALL = listOf(
        Band(PRESCHOOL, "👶", "Preschool",    "2–4",   "G · TV-Y"),
        Band(YOUNGER,   "🧒", "Younger Kids", "5–8",   "G–PG · TV-G"),
        Band(OLDER,     "🧑", "Older Kids",   "9–12",  "PG · TV-PG"),
        Band(TEEN,      "🧑‍🎓", "Teens",        "13–17", "PG-13 · TV-14")
    )

    fun of(id: Int): Band = ALL.getOrElse(id) { ALL[YOUNGER] }

    // ---- feature gates (what a band's kid view is allowed to show) ----
    /** Preschool taps a title and it plays immediately — no info/preview screen at all. */
    fun tapPlaysDirectly(band: Int): Boolean = band <= PRESCHOOL
    fun showsPreview(band: Int): Boolean = band >= YOUNGER          // any info/preview screen
    fun showsTrailers(band: Int): Boolean = band >= YOUNGER
    fun showsDescription(band: Int): Boolean = band >= YOUNGER      // short at YOUNGER, full at OLDER+
    fun showsFullDescription(band: Int): Boolean = band >= OLDER
    fun showsSearch(band: Int): Boolean = band >= OLDER
    fun showsRatings(band: Int): Boolean = band >= TEEN
    fun allowsDownloads(band: Int): Boolean = band >= YOUNGER

    /** Max US movie certification permitted for this band (used by the optional auto-filter). */
    fun maxMovieCert(band: Int): String = when (band) {
        PRESCHOOL -> "G"
        YOUNGER -> "PG"
        OLDER -> "PG"
        else -> "PG-13"
    }

    // ---- content-certification cap (for the pick-list filter + auto mode) ----
    // A single 0..3 "maturity level" that maps both movie (G/PG/PG-13/R) and TV (TV-Y…TV-MA) ratings.
    /** Highest maturity level this band may see. */
    fun maxLevel(band: Int): Int = when (band) {
        PRESCHOOL -> 0   // G / TV-Y / TV-G
        YOUNGER -> 1     // + PG / TV-PG
        OLDER -> 1
        else -> 2        // Teen: + PG-13 / TV-14
    }

    /** Map a certification string to a maturity level, or -1 if unknown / unrated. */
    fun certLevel(cert: String?): Int {
        val c = cert?.trim()?.uppercase() ?: return -1
        return when {
            c.isBlank() || c in setOf("N/A", "NR", "NOT RATED", "UNRATED", "UR", "TBD") -> -1
            c in setOf("G", "TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G", "U", "APPROVED", "E", "EC") -> 0
            c in setOf("PG", "TV-PG") -> 1
            c in setOf("PG-13", "TV-14", "12", "12A", "15") -> 2
            c in setOf("R", "TV-MA", "NC-17", "X", "MA-17", "18", "AO") -> 3
            else -> -1
        }
    }

    /** Should a title with this certification be shown to [band]? Unknown certs defer to [hideUnrated]. */
    fun allows(cert: String?, band: Int, hideUnrated: Boolean): Boolean {
        val lvl = certLevel(cert)
        if (lvl == -1) return !hideUnrated
        return lvl <= maxLevel(band)
    }
}
