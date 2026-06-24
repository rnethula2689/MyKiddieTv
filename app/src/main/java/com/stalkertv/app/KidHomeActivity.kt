package com.stalkertv.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.stalkertv.app.databinding.ActivityKidhomeBinding
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKidhomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.greeting.text = "Hi ${Profiles.kidName(this)}! 👋"

        b.liveBtn.setOnClickListener { openLive() }
        b.moviesBtn.setOnClickListener {
            Toast.makeText(this, "Movies are coming soon!", Toast.LENGTH_SHORT).show()
        }

        connectPortal()
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
            runOnUiThread {
                connected = err == null
                b.status.text = if (connected) {
                    val n = Profiles.allowedChannels(this).size
                    if (n == 0) "No channels yet. Ask a grown-up to add some." else "Ready! 🎉"
                } else "Couldn't connect. Ask a grown-up to check Settings."
            }
        }
    }

    private fun openLive() {
        val channels = Profiles.allowedChannels(this)
        if (channels.isEmpty()) {
            Toast.makeText(this, "No channels yet — ask a grown-up.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!connected) {
            Toast.makeText(this, "Still getting ready…", Toast.LENGTH_SHORT).show()
            return
        }
        LiveGridActivity.channels = channels
        LiveGridActivity.gridTitle = "Live TV"
        LiveGridActivity.kidMode = true
        startActivity(Intent(this, LiveGridActivity::class.java))
    }
}
