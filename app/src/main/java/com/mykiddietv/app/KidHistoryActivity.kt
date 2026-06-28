package com.mykiddietv.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mykiddietv.app.databinding.ActivityKidhistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Parent view of what the child watched (read-only log + clear). */
class KidHistoryActivity : AppCompatActivity() {
    private lateinit var b: ActivityKidhistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKidhistoryBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.clearBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear watch history?")
                .setPositiveButton("Clear") { _, _ -> KidHistory.clear(this); refresh() }
                .setNegativeButton("Cancel", null)
                .show()
        }
        refresh()
    }

    private fun refresh() {
        val all = KidHistory.all(this)
        b.empty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        b.clearBtn.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
        val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.US)
        b.list.text = all.joinToString("\n") { "• ${it.title}\n    ${fmt.format(Date(it.ts))}" }
    }
}
