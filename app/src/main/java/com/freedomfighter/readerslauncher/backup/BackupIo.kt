package com.freedomfighter.readerslauncher.backup

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.BuildConfig
import com.freedomfighter.readerslauncher.data.AppWidgetTile
import com.freedomfighter.readerslauncher.data.CalendarTile
import com.freedomfighter.readerslauncher.data.HomeState
import com.freedomfighter.readerslauncher.data.TasksTile
import com.freedomfighter.readerslauncher.widgets.CalendarSource
import com.freedomfighter.readerslauncher.widgets.TaskSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The Android side of [LauncherBackup]: gathering the backup, the share sheet, restoring. */
object BackupIo {
    private fun dir(context: Context) = File(context.cacheDir, "backup")

    /** A shared backup lying in the cache is gone at the next export and at every start. */
    fun cleanUp(context: Context) { dir(context).listFiles()?.forEach { it.delete() } }

    fun build(context: Context, app: App): LauncherBackup.Backup {
        val s = app.prefs.settings.value
        // widget tiles added before 1.16 did not keep their provider: ask the system while the ids are alive
        val home = withProviders(app, app.store.state.value)
        if (home != app.store.state.value) app.store.update { withProviders(app, it) }
        val labels = app.apps.apps.value.associate { it.ref.packageName to it.label }
        val pkgs = LauncherBackup.referencedApps(home).map { it.packageName } +
            LauncherBackup.widgetTiles(home).map { LauncherBackup.packageOfProvider(it.provider) }.filter { it.isNotBlank() } +
            home.hidden.map { it.packageName }
        val calendars = CalendarSource.calendars(context)
        val calendarNames = home.allTiles.filterIsInstance<CalendarTile>().associate { t ->
            t.id to (t.calendarIds.mapNotNull { id -> calendars.firstOrNull { it.id == id }?.let { LauncherBackup.CalendarName(it.account, it.name) } } +
                t.calendarKeys.map { LauncherBackup.CalendarName(it.substringBefore(0.toChar()), it.substringAfter(0.toChar())) })
        }.filterValues { it.isNotEmpty() }
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return LauncherBackup.Backup(
            exported = iso.format(Date()),
            launcher_version = BuildConfig.VERSION_NAME,
            settings = LauncherBackup.Settings(
                theme = s.theme.name, font = s.font.name, textSize = s.textSize.name, align = s.align.name,
                doubleTapTheme = s.doubleTapTheme, swipeDownNotifications = s.swipeDownNotifications, showStatusBar = s.showStatusBar,
                haptics = s.haptics, autoRotate = s.autoRotate, doubleTapShortcuts = s.doubleTapShortcuts, readerSp = s.readerSp
            ),
            home = home,
            apps = pkgs.distinct().mapNotNull { p -> labels[p]?.let { p to it } }.toMap(),
            calendars = calendarNames
        )
    }

    private fun withProviders(app: App, h: HomeState): HomeState {
        fun fill(t: com.freedomfighter.readerslauncher.data.Tile) =
            if (t is AppWidgetTile && t.provider.isBlank() && t.appWidgetId >= 0)
                app.widgetManager.getAppWidgetInfo(t.appWidgetId)?.provider?.let { t.copy(provider = it.flattenToString()) } ?: t
            else t
        return h.copy(tiles = h.tiles.map(::fill), morePages = h.morePages.map { p -> p.copy(tiles = p.tiles.map(::fill)) })
    }

    fun share(context: Context, json: String, chooserTitle: String) {
        cleanUp(context)
        val file = File(dir(context).apply { mkdirs() }, LauncherBackup.FILE_NAME)
        file.writeText(json)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".backup", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, LauncherBackup.FILE_NAME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(LauncherBackup.FILE_NAME, uri)
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun readText(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""

    fun installedProviders(app: App): Set<String> =
        app.widgetManager.installedProviders.map { it.provider.flattenToString() }.toSet()

    fun missing(app: App, b: LauncherBackup.Backup): LauncherBackup.Missing =
        LauncherBackup.missing(b, app.apps.apps.value.map { it.ref }, installedProviders(app))

    /** The app's page in a store: F-Droid / Play through market://, else F-Droid's web page. */
    fun openStore(context: Context, pkg: String) {
        for (u in LauncherBackup.storeUris(pkg)) {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(i) }.isSuccess) return
        }
    }

    /**
     * Replaces the home screen and the look with the backup's. The widgets of the old home are
     * released; the new widget tiles come without ids and are bound by the activity afterwards.
     */
    fun apply(context: Context, app: App, b: LauncherBackup.Backup) {
        val old = app.store.state.value
        LauncherBackup.widgetTiles(old).filter { it.appWidgetId >= 0 }.forEach { runCatching { app.widgetHost.deleteAppWidgetId(it.appWidgetId) } }
        if (b.version >= 1) applySettings(app, b.settings)
        var home = LauncherBackup.prepare(b, app.apps.apps.value.map { it.ref })
        home = resolveCalendars(context, home)
        home = resolveTaskLists(context, home)
        app.store.setPage(0)
        app.store.update { home }
    }

    private fun applySettings(app: App, s: LauncherBackup.Settings) {
        val p = app.prefs
        runCatching { p.setTheme(enumValueOf(s.theme)) }
        runCatching { p.setFont(enumValueOf(s.font)) }
        runCatching { p.setTextSize(enumValueOf(s.textSize)) }
        runCatching { p.setAlign(enumValueOf(s.align)) }
        p.setDoubleTapTheme(s.doubleTapTheme); p.setSwipeDownNotifications(s.swipeDownNotifications); p.setShowStatusBar(s.showStatusBar)
        p.setHaptics(s.haptics); p.setAutoRotate(s.autoRotate); p.setDoubleTapShortcuts(s.doubleTapShortcuts); p.setReaderSp(s.readerSp)
    }

    /** Agenda tiles restored by calendar name: matched now if calendar access is granted, else by the tile later. */
    fun resolveCalendars(context: Context, h: HomeState): HomeState {
        if (!CalendarSource.hasPermission(context)) return h
        val here = CalendarSource.calendars(context).map { Triple(it.id, it.account, it.name) }
        fun fix(t: com.freedomfighter.readerslauncher.data.Tile) =
            if (t is CalendarTile && t.calendarKeys.isNotEmpty()) {
                val ids = LauncherBackup.matchCalendars(t.calendarKeys, here)
                // nothing matches (another account on this phone): every calendar, as a new tile would
                t.copy(calendarIds = ids.ifEmpty { here.map { it.first } }, calendarKeys = emptyList())
            } else t
        return h.copy(tiles = h.tiles.map(::fix), morePages = h.morePages.map { p -> p.copy(tiles = p.tiles.map(::fix)) })
    }

    /** Task lists have per-phone ids too: found again by title when the task app can be read. */
    private fun resolveTaskLists(context: Context, h: HomeState): HomeState {
        val cache = HashMap<String, List<TaskSource.TaskList>>()
        fun fix(t: com.freedomfighter.readerslauncher.data.Tile) =
            if (t is TasksTile) {
                val lists = cache.getOrPut(t.source) { runCatching { TaskSource.of(context, t.source).let { s -> if (s.ready) s.lists() else emptyList() } }.getOrDefault(emptyList()) }
                lists.firstOrNull { it.title == t.listTitle }?.let { t.copy(listId = it.id) } ?: t
            } else t
        return h.copy(tiles = h.tiles.map(::fix), morePages = h.morePages.map { p -> p.copy(tiles = p.tiles.map(::fix)) })
    }

    fun providerComponent(provider: String): ComponentName? = ComponentName.unflattenFromString(provider)
}
