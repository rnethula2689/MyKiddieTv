package com.mykiddietv.app

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import coil.load
import java.util.concurrent.Executors

/**
 * Kid-friendly preview screen for a single movie, shown for age bands YOUNGER and up (Preschool
 * plays straight away with no screen). What appears scales with the band:
 *   YOUNGER → big art + Play + Trailer + short description
 *   OLDER   → + full description
 *   TEEN    → + IMDb / Rotten Tomatoes ratings
 * Play uses the same kid VOD player path as the movies list.
 */
class KidDetailActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private var connected = false

    private lateinit var vodId: String
    private lateinit var cmd: String
    private lateinit var title: String
    private var poster: String = ""
    private var trailerKey: String? = null
    private var isSeries: Boolean = false

    private lateinit var backdrop: ImageView
    private lateinit var posterView: ImageView
    private lateinit var titleView: TextView
    private lateinit var ratingsView: TextView
    private lateinit var overviewView: TextView
    private lateinit var trailerBtn: Button

    // Series-only UI + state.
    private var seasonBtn: Button? = null
    private var downloadSeasonBtn: Button? = null
    private var downloadShowBtn: Button? = null
    private var episodesBox: LinearLayout? = null
    private var seasons: List<Portal.Season> = emptyList()
    private var curSeason: Portal.Season? = null
    private var firstEp: Portal.Episode? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vodId = intent.getStringExtra("vodId") ?: ""
        cmd = intent.getStringExtra("cmd") ?: ""
        title = intent.getStringExtra("title") ?: "Movie"
        poster = intent.getStringExtra("poster") ?: ""
        isSeries = intent.getBooleanExtra("isSeries", false)

        val root = FrameLayout(this).apply { setBackgroundColor(0xFF0B0F14.toInt()) }

        backdrop = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.28f
        }
        root.addView(backdrop)

        val scroll = ScrollView(this).apply { layoutParams = FrameLayout.LayoutParams(-1, -1) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
        }
        scroll.addView(col); root.addView(scroll)
        setContentView(root)

        posterView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(190), dp(285))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        if (poster.isNotBlank()) posterView.load(poster)
        col.addView(posterView)

        titleView = TextView(this).apply {
            text = Tmdb.cleanTitle(title); textSize = 24f; setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
        }
        col.addView(titleView)

        ratingsView = TextView(this).apply {
            textSize = 16f; setTextColor(0xFFFFD54F.toInt()); gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) }
        }
        col.addView(ratingsView)

        overviewView = TextView(this).apply {
            textSize = 15f; setTextColor(0xFFC9D2DC.toInt()); gravity = Gravity.CENTER
            maxLines = 100
            ellipsize = android.text.TextUtils.TruncateAt.END
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
        }
        col.addView(overviewView)

        // ---- Play (always) ----
        val play = Button(this).apply {
            text = "▶  Play"; textSize = 18f; setTextColor(0xFF08210F.toInt())
            background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(0xFF19C37D.toInt()) }
            setPadding(dp(40), dp(14), dp(40), dp(14))
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(22) }
            setOnClickListener { play() }
        }
        col.addView(play)

        // ---- Download (movie mode only) ----
        if (!isSeries) {
            val dl = Button(this).apply {
                text = "📥  Download"; textSize = 16f; setTextColor(0xFFE6EDF3.toInt())
                background = GradientDrawable().apply { cornerRadius = dp(26).toFloat(); setColor(0x333DA5FF) }
                setPadding(dp(34), dp(12), dp(34), dp(12))
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) }
                setOnClickListener { downloadMovie() }
            }
            col.addView(dl)
        }

        // ---- Trailer (YOUNGER+) ----
        trailerBtn = Button(this).apply {
            text = "🎬  Watch trailer"; textSize = 15f; setTextColor(0xFFE6EDF3.toInt())
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(0x22FFFFFF) }
            setPadding(dp(28), dp(10), dp(28), dp(10))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) }
            setOnClickListener {
                val key = trailerKey
                if (key.isNullOrBlank()) toast("No trailer available yet.")
                else startActivity(Intent(this@KidDetailActivity, TrailerActivity::class.java).putExtra("videoId", key))
            }
        }
        col.addView(trailerBtn)

        // ---- Series: season selector + episode list (below the header) ----
        if (isSeries) {
            seasonBtn = Button(this).apply {
                text = "Season  ▾"; textSize = 15f; setTextColor(0xFFE6EDF3.toInt())
                background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(0x33FFFFFF) }
                setPadding(dp(28), dp(10), dp(28), dp(10))
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(20) }
                setOnClickListener { pickSeason() }
            }
            col.addView(seasonBtn)

            downloadSeasonBtn = Button(this).apply {
                text = "📥  Download this season"; textSize = 15f; setTextColor(0xFFE6EDF3.toInt())
                background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(0x333DA5FF) }
                setPadding(dp(28), dp(10), dp(28), dp(10))
                isAllCaps = false
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) }
                setOnClickListener { curSeason?.let { confirmDownloadSeason(it) } }
            }
            col.addView(downloadSeasonBtn)

            downloadShowBtn = Button(this).apply {
                text = "📥  Download whole show"; textSize = 15f; setTextColor(0xFFE6EDF3.toInt())
                background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(0x333DA5FF) }
                setPadding(dp(28), dp(10), dp(28), dp(10))
                isAllCaps = false
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) }
                setOnClickListener { confirmDownloadWholeShow() }
            }
            col.addView(downloadShowBtn)

            episodesBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
            }
            col.addView(episodesBox)
        }

        play.requestFocus()
        connectPortal()
        loadDetails()
        if (isSeries) loadSeasons()
    }

    override fun onResume() { super.onResume(); KidGuard.immersive(this); KidLimits.onResume(this) }
    override fun onPause() { super.onPause(); KidLimits.onPause(this) }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun connectPortal() {
        val acct = Configs.active(this) ?: return
        Portal.portalUrl = acct.portal; Portal.mac = acct.mac; Portal.sn = acct.sn
        io.execute { val err = Portal.connect(); runOnUiThread { connected = err == null } }
    }

    /** Fetch TMDb art / overview / trailer (+ OMDb ratings for Teens), applying band gates. */
    private fun loadDetails() {
        val key = BuildConfig.TMDB_KEY
        if (key.isBlank()) return
        io.execute {
            val d = Tmdb.details(key, title, "", isSeries) ?: return@execute // isSeries → TV lookup
            val om = if (BuildConfig.OMDB_KEY.isNotBlank())
                Omdb.ratings(BuildConfig.OMDB_KEY, title, "") else null
            trailerKey = d.trailers.firstOrNull()?.youtubeKey
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (d.title.isNotBlank()) titleView.text = d.title
                (d.backdropUrl ?: d.posterUrl)?.let { backdrop.load(it) }
                if (poster.isBlank()) d.posterUrl?.let { posterView.load(it) }
                if (d.overview.isNotBlank()) {
                    overviewView.text = d.overview; overviewView.visibility = View.VISIBLE
                }
                if (!trailerKey.isNullOrBlank()) trailerBtn.visibility = View.VISIBLE
                run {
                    val parts = ArrayList<String>()
                    if (om?.imdb != null) parts.add("IMDb ${om.imdb}") else if (d.rating > 0) parts.add("★ %.1f".format(d.rating))
                    om?.rottenTomatoes?.let { parts.add("🍅 $it") }
                    if (parts.isNotEmpty()) { ratingsView.text = parts.joinToString("    ·    "); ratingsView.visibility = View.VISIBLE }
                }
            }
        }
    }

    private fun play() {
        if (isSeries) {
            // Series has no single file — play the first episode of the current season.
            val e = firstEp; val s = curSeason
            if (e == null || s == null) { toast("Loading episodes…"); return }
            playEpisode(s, e)
            return
        }
        if (!connected) { toast("Still getting ready…"); return }
        toast("Opening $title…")
        io.execute {
            val url = Portal.playVodUrl(vodId, cmd)
            runOnUiThread {
                if (url.isNullOrEmpty()) toast("Couldn't play “$title”. Try again.")
                else {
                    PlayerActivity.kidMode = true
                    // Carry the resume contract so the movie records progress (Continue Watching) and
                    // picks up where the kid left off.
                    val start = Resume.get(this, vodId)?.takeIf { Resume.resumable(it) }?.position ?: 0L
                    startActivity(Intent(this, PlayerActivity::class.java)
                        .putExtra("url", url).putExtra("title", title)
                        .putExtra("resumeId", vodId).putExtra("resumeSource", "vod|$vodId|$cmd")
                        .putExtra("resumePoster", poster).putExtra("resumeStart", start))
                }
            }
        }
    }

    // ---- Series: seasons + episodes ----
    private fun loadSeasons() {
        io.execute {
            val ss = Portal.seriesSeasons(vodId)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                seasons = ss
                if (ss.isEmpty()) return@runOnUiThread
                seasonBtn?.visibility = View.VISIBLE
                downloadSeasonBtn?.visibility = View.VISIBLE
                downloadShowBtn?.visibility = View.VISIBLE
                selectSeason(ss.first())
            }
        }
    }

    private fun pickSeason() {
        if (seasons.isEmpty()) return
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Choose a season")
            .setItems(seasons.map { it.name }.toTypedArray()) { _, w -> selectSeason(seasons[w]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun selectSeason(s: Portal.Season) {
        curSeason = s
        firstEp = null   // don't let Play pair the new season with the old season's first episode
        seasonBtn?.text = "${s.name}  ▾"
        episodesBox?.removeAllViews()
        io.execute {
            val eps = Portal.seriesEpisodes(vodId, s.id).reversed() // portal is newest-first → E1..En
            runOnUiThread {
                if (isFinishing || curSeason?.id != s.id) return@runOnUiThread
                firstEp = eps.firstOrNull()
                buildEpisodes(eps, s)
            }
        }
    }

    private fun buildEpisodes(eps: List<Portal.Episode>, s: Portal.Season) {
        val box = episodesBox ?: return
        box.removeAllViews()
        for (e in eps) {
            val row = Button(this).apply {
                text = "▶  ${e.name}    📥"; textSize = 15f; setTextColor(0xFFE6EDF3.toInt())
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(0x22FFFFFF) }
                setPadding(dp(20), dp(12), dp(20), dp(12))
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
                setOnClickListener { playEpisode(s, e) }
                setOnLongClickListener { downloadEpisode(s, e); true }
            }
            box.addView(row)
        }
    }

    // ---- downloads ----
    /** Enqueue this movie for offline (movie mode). Matches the download contract used by KidContentActivity. */
    private fun downloadMovie() {
        if (vodId.isBlank()) { toast("Can't download this yet."); return }
        if (Downloads.has(applicationContext, vodId)) { toast("Already downloaded."); return }
        Downloads.enqueue(applicationContext, vodId, title, poster, "vod|$vodId|$cmd")
        Profiles.addKidDownload(applicationContext, vodId)
        toast("Downloading “$title” — see Downloaded Movies & Shows.")
    }

    /** Enqueue a single episode per the contract (key/title use the ⟫-separated hierarchy). Returns true if newly queued. */
    private fun enqueueEpisode(s: Portal.Season, e: Portal.Episode): Boolean {
        val key = "$vodId|${s.id}|${e.id}"
        if (Downloads.has(applicationContext, key)) return false
        val seasonName = s.name.ifBlank { "Season" }
        val epTitle = "$title ⟫ $seasonName ⟫ ${e.name}"
        Downloads.enqueue(applicationContext, key, epTitle, poster, "ep|$vodId|${s.id}|${e.id}")
        Profiles.addKidDownload(applicationContext, key)
        return true
    }

    /** Single-episode download (long-press an episode row). */
    private fun downloadEpisode(s: Portal.Season, e: Portal.Episode) {
        if (enqueueEpisode(s, e)) toast("Downloading “${e.name}” — see Downloaded Movies & Shows.")
        else toast("Already downloaded.")
    }

    /** Confirm + download every episode of one season (uses the currently-loaded episode list when it matches). */
    private fun confirmDownloadSeason(s: Portal.Season) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Download season")
            .setMessage("Download all episodes of “${s.name}” from “$title” for ${Profiles.kidName(this)}?")
            .setPositiveButton("Yes, download") { _, _ -> startSeasonDownload(s) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startSeasonDownload(s: Portal.Season) {
        toast("Getting “${s.name}” ready to download…")
        io.execute {
            val eps = Portal.seriesEpisodes(vodId, s.id)
            var started = 0
            for (e in eps) if (enqueueEpisode(s, e)) started++
            val n = started
            runOnUiThread { toast("Downloading $n episode(s) — see Downloaded Movies & Shows.") }
        }
    }

    /** Confirm + download every episode of every season of the show. */
    private fun confirmDownloadWholeShow() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Download whole show")
            .setMessage("Download every episode of “$title” for ${Profiles.kidName(this)}?")
            .setPositiveButton("Yes, download") { _, _ -> startWholeShowDownload() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startWholeShowDownload() {
        toast("Getting “$title” ready to download…")
        io.execute {
            val ss = if (seasons.isNotEmpty()) seasons else Portal.seriesSeasons(vodId)
            var started = 0
            for (s in ss) {
                val eps = Portal.seriesEpisodes(vodId, s.id)
                for (e in eps) if (enqueueEpisode(s, e)) started++
            }
            val n = started
            runOnUiThread { toast("Downloading $n episode(s) — see Downloaded Movies & Shows.") }
        }
    }

    private fun playEpisode(s: Portal.Season, e: Portal.Episode) {
        if (!connected) { toast("Still getting ready…"); return }
        val label = "$title — ${e.name}"
        toast("Opening $label…")
        io.execute {
            val url = Portal.playEpisodeUrl(vodId, s.id, e.id)
            runOnUiThread {
                if (url.isNullOrEmpty()) toast("Couldn't play “$label”. Try again.")
                else {
                    PlayerActivity.kidMode = true
                    val rid = "$vodId|${s.id}|${e.id}"
                    val start = Resume.get(this, rid)?.takeIf { Resume.resumable(it) }?.position ?: 0L
                    startActivity(Intent(this, PlayerActivity::class.java)
                        .putExtra("url", url).putExtra("title", label)
                        .putExtra("resumeId", rid).putExtra("resumeSource", "ep|$vodId|${s.id}|${e.id}")
                        .putExtra("resumePoster", poster).putExtra("resumeStart", start))
                }
            }
        }
    }
}
