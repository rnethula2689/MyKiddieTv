package com.mykiddietv.app

/** The complete MyKiddieTv user guide — one plain-text source shown in HelpActivity and exported to PDF. */
object UserGuide {
    const val TITLE = "MyKiddieTv — User Guide"

    val TEXT = """
MyKiddieTv — USER GUIDE
=======================
A safe, kid-friendly IPTV app where parents pick exactly what their children
can watch. Open this any time from Settings > User guide. Tap "Save as PDF"
to keep an offline copy.


1. HOW IT WORKS (FOR PARENTS)
-----------------------------
MyKiddieTv has two sides:
  - A simple Kids home where children only see what you allow.
  - A parent area (Settings) protected by a passcode, where you configure
    everything below.
Set your parent passcode first (Settings > Parental PIN), then build the
child's allowed list (Settings > Profiles / whitelist).


2. GETTING STARTED — ADD YOUR PROVIDER
--------------------------------------
  1. Open Settings (gear icon, top-right — or the MENU button on a TV remote).
  2. Choose "IPTV configuration".
  3. Tap "Add provider" and enter your Portal URL, MAC address, and Serial
     Number (from your IPTV provider).
  4. Tap Submit. Content loads automatically.


3. SET THE PARENT PASSCODE
--------------------------
  1. Settings > Parental PIN.
  2. Enter a PIN (at least 3-4 digits) and save.
This PIN protects Settings and the parent-only areas, so children can't
change what they're allowed to watch or exit kids mode.


4. BUILD THE CHILD'S ALLOWED LIST (WHITELIST / PROFILES)
--------------------------------------------------------
  1. Settings > Profiles.
  2. Create a profile for your child and choose exactly which channels and
     which movie/series categories it may show.
  3. The Kids home then displays only those picks.
You can keep several profiles (e.g. one per child) and switch between them.


5. SCREEN TIME & BEDTIME
------------------------
  1. Settings > Kids.
  2. Set a daily time limit and/or a bedtime window.
  3. When the limit is reached or bedtime arrives, playback stops and a lock
     screen appears ("Time's up" / "Bedtime"); your passcode unlocks it.
Usage resets each day automatically.


6. WATCH HISTORY (FOR PARENTS)
------------------------------
Settings > Kids > Watch history shows what your child has watched, with times.
Use "Clear" to reset it.


7. THE KIDS HOME
----------------
Children see big, simple tiles of the allowed channels and titles. Selecting
one plays it full screen. There are no menus that let them wander outside the
allowed list.


8. WATCHING LIVE TV
-------------------
  1. Open a live channel from the Kids home (or Live TV).
  2. Full screen:
       - UP / DOWN change channel.
       - Tap (or OK) shows/hides the controls.
       - A slim "Now Playing" bar shows the current programme, a progress
         bar, and what's next; it hides after a few seconds.


9. TV GUIDE (EPG)
-----------------
  1. Live TV > "TV Guide - what's on now" (parent area).
  2. See Now / Next per channel; open a channel's full-day schedule.
  3. Set reminders for upcoming programmes, or watch past ones if your
     provider offers catch-up.
Optional external guide: Settings > TV Guide (EPG).


10. MOVIES & SERIES
-------------------
Allowed movies/series appear as posters. Select a movie for Play, Watch
trailer, Info, Watch later, or Download. For a series, choose Season > Episode.


11. FAVOURITES, WATCH LATER, CONTINUE WATCHING
----------------------------------------------
  - Favourites: starred channels/titles.
  - Watch Later: a saved list (search, sort, export from its tab).
  - Continue Watching: resume anything started; long-press to remove.


12. DOWNLOADS (WATCH OFFLINE)
-----------------------------
From a title's options choose Download; play finished items from Downloads
even without internet.


13. RECORDINGS
--------------
Live recordings (where supported) are listed under Recordings — select to
play or remove.


14. CASTING & SUBTITLES
-----------------------
  - Cast to TV (player menu) sends the stream to a DLNA/Chromecast device on
    your network. (Not available on Fire OS devices.)
  - Subtitles (movie player menu) searches/loads subtitles; add an
    OpenSubtitles API key in Settings > Subtitles.


15. PLAYER CONTROLS
-------------------
Aspect ratio, volume and brightness are on the player's top bar / side panels.
Sleep timer is in the player menu and in Settings.


16. SETTINGS — EVERY SECTION (PARENT AREA)
------------------------------------------
  - IPTV configuration: add/edit your provider(s).
  - Profiles: the child's allowed channels/categories.
  - Personalization: hide the "Recently Added" and/or "For You" rows.
  - Remote control (key mapping): re-assign remote buttons - see 17.
  - Kids: screen-time limit, bedtime, and watch history.
  - Parental PIN: the passcode that protects this area.
  - Playback settings: buffering and hardware decoding (raise buffer for
    stutter; turn hardware decoding off if a video won't play).
  - Sleep timer: auto-stop after a set time; can close the app when it fires.
  - Storage: clear Cache, Favourites, Watch Later, Continue Watching,
    Downloads, or Recordings - selectively or all.
  - Subtitles: OpenSubtitles API key.
  - TV Guide (EPG): optional external XMLTV URL (.xml / .xml.gz).
  - App updates: check for and install the latest version.
  - Sync & Backup: back up / restore / delete your saved lists.
  - Troubleshooting: diagnostics (see 18).
  - User guide / About / Exit.


17. REMOTE CONTROL — MAP YOUR KEYS
----------------------------------
Make any remote work the way you like.
  1. Settings > Remote control (key mapping).
  2. Select an action (Channel up/down, Play/Pause, Rewind, Fast-forward,
     Change aspect ratio, Open menu, Show info).
  3. Press the remote button you want for it.
  4. "Reset all to default" restores standard behaviour.
Back and Home can never be re-mapped, so you can always exit. Mappings apply
in the live player.


18. TROUBLESHOOTING
-------------------
  - Nothing loads: check internet; Settings > Troubleshooting > "Test portal
    connection"; re-check Portal URL / MAC / Serial.
  - "Couldn't open ... unexpected response": provider hiccup - try again.
  - Buffering/failure: it auto-retries; try Retry in the player menu, or raise
    the buffer in Playback settings.
  - No guide: set an external XMLTV source in Settings > TV Guide (EPG).
  - App closed unexpectedly: Settings > Troubleshooting shows the last crash
    details; "Clear" dismisses it.
  - Remote buttons wrong: remap them (section 17).


19. TIPS
--------
  - Set the passcode and screen-time limits before handing the device over.
  - Keep the allowed list tight - children only ever see what you add.
  - Back up your lists before reinstalling, and keep the app updated.

— End of guide —
""".trimIndent()
}
