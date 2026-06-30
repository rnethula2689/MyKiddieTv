package com.mykiddietv.app

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mykiddietv.app.databinding.ActivityProfilesBinding

/**
 * Launcher screen. Shows two profiles:
 *   • Kid  → the kid home (only whitelisted content).
 *   • Parent → 4-digit passcode → the full MyKiddieTv browse experience.
 */
class ProfileActivity : AppCompatActivity() {
    private lateinit var b: ActivityProfilesBinding
    private var pendingCameraPath: String? = null

    private val pickGallery = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val a = Avatars.importFrom(this, uri)
            if (a != null) { Profiles.setKidAvatar(this, a); renderKidAvatar() } else toast("Couldn't load that image.")
        }
    }
    private val takePhoto = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok ->
        val p = pendingCameraPath
        if (ok && p != null) { Profiles.setKidAvatar(this, Avatars.shrinkFile(this, p) ?: "file:$p"); renderKidAvatar() }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityProfilesBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.kidTile.setOnClickListener { openKid() }
        b.parentTile.setOnClickListener { openParent() }
        // Long-press the Kids tile to change its picture (parent-gated if a passcode is set).
        b.kidTile.setOnLongClickListener { ensureParent { showKidPictureMenu() }; true }
    }

    override fun onResume() {
        super.onResume()
        b.parentName.text = Profiles.parentName(this)
        b.kidName.text = Profiles.kidName(this)
        renderKidAvatar()
        b.kidTile.requestFocus()
    }

    /** Paint the kid tile's picture: a photo (circle), a chosen emoji, or the default teddy bear. */
    private fun renderKidAvatar() {
        val av = Profiles.kidAvatar(this)
        when {
            av.startsWith("file:") -> {
                val bm = Avatars.circularBitmap(av.removePrefix("file:"), (120 * resources.displayMetrics.density).toInt())
                if (bm != null) { b.kidAvatar.text = ""; b.kidAvatar.background = android.graphics.drawable.BitmapDrawable(resources, bm) }
                else { b.kidAvatar.text = "🧸"; b.kidAvatar.background = null }
            }
            av.startsWith("emoji:") -> { b.kidAvatar.text = av.removePrefix("emoji:"); b.kidAvatar.background = null }
            else -> { b.kidAvatar.text = "🧸"; b.kidAvatar.background = null }
        }
    }

    /** Run [block] only after the parent passcode (if one is set). */
    private fun ensureParent(block: () -> Unit) {
        if (!Profiles.hasPasscode(this)) { block(); return }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "4-digit passcode"; gravity = Gravity.CENTER; textSize = 24f
            filters = arrayOf<android.text.InputFilter>(android.text.InputFilter.LengthFilter(4))
        }
        val pad = (24 * resources.displayMetrics.density).toInt()
        AlertDialog.Builder(this)
            .setTitle("Parent passcode")
            .setView(android.widget.FrameLayout(this).apply { setPadding(pad, pad / 2, pad, 0); addView(input) })
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == Profiles.passcode(this)) block() else toast("Wrong passcode.")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showKidPictureMenu() {
        AlertDialog.Builder(this)
            .setTitle("Change ${Profiles.kidName(this)}'s picture")
            .setItems(arrayOf("📷  Take a photo", "🖼  Choose from gallery", "🙂  Pick a fun icon", "↺  Default (teddy bear)")) { _, w ->
                when (w) {
                    0 -> launchCamera()
                    1 -> try { pickGallery.launch("image/*") } catch (_: Exception) { toast("No gallery app available.") }
                    2 -> showEmojiPick()
                    3 -> { Profiles.setKidAvatar(this, ""); renderKidAvatar() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEmojiPick() {
        val emoji = Avatars.EMOJI.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Pick a fun icon")
            .setItems(emoji) { _, w -> Profiles.setKidAvatar(this, "emoji:${emoji[w]}"); renderKidAvatar() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchCamera() {
        try {
            val dir = java.io.File(getExternalFilesDir(null), "avatars").apply { mkdirs() }
            val f = java.io.File(dir, "kid_${System.currentTimeMillis()}.jpg")
            pendingCameraPath = f.absolutePath
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            takePhoto.launch(uri)
        } catch (e: Exception) { toast("Camera not available: ${e.message}") }
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
