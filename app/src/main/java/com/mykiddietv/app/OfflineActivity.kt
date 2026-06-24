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
 * Offline downloads, grouped Movies + Series → Season → Episode.
 *  • Parent (kidMode=false): browse APPROVED content; tap a leaf to download / play / delete.
 *  • Kid   (kidMode=true):   browse COMPLETED downloads; tap a leaf to play offline.
 */
class OfflineActivity : AppCompatActivity(), Downloads.Listener {
    companion object { var kidMode = false }

    private lateinit var b: ActivityKidmoviesBinding
    private val adapter = RowAdapter()

    data class Entry(
        val downloadId: String, val source: String, val poster: String, val title: String,
        val isEpisode: Boolean,
        val seriesId: String = "", val seriesName: String = "",
        val seasonName: String = "", val episodeName: String = ""
    )

    private var entries = listOf<Entry>()
    private var curSeriesId: String? = null   // null = home
    private var curSeason: String? = null      // null = series root (season list)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKidmoviesBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        if (kidMode) KidGuard.immersive(this)
        loadEntries()
        render()
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
        entries = if (kidMode) kidEntries() else parentEntries()
    }

    /** Parent: every approved movie + episode is downloadable. */
    private fun parentEntries(): List<Entry> {
        val movies = Profiles.allowedVod(this).filter { !it.isSeries }.map {
            Entry(it.id, "vod|${it.id}|${it.cmd}", it.posterUrl, it.name, false)
        }
        val eps = Profiles.allowedEpisodes(this).map {
            Entry(it.key, it.source, it.poster, "${it.seriesName} — ${it.name}", true,
                it.seriesId, it.seriesName, it.seasonName.ifBlank { "Season" }, it.name)
        }
        return movies + eps
    }

    /** Kid: only completed downloads, named via the approved store where possible. */
    private fun kidEntries(): List<Entry> {
        val eps = Profiles.allowedEpisodes(this).associateBy { it.key }
        val movies = Profiles.allowedVod(this).associateBy { it.id }
        return Downloads.list(this).filter { it.status == Downloads.DONE }.map { dl ->
            val ep = eps[dl.id]
            if (ep != null) Entry(dl.id, dl.source, dl.poster.ifBlank { ep.poster }, "${ep.seriesName} — ${ep.name}", true,
                ep.seriesId, ep.seriesName, ep.seasonName.ifBlank { "Season" }, ep.name)
            else Entry(dl.id, dl.source, dl.poster, movies[dl.id]?.name ?: dl.title, false)
        }
    }

    // ---- rendering ----
    private fun render() {
        val sid = curSeriesId
        val season = curSeason
        when {
            sid == null -> renderHome()
            season == null -> renderSeasons(sid)
            else -> renderEpisodes(sid, season)
        }
    }

    private fun renderHome() {
        b.title.text = if (kidMode) "⬇ Downloaded" else "Manage Downloads"
        val rows = ArrayList<ChannelsActivity.Row>()
        entries.filter { !it.isEpisode }.sortedBy { it.title.lowercase() }
            .forEach { e -> rows.add(leafRow(e)) }
        entries.filter { it.isEpisode }.groupBy { it.seriesId }.toSortedMap(compareBy { it })
            .forEach { (sid, eps) ->
                val name = eps.first().seriesName
                rows.add(ChannelsActivity.Row("📁  $name  (${eps.size})", eps.first().poster, name) {
                    curSeriesId = sid; curSeason = null; render()
                })
            }
        showRows(rows, if (kidMode) "Nothing downloaded yet." else "No approved content yet — approve some first.")
    }

    private fun renderSeasons(sid: String) {
        val eps = entries.filter { it.isEpisode && it.seriesId == sid }
        b.title.text = eps.firstOrNull()?.seriesName ?: "Series"
        val rows = eps.groupBy { it.seasonName }.toSortedMap(compareBy { it })
            .map { (season, list) ->
                ChannelsActivity.Row("📁  $season  (${list.size})", list.first().poster, season) {
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

    /** A movie or episode leaf row. */
    private fun leafRow(e: Entry): ChannelsActivity.Row {
        val name = if (e.isEpisode) e.episodeName else e.title
        val label = if (kidMode) "🎬  $name" else "${statusPrefix(e)}  $name"
        return ChannelsActivity.Row(label, e.poster, name) {
            if (kidMode) playOffline(e) else parentLeafMenu(e)
        }
    }

    private fun dlItem(id: String) = Downloads.list(this).firstOrNull { it.id == id }

    private fun statusPrefix(e: Entry): String = when (dlItem(e.downloadId)?.status) {
        Downloads.DONE -> "✓"
        Downloads.DOWNLOADING -> {
            val it = dlItem(e.downloadId)!!
            val pct = if (it.total > 0) (it.done * 100 / it.total).toInt() else 0
            "⏬ $pct%"
        }
        Downloads.PAUSED -> "⏸"
        Downloads.QUEUED -> "…"
        Downloads.ERROR -> "⚠"
        else -> "⬇"
    }

    // ---- parent actions ----
    private fun parentLeafMenu(e: Entry) {
        val it = dlItem(e.downloadId)
        when (it?.status) {
            Downloads.DONE -> AlertDialog.Builder(this).setTitle(e.title)
                .setItems(arrayOf("▶  Play offline", "🗑  Delete download")) { _, w ->
                    if (w == 0) playOffline(e) else { Downloads.delete(applicationContext, e.downloadId); loadEntries(); render() }
                }.show()
            Downloads.DOWNLOADING -> AlertDialog.Builder(this).setTitle(e.title)
                .setItems(arrayOf("⏸  Pause", "🗑  Stop & remove")) { _, w ->
                    if (w == 0) Downloads.pause(applicationContext, e.downloadId) else Downloads.delete(applicationContext, e.downloadId)
                }.show()
            Downloads.PAUSED, Downloads.QUEUED -> AlertDialog.Builder(this).setTitle(e.title)
                .setItems(arrayOf("▶  Resume", "🗑  Delete")) { _, w ->
                    if (w == 0) Downloads.resume(applicationContext, e.downloadId) else Downloads.delete(applicationContext, e.downloadId)
                }.show()
            Downloads.ERROR -> AlertDialog.Builder(this).setTitle(e.title)
                .setItems(arrayOf("↻  Retry", "🗑  Remove")) { _, w ->
                    if (w == 0) Downloads.resume(applicationContext, e.downloadId) else Downloads.delete(applicationContext, e.downloadId)
                }.show()
            else -> AlertDialog.Builder(this).setTitle(e.title)
                .setMessage("Download this for the kid to watch offline?")
                .setPositiveButton("⬇ Download") { _, _ ->
                    Downloads.enqueue(applicationContext, e.downloadId, e.title, e.poster, e.source)
                    Toast.makeText(this, "Download started.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null).show()
        }
    }

    private fun playOffline(e: Entry) {
        val it = dlItem(e.downloadId)
        if (it == null || it.status != Downloads.DONE) { Toast.makeText(this, "Not downloaded yet.", Toast.LENGTH_SHORT).show(); return }
        val f = Downloads.fileFor(this, it)
        if (!f.exists()) { Downloads.delete(applicationContext, e.downloadId); loadEntries(); render(); return }
        PlayerActivity.kidMode = kidMode
        startActivity(Intent(this, PlayerActivity::class.java)
            .putExtra("url", Uri.fromFile(f).toString())
            .putExtra("title", if (e.isEpisode) "${e.seriesName} — ${e.episodeName}" else e.title))
    }

    override fun onBackPressed() {
        when {
            curSeason != null -> { curSeason = null; render() }
            curSeriesId != null -> { curSeriesId = null; render() }
            else -> super.onBackPressed()
        }
    }
}
