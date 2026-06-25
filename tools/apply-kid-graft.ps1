param([string]$root = "C:\Claude temp delete\MyKiddieTv")
$enc = New-Object System.Text.UTF8Encoding($false)
$J = "$root\app\src\main\java\com\mykiddietv\app"
$L = "$root\app\src\main\res\layout"
$fail = 0
function Repl($file, $old, $new, $tag) {
  $t = [IO.File]::ReadAllText($file).Replace("`r`n","`n")
  $old = $old.Replace("`r`n","`n"); $new = $new.Replace("`r`n","`n")
  if ($t.Contains($new)) { "  [skip] $tag (already grafted)"; return }
  if (-not $t.Contains($old)) { "  [MISS] $tag — anchor not found"; $script:fail++; return }
  [IO.File]::WriteAllText($file, $t.Replace($old, $new), $enc)
  "  [ok]   $tag"
}

"== ChannelsActivity =="
Repl "$J\ChannelsActivity.kt" @'
        LiveGridActivity.gridTitle = title
        startActivity(Intent(this, LiveGridActivity::class.java))
'@ @'
        LiveGridActivity.gridTitle = title
        LiveGridActivity.kidMode = false
        startActivity(Intent(this, LiveGridActivity::class.java))
'@ "openLiveGrid kidMode reset"

Repl "$J\ChannelsActivity.kt" @'
                    LiveVlcActivity.liveChannels = allChannels
                    val idx = allChannels.indexOfFirst { it.id == ch.id }
'@ @'
                    LiveVlcActivity.liveChannels = allChannels
                    LiveVlcActivity.kidMode = false
                    val idx = allChannels.indexOfFirst { it.id == ch.id }
'@ "playChannel kidMode reset"

"== LiveGridActivity =="
Repl "$J\LiveGridActivity.kt" @'
        var gridTitle: String = "Live TV"
    }
'@ @'
        var gridTitle: String = "Live TV"
        // When launched from the kid home, hide parent-only menu items (Settings, App updates).
        var kidMode: Boolean = false
    }
'@ "companion kidMode"

Repl "$J\LiveGridActivity.kt" @'
        LiveVlcActivity.liveChannels = all
        val idx = all.indexOfFirst { it.id == ch.id }
'@ @'
        LiveVlcActivity.liveChannels = all
        LiveVlcActivity.kidMode = kidMode
        val idx = all.indexOfFirst { it.id == ch.id }
'@ "openFullscreen kidMode"

Repl "$J\LiveGridActivity.kt" @'
        val items = arrayOf("🔄   Refresh", "⚙   Settings", "📥   App updates", "ℹ️   About", "✖   Exit")
        val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> refreshGrid()
                    1 -> startActivity(Intent(this, SettingsActivity::class.java))
                    2 -> startActivity(Intent(this, AppUpdatesActivity::class.java))
                    3 -> About.show(this)
                    4 -> finishAffinity()
                }
            }
'@ @'
        val items = if (kidMode)
            arrayOf("🔄   Refresh", "ℹ️   About")
        else
            arrayOf("🔄   Refresh", "⚙   Settings", "📥   App updates", "ℹ️   About", "✖   Exit")
        val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                val action = items[which]
                when {
                    action.contains("Refresh") -> refreshGrid()
                    action.contains("Settings") -> startActivity(Intent(this, SettingsActivity::class.java))
                    action.contains("App updates") -> startActivity(Intent(this, AppUpdatesActivity::class.java))
                    action.contains("About") -> About.show(this)
                    action.contains("Exit") -> finishAffinity()
                }
            }
'@ "LiveGrid menu gate"

# Kid guardrails in the Live grid: no long-press-Back exit, immersive, no catch-up.
Repl "$J\LiveGridActivity.kt" @'
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) { confirmExit(); return true }
'@ @'
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            if (kidMode) finish() else confirmExit()
            return true
        }
'@ "LiveGrid kid long-press Back no exit"

Repl "$J\LiveGridActivity.kt" @'
    private fun openCatchup(ch: Portal.Channel) {
        startActivity(
'@ @'
    private fun openCatchup(ch: Portal.Channel) {
        if (kidMode) return  // catch-up is not exposed on the kid side
        startActivity(
'@ "LiveGrid kid disable catch-up"

Repl "$J\LiveGridActivity.kt" @'
    private fun activate(ch: Portal.Channel) {
'@ @'
    override fun onResume() {
        super.onResume()
        if (kidMode) KidGuard.immersive(this)
    }

    private fun activate(ch: Portal.Channel) {
'@ "LiveGrid kid immersive onResume"

"== LiveVlcActivity =="
Repl "$J\LiveVlcActivity.kt" @'
        var liveChannels: List<Portal.Channel> = emptyList()
    }
'@ @'
        var liveChannels: List<Portal.Channel> = emptyList()
        // Set by the launching screen; hides parent-only menu items for kids.
        var kidMode: Boolean = false
    }
'@ "LiveVlc companion kidMode"

Repl "$J\LiveVlcActivity.kt" @'
        val items = arrayOf("⚙   Settings", "📥   App updates", "ℹ️   About", "✖   Exit")
        val dlg = AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, SettingsActivity::class.java))
                    1 -> startActivity(Intent(this, AppUpdatesActivity::class.java))
                    2 -> About.show(this)
                    3 -> finishAffinity()
                }
            }
