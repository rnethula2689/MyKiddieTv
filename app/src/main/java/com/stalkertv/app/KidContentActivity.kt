package com.stalkertv.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.stalkertv.app.databinding.ActivityKidcontentBinding
import java.util.concurrent.Executors

/**
 * Parent-only screen for whitelisting kid content. Two tabs:
 *   • Live TV — tick individual channels.
 *   • Movies  — search the VOD library and tick individual movies/series.
 * Selections auto-save to [Profiles] as they're toggled.
 */
class KidContentActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var b: ActivityKidcontentBinding
    private val adapter = CheckAdapter { pos -> onToggle(pos) }

    private enum class Mode { LIVE, MOVIES }
    private var mode = Mode.LIVE

    // Working selections (id -> full object), seeded from saved whitelist.
    private val selChannels = LinkedHashMap<String, Portal.Channel>()
    private val selVod = LinkedHashMap<String, Portal.VodItem>()

    private var allChannels = listOf<Portal.Channel>()
    private val channelById = HashMap<String, Portal.Channel>()
    private val vodById = HashMap<String, Portal.VodItem>()

    private var pendingSearch: Runnable? = null
    private var searchSeq = 0
    private var connected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKidcontentBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter

        Profiles.allowedChannels(this).forEach { selChannels[it.id] = it }
        Profiles.allowedVod(this).forEach { selVod[it.id] = it; vodById[it.id] = it }

        b.liveTab.setOnClickListener { switchMode(Mode.LIVE) }
        b.moviesTab.setOnClickListener { switchMode(Mode.MOVIES) }
        b.clearBtn.setOnClickListener { b.search.setText("") }
        b.search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = onQuery(s?.toString() ?: "")
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
        })

        connectAndLoad()
    }

    private fun connectAndLoad() {
        val acct = Configs.active(this)
        if (acct == null) {
            b.status.text = "Add an IPTV provider first (IPTV Configuration below in Settings)."
            return
        }
        Portal.portalUrl = acct.portal
        Portal.mac = acct.mac
        Portal.sn = acct.sn
        b.status.text = "Connecting…"
        io.execute {
            val err = Portal.connect()
            val ch = if (err == null) Portal.liveChannels() else emptyList()
            runOnUiThread {
                if (err != null) { b.status.text = err; return@runOnUiThread }
                connected = true
                allChannels = ch
                channelById.clear()
                ch.forEach { channelById[it.id] = it }
                switchMode(Mode.LIVE)
            }
        }
    }

    private fun switchMode(m: Mode) {
        mode = m
        b.search.setText("")
        b.search.hint = if (m == Mode.LIVE) "Search channels…" else "Search movies & shows…"
        render("")
    }

    private fun onQuery(q: String) {
        if (mode == Mode.LIVE) render(q) else movieSearch(q)
    }

    // ---- LIVE ----
    private fun render(q: String) {
        if (mode == Mode.LIVE) {
            val query = q.trim()
            val list = if (query.isEmpty()) allChannels
            else allChannels.filter { it.name.contains(query, ignoreCase = true) }
            b.status.text = "${selChannels.size} channel(s) selected"
            adapter.submit(list.map { ch ->
                CheckAdapter.Item(ch.id, channelLabel(ch), ch.logoUrl, selChannels.containsKey(ch.id))
            })
        } else {
            // Movies tab with empty query → show what's already selected.
            b.status.text = "${selVod.size} movie(s)/show(s) selected — type to search for more"
            adapter.submit(selVod.values.map { v ->
                CheckAdapter.Item(v.id, vodLabel(v), v.posterUrl, true)
            })
        }
    }

    private fun channelLabel(ch: Portal.Channel) =
        "📺  " + (if (ch.number.isNotEmpty()) "${ch.number}. " else "") + ch.name

    private fun vodLabel(v: Portal.VodItem) = (if (v.isSeries) "📁  " else "🎬  ") + v.name

    // ---- MOVIES search ----
    private fun movieSearch(q: String) {
        val query = q.trim()
        pendingSearch?.let { ui.removeCallbacks(it) }
        searchSeq++
        if (query.length < 2) { render(""); return }
        b.status.text = "Searching…"
        val seq = searchSeq
        val task = Runnable {
            io.execute {
                val results = Portal.vodSearch(query)
                runOnUiThread {
                    if (seq != searchSeq) return@runOnUiThread
                    results.forEach { vodById[it.id] = it }
                    b.status.text = if (results.isEmpty()) "No results for “$query”."
                        else "${results.size} result(s) — ${selVod.size} selected"
                    adapter.submit(results.map { v ->
                        CheckAdapter.Item(v.id, vodLabel(v), v.posterUrl, selVod.containsKey(v.id))
                    })
                }
            }
        }
        pendingSearch = task
        ui.postDelayed(task, 450)
    }

    // ---- toggle + save ----
    private fun onToggle(pos: Int) {
        if (pos < 0) return
        val item = adapter.itemAt(pos)
        if (mode == Mode.LIVE) {
            if (selChannels.containsKey(item.id)) selChannels.remove(item.id)
            else channelById[item.id]?.let { selChannels[item.id] = it }
            Profiles.saveChannels(this, selChannels.values.toList())
            b.status.text = "${selChannels.size} channel(s) selected"
        } else {
            if (selVod.containsKey(item.id)) selVod.remove(item.id)
            else vodById[item.id]?.let { selVod[item.id] = it }
            Profiles.saveVod(this, selVod.values.toList())
            b.status.text = "${selVod.size} movie(s)/show(s) selected"
        }
        item.checked = !item.checked
        adapter.notifyItemChanged(pos)
    }
}
