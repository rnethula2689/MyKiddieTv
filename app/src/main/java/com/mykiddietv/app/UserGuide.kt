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
You can set up MULTIPLE children, each with their own name and picture. For
each child you choose ONE thing: whether YOU manage their content (they see
only what you approve) or they get full access (see section 4).
Set your parent passcode first (Settings > Parental PIN), then add each child
and choose their content.


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


4. KID PROFILES & CONTENT
-------------------------
You can create one profile per child, each with its own name and picture, and
one simple content choice.

Add or edit a child:
  1. On "Who's watching?", tap "+ Add kid" (or long-press an existing kid
     tile > Edit). Enter the parent passcode if asked.
  2. Enter the child's NAME.
  3. Set "Manage this kid's content":
       - ON (recommended): the child ONLY sees the shows & channels you
         approve. Nothing else appears in their kid home.
       - OFF: the child can browse the full service like an adult. You can
         still hide adult folders — see "Choose allowed folders" below.
         Turning management off shows a warning you must confirm.
  4. Pick a PICTURE (a fun icon, a photo, or the default teddy bear).
  5. Save - the child appears on "Who's watching?".

Managed child (management ON) — build their approved list:
  Manage Kid Content (parent Home 👶, or Settings > Kids > Manage kid content)
  > Live TV / Movies & Shows > tick titles > "Add selected". Review or remove
  anything later under "Approved Content". Each child keeps their OWN list.

Full-access child (management OFF) — choose their FOLDERS:
  Open the same Manage Kid Content and pick that child (or long-press their
  tile > "Choose allowed folders"). Tick which Live TV genres and Movie
  categories they may browse; untick adult folders to hide them.
  In the kid's home, Live TV then opens as tidy FOLDERS — "All Channels"
  plus one folder per allowed genre — instead of one long channel list.
(To change a child's PICTURE, long-press their tile > Edit — see section 7.)


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


7. THE "WHO'S WATCHING?" SCREEN
-------------------------------
When the app opens you see a tile for each child, a "+ Add kid" tile, and the
Parent tile. Tap a child for their kid home; tap Parent (passcode) for
Settings and full browsing.

Manage a child: PRESS AND HOLD (long-press) their tile (parent passcode if
set) for a menu:
  - "Manage content" — choose/approve what they can watch.
  - "Edit" — change name, content setting and picture (photo, gallery, fun
    icon, or the default teddy bear).
  - "Delete" — remove the profile.
On a remote, hold the OK/centre button on the tile.

The PARENT tile can have a picture too: press and hold it (passcode), then
take a photo, choose from the gallery, pick a fun icon, or return to the
default silhouette. A single tap still asks for the passcode and opens
parent mode as usual.

In the kid home, children see big, simple tiles. A managed child sees ONLY
what you approved, with no way to wander outside it; a child with content
management OFF can browse the full catalogue.


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
Selecting a title opens its DETAILS screen — backdrop, poster, rating, genre,
runtime, full overview, a Trailers rail and a Cast rail, with Play plus icon
buttons for Favourite (★), Watch later (🕒) and Download (⬇). For a SERIES the
screen has a Season selector and an episode list. It adapts to portrait or
landscape automatically.


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


14. SUBTITLES
-------------
  - Subtitles (movie player menu) opens a search: the movie's title is
    pre-filled (edit it if needed), pick a LANGUAGE from the dropdown
    (remembered for next time) and press Search. Each result shows its
    DOWNLOAD COUNT — higher usually means better quality/sync. Tap one to
    apply it; the chosen subtitle is remembered for that movie.
  - In the VLC player, the 💬 button on the top bar picks between the movie's
    built-in subtitle tracks, Off, or an online search. Movies with built-in
    English subtitles show them automatically — same as the Default player.
    If a subtitle appears slightly early or late, use 💬 > Adjust timing to
    shift it in half-second steps.
  - Add a free OpenSubtitles API key in Settings > Subtitles.


15. PLAYER CONTROLS
-------------------
Aspect ratio, volume and brightness are on the player's top bar / side panels.
Sleep timer is in the player menu and in Settings.


16. SETTINGS — EVERY SECTION (PARENT AREA)
------------------------------------------
  - IPTV configuration: add/edit your provider(s).
  - Profiles: content-filter profiles for the parent browse (which categories
    show). (Kid profiles themselves live on the "Who's watching?" screen.)
  - Personalization: hide the "Recently Added" and/or "For You" rows.
  - Remote control (key mapping): re-assign remote buttons - see 17.
  - Kids: manage each child's approved content, the "manage content" on/off
    choice, screen-time limit, bedtime, and watch history.
  - Parental PIN: the passcode that protects this area.
  - Playback settings: buffering and hardware decoding (raise buffer for
    stutter; turn hardware decoding off if a video won't play).
  - Sleep timer: auto-stop after a set time; can close the app when it fires.
    While it runs, a timer chip with a live m:ss countdown shows in the top bar -
    select it to change/extend or turn it off.
  - Fast-scroll: in a long list, HOLD Up to jump to the top / A-Z bar. In the
    User Guide, Up/Down page-scroll.
  - Storage: clear Cache, Favourites, Watch Later, Continue Watching,
    Downloads, or Recordings - selectively or all.
  - Subtitles: OpenSubtitles API key.
  - TV Guide (EPG): optional external XMLTV URL (.xml / .xml.gz).
  - App updates: check for and install the latest version.
  - Sync & Backup: back up / restore / delete your saved lists.
  - Troubleshooting: diagnostics (see 18).
  - User guide / About.
  - Restart app: fully closes and relaunches the app (a quick fix for
    glitches without reaching for the Fire TV settings).
  - Restore factory defaults: wipes EVERYTHING — portal settings, kid
    profiles, parental PIN, approved content, favourites, downloads and
    caches — so the app starts like a brand-new install. Asks for
    confirmation first; this cannot be undone.
  - Exit.


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
  - A movie STUTTERS at the start or after seeking (most common with 4K/UHD
    titles or on a slower/public Wi-Fi): set Settings > Playback settings >
    Buffer to HIGH - it pre-loads much more before playing and usually removes
    the stutter. Keep Hardware decoding ON, and prefer home Wi-Fi over a public
    hotspot for 4K.
  - A movie's audio is too QUIET (some titles are mastered at a low level): open
    the player menu and pick "Audio boost" to cycle Off > +4 > +8 > +12 dB. It
    lifts the volume above the normal 100% and is remembered for next time.
  - A movie won't play smoothly in the default player: from the parent-side
    player menu pick "Switch player (VLC)" to play it through the VLC engine
    (it handles some codecs/containers better). The VLC player has all the same
    menu options, and position, subtitles, speed, Continue Watching and
    autoplay-next carry over; "Switch player (Default)" switches back.
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
