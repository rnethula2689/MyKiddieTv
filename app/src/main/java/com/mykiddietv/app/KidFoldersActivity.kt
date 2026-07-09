package com.mykiddietv.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

/**
 * Folder access for a FULL-ACCESS kid (manageContent = false): the parent ticks which Live TV genres and
 * Movie/VOD categories the child may browse, so adult folders can be hidden while keeping broad access
 * inside the allowed ones. Saves to the active kid's allLiveFolders/liveFolders + allVodFolders/vodFolders.
 */
class KidFoldersActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var kid: Profiles.Kid
    private val liveBoxes = ArrayList<CheckBox>()
    private val vodBoxes = ArrayList<CheckBox>()
    private lateinit var liveList: LinearLayout
    private lateinit var vodList: LinearLayout
    private lateinit var status: TextView
    private lateinit var liveAllBtn: Button
    private lateinit var vodAllBtn: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kid = Profiles.activeKid(this) ?: run { finish(); return }

        val root = ScrollView(this).apply { setBackgroundColor(0xFF0B0F14.toInt()) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        root.addView(col); setContentView(root)

        col.addView(TextView(this).apply {
            text = "Folders for ${kid.name}"; textSize = 26f; setTextColor(0xFF19C37D.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, dp(6))
        })
        col.addView(TextView(this).apply {
            text = "Tick the Live TV and Movie folders ${kid.name} may browse. Unticked folders (e.g. adult categories) are hidden from them."
            textSize = 13f; setTextColor(0xFF8B97A5.toInt()); setPadding(0, 0, 0, dp(8))
        })

        status = TextView(this).apply { setTextColor(0xFF8B97A5.toInt()); textSize = 14f; text = "Loading folders…"; setPadding(0, dp(8), 0, dp(8)) }
        col.addView(status)

        liveAllBtn = Button(this).apply { text = "Select all"; textSize = 12f; setOnClickListener { toggleAll(liveBoxes) } }
        col.addView(sectionHeader("📺  Live TV", liveAllBtn))
        liveList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(liveList)

        vodAllBtn = Button(this).apply { text = "Select all"; textSize = 12f; setOnClickListener { toggleAll(vodBoxes) } }
        col.addView(sectionHeader("🎬  Movies & Shows", vodAllBtn))
        vodList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(vodList)

        col.addView(Button(this).apply {
            text = "Save"; setOnClickListener { save() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) }
        })

        loadCategories()
    }

    private fun sectionHeader(title: String, allBtn: Button): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(16), 0, dp(4)) }
        row.addView(TextView(this).apply {
            text = title; textSize = 16f; setTextColor(0xFFE6EDF3.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(allBtn)
        return row
    }

    private fun loadCategories() {
        var genres = ChannelsActivity.catGenres()
        var vodCats = ChannelsActivity.catVodCats()
        if (genres.isNotEmpty() && vodCats.isNotEmpty()) { populate(genres, vodCats); return }
        val acct = Configs.active(this) ?: run { status.text = "Add an IPTV provider first (Settings)."; return }
        Portal.portalUrl = acct.portal; Portal.mac = acct.mac; Portal.sn = acct.sn
        io.execute {
            val err = Portal.connect()
            if (err == null) {
                if (genres.isEmpty()) genres = Portal.liveGenres()
                if (vodCats.isEmpty()) vodCats = Portal.vodCategories()
                ChannelsActivity.cacheCatalog(genres, vodCats)
            }
            ui.post { if (err != null) status.text = err else populate(genres, vodCats) }
        }
    }

    private fun populate(genres: List<Portal.Genre>, vodCats: List<Portal.VodCat>) {
        status.visibility = View.GONE
        for (g in genres) addBox(liveList, liveBoxes, g.title + (if (g.censored) "  🔒" else ""), g.id, kid.allLiveFolders || kid.liveFolders.contains(g.id))
        for (c in vodCats) addBox(vodList, vodBoxes, c.title, c.id, kid.allVodFolders || kid.vodFolders.contains(c.id))
        updateAllLabels()
    }

    private fun addBox(parent: LinearLayout, into: ArrayList<CheckBox>, label: String, id: String, checked: Boolean) {
        val cb = CheckBox(this)
        cb.text = label; cb.tag = id; cb.isChecked = checked
        cb.setTextColor(0xFFE6EDF3.toInt()); cb.textSize = 15f; cb.isFocusable = true
        cb.setPadding(cb.paddingLeft, dp(6), dp(6), dp(6))
        cb.setOnCheckedChangeListener { _, _ -> updateAllLabels() }
        parent.addView(cb); into.add(cb)
    }

    private fun toggleAll(boxes: List<CheckBox>) {
        val target = !(boxes.isNotEmpty() && boxes.all { it.isChecked })
        boxes.forEach { it.isChecked = target }
        updateAllLabels()
    }

    private fun updateAllLabels() {
        liveAllBtn.text = if (liveBoxes.isNotEmpty() && liveBoxes.all { it.isChecked }) "Deselect all" else "Select all"
        vodAllBtn.text = if (vodBoxes.isNotEmpty() && vodBoxes.all { it.isChecked }) "Deselect all" else "Select all"
    }

    private fun save() {
        val liveChecked = liveBoxes.filter { it.isChecked }.map { it.tag as String }
        val vodChecked = vodBoxes.filter { it.isChecked }.map { it.tag as String }
        if (liveChecked.isEmpty() && vodChecked.isEmpty()) { toast("Pick at least one folder."); return }
        kid.allLiveFolders = liveBoxes.isNotEmpty() && liveChecked.size == liveBoxes.size
        kid.liveFolders.clear(); if (!kid.allLiveFolders) kid.liveFolders.addAll(liveChecked)
        kid.allVodFolders = vodBoxes.isNotEmpty() && vodChecked.size == vodBoxes.size
        kid.vodFolders.clear(); if (!kid.allVodFolders) kid.vodFolders.addAll(vodChecked)
        Profiles.saveKid(this, kid)
        toast("Saved ✓")
        finish()
    }
}
