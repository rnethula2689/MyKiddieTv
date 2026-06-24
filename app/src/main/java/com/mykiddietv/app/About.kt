package com.mykiddietv.app

import android.content.Context
import androidx.appcompat.app.AlertDialog

object About {
    fun show(ctx: Context) {
        val msg = "MyKiddieTv\n\n" +
            "Version:   ${BuildConfig.VERSION_NAME}  (build ${BuildConfig.VERSION_CODE})\n" +
            "Built:   ${BuildConfig.BUILD_TIME}\n\n" +
            "Developer:   RN\n" +
            "Updates:   is.gd/mykiddietv"
        AlertDialog.Builder(ctx)
            .setTitle("About")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .setNeutralButton("App updates / page") { _, _ ->
                ctx.startActivity(android.content.Intent(ctx, AppUpdatesActivity::class.java))
            }
            .show()
    }
}
