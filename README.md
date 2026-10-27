# XEU Community

An unofficial Android client for the [xiaomi.eu community forum](https://xiaomi.eu/community/). Not affiliated with Xiaomi or xiaomi.eu.

## Features

- **Forum reader** – every category, forum and thread in a native UI with paging, images, quotes and "jump to latest".
- **My devices** – pick your phones (by codename) and get a combined feed of their forums.
- **ROM tracker** – background checks of xiaomi.eu's SourceForge feed and release forum, with notifications for your devices.
- **Account** – log in on the forum's own page, switch between several accounts, see your profile, alerts, direct messages, watched threads, bookmarks and account settings.
- **Notifications** – forum alerts (replies, quotes, mentions, reactions) and new direct messages, checked every 15 min–3 h.
- **Material You** – Material 3 with dynamic color from your wallpaper (Android 12+), light/dark/system theme and a themed icon.
- **In-app updates** – stable and dev channels from GitHub releases.

## Get the APK

### Option A – Releases
- **Dev:** every push to `dev` replaces the **dev** pre-release.
- **Stable:** push a tag like `v1.0.0` (an annotated tag's message becomes the release notes) to publish a stable release.

Each release has the APK plus `update.json`. The app checks the GitHub API first; if that fails (for example GitHub's 60 requests/hour limit), it reads `update.json` through the release download link instead, which isn't rate limited. Downloads are checked against the SHA-256 before installing.

Signing uses the repo secrets `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD`, so every build installs over the previous one.

### Option B – Android Studio
Open the folder in Android Studio (Ladybug or newer), let Gradle sync, and press Run.

## Notes

- Requires Android 8.0+.
- Logging in: tick **Stay logged in** on the login page so the session survives app restarts. Google sign-in is blocked inside in-app browsers, so use your forum username/email and password.
- On Android 12+, to open xiaomi.eu links in the app: *Settings → Apps → XEU Community → Open by default → Add links*.
- The app reads the public HTML pages and RSS feeds. If the forum changes its theme, selectors in `ForumParser.kt` may need updating.
- Please be a good citizen: the tracker's minimum interval is 1 hour, and the forum's rules (English only, no ROM ETA questions) apply to anything you post.

## Project layout

```
app/src/main/java/dev/xeu/community/
  data/        Http (OkHttp + WebView cookie sharing), ForumParser (Jsoup), ForumRepository, Prefs
  work/        RomCheckWorker – background ROM checks and notifications
  ui/          Compose screens: Forums, Forum, Thread, Compose, Devices, ROMs, Account/Login
```
