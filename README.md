# XEU Forums

An unofficial Android client for the [Xiaomi.EU community forum](https://xiaomi.eu/community/). Not affiliated with Xiaomi or Xiaomi.EU.

[<img src="https://raw.githubusercontent.com/jaycee-h/jaycee-h.github.io/main/site/get-it-on-fdroid.png" alt="Get it on F-Droid" height="60">](https://jaycee-h.github.io/)

## Features

- **Forum reader** – every category, forum and thread in a native UI with paging, images, quotes and "jump to latest".
- **My devices** – pick your phones (by codename) and get a combined feed of their forums.
- **Account** – log in on the forum's login page, switch between accounts, see your profile, alerts, direct messages and watched threads, all inside the app.
- **Notifications** – forum alerts (replies, quotes, mentions, reactions) and new direct messages, checked every 15 min–3 h.
- **Design** – Material 3 with the XEU orange theme in light and dark mode, and a themed icon.
- **In-app updates** – stable and dev channels from GitHub releases.

## Get the APK

### Option A – Releases
- **Dev:** every push to `dev` replaces the **dev** pre-release.
- **Stable:** set the new version in `version.txt`, write the notes in `release-notes.md`, and push to `main`. CI creates the tag (e.g. `v1.0.0`) and publishes the stable release. Pushing a `v*` tag yourself works too.

Each release has the APK plus `update.json`. The app checks the GitHub API first; if that fails (for example GitHub's 60 requests/hour limit), it reads `update.json` through the release download link instead, which isn't rate limited. Downloads are checked against the SHA-256 before installing.

Signing uses the repo secrets `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD`, so every build installs over the previous one.

### Option B – F-Droid
[<img src="https://raw.githubusercontent.com/jaycee-h/jaycee-h.github.io/main/site/get-it-on-fdroid.png" alt="Get it on F-Droid" height="60">](https://jaycee-h.github.io/)

XEU Forums is in the **jaycee-h apps** F-Droid repository (works with F-Droid, Droid-ify and Neo Store):
open **https://jaycee-h.github.io/** on your phone and tap the **Get it on F-Droid** badge, or add
`https://jaycee-h.github.io/repo` under Settings → Repositories.
Fingerprint: `1BDCB444EB7C5E4B1B5BF62809B942F532C6E0E486306ACBDD78B467B8F57227`.
It picks up new stable releases from this repo automatically (checked every 3 hours).

### Option C – Android Studio
Open the folder in Android Studio (Ladybug or newer), let Gradle sync, and press Run.

## Notes

- Requires Android 8.0+.
- Logging in: tick **Stay logged in** on the login page so the session survives app restarts. Google sign-in is blocked inside in-app browsers, so use your forum username/email and password.
- On Android 12+, to open xiaomi.eu links in the app: *Settings → Apps → XEU Forums → Open by default → Add links*.
- The app reads the public HTML pages and RSS feeds. If the forum changes its theme, selectors in `ForumParser.kt` may need updating.
- The forum's rules (English only, no ROM ETA questions) apply to anything you post.

## Project layout

```
app/src/main/java/dev/xeu/forums/
  data/        Http (OkHttp + WebView cookie sharing), ForumParser (Jsoup), ForumRepository, Prefs
  work/        Workers – background checks for forum alerts, messages and app updates
  ui/          Compose screens: Forums, Forum, Thread, Compose, Devices, Account, Search, Settings
```
