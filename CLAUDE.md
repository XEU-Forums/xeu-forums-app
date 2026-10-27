# XEU Forums – notes for Claude

Unofficial Android app for the Xiaomi.EU community forum (XenForo 2 at https://www.xiaomi.eu/community/).
Kotlin + Jetpack Compose (Material 3). Package `dev.xeu.forums`. Repo: `XEU-Forums/xeu-forums-app`.

## Owner's standing rules

- **Commits:** author and committer are the owner (`pagnchanak <chea.pagnchanak@gmail.com>`).
  Never add `Co-Authored-By: Claude`, session links or any other AI attribution to commits, tags or PRs.
- **Branches:** work on `dev`. Keep `main` equal to `dev` unless told otherwise (fast-forward only).
- **Naming:** the app is **XEU Forums**; the forum is **Xiaomi.EU** (always this capitalization in UI text,
  notifications and docs). Home header reads "UNOFFICIAL APP" / "Xiaomi.EU Forums".
- **Unofficial:** never use Xiaomi's or Xiaomi.EU's logos or a copy of the Mi mark. The icon is original
  (two chat bubbles + "XEU" in Inter Display SemiBold, converted to outlines).
- **Theme:** orange palette only (`ui/Theme.kt`), light/dark/system. No Monet / dynamic colour.
- **Everything in-app:** never open the system browser. Xiaomi.EU pages must be native screens; if something
  only works on the website, remove the option instead of showing a web page. Exceptions: the login page
  (WebView), links to *other* websites inside posts (in-app browser), and the GitHub link in Settings.
  `isNativeForumLink()` in `data/Http.kt` decides what the app can open.
- **Tabs:** exactly three – Forums, My devices, Account. The ROM tracker was removed on purpose; don't re-add it.
- **Alerts:** only show/notify alerts the app can open natively. Bell with unread badge on the Forums header.

## Layout

```
app/src/main/java/dev/xeu/forums/
  data/    Http (OkHttp + WebView cookies, URL normalization), ForumParser/AccountParser (Jsoup),
           ForumRepository, Accounts (multi-account, Keystore-encrypted sessions), Prefs, Settings, ProfileCache
  update/  Updater – GitHub releases OTA (API first, update.json mirror fallback, SHA-256 check)
  work/    Workers (alerts/DM notifications, update checks), Notifier (channels)
  ui/      Compose screens + Components.kt (HeroHeader, SectionCard, CardRow, Pill…), Theme.kt
```

Gotchas:
- The forum answers on both `xiaomi.eu` and `www.xiaomi.eu`; always go through `normalizeForumUrl()` so the
  login cookies apply (feeds link to the bare host).
- Conversations (DMs) render in `ThreadScreen`; their messages are `article.message--conversationMessage`.

## Build and release (CI only – no Android SDK in the cloud workspace)

- `.github/workflows/build.yml` builds on every push. No workflow artifacts (keeps Actions storage at zero).
- **Dev:** push to `dev` → replaces the rolling `dev` pre-release (`XEU-Forums-<version>-dev.<run>.apk` + `update.json`).
- **Stable:** bump `version.txt`, write `release-notes.md`, push to `main` → CI creates tag `v<version>` and the
  stable release (marked Latest). Pushing a `v*` tag also works, but the Claude session's git proxy blocks tag
  pushes and tag creation via the API, so use the `main` route.
- `versionCode` = workflow run number (shared by both channels).
- Signing: repo secrets `KEYSTORE_BASE64` + `KEYSTORE_PASSWORD` (alias `xeu`). Never commit the keystore.
- Compile errors show up as check-run annotations (`Report compile errors` step) – read them with
  `gh api repos/XEU-Forums/xeu-forums-app/check-runs/<job-id>/annotations`, since raw job logs can't be downloaded
  from the Claude session.
- **F-Droid repo:** the `fdroid` job (push to `main`) rebuilds a self-hosted F-Droid repo from the latest 3 stable
  releases and force-pushes it as a single commit to `gh-pages` (GitHub Pages:
  https://xeu-forums.github.io/xeu-forums-app/). Index signed with secrets `FDROID_KEYSTORE_BASE64` +
  `FDROID_KEYSTORE_PASS` (alias `xeu-repo`, fingerprint `1BDCB444…F57227`); without them it's a dry run.
  Listing lives in `fdroid/metadata/`, landing page in `fdroid/index.html`. Stable only – never put dev builds there.
- After a release, verify: `update.json` from the release download URL, APK SHA-256 matches, APK signed with the
  release key, `actions/artifacts` total is 0.
