package com.mykiddietv.app

import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * The per-kid "Content settings" dialog, shared by the Manage-Kid-Content 🎛️ gear and
 * Settings ▸ Kids ▸ Content settings.
 *
 * It also hosts the GLOBAL "filter by age rating" master switch: because IPTV portal rating data
 * is patchy, a parent can turn the age filter off entirely and curate by judgement — every title
 * then shows (with its rating badge) in the parent's pick list. Kids' own browsing always keeps
 * their per-kid age cap, so switching this off never exposes a kid directly.
 */
object KidContentSettings {

    fun show(a: AppCompatActivity, kid: Profiles.Kid, onSaved: (() -> Unit)? = null) {
        val band = AgeBands.of(kid.ageBand)
        val dp = a.resources.displayMetrics.density
        val pad = (20 * dp).toInt()
        fun tv(t: String, size: Float, color: Int) = TextView(a).apply { text = t; textSize = size; setTextColor(color) }

        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }

        // --- Global master switch ---
        val cbGlobal = CheckBox(a).apply {
            text = "Filter kids' content by age rating"
            isChecked = Configs.kidFilterEnabled(a)
            setTextColor(0xFFE6EDF3.toInt())
        }
        col.addView(cbGlobal)
        col.addView(tv("Off: show every title (with its rating) in the pick list so you can curate by hand — " +
            "handy when the portal's rating data is unreliable. Your kids' own browsing still respects their age cap.",
            12f, 0xFF8B97A5.toInt()))

        // --- Per-kid options ---
        col.addView(tv("\n${kid.name}  ·  ${band.emoji} ${band.name}  (cap ${band.rating})", 13f, 0xFF8B97A5.toInt()))
        col.addView(tv("How should ${kid.name} get movies & shows?", 15f, 0xFFE6EDF3.toInt()))
        val rg = RadioGroup(a)
        val rbPick = RadioButton(a).apply { text = "I'll hand-pick titles"; id = 1; setTextColor(0xFFE6EDF3.toInt()) }
        val rbAuto = RadioButton(a).apply { text = "Show everything within their age cap (no picking)"; id = 2; setTextColor(0xFFE6EDF3.toInt()) }
        rg.addView(rbPick); rg.addView(rbAuto); rg.check(if (kid.filterMode == "auto") 2 else 1); col.addView(rg)
        col.addView(tv("Titles above ${kid.name}'s age cap (${band.rating}) are hidden while filtering is on.", 12f, 0xFF8B97A5.toInt()))
        val cbHide = CheckBox(a).apply { text = "Hide titles with no age rating"; isChecked = kid.hideUnrated; setTextColor(0xFFE6EDF3.toInt()) }
        col.addView(cbHide)

        // Grey out the per-kid picking options when the global filter is off.
        fun sync() {
            val on = cbGlobal.isChecked
            rbPick.isEnabled = on; rbAuto.isEnabled = on; cbHide.isEnabled = on
        }
        cbGlobal.setOnCheckedChangeListener { _, _ -> sync() }; sync()

        AlertDialog.Builder(a)
            .setTitle("Content settings")
            .setView(ScrollView(a).apply { addView(col) })
            .setPositiveButton("Save") { _, _ ->
                Configs.setKidFilterEnabled(a, cbGlobal.isChecked)
                kid.filterMode = if (rg.checkedRadioButtonId == 2) "auto" else "pick"
                kid.hideUnrated = cbHide.isChecked
                Profiles.saveKid(a, kid)
                onSaved?.invoke()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
