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
  **agenda** (next event, swipe for the following ones), **tasks** (a Tasks.org list:
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

## Tasks widget

The tasks tile reads a list from [Tasks.org](https://f-droid.org/packages/org.tasks/), the
open-source task app. Tasks.org keeps lists on the phone or syncs them with CalDAV
(Nextcloud, iCloud…), Google Tasks, Microsoft To Do or EteSync — so any backend works, and
the launcher never talks to a server itself.

Setup is two taps: *add widget → tasks* asks for Tasks.org's read/write permission, then
lists your lists. Pick one.

* With Tasks.org **15.11 or later** (its public content-provider API), ☐ completes the task
  in place and + adds one directly.
* With older versions the provider is read-only from outside: ☐ opens the task in Tasks.org
  and + opens its editor with the title filled in.

The tile follows Tasks.org's change notifications, so it refreshes as soon as a task is
added, completed or synced.

## Build

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Kotlin, Jetpack Compose (foundation only, no Material), kotlinx-serialization for the
single `home.json` state file. No other dependency. minSdk 26, targetSdk 34.

Export / import of the whole configuration is available in settings (a JSON file).

## Licence

MIT. See `LICENSE` for the µLauncher attribution.
