package com.mykiddietv.app

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mykiddietv.app.databinding.ActivityKidcontentBinding
import java.util.concurrent.Executors

/**
 * Parent-only screen for whitelisting kid content. It mirrors the parent home
 * (Live TV / Movies, global search, A-Z) but every channel / movie row carries a
 * checkbox. "Select all" ticks everything in the current folder, "Add selected"
 * asks for confirmation, then merges the picks into the kid whitelist ([Profiles]).
 */
class KidContentActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var b: ActivityKidcontentBinding
    private lateinit var adapter: KidPickAdapter

    enum class Kind { FOLDERS, GLOBAL, CHANNELS, VOD_ALL, VOD_CATEGORY }
    data class Page(
        val title: String,
        val nodes: List<KidNode>,
        val kind: Kind,
        val scopeChannels: List<Portal.Channel>? = null,
        val scopeCat: String? = null
    )

    private val backStack = ArrayDeque<Page>()
    private var displayed = listOf<KidNode>()   // what the adapter currently shows (page nodes or search/az results)

    // Pending picks (accumulate across folders until "Add selected").
    private val pendingChannels = LinkedHashMap<String, Portal.Channel>()
    private val pendingVod = LinkedHashMap<String, Portal.VodItem>()
    private val pendingEpisodes = LinkedHashMap<String, Profiles.KidEpisode>()
    // Already-saved ids/keys, for the "✓ added" badge. Refreshed after each add.
    private var savedChannelIds = setOf<String>()
    private var savedVodIds = setOf<String>()
    private var savedEpisodeKeys = setOf<String>()

    private var allChannels = listOf<Portal.Channel>()
    private var genres = listOf<Portal.Genre>()
    private var byGenre = mapOf<String, List<Portal.Channel>>()

    private var pendingSearch: Runnable? = null
    private var searchSeq = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKidcontentBinding.inflate(layoutInflater)
        setContentView(b.root)

        savedChannelIds = Profiles.allowedChannelIds(this)
        savedVodIds = Profiles.allowedVodIds(this)
        savedEpisodeKeys = Profiles.allowedEpisodeKeys(this)

        adapter = KidPickAdapter({ n -> isChecked(n) }, { pos -> onRowClick(pos) })
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter

        b.searchBtn.setOnClickListener { toggleSearch() }
        b.clearBtn.setOnClickListener { b.search.setText(""); b.search.requestFocus() }
        b.search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = filter(s?.toString() ?: "")
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
        })
        b.selectAllBtn.setOnClickListener { onSelectAll() }
        b.addBtn.setOnClickListener { onAddSelected() }
        buildAzBar()

        connectAndLoad()
    }

    private fun connectAndLoad() {
        val acct = Configs.active(this)
        if (acct == null) {
            b.status.text = "Add an IPTV provider first (IPTV Configuration in Settings)."
            return
        }
        Portal.portalUrl = acct.portal
        Portal.mac = acct.mac
        Portal.sn = acct.sn
        b.status.text = "Connecting…"
        io.execute {
            val err = Portal.connect()
            val ch = if (err == null) Portal.liveChannels() else emptyList()
            val g = if (err == null) Portal.liveGenres() else emptyList()
            runOnUiThread {
                if (err != null) { b.status.text = err; return@runOnUiThread }
                allChannels = ch
                genres = g
                byGenre = ch.groupBy { it.genreId }
                b.status.text = ""
                showHome()
            }
        }
    }

    // ---- selection helpers ----
    private fun isChecked(n: KidNode): Boolean =
        (n.channel != null && pendingChannels.containsKey(n.channel.id)) ||
            (n.vod != null && pendingVod.containsKey(n.vod.id)) ||
            (n.episode != null && pendingEpisodes.containsKey(n.episode.key))

    private fun toggle(n: KidNode) {
        n.channel?.let { if (pendingChannels.remove(it.id) == null) pendingChannels[it.id] = it }
        n.vod?.let { if (pendingVod.remove(it.id) == null) pendingVod[it.id] = it }
        n.episode?.let { if (pendingEpisodes.remove(it.key) == null) pendingEpisodes[it.key] = it }
    }

    private fun onRowClick(pos: Int) {
        val n = adapter.nodeAt(pos)
        val open = n.open
        if (open != null) { open(); return }
        toggle(n)
        adapter.notifyItemChanged(pos)
        updateBottomBar()
    }

    private fun pendingCount() = pendingChannels.size + pendingVod.size + pendingEpisodes.size

    private fun updateBottomBar() {
        val picks = displayed.filter { it.isPick }
        b.bottomBar.visibility = if (picks.isEmpty()) View.GONE else View.VISIBLE
        val allSelected = picks.isNotEmpty() && picks.all { isChecked(it) }
        b.selectAllBtn.text = if (allSelected) "Deselect all" else "Select all"
        b.addBtn.text = if (pendingCount() > 0) "Add selected (${pendingCount()})" else "Add selected"
    }

    private fun onSelectAll() {
        val picks = displayed.filter { it.isPick }
        if (picks.isEmpty()) return
        val allSelected = picks.all { isChecked(it) }
        if (allSelected) {
            picks.forEach {
                it.channel?.let { c -> pendingChannels.remove(c.id) }
                it.vod?.let { v -> pendingVod.remove(v.id) }
                it.episode?.let { e -> pendingEpisodes.remove(e.key) }
            }
        } else {
            picks.forEach {
                it.channel?.let { c -> pendingChannels[c.id] = c }
                it.vod?.let { v -> pendingVod[v.id] = v }
                it.episode?.let { e -> pendingEpisodes[e.key] = e }
            }
        }
        adapter.notifyDataSetChanged()
        updateBottomBar()
    }

    private fun onAddSelected() {
        if (pendingCount() == 0) {
            b.status.text = "Tick some channels or movies first."
            return
        }
        val names = pendingChannels.values.map { it.name } +
            pendingVod.values.map { it.name } +
            pendingEpisodes.values.map { "${it.seriesName} — ${it.name}" }
        val preview = names.take(12).joinToString("\n") { "• $it" } +
            (if (names.size > 12) "\n…and ${names.size - 12} more" else "")
        val msg = "Add ${pendingChannels.size} channel(s), ${pendingVod.size} movie(s)/show(s) " +
            "and ${pendingEpisodes.size} episode(s) to ${Profiles.kidName(this)}'s list?\n\n$preview"
        AlertDialog.Builder(this)
            .setTitle("Confirm")
            .setMessage(msg)
            .setPositiveButton("Yes, add") { _, _ -> commitSelection() }
            .setNegativeButton("Go back", null)
            .show()
    }

    private fun commitSelection() {
        // Merge picks into the saved whitelist (union — never drops existing).
        val ch = Profiles.allowedChannels(this).associateBy { it.id }.toMutableMap()
        pendingChannels.forEach { ch[it.key] = it.value }
        Profiles.saveChannels(this, ch.values.toList())

        val vod = Profiles.allowedVod(this).associateBy { it.id }.toMutableMap()
        pendingVod.forEach { vod[it.key] = it.value }
        Profiles.saveVod(this, vod.values.toList())

        val eps = Profiles.allowedEpisodes(this).associateBy { it.key }.toMutableMap()
        pendingEpisodes.forEach { eps[it.key] = it.value }
        Profiles.saveEpisodes(this, eps.values.toList())

        val added = pendingCount()
        pendingChannels.clear(); pendingVod.clear(); pendingEpisodes.clear()
        savedChannelIds = Profiles.allowedChannelIds(this)
        savedVodIds = Profiles.allowedVodIds(this)
        savedEpisodeKeys = Profiles.allowedEpisodeKeys(this)
        b.status.text = "Added $added item(s) to ${Profiles.kidName(this)}'s list ✓"
        // Rebuild current view so badges/checkboxes refresh.
        backStack.lastOrNull()?.let { display(rebuildBadges(it)) }
    }

    /** Re-evaluate the "already added" badge on a page's pick nodes. */
    private fun rebuildBadges(p: Page): Page {
        val nodes = p.nodes.map { n ->
            when {
                n.channel != null -> n.copy(alreadyAdded = savedChannelIds.contains(n.channel.id))
                n.vod != null -> n.copy(alreadyAdded = savedVodIds.contains(n.vod.id))
                n.episode != null -> n.copy(alreadyAdded = savedEpisodeKeys.contains(n.episode.key))
                else -> n
            }
        }
        return p.copy(nodes = nodes)
    }

    // ---- navigation ----
    private fun push(p: Page) { backStack.addLast(p); display(p) }

    private fun display(p: Page) {
        if (backStack.isNotEmpty()) backStack[backStack.size - 1] = p
        b.title.text = p.title
        if (b.search.text.isNotEmpty()) b.search.setText("")
        b.searchRow.visibility = View.GONE
        submit(p.nodes)
        b.list.scrollToPosition(0)
        b.list.requestFocus()
    }

    private fun submit(nodes: List<KidNode>) {
        displayed = nodes
        adapter.submit(nodes)
        updateBottomBar()
    }

    override fun onBackPressed() {
        if (b.searchRow.visibility == View.VISIBLE) {
            b.search.setText(""); b.searchRow.visibility = View.GONE; return
        }
        if (backStack.size > 1) {
            backStack.removeLast()
            display(backStack.last())
        } else super.onBackPressed()
    }

    // ---- screens ----
    private fun showHome() {
        backStack.clear()
        push(Page("Manage Kid Content", listOf(
            KidNode("📺   Live TV", null, "Live TV", open = { showLive() }),
            KidNode("🎬   Movies & Shows", null, "Movies", open = { showMovies() })
        ), Kind.GLOBAL))
    }

    private fun showLive() {
        val nodes = ArrayList<KidNode>()
        nodes.add(KidNode("All Channels  (${allChannels.size})", null, "All Channels",
            open = { showChannels(allChannels, "All Channels") }))
        for (g in genres) {
            val list = byGenre[g.id] ?: emptyList()
            if (list.isNotEmpty()) nodes.add(KidNode("${g.title}  (${list.size})", null, g.title,
                open = { showChannels(list, g.title) }))
        }
        push(Page("Live TV", nodes, Kind.CHANNELS, scopeChannels = allChannels))
    }

    private fun showChannels(list: List<Portal.Channel>, title: String) {
        push(Page(title, list.map { channelNode(it) }, Kind.CHANNELS, scopeChannels = list))
    }

    private fun showMovies() {
        b.status.text = "Loading movies…"
        io.execute {
            val cats = Portal.vodCategories()
            runOnUiThread {
                b.status.text = ""
                if (cats.isEmpty()) { b.status.text = "No movie categories found."; return@runOnUiThread }
                push(Page("Movies", cats.map { c ->
                    KidNode(c.title, null, c.title, open = { showVodList(c) })
                }, Kind.VOD_ALL))
            }
        }
    }

    private fun showVodList(cat: Portal.VodCat) {
        b.status.text = "Loading ${cat.title}…"
        io.execute {
            val (items, pages) = Portal.vodList(cat.id, 1)
            runOnUiThread {
                b.status.text = ""
                push(Page(cat.title, vodNodes(cat, ArrayList(items), 1, pages), Kind.VOD_CATEGORY, scopeCat = cat.id))
            }
        }
    }

    private fun vodNodes(cat: Portal.VodCat, acc: ArrayList<Portal.VodItem>, loaded: Int, total: Int): List<KidNode> {
        val nodes = ArrayList<KidNode>()
        acc.forEach { nodes.add(vodNode(it)) }
        if (loaded < total) {
            nodes.add(KidNode("⬇  Load more  ($loaded/$total)", null, "", open = {
                b.status.text = "Loading…"
                io.execute {
                    val (more, _) = Portal.vodList(cat.id, loaded + 1)
                    runOnUiThread {
                        b.status.text = ""
                        acc.addAll(more)
                        display(Page(cat.title, vodNodes(cat, acc, loaded + 1, total), Kind.VOD_CATEGORY, scopeCat = cat.id))
                    }
                }
            }))
        }
        return nodes
    }

    private fun channelNode(ch: Portal.Channel) = KidNode(
        "📺  " + (if (ch.number.isNotEmpty()) "${ch.number}. " else "") + ch.name,
        ch.logoUrl, ch.name, channel = ch, alreadyAdded = savedChannelIds.contains(ch.id)
    )

    // A movie is a pick; a series is a folder you drill into (seasons → episodes).
    private fun vodNode(v: Portal.VodItem): KidNode =
        if (v.isSeries)
            KidNode("📁  ${v.name}", v.posterUrl, v.name, open = { showSeasons(v) })
        else
            KidNode("🎬  ${v.name}", v.posterUrl, v.name, vod = v, alreadyAdded = savedVodIds.contains(v.id))

    private fun showSeasons(series: Portal.VodItem) {
        b.status.text = "Loading ${series.name}…"
        io.execute {
            val seasons = Portal.seriesSeasons(series.id)
            runOnUiThread {
                b.status.text = ""
                if (seasons.isEmpty()) { b.status.text = "No seasons found for ${series.name}."; return@runOnUiThread }
                push(Page(series.name, seasons.reversed().map { s ->
                    KidNode(s.name, null, s.name, open = { showEpisodes(series, s) })
                }, Kind.FOLDERS))
            }
        }
    }

    private fun showEpisodes(series: Portal.VodItem, season: Portal.Season) {
        b.status.text = "Loading ${season.name}…"
        io.execute {
            val eps = Portal.seriesEpisodes(series.id, season.id)
            runOnUiThread {
                b.status.text = ""
                if (eps.isEmpty()) { b.status.text = "No episodes found."; return@runOnUiThread }
                push(Page("${series.name} — ${season.name}", eps.reversed().map { e ->
                    episodeNode(series, season, e)
                }, Kind.FOLDERS))
            }
        }
    }

    private fun episodeNode(series: Portal.VodItem, season: Portal.Season, e: Portal.Episode): KidNode {
        val ep = Profiles.KidEpisode(
            seriesId = series.id, seriesName = series.name,
            seasonId = season.id, episodeId = e.id,
            name = e.name, poster = series.posterUrl
        )
        return KidNode("🎬  ${e.name}", series.posterUrl, e.name, episode = ep,
            alreadyAdded = savedEpisodeKeys.contains(ep.key))
    }

    // ---- search ----
    private fun toggleSearch() {
        if (b.searchRow.visibility == View.VISIBLE) {
            b.search.setText(""); b.searchRow.visibility = View.GONE
        } else {
            b.searchRow.visibility = View.VISIBLE
            b.search.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(b.search, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun filter(q: String) {
        val page = backStack.lastOrNull() ?: return
        val query = q.trim()
        when (page.kind) {
            Kind.FOLDERS -> submit(if (query.isEmpty()) page.nodes else page.nodes.filter { it.label.contains(query, true) })
            Kind.CHANNELS -> {
                if (query.isEmpty()) { submit(page.nodes); return }
                val list = (page.scopeChannels ?: allChannels).asSequence()
                    .filter { it.name.contains(query, true) }.take(500).map { channelNode(it) }.toList()
                b.status.text = if (list.isEmpty()) "No channels match “$query”." else ""
                submit(list)
            }
            Kind.GLOBAL -> globalSearch(query, page)
            Kind.VOD_ALL -> vodSearch(query, null, page)
            Kind.VOD_CATEGORY -> vodSearch(query, page.scopeCat, page)
        }
    }

    private fun globalSearch(query: String, page: Page) {
        pendingSearch?.let { ui.removeCallbacks(it) }; searchSeq++
        if (query.isEmpty()) { b.status.text = ""; submit(page.nodes); return }
        val chNodes = allChannels.asSequence().filter { it.name.contains(query, true) }
            .take(150).map { channelNode(it) }.toList()
        submit(chNodes)
        if (query.length < 2) return
        b.status.text = "Searching movies & shows…"
        val seq = searchSeq
        val task = Runnable {
            io.execute {
                val vod = Portal.vodSearch(query)
                runOnUiThread {
                    if (seq != searchSeq) return@runOnUiThread
                    b.status.text = ""
                    submit(chNodes + vod.map { vodNode(it) })
                }
            }
        }
        pendingSearch = task; ui.postDelayed(task, 450)
    }

    private fun vodSearch(query: String, catId: String?, page: Page) {
        pendingSearch?.let { ui.removeCallbacks(it) }; searchSeq++
        if (query.isEmpty()) { b.status.text = ""; submit(page.nodes); return }
        if (query.length < 2) return
        b.status.text = "Searching…"
        val seq = searchSeq
        val task = Runnable {
            io.execute {
                val vod = if (catId == null) Portal.vodSearch(query) else Portal.vodSearchInCategory(catId, query)
                runOnUiThread {
                    if (seq != searchSeq) return@runOnUiThread
                    val nodes = vod.map { vodNode(it) }
                    b.status.text = if (nodes.isEmpty()) "No results for “$query”." else ""
                    submit(nodes)
                }
            }
        }
        pendingSearch = task; ui.postDelayed(task, 450)
    }

    // ---- A-Z ----
    private fun buildAzBar() {
        val labels = listOf("ALL") + ('A'..'Z').map { it.toString() } + ('0'..'9').map { it.toString() }
        for (lbl in labels) {
            val tv = android.widget.TextView(this)
            tv.text = lbl
            tv.setTextColor(0xFFE6EDF3.toInt())
            tv.textSize = 15f
            tv.setPadding(20, 12, 20, 12)
            tv.isFocusable = true; tv.isClickable = true
            tv.setBackgroundResource(R.drawable.item_bg)
            tv.setOnClickListener { azFilter(if (lbl == "ALL") null else lbl) }
            b.azBar.addView(tv)
        }
    }

    private fun azFilter(letter: String?) {
        if (b.search.text.isNotEmpty()) b.search.setText("")
        val page = backStack.lastOrNull() ?: return
        if (letter == null) { submit(page.nodes); return }
        if (page.kind == Kind.VOD_CATEGORY && page.scopeCat != null) {
            val cat = page.scopeCat
            b.status.text = "Loading “$letter”…"
            io.execute {
                val items = Portal.vodByLetter(cat, letter)
                runOnUiThread {
                    b.status.text = if (items.isEmpty()) "No titles starting with “$letter”." else ""
                    submit(items.map { vodNode(it) })
                }
            }
        } else {
            submit(page.nodes.filter { it.sortKey.trimStart().startsWith(letter, ignoreCase = true) })
        }
    }
}
