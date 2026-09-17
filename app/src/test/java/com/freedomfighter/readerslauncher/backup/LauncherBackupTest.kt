package com.freedomfighter.readerslauncher.backup

import com.freedomfighter.readerslauncher.backup.LauncherBackup.Backup
import com.freedomfighter.readerslauncher.backup.LauncherBackup.Read
import com.freedomfighter.readerslauncher.data.AppRef
import com.freedomfighter.readerslauncher.data.AppTile
import com.freedomfighter.readerslauncher.data.AppWidgetTile
import com.freedomfighter.readerslauncher.data.CalendarTile
import com.freedomfighter.readerslauncher.data.CategoryTile
import com.freedomfighter.readerslauncher.data.ClockTile
import com.freedomfighter.readerslauncher.data.Grid
import com.freedomfighter.readerslauncher.data.HomePage
import com.freedomfighter.readerslauncher.data.HomeState
import com.freedomfighter.readerslauncher.data.MindfulTile
import com.freedomfighter.readerslauncher.data.Place
import com.freedomfighter.readerslauncher.data.TasksTile
import com.freedomfighter.readerslauncher.data.WeatherTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherBackupTest {
    private val sep = 0.toChar()
    private val notes = AppRef("com.freedomfighter.readersnotes", "com.freedomfighter.readersnotes.MainActivity")
    private val cal = AppRef("com.freedomfighter.readerscalendar", "com.freedomfighter.readerscalendar.MainActivity")
    private val signal = AppRef("org.thoughtcrime.securesms", "org.thoughtcrime.securesms.RoutingActivity")
    private val camera = AppRef("com.android.camera2", "com.android.camera.CameraLauncher")
    private val workMail = AppRef("com.example.mail", "com.example.mail.Main", user = 10)
    private val signalWidget = "org.thoughtcrime.securesms/org.thoughtcrime.securesms.widget.Widget"

    private val home = HomeState(
        tiles = listOf(
            ClockTile(id = "clock"),
            AppTile(id = "t-notes", app = notes),
            CategoryTile(id = "c", name = "messages", apps = listOf(signal, camera)),
            WeatherTile(id = "w", place = Place("Lausanne", 46.52, 6.63), fiveDays = true),
            CalendarTile(id = "agenda", calendarIds = listOf(3, 7), app = "com.freedomfighter.readerscalendar")
        ),
        morePages = listOf(HomePage(id = "p2", tiles = listOf(
            MindfulTile(id = "m", mode = "interval", intervalMin = 15),
            TasksTile(id = "tasks", listId = "42", listTitle = "Inbox", source = "readers"),
            AppWidgetTile(id = "wid", appWidgetId = 17, label = "Signal chats", cells = 6, provider = signalWidget)
        ))),
        grid = Grid(columns = 4, slots = listOf(camera, cal, null, signal, null)),
        hidden = listOf(AppRef("com.android.stk", "com.android.stk.StkMain")),
        renames = mapOf(notes.key to "notes", signal.key to "signal")
    )
    private val backup = Backup(
        exported = "2026-09-17T15:30:00Z", launcher_version = "1.16.0",
        settings = LauncherBackup.Settings(theme = "LIGHT", font = "SERIF", textSize = "LARGE", haptics = false, readerSp = 20),
        home = home,
        apps = mapOf(notes.packageName to "Reader's Notes", signal.packageName to "Signal", camera.packageName to "Camera", cal.packageName to "Reader's Calendar"),
        calendars = mapOf("agenda" to listOf(LauncherBackup.CalendarName("me@x.ch", "Personal"), LauncherBackup.CalendarName("local", "Work")))
    )

    @Test fun roundTrip() {
        val text = LauncherBackup.encode(backup)
        assertTrue(text.contains("\"format\": \"readers-launcher-backup\""))
        assertEquals(Read.Ok(backup), LauncherBackup.decode(text))
    }

    @Test fun unknownKeysIgnored() {
        val text = LauncherBackup.encode(backup).replaceFirst("{", "{\n  \"future_field\": {\"x\": [1, 2]},")
            .replace("\"readerSp\": 20", "\"readerSp\": 20, \"newSetting\": true")
        assertEquals(Read.Ok(backup), LauncherBackup.decode(text))
    }

    @Test fun foreignFilesRefused() {
        assertEquals(Read.Foreign, LauncherBackup.decode("""{"format": "readers-credentials", "version": 1, "readers-notes": {}}"""))
        assertEquals(Read.Foreign, LauncherBackup.decode("""{"hello": "world"}"""))
        assertEquals(Read.Foreign, LauncherBackup.decode("not json at all"))
        assertEquals(Read.Foreign, LauncherBackup.decode("""[1, 2, 3]"""))
    }

    @Test fun newerVersionRefusedOlderAccepted() {
        assertEquals(Read.TooNew(2), LauncherBackup.decode(LauncherBackup.encode(backup).replace("\"version\": 1,", "\"version\": 2,")))
        val older = LauncherBackup.decode(LauncherBackup.encode(backup.copy(version = 0)))
        assertTrue(older is Read.Ok && older.backup.home == home)
        // the launcher's former "export configuration" file: the bare home state
        val legacy = LauncherBackup.decode(LauncherBackup.json.encodeToString(HomeState.serializer(), home))
        assertTrue(legacy is Read.Ok && legacy.backup.home == home && legacy.backup.version == 0)
    }

    @Test fun missingAppsAndWidgetsDetected() {
        val installed = listOf(notes, camera, cal)
        val m = LauncherBackup.missing(backup, installed, providers = emptySet())
        assertEquals(listOf("org.thoughtcrime.securesms"), m.apps.map { it.packageName })
        assertEquals("Signal", m.apps[0].label)
        assertEquals(listOf("category:messages", "widget", "grid"), m.apps[0].places)
        assertEquals(1, m.widgets.size)
        assertTrue(!m.widgets[0].packageInstalled)
        assertTrue(LauncherBackup.missing(backup, installed + signal, setOf(signalWidget)).isEmpty)
        val w = LauncherBackup.missing(backup, installed + signal, emptySet())
        assertTrue(w.apps.isEmpty() && w.widgets.single().packageInstalled)
    }

    @Test fun prepareSkipsMissingAndRemaps() {
        val renamedActivity = camera.copy(activity = "com.android.camera.NewLauncher")
        val h = LauncherBackup.prepare(backup, listOf(notes, renamedActivity, cal))
        assertEquals(listOf("clock", "t-notes", "c", "w", "agenda"), h.tiles.map { it.id })
        assertEquals(listOf(renamedActivity), (h.tiles[2] as CategoryTile).apps)
        assertEquals(listOf(renamedActivity, cal, null, null, null), h.grid!!.slots)
        assertEquals(4, h.grid!!.columns)
        val wid = h.morePages[0].tiles[2] as AppWidgetTile
        assertEquals(-1, wid.appWidgetId); assertEquals(6, wid.cells); assertEquals(signalWidget, wid.provider)
        val agenda = h.tiles[4] as CalendarTile
        assertEquals(emptyList<Long>(), agenda.calendarIds)
        assertEquals(listOf("me@x.ch${sep}Personal", "local${sep}Work"), agenda.calendarKeys)
        assertEquals(home.hidden, h.hidden)
        assertEquals(mapOf(notes.key to "notes", signal.key to "signal"), h.renames)
        assertEquals(home.morePages[0].tiles.take(2), h.morePages[0].tiles.take(2))
        assertEquals(home.tiles[3], h.tiles[3])
    }

    @Test fun missingAppTileLeavesNoGap() {
        val h = LauncherBackup.prepare(backup, listOf(camera, cal))
        assertEquals(listOf("clock", "c", "w", "agenda"), h.tiles.map { it.id })
    }

    @Test fun workProfileResolution() {
        val personal = workMail.copy(user = -1)
        assertEquals(workMail.copy(user = 11), LauncherBackup.resolve(workMail, listOf(personal, workMail.copy(user = 11))))
        assertEquals(personal, LauncherBackup.resolve(workMail, listOf(personal)))
    }

    @Test fun legacyCalendarIdsKept() {
        val h = LauncherBackup.prepare(backup.copy(calendars = emptyMap()), listOf(notes, camera, cal, signal))
        assertEquals(listOf(3L, 7L), (h.tiles[4] as CalendarTile).calendarIds)
    }

    @Test fun calendarsMatchedByName() {
        val here = listOf(Triple(11L, "local", "Work"), Triple(12L, "other@y.ch", "Personal"), Triple(13L, "me@x.ch", "Personal"))
        assertEquals(listOf(13L, 11L), LauncherBackup.matchCalendars(listOf("me@x.ch${sep}Personal", "local${sep}Work"), here))
        assertEquals(listOf(12L), LauncherBackup.matchCalendars(listOf("gone@z.ch${sep}Personal"), here.filter { it.first == 12L }))
        assertEquals(emptyList<Long>(), LauncherBackup.matchCalendars(listOf("a${sep}Nope"), here))
    }
}
