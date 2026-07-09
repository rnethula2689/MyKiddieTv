package com.mykiddietv.app

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Create or edit a kid profile: Name → Content management → Picture. "Manage content" decides whether
 * the kid is limited to the parent-approved whitelist or can watch everything. Built in code (no XML)
 * since it's a simple linear form. Pass "kidId" to edit an existing kid.
 */
class KidEditActivity : AppCompatActivity() {

    private var editing: Profiles.Kid? = null
    private var manageContent = true   // default: approved-only (the safe choice)
    private var chosenAvatar = ""
    private var pendingCameraPath: String? = null

    private lateinit var nameField: EditText
    private lateinit var avatarPreview: TextView

    private val accent = 0xFF19C37D.toInt()

    private val pickGallery = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val a = Avatars.importFrom(this, uri)
            if (a != null) { chosenAvatar = a; refreshAvatar() } else toast("Couldn't load that image.")
        }
    }
    private val takePhoto = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok ->
        val p = pendingCameraPath
        if (ok && p != null) { chosenAvatar = Avatars.shrinkFile(this, p) ?: "file:$p"; refreshAvatar() }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editing = Profiles.kid(this, intent.getStringExtra("kidId"))
        editing?.let { manageContent = it.manageContent; chosenAvatar = it.avatar }

        val root = ScrollView(this).apply { setBackgroundColor(0xFF0B0F14.toInt()) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        root.addView(col)
        setContentView(root)

        col.addView(head(if (editing == null) "Add kid" else "Edit kid"))

        // ---- name ----
        col.addView(sectionLabel("Name"))
        nameField = EditText(this).apply {
            setSingleLine(); hint = "Kid's name"; setText(editing?.name ?: "")
            setTextColor(0xFFE6EDF3.toInt()); setHintTextColor(0xFF6B7684.toInt())
        }
        col.addView(nameField)

        // ---- content management ----
        col.addView(sectionLabel("Content"))
        col.addView(TextView(this).apply {
            text = "ON: this child only sees the shows & channels you approve in Manage Kid Content.\n" +
                "OFF: this child can watch everything on the service — including restricted / adult content."
            setTextColor(0xFF8B97A5.toInt()); textSize = 12f; setPadding(0, 0, 0, dp(8))
        })
        col.addView(manageSwitch())

        // ---- picture ----
        col.addView(sectionLabel("Picture"))
        avatarPreview = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96)); gravity = Gravity.CENTER; textSize = 44f
        }
        col.addView(avatarPreview)
        col.addView(emojiRow())
        val picBtns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        picBtns.addView(Button(this).apply { text = "📷 Camera"; setOnClickListener { launchCamera() } })
        picBtns.addView(Button(this).apply { text = "🖼 Gallery"; setOnClickListener {
            try { pickGallery.launch("image/*") } catch (_: Exception) { toast("No gallery app available.") } } })
        picBtns.addView(Button(this).apply { text = "↺ Default"; setOnClickListener { chosenAvatar = ""; refreshAvatar() } })
        col.addView(picBtns)

        // ---- save / delete ----
        val saveBtn = Button(this).apply { text = "Save"; setOnClickListener { save() } }
        saveBtn.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) }
        col.addView(saveBtn)
        if (editing != null) col.addView(Button(this).apply {
            text = "Delete profile"; setTextColor(0xFFFF6B6B.toInt()); setOnClickListener { confirmDelete() }
        })

        refreshAvatar()
    }

    private fun head(t: String) = TextView(this).apply {
        text = t; textSize = 26f; setTextColor(accent); setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(12))
    }

    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t; textSize = 15f; setTextColor(0xFFE6EDF3.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(6))
    }

    /** The "Manage this kid's content" toggle. Turning it OFF shows an adult-content disclaimer. */
    private fun manageSwitch(): android.widget.Switch {
        val sw = android.widget.Switch(this).apply {
            text = "  Manage this kid's content"
            textSize = 16f; setTextColor(0xFFE6EDF3.toInt())
            isChecked = manageContent
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }
        sw.setOnCheckedChangeListener { _, checked ->
            if (checked) { manageContent = true; return@setOnCheckedChangeListener }
            // Turning management OFF → confirm the parent understands the child gets full access.
            AlertDialog.Builder(this)
                .setTitle("Allow ALL content?")
                .setMessage("With content management OFF, this child can watch EVERYTHING on the service — " +
                    "including restricted, R-rated and adult content, with no filtering.\n\nAre you sure?")
                .setPositiveButton("Yes, allow everything") { _, _ -> manageContent = false }
                .setNegativeButton("Cancel") { _, _ -> sw.isChecked = true }
                .setOnCancelListener { sw.isChecked = true }
                .show()
        }
        return sw
    }

    private fun emojiRow(): HorizontalScrollView {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val rowL = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        // "default" tile first
        rowL.addView(emojiTile("🧸", ""))
        for (e in Avatars.EMOJI) rowL.addView(emojiTile(e, "emoji:$e"))
        scroll.addView(rowL)
        return scroll
    }

    private fun emojiTile(display: String, value: String): TextView {
        val tv = TextView(this)
        val lp = LinearLayout.LayoutParams(dp(52), dp(52)); lp.marginEnd = dp(8); tv.layoutParams = lp
        tv.gravity = Gravity.CENTER; tv.textSize = 24f; tv.text = display
        tv.isFocusable = true; tv.isClickable = true
        val bg = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0x22FFFFFF) }
        tv.background = bg
        tv.setOnFocusChangeListener { v, f -> val s = if (f) 1.2f else 1f; v.animate().scaleX(s).scaleY(s).setDuration(120).start() }
        tv.setOnClickListener { chosenAvatar = value; refreshAvatar() }
        return tv
    }

    private fun refreshAvatar() {
        val initial = nameField.text?.toString()?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        if (chosenAvatar.isBlank()) { avatarPreview.background = null; avatarPreview.text = "🧸"; avatarPreview.textSize = 44f }
        else { Avatars.render(avatarPreview, chosenAvatar, accent, initial, false); avatarPreview.textSize = if (chosenAvatar.startsWith("emoji:")) 34f else 22f }
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

    private fun save() {
        val name = nameField.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) { toast("Please enter a name."); return }
        val k = editing?.also { it.name = name; it.manageContent = manageContent; it.avatar = chosenAvatar }
            ?: Profiles.Kid(id = Profiles.newKidId(), name = name, avatar = chosenAvatar, manageContent = manageContent)
        Profiles.saveKid(this, k)
        Profiles.setActiveKid(this, k.id)
        toast("Saved ✓")
        finish()
    }

    private fun confirmDelete() {
        val k = editing ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete “${k.name}”?")
            .setMessage("Removes the profile and its approved-content list.")
            .setPositiveButton("Delete") { _, _ -> Profiles.deleteKid(this, k.id); finish() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
