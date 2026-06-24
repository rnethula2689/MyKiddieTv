# MyKiddieTv

A kid-safe IPTV app for Amazon Fire tablets / Fire TV, built on the `firetv`
codebase. It speaks the Stalker/Ministra portal protocol and reuses that player, with
a profile gate and a parental content whitelist added on top.

## How it works

**Launcher → Profile picker** (`ProfileActivity`) with two profiles:

- **Kid** (🧸) → opens the **Kids home** (`KidHomeActivity`). Shows only the live channels
  and movies/series the parent has whitelisted. Parent-only menus (Settings, App updates)
  are hidden in every kid screen.
- **Parent** (👤) → asks for a **4-digit passcode**, then opens the full MyKiddieTv browse
  experience (`ChannelsActivity`) — Live TV, all genres, full VOD, search, etc.

> First run: no passcode is set, so the Parent profile opens directly. Set a passcode in
> **Settings → Kid Profile** right away.

## Parent setup (Settings → Kid Profile)

- **Parent / Kid profile names** — shown on the profile picker and kid greeting.
- **4-digit passcode** — required to enter the Parent profile (leave blank = open).
- **Manage Kid Content** (`KidContentActivity`):
  - **Live TV** tab — tick **individual channels** the kid may watch.
  - **Movies** tab — search the VOD library and tick **individual movies/series**.
  - Selections auto-save. The kid only ever sees these.

You still add your IPTV provider (Portal URL / MAC / Serial) under **Settings → IPTV
Configuration**, exactly as in the original app.

## What's built vs. later

- ✅ Profile picker, passcode gate, parent full app, settings, content whitelist.
- ✅ Kid **Live TV** is wired to the whitelist and plays today.
- ⏳ The richer **Kids home** (movies browsing UI) is a placeholder for now —
  the "Movies & Shows" button says "coming soon". The selected movies are already
  stored and ready for that screen.

## Building the APK

There's no Gradle wrapper checked in (same as the original). Build via GitHub Actions:
push to a GitHub repo and the workflow in `.github/workflows/build.yml` produces a
debug APK and attaches it to an `apk-latest` release for easy sideloading onto the
Fire tablet. (App id: `com.mykiddietv.app`, so it installs alongside the original.)

Locally you'd need JDK 17 + Android SDK + Gradle 8.7, then `gradle assembleDebug`.