'@ @'
        val items = if (kidMode)
            arrayOf("ℹ️   About", "✖   Exit")
        else
            arrayOf("⚙   Settings", "📥   App updates", "ℹ️   About", "✖   Exit")
        val dlg = AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                val action = items[which]
                when {
                    action.contains("Settings") -> startActivity(Intent(this, SettingsActivity::class.java))
                    action.contains("App updates") -> startActivity(Intent(this, AppUpdatesActivity::class.java))
                    action.contains("About") -> About.show(this)
                    action.contains("Exit") -> finishAffinity()
                }
            }
'@ "LiveVlc menu gate"

"== SettingsActivity =="
Repl "$J\SettingsActivity.kt" @'
        b.deleteBtn.setOnClickListener { onDelete() }
'@ @'
        b.deleteBtn.setOnClickListener { onDelete() }

        // ---- Kid profile section ----
        b.parentName.setText(Profiles.parentName(this))
        b.kidName.setText(Profiles.kidName(this))
        b.passcode.setText(Profiles.passcode(this))
        b.saveProfileBtn.setOnClickListener { onSaveProfile() }
'@ "Settings onCreate hooks"

Repl "$J\SettingsActivity.kt" @'
    private fun refreshList() {
'@ @'
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
'@ "Settings onSaveProfile"

"== activity_settings.xml =="
Repl "$L\activity_settings.xml" @'
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="IPTV Configuration"
            android:textColor="#19c37d"
            android:textSize="24sp"
            android:textStyle="bold" />
'@ @'
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Kid Profile"
            android:textColor="#19c37d"
            android:textSize="24sp"
            android:textStyle="bold" />

        <EditText
            android:id="@+id/parentName"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="10dp"
            android:hint="Parent profile name"
            android:textColorHint="#5a6675"
            android:textColor="#e6edf3"
            android:singleLine="true" />

        <EditText
            android:id="@+id/kidName"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="10dp"
            android:hint="Kid profile name"
            android:textColorHint="#5a6675"
            android:textColor="#e6edf3"
            android:singleLine="true" />

        <EditText
            android:id="@+id/passcode"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="10dp"
            android:hint="4-digit parent passcode"
            android:textColorHint="#5a6675"
            android:textColor="#e6edf3"
            android:inputType="numberPassword"
            android:maxLength="4"
            android:singleLine="true" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginTop="14dp">

            <Button
                android:id="@+id/saveProfileBtn"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Save profile" />
        </LinearLayout>

        <TextView
            android:id="@+id/profileMsg"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="10dp"
            android:textColor="#e6edf3"
            android:textSize="15sp"
            android:text="" />

        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:background="#1c2530"
            android:layout_marginVertical="20dp" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="IPTV Configuration"
            android:textColor="#19c37d"
            android:textSize="24sp"
            android:textStyle="bold" />
'@ "Settings layout Kid section"

"== PlayerActivity (VOD, reachable by kids) =="
Repl "$J\PlayerActivity.kt" @'
        var liveChannels: List<Portal.Channel> = emptyList()
    }
'@ @'
        var liveChannels: List<Portal.Channel> = emptyList()
        // Set by the launching screen; hides parent-only menu items for kids.
        var kidMode: Boolean = false
    }
'@ "Player companion kidMode"

Repl "$J\PlayerActivity.kt" @'
        val items = arrayOf("💬   Subtitles", "⚙   Settings", "📥   App updates", "ℹ️   About", "✖   Exit")
        val dlg = AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> searchSubtitles()
                    1 -> startActivity(android.content.Intent(this, SettingsActivity::class.java))
                    2 -> startActivity(android.content.Intent(this, AppUpdatesActivity::class.java))
                    3 -> About.show(this)
                    4 -> finishAffinity()
                }
            }
'@ @'
        val items = if (kidMode)
            arrayOf("💬   Subtitles", "ℹ️   About", "✖   Exit")
        else
            arrayOf("💬   Subtitles", "⚙   Settings", "📥   App updates", "ℹ️   About", "✖   Exit")
        val dlg = AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                val action = items[which]
                when {
                    action.contains("Subtitles") -> searchSubtitles()
                    action.contains("Settings") -> startActivity(android.content.Intent(this, SettingsActivity::class.java))
                    action.contains("App updates") -> startActivity(android.content.Intent(this, AppUpdatesActivity::class.java))
                    action.contains("About") -> About.show(this)
                    action.contains("Exit") -> finishAffinity()
                }
            }
'@ "Player menu gate"

