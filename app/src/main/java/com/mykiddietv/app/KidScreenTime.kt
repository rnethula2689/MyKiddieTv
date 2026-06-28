package com.mykiddietv.app

import android.app.Activity
import android.app.TimePickerDialog
import androidx.appcompat.app.AlertDialog

/** Parent-side configuration dialog for kid screen-time: daily limit + bedtime window. */
object KidScreenTime {
    private val limitValues = intArrayOf(0, 30, 60, 90, 120, 180)
    private val limitLabels = arrayOf("Off", "30 minutes", "1 hour", "1 hour 30 min", "2 hours", "3 hours")

    fun show(a: Activity) {
        val lim = KidLimits.dailyLimitMin(a)
        val limLabel = if (lim <= 0) "Off" else KidLimits.fmtMin(lim)
        val bedLabel = if (KidLimits.bedtimeOn(a))
            "${KidLimits.fmtClock(KidLimits.bedStartMin(a))} – ${KidLimits.fmtClock(KidLimits.bedEndMin(a))}"
        else "Off"
        val used = KidLimits.usedMin(a)
        val items = arrayOf(
            "⏱   Daily limit — $limLabel",
            "🌙   Bedtime — $bedLabel",
            "ℹ   Watched today: ${KidLimits.fmtMin(used)}"
        )
        AlertDialog.Builder(a)
            .setTitle("Kid screen time")
            .setItems(items) { _, w ->
                when (w) {
                    0 -> chooseLimit(a)
                    1 -> chooseBedtime(a)
                    2 -> show(a)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun chooseLimit(a: Activity) {
        val cur = limitValues.indexOf(KidLimits.dailyLimitMin(a)).coerceAtLeast(0)
        AlertDialog.Builder(a)
            .setTitle("Daily watch-time limit")
            .setSingleChoiceItems(limitLabels, cur) { d, w ->
                KidLimits.setDailyLimitMin(a, limitValues[w])
                d.dismiss(); show(a)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseBedtime(a: Activity) {
        AlertDialog.Builder(a)
            .setTitle("Bedtime")
            .setItems(arrayOf("Turn off", "Set bedtime…")) { _, w ->
                if (w == 0) { KidLimits.setBedtime(a, -1, -1); show(a) }
                else pickStart(a)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickStart(a: Activity) {
        val s = KidLimits.bedStartMin(a).let { if (it < 0) 20 * 60 else it } // default 8:00 PM
        TimePickerDialog(a, { _, h, m ->
            val start = h * 60 + m
            pickEnd(a, start)
        }, s / 60, s % 60, false).apply { setTitle("Bedtime starts at") }.show()
    }

    private fun pickEnd(a: Activity, start: Int) {
        val e = KidLimits.bedEndMin(a).let { if (it < 0) 7 * 60 else it } // default 7:00 AM
        TimePickerDialog(a, { _, h, m ->
            val end = h * 60 + m
            KidLimits.setBedtime(a, start, end)
            show(a)
        }, e / 60, e % 60, false).apply { setTitle("Bedtime ends at") }.show()
    }
}
