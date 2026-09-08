# Reader's Launcher

A black-and-white, text-only Android launcher in the spirit of the Light Phone III.
No icons, no colours, no wallpaper: a single column of words.

* Every screen is either white on black or black on white. Flip between the two at any
  time with a double tap on empty home-screen space, from the long-press menu of any
  tile, from the quick-settings tile "Reader's theme", or in settings (which also offers
  "follow system").
* The home screen is a single fixed page — it never scrolls. It holds a column of tiles:
  an **app** (its name), a **category** (a name that opens a text list of apps), or a
  **widget**. Once the page is full, the menu says so instead of offering to add more.
  A special **grid** of 3 to 5 squares is pinned at the bottom for the unavoidable apps
  (phone, messages…); their icons are rendered as monochrome glyphs in the current theme.
* Built-in widgets, all of a fixed height so nothing on the page ever shifts: **clock**,
  **mot du jour** (the Littré dictionary's word of the day, from
  [Le Littré](https://github.com/funkypitt/littre-app); tap for the definition),
  **weather** (MeteoSwiss for places in Switzerland, Open-Meteo elsewhere or as fallback, no
  key; tap for five days),
  **agenda** (next event, swipe for the following ones), **tasks** (a Tasks.org list:
  first task, swipe for the following ones, ☐ to complete, + to add). Any standard app
  widget can be added too.
* Two **books** live beside the home screen: swipe right for the left one, left for the
  right one. A very plain reader — tap the right half of the page to go forward, the left
  half to go back, long press for chapters and text size — and it reopens where you stopped.
  The screen stays on while a page is open (released after 15 minutes without a page turn).
  EPUB, MOBI (PalmDoc), FB2 and plain text; no PDF.
* English by default; French, German, Spanish, Portuguese and Russian follow the device
  language.

## Gestures

| Gesture | Effect |
|---|---|
| long press on empty space | add app / category · add grid launcher · add widget · settings |
| long press on a tile | open · edit · rename · move · arrange · remove · app info · uninstall, plus add / flip theme / settings |
| arrange tiles | drag a row anywhere in the list, across as many rows as needed |
| tap a tile | open the app / the category |
| double tap an app tile or grid square | its app shortcuts (also under "shortcuts" in the long-press menus; can be turned off in settings) |
| double tap on empty space | flip the theme |
| swipe up | all apps, alphabetical, with search; long press an app for its options |
| swipe up and hold | text list of recently used apps ("open apps") |
| swipe down | notification shade |
| swipe right / left | the book on that side (long press in the reader for its menu) |
| the book tile | the book you were reading in [Reader's Books](https://github.com/funkypitt/readers-books), a dim "book" under it; tap carries on at the current page |
| the notes tile | the latest note from [Reader's Notes](https://github.com/funkypitt/readers-notes); swipe sideways for the others, tap opens it, + writes a new one |
| tap the agenda tile | today's and tomorrow's events, with "open the agenda" on top. The tile's setup chooses which calendar app opens: [Reader's Calendar](https://github.com/funkypitt/readers-calendar) when installed, otherwise whatever the system picks |
| swipe left / right on the agenda tile | next / previous upcoming event |
| swipe left / right on the tasks tile | next / previous open task |
| tap the weather tile | one day ↔ five days |
| long press a grid square | choose / change / clear the app, number of squares |

### A note on "open apps"

Android does not let a third-party launcher replace the system's recents view: the
swipe-up-and-hold gesture *from inside another app* is handled by the system and always
shows the standard previews. Reader's Launcher offers the closest thing available: on the
home screen, swipe up and hold to get a text list of the apps used recently, ordered by
last use. This needs the "usage access" special permission (settings → usage access).

## Tasks widget

The tasks tile reads a list from one of two apps, chosen in its setup:

* [Reader's Tasks](https://github.com/funkypitt/readers-tasks-android), the companion
  CalDAV client in the same black-and-white style (Infomaniak, Nextcloud, Tasks.org
  Cloud…). No permission prompt: both apps are signed with the same key.
* [Tasks.org](https://f-droid.org/packages/org.tasks/), which also syncs with Google
  Tasks, Microsoft To Do or EteSync.

The launcher never talks to a server itself. The tile shows the first open task; swipe
left or right to step through the others, ☐ completes, + adds.

* With Tasks.org **15.11 or later** (its public content-provider API), ☐ completes the task
  in place and + adds one directly.
* With older versions the provider is read-only from outside: ☐ opens the task in Tasks.org
  and + opens its editor with the title filled in.

The tile follows Tasks.org's change notifications, so it refreshes as soon as a task is
added, completed or synced.

## Books

Two slots, left and right of the home screen, each remembering its own book and position.
Any ebook opened from a file manager or shared to the launcher ("open with") asks which
side to put it on.
Opening a book copies it into the launcher's private storage, so it keeps working after the
original file moves. Text only: images, footnote links and styling are dropped, chapters are
paginated to the screen, and the reading size defaults to a value derived from the screen
width (adjustable from the reader's long-press menu). Formats: EPUB 2/3, MOBI/PRC/AZW with
PalmDoc or no compression (Kindle-store HUFF/CDIC or DRM files are refused), FictionBook 2,
plain text.

## Build

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Kotlin, Jetpack Compose (foundation only, no Material), kotlinx-serialization for the
single `home.json` state file. No other dependencies. minSdk 26, targetSdk 34.

Export / import of the whole configuration is available in settings (a JSON file).

## Licence

MIT. See `LICENSE` for the µLauncher attribution.
