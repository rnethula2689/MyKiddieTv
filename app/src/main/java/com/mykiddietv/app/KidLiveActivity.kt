package com.mykiddietv.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mykiddietv.app.databinding.ActivityKidmoviesBinding
import java.util.concurrent.Executors

/**
 * Live TV folder chooser for a full-access (not content-managed) kid. Instead of a flat channel
 * dump, the kid first picks a folder: "📺 All Channels" plus one folder per allowed Live genre.
 * Tapping a folder opens [LiveGridActivity] scoped to that folder's channels (kid mode).
 *
 * Reuses the Movies layout ([ActivityKidmoviesBinding]) for the grid + title/status.
 * Content-managed kids never reach here — they keep the flat list from [KidHomeActivity].
 */
class KidLiveActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var b: ActivityKidmoviesBinding
    private val adapter = RowAdapter()
    private var connected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKidmoviesBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Same multi-column grid as the parent side: chips tile in columns, everything else full width.
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

        b.title.text = "Live TV"
        b.searchBtn.visibility = View.GONE
        b.sortBtn.visibility = View.GONE
        b.azScroll.visibility = View.GONE

        connectPortal()
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
        val acct = Configs.active(this)
        if (acct == null) {
            b.status.visibility = View.VISIBLE
            b.status.text = "Not set up yet. Ask a grown-up."
            return
        }
        b.status.visibility = View.VISIBLE
        b.status.text = "Loading…"
        Portal.portalUrl = acct.portal
        Portal.mac = acct.mac
        Portal.sn = acct.sn
        io.execute {
            val err = Portal.connect()
            val channels = if (err == null) Portal.liveChannels() else emptyList()
            val genres = if (err == null) Portal.liveGenres() else emptyList()
            runOnUiThread {
                connected = err == null
                if (!connected) {
                    b.status.visibility = View.VISIBLE
                    b.status.text = "Couldn't connect. Ask a grown-up to check Settings."
                    return@runOnUiThread
                }
                showFolders(channels, genres)
            }
        }
    }

    /** Build the folder grid: "All Channels" + one chip per allowed genre that has channels. */
    private fun showFolders(channels: List<Portal.Channel>, genres: List<Portal.Genre>) {
        // Only channels in folders this kid is allowed to see.
        val allowed = channels.filter { Profiles.liveFolderAllowed(this, it.genreId) }
        val byGenre = allowed.groupBy { it.genreId }
        val rows = ArrayList<ChannelsActivity.Row>()
        rows.add(ChannelsActivity.Row("📺  All Channels  (${allowed.size})", null, "", chip = true) {
            openGrid(allowed, "All Channels")
        })
        for (g in genres) {
            if (!Profiles.liveFolderAllowed(this, g.id)) continue
            val list = byGenre[g.id] ?: continue
            if (list.isEmpty()) continue
            rows.add(ChannelsActivity.Row("${g.title}  (${list.size})", null, g.title, chip = true) {
                openGrid(list, g.title)
            })
        }
        b.status.visibility = if (allowed.isEmpty()) View.VISIBLE else View.GONE
        if (allowed.isEmpty()) b.status.text = "No channels available."
        adapter.submit(rows)
        b.list.scrollToPosition(0)
    }

    private fun openGrid(list: List<Portal.Channel>, title: String) {
        if (list.isEmpty()) {
            Toast.makeText(this, "No channels here.", Toast.LENGTH_SHORT).show()
            return
        }
        LiveGridActivity.channels = list
        LiveGridActivity.gridTitle = title
        LiveGridActivity.kidMode = true
        startActivity(Intent(this, LiveGridActivity::class.java))
    }
}
