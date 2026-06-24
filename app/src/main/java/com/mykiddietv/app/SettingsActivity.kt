package com.mykiddietv.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mykiddietv.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySettingsBinding
    private var accounts = mutableListOf<Configs.Account>()
    private var editingIndex: Int? = null // null = adding a new one

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        accounts = Configs.load(this)
        refreshList()
        loadForm(null)

        b.submitBtn.setOnClickListener { onSubmit() }
        b.clearBtn.setOnClickListener { loadForm(null) }
        b.deleteBtn.setOnClickListener { onDelete() }

        // ---- Kid profile section ----
        b.parentName.setText(Profiles.parentName(this))
        b.kidName.setText(Profiles.kidName(this))
        b.passcode.setText(Profiles.passcode(this))
        b.saveProfileBtn.setOnClickListener { onSaveProfile() }
        b.kidContentBtn.setOnClickListener {
            startActivity(android.content.Intent(this, KidContentActivity::class.java))
        }

        b.ossKey.setText(Configs.ossKey(this))
        b.saveKeyBtn.setOnClickListener {
            val key = b.ossKey.text.toString().trim()
            Configs.setOssKey(this, key)
            Subtitles.apiKey = key
            b.msg.text = if (key.isEmpty()) "Subtitle key cleared." else "Subtitle key saved ✓"
        }
    }

    private fun onSaveProfile() {
        val parent = b.parentName.text.toString().trim().ifBlank { "Parent" }
        val kid = b.kidName.text.toString().trim().ifBlank { "Kids" }
        val code = b.passcode.text.toString().trim()
        if (code.isNotEmpty() && code.length != 4) {
            b.profileMsg.text = "Passcode must be exactly 4 digits (or left blank)."
            return
        }
        Profiles.setNames(this, parent, kid)
        Profiles.setPasscode(this, code)
        b.profileMsg.text = if (code.isEmpty())
            "Saved. ⚠ No passcode set — anyone can open the Parent profile."
        else "Saved ✓  Parent passcode is set."
    }

    private fun refreshList() {
        b.configList.removeAllViews()
        val active = Configs.activeIndex(this)
        if (accounts.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No providers yet — add one below."
            tv.setTextColor(0xFF8b97a5.toInt())
            tv.setPadding(16, 16, 16, 16)
            b.configList.addView(tv)
            return
        }
        accounts.forEachIndexed { i, a ->
            val tv = TextView(this)
            tv.text = (if (i == active) "✓  " else "    ") + a.name + "   (" + a.mac + ")"
            tv.setTextColor(0xFFE6EDF3.toInt())
            tv.textSize = 16f
            tv.setPadding(20, 20, 20, 20)
            tv.isFocusable = true
            tv.isClickable = true
            tv.setBackgroundResource(R.drawable.item_bg)
            tv.setOnClickListener { selectConfig(i) }
            b.configList.addView(tv)
        }
    }

    /** Tap an existing provider → make it active and load it into the form. */
    private fun selectConfig(index: Int) {
        Configs.setActive(this, index)
        Configs.dirty = true
        loadForm(index)
        refreshList()
        b.msg.text = "Switched to “${accounts[index].name}”. Press Back to load it."
    }

    private fun loadForm(index: Int?) {
        editingIndex = index
        if (index == null) {
            b.name.setText(""); b.portal.setText(""); b.mac.setText(""); b.sn.setText("")
            b.deleteBtn.isEnabled = false
            b.msg.text = "Adding a new provider."
        } else {
            val a = accounts[index]
            b.name.setText(a.name); b.portal.setText(a.portal); b.mac.setText(a.mac); b.sn.setText(a.sn)
            b.deleteBtn.isEnabled = true
            b.msg.text = "Editing “${a.name}”."
        }
    }

    private fun onSubmit() {
        val portal = b.portal.text.toString().trim()
        val mac = b.mac.text.toString().trim()
        val sn = b.sn.text.toString().trim()
        val name = b.name.text.toString().trim().ifBlank { "Provider ${accounts.size + 1}" }
        if (portal.isEmpty() || mac.isEmpty()) {
            b.msg.text = "Portal URL and MAC are required."
            return
        }
        val acct = Configs.Account(name, portal, mac, sn)
        val idx = if (editingIndex == null) {
            accounts.add(acct); accounts.size - 1
        } else {
            accounts[editingIndex!!] = acct; editingIndex!!
        }
        Configs.save(this, accounts)
        Configs.setActive(this, idx)
        Configs.dirty = true
        editingIndex = idx
        refreshList()
        loadForm(idx)
        b.msg.text = "Saved & active: “$name”. Press Back to load it."
    }

    private fun onDelete() {
        val idx = editingIndex ?: return
        if (idx !in accounts.indices) return
        val removed = accounts.removeAt(idx)
        Configs.save(this, accounts)
        if (Configs.activeIndex(this) >= accounts.size) Configs.setActive(this, 0)
        Configs.dirty = true
        refreshList()
        loadForm(null)
        b.msg.text = "Deleted “${removed.name}”."
    }
}
