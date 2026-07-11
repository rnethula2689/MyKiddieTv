package com.mykiddietv.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    // Full-catalogue browse (manageContent = false): current category being viewed. (Series now open
    // their own rich detail screen [KidDetailActivity], so no season/episode state lives here.)
    private var autoCat: Portal.VodCat? = null
    // Full-catalogue browse controls (search / A–Z / sort).
    private var searchSeq = 0
    private var pendingSearch: Runnable? = null
    private var autoSortAdded = true       // true = recently added (default), false = A–Z by name
    // Bumped on any navigation so a stale progressive page-load can't append into the wrong screen.
    private var autoLoadSeq = 0

    /** false = kid browses the whole catalogue; true = only the parent-approved whitelist. */
    private fun manageContent(): Boolean = Profiles.activeManageContent(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKidmoviesBinding.inflate(layoutInflater)
        setContentView(b.root)
        // One grid for everything (mirrors the parent side): folder chips tile in columns, movie
        // posters tile in columns, and normal rows (Load more, home entries) span the full width.
        val wdp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val chipCols = (wdp / 200f).toInt().coerceIn(2, 4)
        val posterCols = if (wdp >= 900) 6 else if (wdp >= 600) 5 else 3
        val total = 60
        val chipSpan = total / chipCols
        val posterSpan = total / posterCols
        val glm = androidx.recyclerview.widget.GridLayoutManager(this, total)
        glm.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = when {
                adapter.isChip(position) -> chipSpan
                adapter.isPoster(position) -> posterSpan
                else -> total
            }
        }
        b.list.layoutManager = glm
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
        autoCat = null; ++autoLoadSeq
        b.title.text = "Movies & Shows"
        setTools(search = false, category = false)
        b.status.visibility = View.GONE
        adapter.submit(listOf(
            ChannelsActivity.Row("📺  Live Movies & Shows", null, "", chip = true) { showStreaming() },
            ChannelsActivity.Row("⬇  Downloaded Movies & Shows", null, "", chip = true) {
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
            rows.add(ChannelsActivity.Row("🎬  ${v.name}", v.posterUrl, v.name, poster = true) { openMovie(v) })
        }
        bySeries.forEach { (sid, eps) ->
            val name = eps.first().seriesName
            // Curated subset of approved episodes (not a full series) → poster art, opens the episode list.
            rows.add(ChannelsActivity.Row(name, eps.first().poster, name, poster = true) { showEpisodes(sid) })
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

    /** Full-access series → rich kid detail screen (TMDb overview/rating/trailer + season/episode list). */
    private fun openSeries(v: Portal.VodItem) {
        startActivity(Intent(this, KidDetailActivity::class.java)
            .putExtra("isSeries", true)
            .putExtra("vodId", v.id).putExtra("cmd", v.cmd)
            .putExtra("title", v.name).putExtra("poster", v.posterUrl))
    }

    // ---- Auto mode: browse the whole catalog, filtered to the kid's age cap ----
    private fun showAutoCategories() {
        autoCat = null; ++autoLoadSeq
        b.title.text = "Movies & Shows"
        setTools(search = true, category = false)   // global search across the kid's allowed folders
        b.status.visibility = View.VISIBLE; b.status.text = "Loading…"
        io.execute {
            val cats = Portal.vodCategories()
            runOnUiThread {
                val visible = cats.filter { Profiles.vodFolderAllowed(this, it.id) } // only this kid's allowed folders
                b.status.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
                if (visible.isEmpty()) b.status.text = "Nothing to show right now."
                adapter.submit(visible.map { c -> ChannelsActivity.Row("📁  ${c.title}", null, c.title, chip = true) { showAutoCategory(c) } })
                b.list.scrollToPosition(0)
            }
        }
    }

    // Concurrent page fetches for the progressive category load, kept OFF the single `io` thread so
    // tapping a title / opening a series responds instantly during a big load (mirrors Vibe's pageIo).
    private val pageIo = Executors.newFixedThreadPool(3)

    private fun showAutoCategory(cat: Portal.VodCat) {
        autoCat = cat
        val seq = ++autoLoadSeq   // any navigation here supersedes an in-flight progressive load
        setTools(search = true, category = true)   // search + ⇅ sort + A–Z
        b.title.text = cat.title
        b.status.visibility = View.VISIBLE; b.status.text = "Loading ${cat.title}…"
        io.execute {
            val (items, pages) = Portal.vodList(cat.id, 1, autoSort())
            runOnUiThread {
                if (seq != autoLoadSeq) return@runOnUiThread
                b.status.visibility = if (items.isEmpty() && pages <= 1) View.VISIBLE else View.GONE
                if (items.isEmpty() && pages <= 1) b.status.text = "Nothing here yet."
                adapter.submit(autoRows(items)); b.list.scrollToPosition(0)
                if (pages > 1) { b.status.visibility = View.VISIBLE; b.status.text = "Loading…  ${items.size} titles" }
            }
            if (pages <= 1) return@execute
            // Pages 2..N fetch CONCURRENTLY on pageIo and append in order with a live count (Vibe's
            // loadVodAll pattern). A dedicated driver thread collects the futures so neither the io
            // thread nor a pool thread blocks; the seq guard drops stale fetches + appends.
            val last = pages.coerceAtMost(1000)   // runaway guard
            val sort = autoSort()
            val futures = (2..last).map { p ->
                pageIo.submit(java.util.concurrent.Callable {
                    if (seq != autoLoadSeq) emptyList() else try { Portal.vodList(cat.id, p, sort).first } catch (_: Exception) { emptyList() }
                })
            }
            Thread {
                var shown = items.size
                for (f in futures) {
                    if (seq != autoLoadSeq) break
                    val more = try { f.get() } catch (_: Exception) { emptyList() }
                    if (more.isEmpty()) continue
                    shown += more.size
                    val count = shown
                    runOnUiThread {
                        if (seq != autoLoadSeq) return@runOnUiThread
                        adapter.append(autoRows(more))
                        b.status.text = "Loading…  $count titles"
                    }
                }
                runOnUiThread { if (seq == autoLoadSeq) b.status.visibility = View.GONE }
            }.start()
        }
    }

    /** Build rows for a batch of catalogue items (whole catalogue — this kid is not content-managed).
     *  Movies + series both tile as posters (with art); tapping a series opens its rich detail screen. */
    private fun autoRows(items: List<Portal.VodItem>): List<ChannelsActivity.Row> =
        items.map { v ->
            if (v.isSeries) ChannelsActivity.Row(v.name, v.posterUrl, v.name, poster = true) { openSeries(v) }
            else ChannelsActivity.Row("🎬  ${v.name}", v.posterUrl, v.name, poster = true) { openMovie(v) }
        }

    private fun showEpisodes(seriesId: String) {
        val eps = Profiles.allowedEpisodes(this).filter { it.seriesId == seriesId }
        if (eps.isEmpty()) { showStreaming(); return }
        inStreaming = true; inSeries = seriesId
        b.title.text = eps.first().seriesName
        setTools(search = false, category = false)
        b.status.visibility = View.GONE
        adapter.submit(eps.map { ep ->
            ChannelsActivity.Row("🎬  ${ep.name}", ep.poster, ep.name, poster = true) { playEpisode(ep) }
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
        if (v.isSeries) ChannelsActivity.Row(v.name, v.posterUrl, v.name, poster = true) { openSeries(v) }
        else ChannelsActivity.Row("🎬  ${v.name}", v.posterUrl, v.name, poster = true) { openMovie(v) }

    /** Search movies/shows. All folders → global (index + every portal page); restricted → only the
     *  kid's allowed folders (so hidden adult folders never surface in results). */
    private fun movieSearch(query: String) {
        pendingSearch?.let { ui.removeCallbacks(it) }; searchSeq++
        if (query.isEmpty()) { autoCat?.let { showAutoCategory(it) } ?: showAutoCategories(); return }
        if (query.length < 2) return
        ++autoLoadSeq   // stop any in-flight category page-load appending under the search results
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
        ++autoLoadSeq   // stop any in-flight category page-load appending under the letter view
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
