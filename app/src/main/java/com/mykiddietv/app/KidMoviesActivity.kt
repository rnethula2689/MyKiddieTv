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
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var b: ActivityKidmoviesBinding
    private val adapter = RowAdapter()
    private var connected = false
    private var inStreaming = false        // in the "Live Movies & Shows" list (or deeper)
    private var inSeries: String? = null   // non-null = viewing a series' episodes
    private var streamingRows: List<ChannelsActivity.Row> = emptyList()
    // Full-catalogue browse (manageContent = false): current category / series being viewed.
    private var autoCat: Portal.VodCat? = null
    private var autoSeries: Portal.VodItem? = null
    private var autoInEpisodes = false
    // Full-catalogue browse controls (search / A–Z / sort).
    private var searchSeq = 0
    private var pendingSearch: Runnable? = null
    private var autoSortAdded = true       // true = recently added (default), false = A–Z by name

    /** false = kid browses the whole catalogue; true = only the parent-approved whitelist. */
    private fun manageContent(): Boolean = Profiles.activeManageContent(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKidmoviesBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.searchBtn.setOnClickListener { toggleSearch() }
        b.sortBtn.setOnClickListener { cycleSort() }
        buildAzBar()
        b.search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { onSearchText(s?.toString() ?: "") }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        connectPortal()
        showHome()
    }

    /** Show/hide the 🔍 button and the ⇅ sort + A–Z bar for the current screen. */
    private fun setTools(search: Boolean, category: Boolean) {
        b.searchBtn.visibility = if (search) View.VISIBLE else View.GONE
        b.sortBtn.visibility = if (category) View.VISIBLE else View.GONE
        b.azScroll.visibility = if (category) View.VISIBLE else View.GONE
        if (!search && b.search.visibility == View.VISIBLE) { b.search.setText(""); b.search.visibility = View.GONE }
    }

    private fun toggleSearch() {
        if (b.search.visibility == View.VISIBLE) { b.search.setText(""); b.search.visibility = View.GONE }
        else {
            b.search.visibility = View.VISIBLE; b.search.requestFocus()
            (getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(b.search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun onSearchText(q: String) {
        if (manageContent()) applyStreamingFilter(q) else movieSearch(q.trim())
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
            if (err == null) VodIndex.ensure(applicationContext, acct.sig()) // instant search index
            runOnUiThread { connected = err == null }
        }
    }

    private fun showHome() {
        inStreaming = false; inSeries = null
        autoCat = null; autoSeries = null
        b.title.text = "Movies & Shows"
        setTools(search = false, category = false)
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
        if (!manageContent()) { showAutoCategories(); return } // full catalogue (no whitelist)
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
        setTools(search = rows.isNotEmpty(), category = false)
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
        startActivity(Intent(this, KidDetailActivity::class.java)
            .putExtra("vodId", v.id).putExtra("cmd", v.cmd)
            .putExtra("title", v.name).putExtra("poster", v.posterUrl))
    }

    // ---- Auto mode: browse the whole catalog, filtered to the kid's age cap ----
    private fun showAutoCategories() {
        autoCat = null; autoSeries = null; autoInEpisodes = false
        b.title.text = "Movies & Shows"
        setTools(search = true, category = false)   // global search across the kid's allowed folders
        b.status.visibility = View.VISIBLE; b.status.text = "Loading…"
        io.execute {
            val cats = Portal.vodCategories()
            runOnUiThread {
                val visible = cats.filter { Profiles.vodFolderAllowed(this, it.id) } // only this kid's allowed folders
                b.status.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
                if (visible.isEmpty()) b.status.text = "Nothing to show right now."
                adapter.submit(visible.map { c -> ChannelsActivity.Row("📁  ${c.title}", null, c.title) { showAutoCategory(c) } })
                b.list.scrollToPosition(0)
            }
        }
    }

    private fun showAutoCategory(cat: Portal.VodCat) {
        autoCat = cat; autoSeries = null; autoInEpisodes = false
        setTools(search = true, category = true)   // search + ⇅ sort + A–Z
        b.title.text = cat.title
        b.status.visibility = View.VISIBLE; b.status.text = "Loading ${cat.title}…"
        io.execute {
            val (items, pages) = Portal.vodList(cat.id, 1, autoSort())
            val rows = autoRows(cat, ArrayList(items), 1, pages)
            runOnUiThread {
                b.status.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                if (rows.isEmpty()) b.status.text = "Nothing here yet."
                adapter.submit(rows); b.list.scrollToPosition(0)
            }
        }
    }

    /** Build rows for a category page (whole catalogue — this kid is not content-managed). */
    private fun autoRows(cat: Portal.VodCat, acc: ArrayList<Portal.VodItem>, loaded: Int, total: Int): List<ChannelsActivity.Row> {
        val rows = ArrayList<ChannelsActivity.Row>()
        for (v in acc) {
            if (v.isSeries) rows.add(ChannelsActivity.Row("📁  ${v.name}", v.posterUrl, v.name) { showAutoSeasons(v) })
            else rows.add(ChannelsActivity.Row("🎬  ${v.name}", v.posterUrl, v.name) { openMovie(v) })
        }
        if (loaded < total) rows.add(ChannelsActivity.Row("⬇  Load more", null, "zzzzz") {
            b.status.visibility = View.VISIBLE; b.status.text = "Loading…"
            io.execute {
                val (more, _) = Portal.vodList(cat.id, loaded + 1, autoSort())
                acc.addAll(more)
                val rows2 = autoRows(cat, acc, loaded + 1, total)
                runOnUiThread { b.status.visibility = View.GONE; adapter.submit(rows2) }
            }
        })
        return rows
    }

    private fun showAutoSeasons(series: Portal.VodItem) {
        autoSeries = series; autoInEpisodes = false
        setTools(search = false, category = false)
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
        setTools(search = false, category = false)
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
        setTools(search = false, category = false)
        b.status.visibility = View.GONE
        adapter.submit(eps.map { ep ->
            ChannelsActivity.Row("🎬  ${ep.name}", ep.poster, ep.name) { playEpisode(ep) }
        })
        b.list.scrollToPosition(0)
    }

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
        // An open search closes first; then the normal drill-up.
        if (b.search.visibility == View.VISIBLE) { b.search.setText(""); b.search.visibility = View.GONE; return }
        when {
            autoInEpisodes -> { val s = autoSeries; if (s != null) showAutoSeasons(s) else showAutoCategories() }
            autoSeries != null -> { val c = autoCat; if (c != null) showAutoCategory(c) else showAutoCategories() }
            autoCat != null -> showAutoCategories()
            inSeries != null -> showStreaming()
            inStreaming -> showHome()
            else -> super.onBackPressed()
        }
    }

    // ---- Full-catalogue search / A–Z / sort (full-access kids) ----
    private fun autoSort() = if (autoSortAdded) "added" else "name"

    private fun cycleSort() {
        autoSortAdded = !autoSortAdded
        b.sortBtn.text = if (autoSortAdded) "🕐" else "🔤"
        autoCat?.let { showAutoCategory(it) }
    }

    private fun movieRow(v: Portal.VodItem) =
        if (v.isSeries) ChannelsActivity.Row("📁  ${v.name}", v.posterUrl, v.name) { showAutoSeasons(v) }
        else ChannelsActivity.Row("🎬  ${v.name}", v.posterUrl, v.name) { openMovie(v) }

    /** Search movies/shows. All folders → global (index + every portal page); restricted → only the
     *  kid's allowed folders (so hidden adult folders never surface in results). */
    private fun movieSearch(query: String) {
        pendingSearch?.let { ui.removeCallbacks(it) }; searchSeq++
        if (query.isEmpty()) { autoCat?.let { showAutoCategory(it) } ?: showAutoCategories(); return }
        if (query.length < 2) return
        b.azScroll.visibility = View.GONE
        b.status.visibility = View.VISIBLE; b.status.text = "Searching…"
        val seq = searchSeq
        val kid = Profiles.activeKid(this)
        val task = Runnable {
            io.execute {
                val out = LinkedHashMap<String, Portal.VodItem>()
                if (kid == null || kid.allVodFolders) {
                    if (VodIndex.ready) VodIndex.search(query).forEach { out[it.id] = it }
                    Portal.vodSearch(query) { partial -> partial.forEach { out[it.id] = it }; postResults(seq, out.values.toList()) }
                } else {
                    for (cat in kid.vodFolders) {
                        Portal.vodSearchInCategory(cat, query).forEach { out[it.id] = it }
                        postResults(seq, out.values.toList())
                    }
                }
                postResults(seq, out.values.toList())
            }
        }
        pendingSearch = task; ui.postDelayed(task, 300)
    }

    private fun postResults(seq: Int, items: List<Portal.VodItem>) = runOnUiThread {
        if (seq != searchSeq) return@runOnUiThread
        b.status.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        if (items.isEmpty()) b.status.text = "No results."
        adapter.submit(items.map { movieRow(it) })
    }

    private fun buildAzBar() {
        val labels = listOf("ALL") + ('A'..'Z').map { it.toString() } + ('0'..'9').map { it.toString() }
        for (lbl in labels) {
            val tv = android.widget.TextView(this)
            tv.text = lbl; tv.setTextColor(0xFFE6EDF3.toInt()); tv.textSize = 15f
            tv.setPadding(20, 12, 20, 12); tv.isFocusable = true; tv.isClickable = true
            tv.setBackgroundResource(R.drawable.item_bg)
            tv.setOnClickListener { azFilter(if (lbl == "ALL") null else lbl) }
            b.azBar.addView(tv)
        }
    }

    private fun azFilter(letter: String?) {
        val cat = autoCat ?: return
        if (b.search.text.isNotEmpty()) b.search.setText("")
        if (letter == null) { showAutoCategory(cat); return }
        b.status.visibility = View.VISIBLE; b.status.text = "Loading “$letter”…"
        val seq = ++searchSeq
        io.execute {
            val items = Portal.vodByLetter(cat.id, letter)
            runOnUiThread {
                if (seq != searchSeq) return@runOnUiThread
                b.status.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                if (items.isEmpty()) b.status.text = "No titles starting with “$letter”."
                adapter.submit(items.map { movieRow(it) }); b.list.scrollToPosition(0)
            }
        }
    }
}
