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

    private lateinit var backdrop: ImageView
    private lateinit var posterView: ImageView
    private lateinit var titleView: TextView
    private lateinit var ratingsView: TextView
    private lateinit var overviewView: TextView
    private lateinit var trailerBtn: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vodId = intent.getStringExtra("vodId") ?: ""
        cmd = intent.getStringExtra("cmd") ?: ""
        title = intent.getStringExtra("title") ?: "Movie"
        poster = intent.getStringExtra("poster") ?: ""

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

        play.requestFocus()
        connectPortal()
        loadDetails()
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
            val d = Tmdb.details(key, title, "", false) ?: return@execute
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
        if (!connected) { toast("Still getting ready…"); return }
        toast("Opening $title…")
        io.execute {
            val url = Portal.playVodUrl(vodId, cmd)
            runOnUiThread {
                if (url.isNullOrEmpty()) toast("Couldn't play “$title”. Try again.")
                else {
                    PlayerActivity.kidMode = true
                    startActivity(Intent(this, PlayerActivity::class.java).putExtra("url", url).putExtra("title", title))
                }
            }
        }
    }
}