Repl "$J\ChannelsActivity.kt" @'
                    b.status.visibility = View.GONE
                    startActivity(
                        Intent(this, PlayerActivity::class.java)
'@ @'
                    b.status.visibility = View.GONE
                    PlayerActivity.kidMode = false
                    startActivity(
                        Intent(this, PlayerActivity::class.java)
'@ "play() PlayerActivity kidMode reset"

Repl "$J\CatchupActivity.kt" @'
                    b.status.visibility = View.GONE
                    LiveVlcActivity.liveChannels = emptyList()
'@ @'
                    b.status.visibility = View.GONE
                    LiveVlcActivity.kidMode = false
                    LiveVlcActivity.liveChannels = emptyList()
'@ "CatchupActivity LiveVlc kidMode reset"

"== Guardrails: remove Exit from kid menus =="
Repl "$J\LiveVlcActivity.kt" @'
            arrayOf("ℹ️   About", "✖   Exit")
'@ @'
            arrayOf("ℹ️   About")
'@ "LiveVlc kid menu drop Exit"

Repl "$J\PlayerActivity.kt" @'
            arrayOf("💬   Subtitles", "ℹ️   About", "✖   Exit")
'@ @'
            arrayOf("💬   Subtitles", "ℹ️   About")
'@ "Player kid menu drop Exit"

"== Guardrails: ScreenLock in fullscreen players =="
Repl "$J\LiveVlcActivity.kt" @'
    private var hideBarRunnable: Runnable? = null
'@ @'
    private var hideBarRunnable: Runnable? = null
    private var screenLock: ScreenLock? = null
'@ "LiveVlc screenLock field"

Repl "$J\LiveVlcActivity.kt" @'
        play(url)
        showBar()
    }
'@ @'
        play(url)
        showBar()
        KidGuard.immersive(this)
        screenLock = ScreenLock(this)
    }
'@ "LiveVlc onCreate lock+immersive"

Repl "$J\LiveVlcActivity.kt" @'
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
'@ @'
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (screenLock?.locked == true) return true
        if (event.action == KeyEvent.ACTION_DOWN) {
'@ "LiveVlc dispatchKeyEvent lock guard"

Repl "$J\LiveVlcActivity.kt" @'
    override fun onStop() {
        super.onStop()
        mp?.stop()
    }
'@ @'
    override fun onBackPressed() {
        if (screenLock?.locked == true) return
        super.onBackPressed()
    }

    override fun onStop() {
        super.onStop()
        mp?.stop()
    }
'@ "LiveVlc onBackPressed lock guard"

Repl "$J\PlayerActivity.kt" @'
    private var forceSoftware = false
'@ @'
    private var forceSoftware = false
    private var screenLock: ScreenLock? = null
'@ "Player screenLock field"

Repl "$J\PlayerActivity.kt" @'
        b.playerView.controllerShowTimeoutMs = 6000
        b.playerView.requestFocus()
    }
'@ @'
        b.playerView.controllerShowTimeoutMs = 6000
        b.playerView.requestFocus()
        KidGuard.immersive(this)
        screenLock = ScreenLock(this)
    }
'@ "Player onCreate lock+immersive"

Repl "$J\PlayerActivity.kt" @'
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val kc = event.keyCode
'@ @'
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (screenLock?.locked == true) return true
        val kc = event.keyCode
'@ "Player dispatchKeyEvent lock guard"

Repl "$J\PlayerActivity.kt" @'
    override fun onStop() {
        super.onStop()
        saveResume()
        player?.pause()
    }
'@ @'
    override fun onBackPressed() {
        if (screenLock?.locked == true) return
        super.onBackPressed()
    }

    override fun onStop() {
        super.onStop()
        saveResume()
        player?.pause()
    }
'@ "Player onBackPressed lock guard"

"== Updater: read version file from the release asset (CI writes it per build) =="
Repl "$J\Updater.kt" @'
        "https://raw.githubusercontent.com/rnethula2689/MyKiddieTv/main/latest_version.json"
'@ @'
        "https://github.com/rnethula2689/MyKiddieTv/releases/download/apk-latest/latest_version.json"
'@ "Updater version URL -> release asset"

"== ChannelsActivity: quick Manage Kid Content row on the parent home =="
Repl "$J\ChannelsActivity.kt" @'
        rows.add(Row("🎬   Movies (VOD)", null) { showVodCategories() })
        rows.add(Row("⬇   Downloads", null) { startActivity(Intent(this, DownloadsActivity::class.java)) })
'@ @'
        rows.add(Row("🎬   Movies (VOD)", null) { showVodCategories() })
        rows.add(Row("👶   Manage Kid Content", null) { startActivity(Intent(this, KidContentActivity::class.java)) })
        rows.add(Row("⬇   Downloads", null) { startActivity(Intent(this, DownloadsActivity::class.java)) })
'@ "ChannelsActivity Manage Kid Content home row"

"== DownloadsActivity: parent's own downloads only (kid downloads filtered out) =="
Repl "$J\DownloadsActivity.kt" @'
        val items = Downloads.list(this)
'@ @'
        // Parent's own downloads only — kid downloads live in Approved Content → Downloads.
        val items = Downloads.list(this).filterNot { Profiles.isKidDownload(this, it.id) }
'@ "DownloadsActivity exclude kid downloads"

if ($fail -gt 0) { "`nGRAFT INCOMPLETE: $fail anchor(s) missing — upstream changed; fix manually." }
else { "`nGRAFT OK" }
