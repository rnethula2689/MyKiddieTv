package com.mykiddietv.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mykiddietv.app.databinding.ActivityKidmoviesBinding

/**
 * Downloads browser, grouped Movies + Series → Season → Episode. Driven entirely by the
 * downloads list (so it starts empty and only ever shows real downloads).
 *  • Parent (kidMode=false): selected kid's downloads; tap a leaf to play / pause / delete.
 *  • Kid   (kidMode=true):   selected kid's downloads with read-only progress; completed items play.
 * Episode grouping comes from the download title ("Series ⟫ Season ⟫ Episode") + source ("ep|s|s|e").
 */
class OfflineActivity : AppCompatActivity(), Downloads.Listener {
    companion object {
        var kidMode = false
        const val SEP = " ⟫ "
    }

    private lateinit var b: ActivityKidmoviesBinding
    private val adapter = RowAdapter()

    private data class Entry(
        val item: Downloads.Item, val isEpisode: Boolean,
        val seriesId: String, val seriesName: String,
        val seasonName: String, val episodeName: String, val movieName: String
    )

    private var entries = listOf<Entry>()
    private var curSeriesId: String? = null
    private var curSeason: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKidmoviesBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        if (kidMode) KidGuard.immersive(this)
        loadEntries(); render()
    }

    override fun onResume() {
        super.onResume()
        Downloads.addListener(this)
        Downloads.resumeAllAuto(applicationContext)
        if (kidMode) KidGuard.immersive(this)
        loadEntries(); render()
    }

    override fun onPause() { super.onPause(); Downloads.removeListener(this) }
    override fun onDownloadsChanged() { runOnUiThread { loadEntries(); render() } }

    private fun loadEntries() {
        // Only the active kid's bucket here. Parent-owned downloads stay in the main Downloads screen.
        val kidIds = Profiles.kidDownloadIds(this)
        entries = Downloads.list(this).filter { kidIds.contains(it.id) }.map { toEntry(it) }
    }

    private fun delDownload(id: String) {
        Downloads.delete(applicationContext, id)
        Profiles.removeKidDownload(applicationContext, id)
    }

    private fun toEntry(item: Downloads.Item): Entry {
        val sp = item.source.split("|")
        if (sp.getOrNull(0) == "ep") {
            val tp = item.title.split(SEP)
            val series = tp.getOrNull(0)?.ifBlank { null } ?: "Series"
            val season = tp.getOrNull(1)?.ifBlank { null } ?: "Season"
            val episode = if (tp.size >= 3) tp.subList(2, tp.size).joinToString(SEP) else item.title
            return Entry(item, true, sp.getOrElse(1) { item.id }, series, season, episode, "")
        }
        return Entry(item, false, "", "", "", "", item.title)
    }

    // ---- rendering ----
    private fun render() {
        when {
            curSeriesId == null -> renderHome()
            curSeason == null -> renderSeasons(curSeriesId!!)
            else -> renderEpisodes(curSeriesId!!, curSeason!!)
        }
    }

    private fun renderHome() {
        b.title.text = if (kidMode) "Downloaded Movies & Shows" else "Downloads"
        val rows = ArrayList<ChannelsActivity.Row>()
        entries.filter { !it.isEpisode }.sortedBy { it.movieName.lowercase() }.forEach { rows.add(leafRow(it)) }
        entries.filter { it.isEpisode }.groupBy { it.seriesId }.values
            .sortedBy { it.first().seriesName.lowercase() }
            .forEach { eps ->
                val e = eps.first()
                rows.add(ChannelsActivity.Row("📁  ${e.seriesName}  (${eps.size})", e.item.poster, e.seriesName) {
                    curSeriesId = e.seriesId; curSeason = null; render()
                })
            }
        showRows(rows, if (kidMode) "Nothing downloaded yet." else "No downloads yet. Download from Manage Kid Content → Movies & Shows.")
    }

    private fun renderSeasons(sid: String) {
        val eps = entries.filter { it.isEpisode && it.seriesId == sid }
        b.title.text = eps.firstOrNull()?.seriesName ?: "Series"
        val rows = eps.groupBy { it.seasonName }.toSortedMap(compareBy { it }).map { (season, list) ->
            ChannelsActivity.Row("📁  $season  (${list.size})", list.first().item.poster, season) {
                curSeason = season; render()
            }
        }
        showRows(rows, "No episodes.")
    }

    private fun renderEpisodes(sid: String, season: String) {
        val eps = entries.filter { it.isEpisode && it.seriesId == sid && it.seasonName == season }
            .sortedBy { it.episodeName.lowercase() }
        b.title.text = "${eps.firstOrNull()?.seriesName ?: ""} — $season"
        showRows(eps.map { leafRow(it) }, "No episodes.")
    }

    private fun showRows(rows: List<ChannelsActivity.Row>, emptyMsg: String) {
        b.status.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        if (rows.isEmpty()) b.status.text = emptyMsg
        adapter.submit(rows)
        b.list.scrollToPosition(0)
    }

    private fun leafRow(e: Entry): ChannelsActivity.Row {
        val name = if (e.isEpisode) e.episodeName else e.movieName
        val prefix = if (kidMode && e.item.status == Downloads.DONE) "🎬" else statusPrefix(e.item)
        val detail = statusDetail(e.item)
        val label = "$prefix  $name" + if (detail.isBlank()) "" else "\n$detail"
        return ChannelsActivity.Row(label, e.item.poster, name) {
            if (kidMode) playOffline(e) else parentMenu(e)
        }
    }

    private fun statusPrefix(it: Downloads.Item): String = when (it.status) {
        Downloads.DONE -> "✓"
        Downloads.DOWNLOADING -> "⏬ " + (if (it.total > 0) "${(it.done * 100 / it.total).toInt()}%" else "")
        Downloads.PAUSED -> "⏸"
        Downloads.QUEUED -> "…"
        Downloads.ERROR -> "⚠"
        else -> "•"
    }

    private fun statusDetail(it: Downloads.Item): String = when (it.status) {
        Downloads.DONE -> if (it.hls) "Downloaded • offline ready"
            else if (it.total > 0) "Downloaded • ${sizeStr(it.total)}" else ""
        Downloads.DOWNLOADING -> {
            val pct = if (it.total > 0) (it.done * 100 / it.total).toInt() else 0
            when {
                it.hls -> "Downloading $pct%  (${it.done} / ${it.total} segments)"
                it.total > 0 -> "Downloading $pct%  (${sizeStr(it.done)} / ${sizeStr(it.total)})"
                else -> "Downloading...  ${sizeStr(it.done)}"
            }
        }
        Downloads.PAUSED -> {
            val pct = if (it.total > 0) (it.done * 100 / it.total).toInt() else 0
            val why = if (it.userPaused) "Paused" else "Paused - waiting for network"
            why + if (it.total > 0) " • $pct%" else ""
        }
        Downloads.QUEUED -> "Waiting to download"
        Downloads.ERROR -> "Download failed"
        else -> ""
    }

    private fun sizeStr(bytes: Long): String {
        if (bytes <= 0) return ""
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format("%.1f GB", mb / 1024) else String.format("%.0f MB", mb)
    }

    private fun parentMenu(e: Entry) {
        val id = e.item.id
        when (e.item.status) {
            Downloads.DONE -> AlertDialog.Builder(this).setTitle(e.item.title)
                .setItems(arrayOf("▶  Play", "🗑  Delete download")) { _, w ->
                    if (w == 0) playOffline(e) else delDownload(id)
                }.show()
            Downloads.DOWNLOADING, Downloads.QUEUED -> AlertDialog.Builder(this).setTitle(e.item.title)
                .setItems(arrayOf("⏸  Pause", "🗑  Stop & remove")) { _, w ->
                    if (w == 0) Downloads.pause(applicationContext, id) else delDownload(id)
                }.show()
            Downloads.PAUSED -> AlertDialog.Builder(this).setTitle(e.item.title)
                .setItems(arrayOf("▶  Resume", "🗑  Delete")) { _, w ->
                    if (w == 0) Downloads.resume(applicationContext, id) else delDownload(id)
                }.show()
            else -> AlertDialog.Builder(this).setTitle(e.item.title)
                .setItems(arrayOf("↻  Retry", "🗑  Remove")) { _, w ->
                    if (w == 0) Downloads.resume(applicationContext, id) else delDownload(id)
                }.show()
        }
    }

    private fun playOffline(e: Entry) {
        if (e.item.status != Downloads.DONE) { Toast.makeText(this, statusDetail(e.item).ifBlank { "Not downloaded yet." }, Toast.LENGTH_SHORT).show(); return }
        val f = Downloads.fileFor(this, e.item)
        if (!f.exists()) { delDownload(e.item.id); loadEntries(); render(); return }
        PlayerActivity.kidMode = kidMode
        val kidId = if (kidMode) Profiles.activeKidId(this) ?: "" else ""
        val resume = if (kidMode) Resume.get(applicationContext, e.item.id, kidId) else Resume.get(applicationContext, e.item.id)
        val title = if (e.isEpisode) "${e.seriesName} — ${e.episodeName}" else e.movieName
        startActivity(Intent(this, PlayerActivity::class.java)
            .putExtra("url", Uri.fromFile(f).toString())
            .putExtra("title", title)
            .putExtra("resumeId", e.item.id)
            .putExtra("resumeSource", e.item.source)
            .putExtra("resumePoster", e.item.poster)
            .putExtra("resumeStart", resume?.position ?: 0L)
            .putExtra("year", resume?.year ?: ""))
    }

    override fun onBackPressed() {
        when {
            curSeason != null -> { curSeason = null; render() }
            curSeriesId != null -> { curSeriesId = null; render() }
            else -> super.onBackPressed()
        }
    }
}
