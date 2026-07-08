package com.mykiddietv.app

import android.app.Activity
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Parental guardrails for the kid side: immersive UI, screen-pinning, and passcode prompts. */
object KidGuard {

    /** Hide the status/nav bars (they reappear on swipe). */
    fun immersive(activity: Activity) {
        val w = activity.window
        WindowCompat.setDecorFitsSystemWindows(w, false)
        WindowInsetsControllerCompat(w, w.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** Pin the app (screen pinning / lock-task) so Home & Recents are disabled while the kid is in. */
    fun startLock(activity: Activity) {
        try { activity.startLockTask() } catch (_: Exception) {}
    }

    fun stopLock(activity: Activity) {
        try { activity.stopLockTask() } catch (_: Exception) {}
    }

    /** Ask for the parent passcode; runs [onOk] when correct (or immediately if none is set). */
    fun promptPasscode(activity: Activity, title: String, onOk: () -> Unit) {
        if (!Profiles.hasPasscode(activity)) { onOk(); return }
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "4-digit passcode"
            gravity = Gravity.CENTER
            textSize = 24f
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(4))
        }
        val pad = (24 * activity.resources.displayMetrics.density).toInt()
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(FrameLayout(activity).apply { setPadding(pad, pad / 2, pad, 0); addView(input) })
            .setPositiveButton("OK") { _, _ ->
                if (Profiles.verifyPasscode(activity, input.text.toString())) onOk()
                else {
                    val s = Profiles.passcodeLockSecs(activity)
                    Toast.makeText(activity, if (s > 0) "Too many attempts — wait ${s}s." else "Wrong passcode.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
