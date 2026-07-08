package com.mykiddietv.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mykiddietv.app.databinding.ActivityKidmoviesBinding
import java.util.concurrent.Executors

/**
 * Kid Movies & Shows. Shows only what the parent whitelisted: standalone movies
 * (from [Profiles.allowedVod]) and individual episodes grouped by series
 * (from [Profiles.allowedEpisodes]). Tapping plays via the VOD player in kid mode.
 */
class KidMoviesActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var b: ActivityKidmoviesBinding
    private val adapter = RowAdapter()
    private var connected = false
    private var inStreaming = false        // in the "Live Movies & Shows" list (or deeper)
    private var inSeries: String? = null   // non-null = viewing a series' episodes
    private var band = AgeBands.YOUNGER
    private var streamingRows: List<ChannelsActivity.Row> = emptyList()
    // Auto mode (kid browses everything within their age cap): current category / series being viewed.
    private var autoCat: Portal.VodCat? = null
    private var autoSeries: Portal.VodItem? = null
    private var autoInEpisodes = false

    private fun autoMode(): Boolean = Profiles.activeKid(this)?.filterMode == "auto"
    private fun kidHideUnrated(): Boolean = Profiles.activeKid(this)?.hideUnrated ?: true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKidmoviesBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        band = Profiles.activeBand(this)
        b.search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { applyStreamingFilter(s?.toString() ?: "") }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        connectPortal()
        showHome()
    }

    override fun onResume() {
        super.onResume()
        KidGuard.immersive(this)
        KidLimits.onResume(this)
    }

    override fun onPause() {
        super.onPause()
        KidLimits.onPause(this)
    }

    private fun connectPortal() {
        val acct = Configs.active(this) ?: return
        Portal.portalUrl = acct.portal
        Portal.mac = acct.mac
        Portal.sn = acct.sn
        io.execute {
            val err = Portal.connect()
            runOnUiThread { connected = err == null }
        }
    }

    private fun showHome() {
        inStreaming = false; inSeries = null
        autoCat = null; autoSeries = null
        b.title.text = "Movies & Shows"
        b.search.visibility = View.GONE
        b.status.visibility = View.GONE
        adapter.submit(listOf(
            ChannelsActivity.Row("📺  Live Movies & Shows", null, "") { showStreaming() },
            ChannelsActivity.Row("⬇  Downloaded Movies & Shows", null, "") {
                OfflineActivity.kidMode = true
                startActivity(Intent(this, OfflineActivity::class.java))
            }
        ))
        b.list.scrollToPosition(0)
    }

    /** Approved content streamed online (needs internet). */
    private fun showStreaming() {
        inStreaming = true; inSeries = null
        if (autoMode()) { showAutoCategories(); return }
        b.title.text = "Live Movies & Shows"
        val movies = Profiles.allowedVod(this).filter { !it.isSeries }
        val bySeries = Profiles.allowedEpisodes(this).groupBy { it.seriesId }
        val rows = ArrayList<ChannelsActivity.Row>()
        movies.forEach { v ->
            rows.add(ChannelsActivity.Row("🎬  ${v.name}", v.posterUrl, v.name) { openMovie(v) })
        }
        bySeries.forEach { (sid, eps) ->
            val name = eps.first().seriesName
            rows.add(ChannelsActivity.Row("📁  $name  (${eps.size})", eps.first().poster, name) { showEpisodes(sid) })
        }
        rows.sortBy { it.sortKey.lowercase() }
        streamingRows = rows
        b.search.visibility = if (AgeBands.showsSearch(band) && rows.isNotEmpty()) View.VISIBLE else View.GONE
        applyStreamingFilter(b.search.text?.toString() ?: "")
        b.list.scrollToPosition(0)
    }

    /** Filter the streaming list by the search box (only shown for Older+ bands). */
    private fun applyStreamingFilter(q: String) {
        val query = q.trim().lowercase()
        val filtered = if (query.isEmpty()) streamingRows else streamingRows.filter { it.sortKey.lowercase().contains(query) }
        b.status.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        b.status.text = when {
            streamingRows.isEmpty() -> "Nothing added yet. Ask a grown-up to add movies or shows."
            filtered.isEmpty() -> "No matches."
            else -> ""
        }
        adapter.submit(filtered)
    }

    /** Preschool taps a movie and it just plays; older bands open a band-aware preview screen first. */
    private fun openMovie(v: Portal.VodItem) {
        if (AgeBands.tapPlaysDirectly(band)) { playMovie(v); return }
        startActivity(Intent(this, KidDetailActivity::class.java)
            .putExtra("vodId", v.id).putExtra("cmd", v.cmd)
            .putExtra("title", v.name).putExtra("poster", v.posterUrl))
    }

    // ---- Auto mode: browse the whole catalog, filtered to the kid's age cap ----
    private fun showAutoCategories() {
        autoCat = null; autoSeries = null; autoInEpisodes = false
        b.title.text = "Movies & Shows"
        b.search.visibility = View.GONE
        b.status.visibility = View.VISIBLE; b.status.text = "Loading…"
        io.execute {
            val cats = Portal.vodCategories()
            runOnUiThread {
                val visible = cats.filter { ContentProfiles.vodCatVisible(this, it.id) } // honor content-profile guardrails
                b.status.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
                if (visible.isEmpty()) b.status.text = "Nothing to show right now."
                adapter.submit(visible.map { c -> ChannelsActivity.Row("📁  ${c.title}", null, c.title) { showAutoCategory(c) } })
                b.list.scrollToPosition(0)
            }
        }
    }

    private fun showAutoCategory(cat: Portal.VodCat) {
        autoCat = cat; autoSeries = null; autoInEpisodes = false
        b.title.text = cat.title
        b.status.visibility = View.VISIBLE; b.status.text = "Loading ${cat.title}…"
        io.execute {
            val (items, pages) = Portal.vodList(cat.id, 1)
            val rows = autoRows(cat, ArrayList(items), 1, pages)
            runOnUiThread {
                b.status.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                if (rows.isEmpty()) b.status.text = "Nothing age-appropriate here yet."
                adapter.submit(rows); b.list.scrollToPosition(0)
            }
        }
    }

    /** Build rows for a category page, keeping only titles within the kid's cap. Runs on io (cert lookups, cached). */
    private fun autoRows(cat: Portal.VodCat, acc: ArrayList<Portal.VodItem>, loaded: Int, total: Int): List<ChannelsActivity.Row> {
        val hide = kidHideUnrated()
        val rows = ArrayList<ChannelsActivity.Row>()
        for (v in acc) {
            // Pass the real release year so the cert lookup matches the RIGHT film (an R title must not
            // get a same-name lower-rated film's certificate and slip past the age cap).
            if (!KidRating.show(this, v.name, v.year, band, hide)) continue
            if (v.isSeries) rows.add(ChannelsActivity.Row("📁  ${v.name}", v.posterUrl, v.name) { showAutoSeasons(v) })
            else rows.add(ChannelsActivity.Row("🎬  ${v.name}", v.posterUrl, v.name) { openMovie(v) })
        }
        if (loaded < total) rows.add(ChannelsActivity.Row("⬇  Load more", null, "zzzzz") {
            b.status.visibility = View.VISIBLE; b.status.text = "Loading…"
            io.execute {
                val (more, _) = Portal.vodList(cat.id, loaded + 1)
                acc.addAll(more)
                val rows2 = autoRows(cat, acc, loaded + 1, total)
                runOnUiThread { b.status.visibility = View.GONE; adapter.submit(rows2) }
            }
        })
        return rows
    }

    private fun showAutoSeasons(series: Portal.VodItem) {
        autoSeries = series; autoInEpisodes = false
        b.title.text = series.name
        b.status.visibility = View.VISIBLE; b.status.text = "Loading ${series.name}…"
        io.execute {
            val seasons = Portal.seriesSeasons(series.id)
            runOnUiThread {
                b.status.visibility = if (seasons.isEmpty()) View.VISIBLE else View.GONE
                if (seasons.isEmpty()) { b.status.text = "No episodes yet."; return@runOnUiThread }
                adapter.submit(seasons.reversed().map { s ->
                    ChannelsActivity.Row("📁  ${s.name}", null, s.name) { showAutoEpisodes(series, s) }
                })
                b.list.scrollToPosition(0)
            }
        }
    }

    private fun showAutoEpisodes(series: Portal.VodItem, season: Portal.Season) {
        autoInEpisodes = true
        b.title.text = "${series.name} — ${season.name}"
        b.status.visibility = View.VISIBLE; b.status.text = "Loading ${season.name}…"
        io.execute {
            val eps = Portal.seriesEpisodes(series.id, season.id)
            runOnUiThread {
                b.status.visibility = if (eps.isEmpty()) View.VISIBLE else View.GONE
                if (eps.isEmpty()) { b.status.text = "No episodes."; return@runOnUiThread }
                adapter.submit(eps.reversed().map { e ->
                    ChannelsActivity.Row("🎬  ${e.name}", series.posterUrl, e.name) {
                        play("${series.name} — ${e.name}") { Portal.playEpisodeUrl(series.id, season.id, e.id) }
                    }
                })
                b.list.scrollToPosition(0)
            }
        }
    }

    private fun showEpisodes(seriesId: String) {
        val eps = Profiles.allowedEpisodes(this).filter { it.seriesId == seriesId }
        if (eps.isEmpty()) { showStreaming(); return }
        inStreaming = true; inSeries = seriesId
        b.title.text = eps.first().seriesName
        b.search.visibility = View.GONE
        b.status.visibility = View.GONE
        adapter.submit(eps.map { ep ->
            ChannelsActivity.Row("🎬  ${ep.name}", ep.poster, ep.name) { playEpisode(ep) }
        })
        b.list.scrollToPosition(0)
    }

    private fun playMovie(v: Portal.VodItem) = play(v.name) { Portal.playVodUrl(v.id, v.cmd) }

    private fun playEpisode(ep: Profiles.KidEpisode) =
        play("${ep.seriesName} — ${ep.name}") { Portal.playEpisodeUrl(ep.seriesId, ep.seasonId, ep.episodeId) }

    private fun play(title: String, resolve: () -> String?) {
        if (!connected) { Toast.makeText(this, "Still getting ready…", Toast.LENGTH_SHORT).show(); return }
        b.status.visibility = View.VISIBLE
        b.status.text = "Opening $title…"
        io.execute {
            val url = resolve()
            runOnUiThread {
                if (url.isNullOrEmpty()) {
                    b.status.visibility = View.VISIBLE
                    b.status.text = "Couldn't play “$title”. Try again."
                } else {
                    b.status.visibility = View.GONE
                    PlayerActivity.kidMode = true
                    startActivity(
                        Intent(this, PlayerActivity::class.java)
                            .putExtra("url", url)
                            .putExtra("title", title)
                    )
                }
            }
        }
    }

    override fun onBackPressed() {
        when {
            autoInEpisodes -> { val s = autoSeries; if (s != null) showAutoSeasons(s) else showAutoCategories() }
            autoSeries != null -> { val c = autoCat; if (c != null) showAutoCategory(c) else showAutoCategories() }
            autoCat != null -> showAutoCategories()
            inSeries != null -> showStreaming()
            inStreaming -> showHome()
            else -> super.onBackPressed()
        }
    }
}
