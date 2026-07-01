package com.mykiddietv.app

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mykiddietv.app.databinding.ActivityProfilesBinding

/**
 * Launcher / "Who's watching?" screen. Shows one tile per kid profile (tap → that kid's home),
 * an ➕ Add-kid tile, and the Parent tile (passcode → full browse). Long-pressing a kid tile
 * (parent-gated) offers Manage content / Edit / Delete.
 */
class ProfileActivity : AppCompatActivity() {
    private lateinit var b: ActivityProfilesBinding

    // Friendly default-avatar colours, assigned by tile order.
    private val palette = intArrayOf(
        0xFF4F8CFF.toInt(), 0xFF19C37D.toInt(), 0xFFFF6B6B.toInt(),
        0xFFFFB020.toInt(), 0xFFB36BFF.toInt(), 0xFF20C9C9.toInt(), 0xFFE05BD6.toInt()
    )

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityProfilesBinding.inflate(layoutInflater)
        setContentView(b.root)
    }

    override fun onResume() {
        super.onResume()
        buildTiles()
    }

    private fun buildTiles() {
        b.tileRow.removeAllViews()
        val kids = Profiles.kids(this)
        kids.forEachIndexed { i, k -> b.tileRow.addView(kidTile(k, palette[i % palette.size])) }
        b.tileRow.addView(addKidTile())
        b.tileRow.addView(parentTile())
        b.tileRow.getChildAt(0)?.requestFocus()
    }

    // ---- tiles ----
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tileFrame(): LinearLayout {
        val t = LinearLayout(this)
        val lp = LinearLayout.LayoutParams(dp(180), dp(200)); lp.marginEnd = dp(20)
        t.layoutParams = lp
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER
        t.isFocusable = true; t.isClickable = true
        t.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.item_bg)
        t.setOnFocusChangeListener { v, f -> val s = if (f) 1.06f else 1f; v.animate().scaleX(s).scaleY(s).setDuration(120).start() }
        return t
    }

    /** A wrap-content label with an optional top margin, centred. */
    private fun label(text: String, size: Float, color: Int, bold: Boolean = false, topDp: Int = 0): TextView {
        val tv = TextView(this)
        tv.text = text; tv.textSize = size; tv.setTextColor(color)
        if (bold) tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        tv.gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(topDp)
        tv.layoutParams = lp
        return tv
    }

    /** A fixed 110dp square icon TextView (emoji / initial). */
    private fun iconView(size: Float): TextView {
        val tv = TextView(this)
        tv.layoutParams = LinearLayout.LayoutParams(dp(110), dp(110))
        tv.gravity = Gravity.CENTER
        tv.textSize = size
        tv.setTextColor(0xFFFFFFFF.toInt())
        return tv
    }

    private fun kidTile(k: Profiles.Kid, color: Int): View {
        val t = tileFrame()
        val avatar = iconView(64f)
        val initial = k.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "🙂"
        if (k.avatar.isBlank()) avatar.text = "🧸" else Avatars.render(avatar, k.avatar, color, initial, false)
        if (k.avatar.startsWith("emoji:")) avatar.textSize = 56f
        t.addView(avatar)
        t.addView(label(k.name, 20f, 0xFFE6EDF3.toInt(), true, 8))
        t.addView(label(AgeBands.of(k.ageBand).name, 13f, 0xFF8B97A5.toInt()))
        t.setOnClickListener { Profiles.setActiveKid(this, k.id); startActivity(Intent(this, KidHomeActivity::class.java)) }
        t.setOnLongClickListener { ensureParent { manageKid(k) }; true }
        return t
    }

    private fun addKidTile(): View {
        val t = tileFrame()
        t.addView(iconView(60f).apply { text = "➕"; setTextColor(0xFF19C37D.toInt()) })
        t.addView(label("Add kid", 20f, 0xFFE6EDF3.toInt(), true, 8))
        t.addView(label("new profile", 13f, 0xFF8B97A5.toInt()))
        t.setOnClickListener { ensureParent { startActivity(Intent(this, KidEditActivity::class.java)) } }
        return t
    }

    private fun parentTile(): View {
        val t = tileFrame()
        t.addView(iconView(60f).apply { text = "👤" })
        t.addView(label(Profiles.parentName(this), 20f, 0xFFE6EDF3.toInt(), true, 8))
        t.addView(label("🔒 passcode", 13f, 0xFF8B97A5.toInt()))
        t.setOnClickListener { openParent() }
        return t
    }

    // ---- per-kid management (long-press) ----
    private fun manageKid(k: Profiles.Kid) {
        AlertDialog.Builder(this)
            .setTitle(k.name)
            .setItems(arrayOf("🎬  Manage content", "✏️  Edit (name, age, picture)", "🗑  Delete profile")) { _, w ->
                when (w) {
                    0 -> { Profiles.setActiveKid(this, k.id); startActivity(Intent(this, KidContentActivity::class.java)) }
                    1 -> startActivity(Intent(this, KidEditActivity::class.java).putExtra("kidId", k.id))
                    2 -> confirmDelete(k)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(k: Profiles.Kid) {
        AlertDialog.Builder(this)
            .setTitle("Delete “${k.name}”?")
            .setMessage("This removes the profile and its approved-content list. Downloads made for this kid are not deleted.")
            .setPositiveButton("Delete") { _, _ -> Profiles.deleteKid(this, k.id); buildTiles() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Run [block] only after the parent passcode (if one is set). */
    private fun ensureParent(block: () -> Unit) {
        if (!Profiles.hasPasscode(this)) { block(); return }
        val input = passcodeField()
        val pad = dp(24)
        AlertDialog.Builder(this)
            .setTitle("Parent passcode")
            .setView(android.widget.FrameLayout(this).apply { setPadding(pad, pad / 2, pad, 0); addView(input) })
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == Profiles.passcode(this)) block() else toast("Wrong passcode.")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun passcodeField() = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        hint = "4-digit passcode"; gravity = Gravity.CENTER; textSize = 24f
        filters = arrayOf<android.text.InputFilter>(android.text.InputFilter.LengthFilter(4))
    }

    private fun openParent() {
        if (!Profiles.hasPasscode(this)) {
            Toast.makeText(this, "No passcode set yet — set one in Settings.", Toast.LENGTH_LONG).show()
            enterParent(); return
        }
        val input = passcodeField()
        val pad = dp(24)
        AlertDialog.Builder(this)
            .setTitle("Enter parent passcode")
            .setView(android.widget.FrameLayout(this).apply { setPadding(pad, pad / 2, pad, 0); addView(input) })
            .setPositiveButton("Unlock") { _, _ ->
                if (input.text.toString() == Profiles.passcode(this)) enterParent()
                else Toast.makeText(this, "Wrong passcode.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun enterParent() { startActivity(Intent(this, ChannelsActivity::class.java)) }
}
