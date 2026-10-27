# XEU Community

An unofficial Android client for the [xiaomi.eu community forum](https://xiaomi.eu/community/). Not affiliated with Xiaomi or xiaomi.eu.

## Features

- **Forum reader** – browse every category, forum and thread in a native UI with paging, images, quotes and "jump to latest".
- **My devices** – pick the phones you own (from the forum's device list with codenames) and get a combined feed of their forums.
- **ROM tracker** – checks xiaomi.eu's SourceForge file feed and the HyperOS release forum in the background (every 1–24 h) and notifies you when a build for one of your codenames appears.
- **Log in & post** – log in on the forum's own login page (inside the app), then reply, quote posts and start threads using BB code.

## Get the APK

### Option A – Dev release
Every push to the `dev` branch builds an APK and publishes it as the **dev** pre-release (Releases page). Download the `.apk` from there and install it; later dev builds install over it.

Signing uses two repo secrets, `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD` (Settings → Secrets and variables → Actions). Without them each build gets a throwaway key and won't update over the previous install.

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
