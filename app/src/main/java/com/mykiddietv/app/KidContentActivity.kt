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
        val scopeCat: String? = null,
        val manage: String? = null   // null = add mode; "ch"/"vod" = managing already-approved content
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

    // Approved-content management: items start checked; unchecking adds to removeSet → "Remove".
    private val removeSet = HashSet<String>()
    private var currentManage: String? = null   // mirrors the current page's manage type ("ch"/"vod")

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
        b.downloadBtn.setOnClickListener { onDownloadSelected() }
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
    private fun isChecked(n: KidNode): Boolean {
        // Manage mode: approved items show checked until the parent unchecks (= marks to remove).
        if (currentManage != null) { val id = n.pickId; return id != null && !removeSet.contains(id) }
        return (n.channel != null && pendingChannels.containsKey(n.channel.id)) ||
            (n.vod != null && pendingVod.containsKey(n.vod.id)) ||
            (n.episode != null && pendingEpisodes.containsKey(n.episode.key))
    }

    private fun toggleRemove(n: KidNode) {
        val id = n.pickId ?: return
        if (!removeSet.remove(id)) removeSet.add(id)
    }

    private fun toggle(n: KidNode) {
        n.channel?.let { if (pendingChannels.remove(it.id) == null) pendingChannels[it.id] = it }
        n.vod?.let { if (pendingVod.remove(it.id) == null) pendingVod[it.id] = it }
        n.episode?.let { if (pendingEpisodes.remove(it.key) == null) pendingEpisodes[it.key] = it }
    }

    private fun onRowClick(pos: Int) {
        val n = adapter.nodeAt(pos)
        val open = n.open
        if (open != null) { open(); return }
        if (currentManage != null) toggleRemove(n) else toggle(n)
        adapter.notifyItemChanged(pos)
        updateBottomBar()
    }

    private fun pendingCount() = pendingChannels.size + pendingVod.size + pendingEpisodes.size

    private fun updateBottomBar() {
        val picks = displayed.filter { it.isPick }
        b.bottomBar.visibility = if (picks.isEmpty()) View.GONE else View.VISIBLE
        if (currentManage != null) {
            val pickIds = picks.mapNotNull { it.pickId }
            val allUnchecked = pickIds.isNotEmpty() && pickIds.all { removeSet.contains(it) }
            b.selectAllBtn.text = if (allUnchecked) "Keep all" else "Uncheck all"
            b.addBtn.text = if (removeSet.isNotEmpty()) "Remove (${removeSet.size})" else "Remove"
            b.downloadBtn.visibility = View.GONE
        } else {
            val allSelected = picks.isNotEmpty() && picks.all { isChecked(it) }
            b.selectAllBtn.text = if (allSelected) "Deselect all" else "Select all"
            b.addBtn.text = if (pendingCount() > 0) "Add selected (${pendingCount()})" else "Add selected"
            // Download applies to movies/episodes only (not live channels).
            val hasVod = picks.any { it.vod != null || it.episode != null }
            b.downloadBtn.visibility = if (hasVod) View.VISIBLE else View.GONE
            val dlCount = pendingVod.size + pendingEpisodes.size
            b.downloadBtn.text = if (dlCount > 0) "Download selected ($dlCount)" else "Download selected"
        }
    }

    private fun onSelectAll() {
        val picks = displayed.filter { it.isPick }
        if (picks.isEmpty()) return
        if (currentManage != null) {
            val pickIds = picks.mapNotNull { it.pickId }
            val allUnchecked = pickIds.all { removeSet.contains(it) }
            pickIds.forEach { if (allUnchecked) removeSet.remove(it) else removeSet.add(it) }
            adapter.notifyDataSetChanged(); updateBottomBar(); return
        }
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

    /** Download the checked movies/episodes for offline (they appear in the kid's Downloaded folder). */
    private fun onDownloadSelected() {
        val n = pendingVod.size + pendingEpisodes.size
        if (n == 0) { b.status.text = "Tick some movies or episodes to download."; return }
        val names = pendingVod.values.map { it.name } +
            pendingEpisodes.values.map { "${it.seriesName} — ${it.name}" }
        val preview = names.take(12).joinToString("\n") { "• $it" } +
            (if (names.size > 12) "\n…and ${names.size - 12} more" else "")
        AlertDialog.Builder(this)
            .setTitle("Download for offline")
            .setMessage("Download $n movie(s)/episode(s) for ${Profiles.kidName(this)} to watch offline?\n\n$preview")
            .setPositiveButton("Yes, download") { _, _ -> commitDownloads() }
            .setNegativeButton("Go back", null)
            .show()
    }

    private fun commitDownloads() {
        var started = 0
        pendingVod.values.forEach { v ->
            Downloads.enqueue(applicationContext, v.id, v.name, v.posterUrl, "vod|${v.id}|${v.cmd}")
            Profiles.addKidDownload(applicationContext, v.id) // kid bucket — kept separate from the parent's own downloads
            started++
        }
        pendingEpisodes.values.forEach { e ->
            // Title encodes the hierarchy so the Downloads views can group Series ⟫ Season ⟫ Episode.
            val title = "${e.seriesName} ⟫ ${e.seasonName.ifBlank { "Season" }} ⟫ ${e.name}"
            Downloads.enqueue(applicationContext, e.key, title, e.poster, e.source)
            Profiles.addKidDownload(applicationContext, e.key)
            started++
        }
        pendingVod.clear(); pendingEpisodes.clear()
        b.status.text = "Started $started download(s) — see Approved Content → Downloads."
        backStack.lastOrNull()?.let { display(it) }
        updateBottomBar()
    }

    private fun onAddSelected() {
        if (currentManage != null) { onRemoveSelected(); return }
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
        currentManage = p.manage
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
            KidNode("🎬   Movies & Shows", null, "Movies", open = { showMovies() }),
            KidNode("✅   Approved Content  (review / remove)", null, "Approved", open = { showApproved() }),
            KidNode("⚙   Content settings for ${Profiles.kidName(this)}", null, "Settings", open = { contentSettingsDialog() })
        ), Kind.GLOBAL))
    }

    // ---- per-kid content settings (hand-pick vs auto, list filter, hide unrated) ----
    private fun contentSettingsDialog() {
        val k = Profiles.activeKid(this) ?: run { b.status.text = "No kid selected."; return }
        val band = AgeBands.of(k.ageBand)
        val dp = resources.displayMetrics.density; val pad = (20 * dp).toInt()
        fun tv(t: String, size: Float, color: Int) = android.widget.TextView(this).apply { text = t; textSize = size; setTextColor(color) }
        val col = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        col.addView(tv("${k.name}  ·  ${band.emoji} ${band.name}  (cap ${band.rating})", 13f, 0xFF8B97A5.toInt()))
        col.addView(tv("\nHow should ${k.name} get movies & shows?", 15f, 0xFFE6EDF3.toInt()))
        val rg = android.widget.RadioGroup(this)
        val rbPick = android.widget.RadioButton(this).apply { text = "I'll hand-pick titles"; id = 1; setTextColor(0xFFE6EDF3.toInt()) }
        val rbAuto = android.widget.RadioButton(this).apply { text = "Show everything within their age cap (no picking)"; id = 2; setTextColor(0xFFE6EDF3.toInt()) }
        rg.addView(rbPick); rg.addView(rbAuto); rg.check(if (k.filterMode == "auto") 2 else 1); col.addView(rg)
        val cbFilter = android.widget.CheckBox(this).apply { text = "While I pick, hide titles above the cap"; isChecked = k.filterPickList; setTextColor(0xFFE6EDF3.toInt()) }
        val cbHide = android.widget.CheckBox(this).apply { text = "Hide titles with no age rating"; isChecked = k.hideUnrated; setTextColor(0xFFE6EDF3.toInt()) }
        col.addView(cbFilter); col.addView(cbHide)
        fun sync() { cbFilter.isEnabled = rg.checkedRadioButtonId != 2 }
        rg.setOnCheckedChangeListener { _, _ -> sync() }; sync()
        AlertDialog.Builder(this)
            .setTitle("Content settings")
            .setView(col)
            .setPositiveButton("Save") { _, _ ->
                k.filterMode = if (rg.checkedRadioButtonId == 2) "auto" else "pick"
                k.filterPickList = cbFilter.isChecked
                k.hideUnrated = cbHide.isChecked
                Profiles.saveKid(this, k)
                b.status.text = "Saved settings for ${k.name} ✓"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** In hand-pick mode with "filter my list" on, drop titles above the kid's age cap. Runs on io (network, cached). */
    private fun filterForPick(items: List<Portal.VodItem>): List<Portal.VodItem> {
        val k = Profiles.activeKid(this) ?: return items
        if (k.filterMode != "pick" || !k.filterPickList) return items
        return items.filter { KidRating.show(this, it.name, it.year, k.ageBand, k.hideUnrated) }
    }

    // ---- Approved Content (review & remove already-whitelisted items) ----
    private fun showApproved() {
        val nCh = Profiles.allowedChannels(this).size
        val nVod = Profiles.allowedVod(this).count { !it.isSeries } + Profiles.allowedEpisodes(this).size
        push(Page("Approved Content", listOf(
            KidNode("📺   Approved Live TV Channels  ($nCh)", null, "", open = { showApprovedChannels() }),
            KidNode("🎬   Approved Movies & Shows  ($nVod)", null, "", open = { showApprovedVod() }),
            KidNode("⬇   Downloads (offline)", null, "", open = {
                OfflineActivity.kidMode = false
                startActivity(android.content.Intent(this, OfflineActivity::class.java))
            })
        ), Kind.FOLDERS))
    }

    private fun approvedChannelsPage(): Page {
        val nodes = Profiles.allowedChannels(this).map { ch ->
            KidNode("📺  " + (if (ch.number.isNotEmpty()) "${ch.number}. " else "") + ch.name,
                ch.logoUrl, ch.name, channel = ch)
        }
        return Page("Approved Live TV", nodes, Kind.FOLDERS, manage = "ch")
    }

    private fun approvedVodPage(): Page {
        val movies = Profiles.allowedVod(this).filter { !it.isSeries }.map { v ->
            KidNode("🎬  ${v.name}", v.posterUrl, v.name, vod = v)
        }
        val eps = Profiles.allowedEpisodes(this).map { e ->
            KidNode("📺  ${e.seriesName} — ${e.name}", e.poster, e.name, episode = e)
        }
        return Page("Approved Movies & Shows", movies + eps, Kind.FOLDERS, manage = "vod")
    }

    private fun showApprovedChannels() { removeSet.clear(); push(approvedChannelsPage()) }
    private fun showApprovedVod() { removeSet.clear(); push(approvedVodPage()) }

    private fun onRemoveSelected() {
        if (removeSet.isEmpty()) { b.status.text = "Uncheck the items you want to remove."; return }
        val names = displayed.filter { val id = it.pickId; id != null && removeSet.contains(id) }.map { it.label }
        val preview = names.take(12).joinToString("\n") { "• $it" } +
            (if (names.size > 12) "\n…and ${names.size - 12} more" else "")
        AlertDialog.Builder(this)
            .setTitle("Confirm")
            .setMessage("Remove ${removeSet.size} item(s) from ${Profiles.kidName(this)}'s list?\n\n$preview")
            .setPositiveButton("Yes, remove") { _, _ -> commitRemoval() }
            .setNegativeButton("Go back", null)
            .show()
    }

    private fun commitRemoval() {
        when (currentManage) {
            "ch" -> Profiles.saveChannels(this, Profiles.allowedChannels(this).filterNot { removeSet.contains(it.id) })
            "vod" -> {
                Profiles.saveVod(this, Profiles.allowedVod(this).filterNot { removeSet.contains(it.id) })
                Profiles.saveEpisodes(this, Profiles.allowedEpisodes(this).filterNot { removeSet.contains(it.key) })
            }
        }
        val removed = removeSet.size
        removeSet.clear()
        savedChannelIds = Profiles.allowedChannelIds(this)
        savedVodIds = Profiles.allowedVodIds(this)
        savedEpisodeKeys = Profiles.allowedEpisodeKeys(this)
        b.status.text = "Removed $removed item(s) ✓"
        display(if (currentManage == "ch") approvedChannelsPage() else approvedVodPage())
    }

    private fun showLive() {
        // Respect the active content profile's guardrails: only its allowed categories are pickable here.
        val visibleChannels = allChannels.filter { ContentProfiles.liveCatVisible(this, it.genreId) }
        val nodes = ArrayList<KidNode>()
        nodes.add(KidNode("All Channels  (${visibleChannels.size})", null, "All Channels",
            open = { showChannels(visibleChannels, "All Channels") }))
        for (g in genres) {
            if (!ContentProfiles.liveCatVisible(this, g.id)) continue
            val list = byGenre[g.id] ?: emptyList()
            if (list.isNotEmpty()) nodes.add(KidNode("${g.title}  (${list.size})", null, g.title,
                open = { showChannels(list, g.title) }))
        }
        push(Page("Live TV", nodes, Kind.CHANNELS, scopeChannels = visibleChannels))
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
                val visible = cats.filter { ContentProfiles.vodCatVisible(this, it.id) } // profile guardrails
                push(Page("Movies", visible.map { c ->
                    KidNode(c.title, null, c.title, open = { showVodList(c) })
                }, Kind.VOD_ALL))
            }
        }
    }

    private fun showVodList(cat: Portal.VodCat) {
        b.status.text = "Loading ${cat.title}…"
        io.execute {
            val (items, pages) = Portal.vodList(cat.id, 1)
            val filtered = filterForPick(items)
            runOnUiThread {
                b.status.text = ""
                push(Page(cat.title, vodNodes(cat, ArrayList(filtered), 1, pages), Kind.VOD_CATEGORY, scopeCat = cat.id))
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
                    val moreFiltered = filterForPick(more)
                    runOnUiThread {
                        b.status.text = ""
                        acc.addAll(moreFiltered)
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
            seasonId = season.id, seasonName = season.name, episodeId = e.id,
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
                val vod = filterForPick(Portal.vodSearch(query))
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
                val vod = filterForPick(if (catId == null) Portal.vodSearch(query) else Portal.vodSearchInCategory(catId, query))
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
                val items = filterForPick(Portal.vodByLetter(cat, letter))
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
