package com.stalkertv.app

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.stalkertv.app.databinding.ActivityProfilesBinding

/**
 * Launcher screen. Shows two profiles:
 *   • Kid  → the kid home (only whitelisted content).
 *   • Parent → 4-digit passcode → the full StalkerTV browse experience.
 */
class ProfileActivity : AppCompatActivity() {
    private lateinit var b: ActivityProfilesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityProfilesBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.kidTile.setOnClickListener { openKid() }
        b.parentTile.setOnClickListener { openParent() }
    }

    override fun onResume() {
        super.onResume()
        b.parentName.text = Profiles.parentName(this)
        b.kidName.text = Profiles.kidName(this)
        b.kidTile.requestFocus()
    }

    private fun openKid() {
        startActivity(Intent(this, KidHomeActivity::class.java))
    }

    private fun openParent() {
        // First run (no passcode set yet): let the parent straight in to set one up.
        if (!Profiles.hasPasscode(this)) {
            Toast.makeText(this, "No passcode set yet — set one in Settings.", Toast.LENGTH_LONG).show()
            enterParent()
            return
        }
        promptPasscode()
    }

    private fun promptPasscode() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "4-digit passcode"
            gravity = Gravity.CENTER
            textSize = 24f
            filters = arrayOf<android.text.InputFilter>(android.text.InputFilter.LengthFilter(4))
        }
        val pad = (24 * resources.displayMetrics.density).toInt()
        AlertDialog.Builder(this)
            .setTitle("Enter parent passcode")
            .setView(android.widget.FrameLayout(this).apply {
                setPadding(pad, pad / 2, pad, 0); addView(input)
            })
            .setPositiveButton("Unlock") { _, _ ->
                if (input.text.toString() == Profiles.passcode(this)) {
                    enterParent()
                } else {
                    Toast.makeText(this, "Wrong passcode.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun enterParent() {
        startActivity(Intent(this, ChannelsActivity::class.java))
    }
}
