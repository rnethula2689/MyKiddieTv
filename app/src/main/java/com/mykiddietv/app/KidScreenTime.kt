package com.mykiddietv.app

import android.app.Activity
import android.app.TimePickerDialog
import androidx.appcompat.app.AlertDialog

/**
 * Parent-side configuration dialog for kid screen-time: daily limit + bedtime window.
 * [kidId] scopes the settings to one kid; null means "All kids" (writes to every profile).
 */
object KidScreenTime {
    private val limitValues = intArrayOf(0, 30, 60, 90, 120, 180)
    private val limitLabels = arrayOf("Off", "30 minutes", "1 hour", "1 hour 30 min", "2 hours", "3 hours")

    /** The kid ids these settings should be written to (all profiles when [kidId] is null). */
    private fun targets(a: Activity, kidId: String?): List<String> =
        if (kidId != null) listOf(kidId) else Profiles.kids(a).map { it.id }

    /** A representative kid id for READING current values in the dialog. */
    private fun displayId(a: Activity, kidId: String?): String =
        kidId ?: Profiles.kids(a).firstOrNull()?.id ?: "default"

    fun show(a: Activity, kidId: String?) {
        val all = kidId == null
        val who = if (all) "All kids" else (Profiles.kid(a, kidId)?.name ?: "Kid")
        val d = displayId(a, kidId)
        val lim = KidLimits.dailyLimitMin(a, d)
        val limLabel = if (all) "tap to set for everyone" else if (lim <= 0) "Off" else KidLimits.fmtMin(lim)
        val bedLabel = when {
            all -> "tap to set for everyone"
            KidLimits.bedtimeOn(a, d) -> "${KidLimits.fmtClock(KidLimits.bedStartMin(a, d))} – ${KidLimits.fmtClock(KidLimits.bedEndMin(a, d))}"
            else -> "Off"
        }
        val items = ArrayList<String>()
        items.add("⏱   Daily limit — $limLabel")
        items.add("🌙   Bedtime — $bedLabel")
        if (!all) items.add("ℹ   Watched today: ${KidLimits.fmtMin(KidLimits.usedMin(a, d))}")
        AlertDialog.Builder(a)
            .setTitle("Screen time · $who")
            .setItems(items.toTypedArray()) { _, w ->
                when (w) {
                    0 -> chooseLimit(a, kidId)
                    1 -> chooseBedtime(a, kidId)
                    2 -> show(a, kidId)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun chooseLimit(a: Activity, kidId: String?) {
        val cur = limitValues.indexOf(KidLimits.dailyLimitMin(a, displayId(a, kidId))).coerceAtLeast(0)
        AlertDialog.Builder(a)
            .setTitle("Daily watch-time limit")
            .setSingleChoiceItems(limitLabels, cur) { dlg, w ->
                targets(a, kidId).forEach { KidLimits.setDailyLimitMin(a, it, limitValues[w]) }
                dlg.dismiss(); show(a, kidId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseBedtime(a: Activity, kidId: String?) {
        AlertDialog.Builder(a)
            .setTitle("Bedtime")
            .setItems(arrayOf("Turn off", "Set bedtime…")) { _, w ->
                if (w == 0) { targets(a, kidId).forEach { KidLimits.setBedtime(a, it, -1, -1) }; show(a, kidId) }
                else pickStart(a, kidId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickStart(a: Activity, kidId: String?) {
        val s = KidLimits.bedStartMin(a, displayId(a, kidId)).let { if (it < 0) 20 * 60 else it } // default 8:00 PM
        TimePickerDialog(a, { _, h, m -> pickEnd(a, kidId, h * 60 + m) }, s / 60, s % 60, false)
            .apply { setTitle("Bedtime starts at") }.show()
    }

    private fun pickEnd(a: Activity, kidId: String?, start: Int) {
        val e = KidLimits.bedEndMin(a, displayId(a, kidId)).let { if (it < 0) 7 * 60 else it } // default 7:00 AM
        TimePickerDialog(a, { _, h, m ->
            targets(a, kidId).forEach { KidLimits.setBedtime(a, it, start, h * 60 + m) }
            show(a, kidId)
        }, e / 60, e % 60, false).apply { setTitle("Bedtime ends at") }.show()
    }
}
