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
}
