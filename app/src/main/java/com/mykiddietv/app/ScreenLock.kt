package com.mykiddietv.app

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/**
 * A floating screen-lock for fullscreen players. Tap 🔒 to lock — a transparent blocker
 * swallows all touches (so a kid can't change channel, open menus, or exit), and the host
 * activity swallows key events while [locked]. Tap 🔓 → parent passcode → unlock.
 */
class ScreenLock(private val activity: Activity) {
    var locked = false
        private set

    private val content = activity.findViewById<FrameLayout>(android.R.id.content)

    // Full-screen, (near) transparent, clickable → intercepts touches while locked.
    private val blocker = View(activity).apply {
        isClickable = true
        isFocusable = true
        setBackgroundColor(0x01000000)
        visibility = View.GONE
    }

    private val btn = TextView(activity).apply {
        text = "🔒"
        textSize = 20f
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0x66000000.toInt())
        setPadding(28, 16, 28, 16)
        setOnClickListener { if (locked) requestUnlock() else lock() }
    }

    init {
        content.addView(blocker, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.topMargin = 24; lp.leftMargin = 24
        content.addView(btn, lp)
    }

    private fun lock() {
        locked = true
        btn.text = "🔓"
        blocker.visibility = View.VISIBLE
        btn.bringToFront()
        Toast.makeText(activity, "Screen locked 🔒", Toast.LENGTH_SHORT).show()
    }

    private fun requestUnlock() {
        KidGuard.promptPasscode(activity, "Enter passcode to unlock") {
            locked = false
            btn.text = "🔒"
            blocker.visibility = View.GONE
            Toast.makeText(activity, "Unlocked", Toast.LENGTH_SHORT).show()
        }
    }
}
