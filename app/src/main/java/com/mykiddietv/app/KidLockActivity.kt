package com.mykiddietv.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mykiddietv.app.databinding.ActivityKidlockBinding

/**
 * Shown when kid mode is blocked — daily screen-time used up, or it's bedtime. Stays pinned (the child
 * can't leave); only a parent passcode exits back to the profile picker. Re-checks on resume so the
 * child can't sit here past bedtime/limit and slip back in.
 */
class KidLockActivity : AppCompatActivity() {
    private lateinit var b: ActivityKidlockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKidlockBinding.inflate(layoutInflater)
        setContentView(b.root)
        val reason = intent.getStringExtra("reason") ?: "limit"
        if (reason == "bedtime") {
            b.icon.text = "🌙"
            b.title.text = "It's bedtime"
            b.message.text = "Time to rest. The app will be ready again in the morning. 🌙"
        } else {
            b.icon.text = "⏰"
            b.title.text = "Time's up for today!"
            val lim = KidLimits.dailyLimitMin(this)
            b.message.text = "You've watched your ${KidLimits.fmtMin(lim)} for today. See you tomorrow! 👋"
        }
        b.unlockBtn.setOnClickListener {
            KidGuard.promptPasscode(this, "Enter passcode to exit") {
                KidGuard.stopLock(this)
                startActivity(Intent(this, ProfileActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        KidGuard.immersive(this)
        KidGuard.startLock(this)
    }

    /** Back does nothing — only the parent unlock leaves. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* trapped */ }
}
