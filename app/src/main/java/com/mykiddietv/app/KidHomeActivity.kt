package com.mykiddietv.app

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.mykiddietv.app.databinding.ActivityKidhomeBinding
import java.util.concurrent.Executors

/**
 * Kid landing screen. Only ever shows content the parent whitelisted
 * (see [Profiles]). This is intentionally minimal for now — the richer kid
 * home is built later. Live TV is wired up so the whitelist is usable today.
 */
class KidHomeActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var b: ActivityKidhomeBinding
    private var connected = false
    private var allChannels: List<Portal.Channel> = emptyList()   // full list, for a non-managed kid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKidhomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.greeting.text = "Hi ${Profiles.kidName(this)}! 👋"

        b.liveBtn.setOnClickListener { openLive() }
        b.moviesBtn.setOnClickListener { startActivity(Intent(this, KidMoviesActivity::class.java)) }

        connectPortal()
    }

    override fun onResume() {
        super.onResume()
        // Lock the kid in: hide system bars and pin the app (Home/Recents disabled while pinned).
        KidGuard.immersive(this)
        KidGuard.startLock(this)
        KidLimits.onResume(this) // screen-time: enforce limit/bedtime + start counting
        refreshContinueWatching() // reflect anything just watched
    }

    override fun onPause() {
        super.onPause()
        KidLimits.onPause(this)
    }

    /** Back from the kid home returns to the profile picker (passcode-gated if one is set) —
     *  never exits the app. We navigate to ProfileActivity explicitly so it works regardless
     *  of the back-stack state under screen-pinning. */
    override fun onBackPressed() {
        KidGuard.promptPasscode(this, "Enter passcode to exit Kids") {
            KidGuard.stopLock(this)
            startActivity(Intent(this, ProfileActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
    }

    /** The kid screen can be the first thing launched, so make sure the portal is connected. */
    private fun connectPortal() {
        val acct = Configs.active(this)
        if (acct == null) {
            b.status.text = "Not set up yet. Ask a grown-up to open the Parent profile."
            b.liveBtn.isEnabled = false
            b.moviesBtn.isEnabled = false
            return
        }
        b.status.text = "Getting things ready…"
        Portal.portalUrl = acct.portal
        Portal.mac = acct.mac
        Portal.sn = acct.sn
        io.execute {
            val err = Portal.connect()
            val all = if (err == null && !Profiles.activeManageContent(this)) Portal.liveChannels() else emptyList()
            runOnUiThread {
                connected = err == null
                allChannels = all
                val manage = Profiles.activeManageContent(this)
                b.status.text = if (connected) {
                    val n = if (manage) Profiles.allowedChannels(this).size else all.size
                    if (n == 0) (if (manage) "No channels yet. Ask a grown-up to add some." else "No channels available.")
                    else "Ready! 🎉"
                } else "Couldn't connect. Ask a grown-up to check Settings."
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Show a "Keep watching" rail of movies/episodes the kid is partway through (from the Resume store,
     *  restricted items filtered out). Tapping a card resumes from where they left off. */
    private fun refreshContinueWatching() {
        // Resume is scoped per account (not per kid), so a content-managed kid must only ever see items
        // that are STILL on their own whitelist — never a sibling's. Full-access kids can watch anything,
        // so no extra filter is needed for them. ("Hide, never show.")
        val manage = Profiles.activeManageContent(this)
        val entries = Resume.all(this).filterNot { it.restricted }.filter { Resume.resumable(it) }
            .filter { !manage || isApprovedForKid(it) }
            .take(12)
        b.cwRow.removeAllViews()
        if (entries.isEmpty()) { b.cwSection.visibility = View.GONE; return }
        b.cwSection.visibility = View.VISIBLE
        for (e in entries) b.cwRow.addView(continueCard(e))
    }

    /** Is this resume entry still on the active (content-managed) kid's approved list? */
    private fun isApprovedForKid(e: Resume.Entry): Boolean {
        val p = e.source.split("|")
        return when (p.getOrNull(0)) {
            "vod" -> Profiles.allowedVod(this).any { it.id == p.getOrElse(1) { "" } }
            "ep" -> {
                val seriesId = p.getOrElse(1) { "" }
                val key = "$seriesId|${p.getOrElse(2) { "" }}|${p.getOrElse(3) { "" }}"
                Profiles.allowedEpisodes(this).any { it.key == key } ||
                    Profiles.allowedVod(this).any { it.id == seriesId } // whole series approved
            }
            else -> false
        }
    }

    private fun continueCard(e: Resume.Entry): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(130), -2).apply { marginEnd = dp(14) }
            isFocusable = true; isClickable = true
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val poster = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(122), dp(183))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF1B2430.toInt())
        }
        if (e.poster.isNotBlank()) poster.load(e.poster)
        card.addView(poster)
        val label = TextView(this).apply {
            text = Tmdb.cleanTitle(e.title); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(0xFFC9D2DC.toInt()); textSize = 13f
            layoutParams = LinearLayout.LayoutParams(dp(122), -2).apply { topMargin = dp(6) }
        }
        card.addView(label)
        val border = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setStroke(dp(3), 0xFF19C37D.toInt()) }
        card.setOnFocusChangeListener { _, has ->
            card.background = if (has) border else null
            label.setTextColor(if (has) 0xFFFFFFFF.toInt() else 0xFFC9D2DC.toInt())
        }
        card.setOnClickListener { resumeEntry(e) }
        return card
    }

    private fun resumeEntry(e: Resume.Entry) {
        if (!connected) { Toast.makeText(this, "Still getting ready…", Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(this, "Opening ${Tmdb.cleanTitle(e.title)}…", Toast.LENGTH_SHORT).show()
        io.execute {
            val url = Downloads.resolveSource(e.source)
            runOnUiThread {
                if (url.isNullOrEmpty()) { Toast.makeText(this, "Couldn't open. Try again.", Toast.LENGTH_SHORT).show(); return@runOnUiThread }
                PlayerActivity.kidMode = true
                startActivity(Intent(this, PlayerActivity::class.java)
                    .putExtra("url", url).putExtra("title", e.title)
                    .putExtra("resumeId", e.id).putExtra("resumeSource", e.source)
                    .putExtra("resumePoster", e.poster).putExtra("resumeStart", e.position))
            }
        }
    }

    private fun openLive() {
        if (!connected) {
            Toast.makeText(this, "Still getting ready…", Toast.LENGTH_SHORT).show()
            return
        }
        val manage = Profiles.activeManageContent(this)
        if (!manage) {
            // Full-access kid: show a folder chooser (All Channels + one folder per allowed genre).
            // Hand over the catalogue we already fetched so Live TV opens instantly (no refetch).
            KidLiveActivity.preChannels = allChannels
            startActivity(Intent(this, KidLiveActivity::class.java))
            return
        }
        // Content-managed kid: keep the flat, hand-picked list.
        val channels = Profiles.allowedChannels(this)
        if (channels.isEmpty()) {
            Toast.makeText(this, "No channels yet — ask a grown-up.", Toast.LENGTH_SHORT).show()
            return
        }
        LiveGridActivity.channels = channels
        LiveGridActivity.gridTitle = "Live TV"
        LiveGridActivity.kidMode = true
        startActivity(Intent(this, LiveGridActivity::class.java))
    }
}
