package com.mykiddietv.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mykiddietv.app.databinding.ActivityLivevlcBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.concurrent.Executors

/** Fullscreen live-TV player backed by libVLC (handles any codec/protocol). */
class LiveVlcActivity : AppCompatActivity() {
    companion object {
        var liveChannels: List<Portal.Channel> = emptyList()
        // Set by the launching screen; hides parent-only menu items for kids.
        var kidMode: Boolean = false
    }

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var b: ActivityLivevlcBinding
    private var libVlc: LibVLC? = null
    private var mp: MediaPlayer? = null
    private var channels: List<Portal.Channel> = emptyList()
    private var chIndex = -1
    private var titleText = ""
    private var hideBarRunnable: Runnable? = null
    private var screenLock: ScreenLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLivevlcBinding.inflate(layoutInflater)
        setContentView(b.root)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // don't sleep during live TV

        val url = intent.getStringExtra("url") ?: run { finish(); return }
        titleText = intent.getStringExtra("title") ?: ""
        chIndex = intent.getIntExtra("chIndex", -1)
        channels = liveChannels
        b.title.text = titleText

        val options = arrayListOf(
            "--network-caching=1500",
            "--http-reconnect",
            "--no-drop-late-frames",
            "--no-skip-frames"
        )
        val vlc = LibVLC(this, options)
        libVlc = vlc
        val player = MediaPlayer(vlc)
        mp = player
        player.attachViews(b.vlc, null, false, false)
        player.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Playing -> b.status.visibility = View.GONE
                MediaPlayer.Event.EncounteredError ->
                    b.status.apply { visibility = View.VISIBLE; text = "Couldn't play this channel." }
                else -> {}
            }
        }

        b.menuBtn.setOnClickListener { showMenu() }
        b.root.setOnClickListener { showBar() }

        play(url)
        showBar()
        KidGuard.immersive(this)
        screenLock = ScreenLock(this)
    }

    private fun play(url: String) {
        val vlc = libVlc ?: return
        val player = mp ?: return
        b.status.visibility = View.VISIBLE
        b.status.text = "Loading…"
        player.stop()
        val media = Media(vlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false) // HW first, auto software fallback
        media.addOption(":network-caching=1500")
        media.addOption(":http-user-agent=" + Portal.UA)
        media.addOption(":http-reconnect")
        player.media = media
        media.release()
        player.play()
    }

    /** Up = previous channel, Down = next. */
    private fun switchChannel(delta: Int) {
        if (channels.isEmpty() || chIndex < 0) return
        var idx = chIndex + delta
        if (idx < 0) idx = 0
        if (idx > channels.size - 1) idx = channels.size - 1
        if (idx == chIndex) return
        chIndex = idx
        val ch = channels[idx]
        titleText = ch.name
        b.title.text = ch.name
        showBar()
        b.status.visibility = View.VISIBLE
        b.status.text = "▶  ${ch.name}…"
        io.execute {
            val u = Portal.createLink(ch.cmd)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (u.isNullOrEmpty()) { b.status.text = "No stream for ${ch.name}"; return@runOnUiThread }
                play(u)
            }
        }
    }

    private fun showBar() {
        b.topBar.visibility = View.VISIBLE
        b.hint.visibility = View.VISIBLE
        hideBarRunnable?.let { ui.removeCallbacks(it) }
        val r = Runnable { b.topBar.visibility = View.GONE; b.hint.visibility = View.GONE }
        hideBarRunnable = r
        ui.postDelayed(r, 4000)
    }

    private var menuDialog: AlertDialog? = null
    private fun showMenu() {
        if (menuDialog?.isShowing == true) { menuDialog?.dismiss(); return }
        val items = if (kidMode)
            arrayOf("ℹ️   About")
        else
            arrayOf("⚙   Settings", "📥   App updates", "ℹ️   About", "✖   Exit")
        val dlg = AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                val action = items[which]
                when {
                    action.contains("Settings") -> startActivity(Intent(this, SettingsActivity::class.java))
                    action.contains("App updates") -> startActivity(Intent(this, AppUpdatesActivity::class.java))
                    action.contains("About") -> About.show(this)
                    action.contains("Exit") -> finishAffinity()
                }
            }
            .setOnDismissListener { menuDialog = null }
            .create()
        menuDialog = dlg
        dlg.show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (screenLock?.locked == true) return true
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU -> { showMenu(); return true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { switchChannel(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { switchChannel(1); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { showBar(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onBackPressed() {
        if (screenLock?.locked == true) return
        super.onBackPressed()
    }

    override fun onStop() {
        super.onStop()
        mp?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideBarRunnable?.let { ui.removeCallbacks(it) }
        mp?.let { it.stop(); it.detachViews(); it.release() }
        mp = null
        libVlc?.release()
        libVlc = null
    }
}
