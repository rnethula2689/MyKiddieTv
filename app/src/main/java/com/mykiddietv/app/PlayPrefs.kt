package com.mykiddietv.app

/**
 * Playback preferences kept for the whole app session (in memory), applied by EVERY player —
 * live TV, movies (ExoPlayer + VLC), recordings, downloads, PiP. So if the user mutes, sets a
 * brightness level, or turns on night mode anywhere, it sticks everywhere until they
 * change it or the app is killed. (Loudness itself is the device volume keys' job.)
 *
 *  - muted:  whether the player's own audio output is muted.
 *  - brightPct: brightness level 0..100. -1 = unset → leave the player/system default.
 *  - night:  night-mode dimming overlay on/off.
 */
object PlayPrefs {
    var muted = false
    var brightPct = -1
    var night = false
}
