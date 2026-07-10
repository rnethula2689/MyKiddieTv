package com.mykiddietv.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
