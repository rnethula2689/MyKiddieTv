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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKidmoviesBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter

        connectPortal()
        showHome()
    }

    override fun onResume() {
        super.onResume()
        KidGuard.immersive(this)
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
        b.title.text = "Movies & Shows"
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
        b.title.text = "Live Movies & Shows"
        val movies = Profiles.allowedVod(this).filter { !it.isSeries }
        val bySeries = Profiles.allowedEpisodes(this).groupBy { it.seriesId }
        val rows = ArrayList<ChannelsActivity.Row>()
        movies.forEach { v ->
            rows.add(ChannelsActivity.Row("🎬  ${v.name}", v.posterUrl, v.name) { playMovie(v) })
        }
        bySeries.forEach { (sid, eps) ->
            val name = eps.first().seriesName
            rows.add(ChannelsActivity.Row("📁  $name  (${eps.size})", eps.first().poster, name) { showEpisodes(sid) })
        }
        rows.sortBy { it.sortKey.lowercase() }
        b.status.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        if (rows.isEmpty()) b.status.text = "Nothing added yet. Ask a grown-up to add movies or shows."
        adapter.submit(rows)
        b.list.scrollToPosition(0)
    }

    private fun showEpisodes(seriesId: String) {
        val eps = Profiles.allowedEpisodes(this).filter { it.seriesId == seriesId }
        if (eps.isEmpty()) { showStreaming(); return }
        inStreaming = true; inSeries = seriesId
        b.title.text = eps.first().seriesName
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
            inSeries != null -> showStreaming()
            inStreaming -> showHome()
            else -> super.onBackPressed()
        }
    }
}
