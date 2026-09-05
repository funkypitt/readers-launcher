# Reader's Launcher

A black-and-white, text-only Android launcher in the spirit of the Light Phone III.
No icons, no colours, no wallpaper: a single column of words.

* Every screen is either white on black or black on white. Flip between the two at any
  time with a double tap on the home screen, from the quick-settings tile
  "Reader's theme", or in settings (which also offers "follow system").
* The home screen is a column of tiles: an **app** (its name), a **category** (a name that
  opens a text list of apps), or a **widget**. A special **grid** of 3 to 5 squares is pinned
  at the bottom for the unavoidable apps (phone, messages…); their icons are rendered as
  monochrome glyphs in the current theme.
* Built-in widgets: **clock**, **weather** (Open-Meteo, no key; tap for five days),
  **agenda** (next event, swipe for the following ones), **tasks** (a Google Tasks list:
  first task, ☐ to complete, + to add). Any standard app widget can be added too.

## Gestures

| Gesture | Effect |
|---|---|
| long press on empty space | add app / category · add grid launcher · add widget · settings |
| long press on a tile | open · edit · rename · move · arrange · remove · app info · uninstall |
| tap a tile | open the app / the category |
| double tap on empty space | flip the theme |
| swipe up | all apps, alphabetical, with search; long press an app for its options |
| swipe up and hold | text list of recently used apps ("open apps") |
| swipe down | notification shade |
| swipe left / right on the agenda tile | next / previous upcoming event |
| tap the weather tile | one day ↔ five days |
| long press a grid square | choose / change / clear the app, number of squares |

### A note on "open apps"

Android does not let a third-party launcher replace the system's recents view: the
swipe-up-and-hold gesture *from inside another app* is handled by the system and always
shows the standard previews. Reader's Launcher offers the closest thing available: on the
home screen, swipe up and hold to get a text list of the apps used recently, ordered by
last use. This needs the "usage access" special permission (settings → usage access).

## Google Tasks widget setup

Google requires each app build to have its own OAuth client. One-time steps:

1. Open https://console.cloud.google.com, create a project (any name).
2. *APIs & Services → Library*: enable **Google Tasks API**.
3. *APIs & Services → OAuth consent screen*: external, add your Google account as a test
   user (a test app needs no verification).
4. *APIs & Services → Credentials → Create credentials → OAuth client ID*:
   * Application type: **Android**
   * Package name: `com.freedomfighter.readerslauncher`
   * SHA-1: the certificate the APK is signed with. For the debug key used by this
     repository's builds:
     `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep SHA1`
5. Copy the client ID (ends with `.apps.googleusercontent.com`) and paste it in the app:
   *settings → google tasks setup*, then *sign in with google* and choose a list.

Tokens are stored on the device only. Sign out from the same screen.

## Build

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Kotlin, Jetpack Compose (foundation only, no Material), kotlinx-serialization for the
single `home.json` state file, AppAuth for Google sign-in. minSdk 26, targetSdk 34.

Export / import of the whole configuration is available in settings (a JSON file).

## Licence

MIT. See `LICENSE` for the µLauncher attribution.
