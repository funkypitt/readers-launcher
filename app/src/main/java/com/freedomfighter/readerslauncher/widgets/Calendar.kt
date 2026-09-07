package com.freedomfighter.readerslauncher.widgets

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.data.CalendarTile
import com.freedomfighter.readerslauncher.ui.LocalColors
import com.freedomfighter.readerslauncher.ui.LocalTypo
import com.freedomfighter.readerslauncher.ui.Nav
import com.freedomfighter.readerslauncher.ui.Page
import com.freedomfighter.readerslauncher.ui.Rule
import com.freedomfighter.readerslauncher.ui.ScreenTitle
import com.freedomfighter.readerslauncher.ui.Small
import com.freedomfighter.readerslauncher.ui.T
import com.freedomfighter.readerslauncher.ui.TextRow
import com.freedomfighter.readerslauncher.ui.rememberTick
import com.freedomfighter.readerslauncher.ui.rowPadH
import com.freedomfighter.readerslauncher.ui.rowPadV
import com.freedomfighter.readerslauncher.ui.widgetTwoLineHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class CalendarInfo(val id: Long, val name: String, val account: String)
data class EventInfo(val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean, val location: String?)

object CalendarSource {
    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(context: Context): List<CalendarInfo> {
        if (!hasPermission(context)) return emptyList()
        val out = ArrayList<CalendarInfo>()
        val proj = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME)
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, "${CalendarContract.Calendars.VISIBLE}=1", null, "${CalendarContract.Calendars.ACCOUNT_NAME},${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME}")
            ?.use { c ->
                while (c.moveToNext()) out += CalendarInfo(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "")
            }
        return out
    }

    /** Upcoming (or ongoing) event instances, soonest first, over the next year. */
    fun upcoming(context: Context, calendarIds: List<Long>, max: Int = 60): List<EventInfo> {
        if (!hasPermission(context) || calendarIds.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now - 24 * 3600_000L)
        ContentUris.appendId(builder, now + 365L * 24 * 3600_000L)
        val proj = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID
        )
        val sel = calendarIds.joinToString(",") { it.toString() }
        val out = ArrayList<EventInfo>()
        context.contentResolver.query(
            builder.build(), proj,
            // STATUS is NULL for many locally created events; "NULL != x" would drop them.
            "${CalendarContract.Instances.CALENDAR_ID} IN ($sel) AND (${CalendarContract.Instances.STATUS} IS NULL OR ${CalendarContract.Instances.STATUS} != ${CalendarContract.Instances.STATUS_CANCELED})",
            null, "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { c ->
            while (c.moveToNext() && out.size < max) {
                val allDay = c.getInt(4) == 1
                var begin = c.getLong(2)
                var end = c.getLong(3)
                if (allDay) {
                    // All-day instances are stored in UTC midnight; shift to local midnight.
                    val off = Calendar.getInstance().timeZone.getOffset(begin)
                    begin -= off; end -= off
                }
                if (end < now) continue
                out += EventInfo(c.getLong(0), c.getString(1)?.ifBlank { null } ?: "(untitled)", begin, end, allDay, c.getString(5))
            }
        }
        return out
    }

    const val READERS_CALENDAR = "com.freedomfighter.readerscalendar"

    private fun timeIntent(): Intent {
        val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
        ContentUris.appendId(builder, System.currentTimeMillis())
        return Intent(Intent.ACTION_VIEW).setData(builder.build())
    }

    /** Installed apps that show a calendar day: (package, label). */
    fun calendarApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        return pm.queryIntentActivities(timeIntent(), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedWith(compareBy({ it.first != READERS_CALENDAR }, { it.second.lowercase() }))
    }

    /** Reader's Calendar when installed, else the system's choice. */
    fun defaultApp(context: Context): String = if (calendarApps(context).any { it.first == READERS_CALENDAR }) READERS_CALENDAR else ""

    /** Aim the intent at the chosen app when it is installed and handles it; otherwise let the system pick. */
    private fun Intent.target(context: Context, app: String): Intent {
        if (app.isNotEmpty() && context.packageManager.queryIntentActivities(Intent(this).setPackage(app), 0).isNotEmpty()) setPackage(app)
        return addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun open(context: Context, e: EventInfo, app: String = "") {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id)
        val intent = Intent(Intent.ACTION_VIEW).setData(uri)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
            .target(context, app)
        runCatching { context.startActivity(intent) }.onFailure { openApp(context, app) }
    }

    fun openApp(context: Context, app: String = "") {
        runCatching { context.startActivity(timeIntent().target(context, app)) }
            .onFailure { if (app.isNotEmpty()) runCatching { context.startActivity(timeIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}

@Composable
fun whenString(e: EventInfo, now: Long): String {
    val ctx = LocalContext.current
    val cal = Calendar.getInstance()
    fun dayIndex(t: Long): Long { cal.timeInMillis = t; cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0); return cal.timeInMillis / 86_400_000L }
    val today = dayIndex(now)
    val d = dayIndex(e.begin)
    val dayLabel = when (d - today) {
        0L -> stringResource(R.string.calendar_today)
        1L -> stringResource(R.string.calendar_tomorrow)
        in 2L..6L -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(e.begin)).lowercase()
        else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(e.begin)).lowercase()
    }
    if (e.allDay) return "$dayLabel · " + stringResource(R.string.calendar_all_day)
    val tf = if (android.text.format.DateFormat.is24HourFormat(ctx)) SimpleDateFormat("HH:mm", Locale.getDefault()) else SimpleDateFormat("h:mm a", Locale.getDefault())
    val sameDay = dayIndex(e.end) == d
    return "$dayLabel · ${tf.format(Date(e.begin))}" + if (sameDay) " – ${tf.format(Date(e.end))}" else ""
}

/**
 * Agenda tile: shows one upcoming event; swipe left for the next, right for the previous
 * (never earlier than now); tap opens the event in the calendar app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarTileView(tile: CalendarTile, app: App, onLongPress: () -> Unit, onOpen: () -> Unit = {}) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val tick = rememberTick()
    val now = rememberNow()
    var index by remember(tile.id) { mutableIntStateOf(0) }
    val permitted = CalendarSource.hasPermission(context)
    val events by produceState<List<EventInfo>>(emptyList(), tile.calendarIds, now / (5 * 60_000), permitted) {
        value = withContext(Dispatchers.IO) { CalendarSource.upcoming(context, tile.calendarIds) }
    }
    LaunchedEffect(events.size) { if (index >= events.size) index = maxOf(0, events.size - 1) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val e = events.getOrNull(index)
    Column(
        Modifier
            .fillMaxWidth()
            .height(widgetTwoLineHeight())
            .pointerInput(events.size) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (abs(total) > 60.dp.toPx()) {
                            val next = if (total < 0) index + 1 else index - 1
                            if (next in events.indices) { index = next; tick() }
                        }
                    },
                    onHorizontalDrag = { change, delta -> total += delta; change.consume() }
                )
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    // A tap shows today's and tomorrow's events in the launcher's own style.
                    if (!permitted) permLauncher.launch(Manifest.permission.READ_CALENDAR) else onOpen()
                },
                onLongClick = onLongPress
            )
            .padding(horizontal = rowPadH, vertical = rowPadV),
        verticalArrangement = Arrangement.Center
    ) {
        when {
            !permitted -> T(stringResource(R.string.calendar_permission), size = typo.title, color = colors.dim, maxLines = 2)
            e == null -> T(stringResource(R.string.calendar_none), size = typo.title, color = colors.dim, maxLines = 2)
            else -> {
                // Exactly two lines: the title, then when. The location would make the tile grow.
                T(e.title, maxLines = 1)
                Small(whenString(e, now) + (if (events.size > 1) "   ${index + 1}/${events.size}" else ""), maxLines = 1)
            }
        }
    }
}

/** Today's and tomorrow's events of the tile's calendars, as text; "open the agenda" on top. */
@Composable
fun AgendaScreen(nav: Nav, app: App, tileId: String) {
    val context = LocalContext.current
    val typo = LocalTypo.current
    val colors = LocalColors.current
    val tile = app.store.state.value.tiles.firstOrNull { it.id == tileId } as? CalendarTile
    BackHandler { nav.pop() }
    if (tile == null) { nav.pop(); return }
    val now = rememberNow()
    val events by produceState<List<EventInfo>>(emptyList(), tile.calendarIds, now / 60_000) {
        value = withContext(Dispatchers.IO) { CalendarSource.upcoming(context, tile.calendarIds, max = 200) }
    }
    val cal = Calendar.getInstance()
    fun startOfDay(t: Long): Long { cal.timeInMillis = t; cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0); return cal.timeInMillis }
    val today0 = startOfDay(now); val tomorrow0 = today0 + 86_400_000L; val after0 = tomorrow0 + 86_400_000L
    val today = events.filter { it.begin < tomorrow0 && it.end > today0 }
    val tomorrow = events.filter { it.begin < after0 && it.end > tomorrow0 && it.begin >= tomorrow0 }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.widget_calendar), onBack = { nav.pop() })
            TextRow(stringResource(R.string.agenda_open), size = typo.title, onClick = { CalendarSource.openApp(context, tile.app) })
            Rule()
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                for ((label, list) in listOf(R.string.calendar_today to today, R.string.calendar_tomorrow to tomorrow)) {
                    item { Small(stringResource(label), Modifier.padding(horizontal = rowPadH).padding(top = 18.dp, bottom = 4.dp), color = colors.dim) }
                    if (list.isEmpty()) item { Small(stringResource(R.string.calendar_none), Modifier.padding(horizontal = rowPadH, vertical = 8.dp)) }
                    items(list, key = { "${label}-${it.id}-${it.begin}" }) { e ->
                        TextRow(e.title, secondary = whenString(e, now) + (if (!e.location.isNullOrBlank()) " · " + e.location else ""), onClick = { CalendarSource.open(context, e, tile.app) })
                    }
                }
            }
        }
    }
}

/** Choose which calendars feed a tile; creates the tile when [tileId] is null. */
@Composable
fun CalendarSetupScreen(nav: Nav, app: App, tileId: String?) {
    val context = LocalContext.current
    BackHandler { nav.pop() }
    val existing = tileId?.let { id -> app.store.state.value.tiles.firstOrNull { it.id == id } as? CalendarTile }
    var granted by remember { mutableStateOf(CalendarSource.hasPermission(context)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(Manifest.permission.READ_CALENDAR) }
    val calendars by produceState<List<CalendarInfo>>(emptyList(), granted) {
        value = withContext(Dispatchers.IO) { CalendarSource.calendars(context) }
    }
    var selected by remember { mutableStateOf(existing?.calendarIds?.toSet() ?: emptySet()) }
    LaunchedEffect(calendars) { if (existing == null && selected.isEmpty()) selected = calendars.map { it.id }.toSet() }
    val apps = remember { CalendarSource.calendarApps(context) }
    var chosenApp by remember { mutableStateOf(existing?.app ?: CalendarSource.defaultApp(context)) }
    val colors = LocalColors.current

    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(
                stringResource(R.string.widget_calendar), onBack = { nav.pop() },
                trailing = stringResource(R.string.action_ok),
                onTrailing = {
                    val ids = selected.toList()
                    if (existing != null) app.store.replaceTile(existing.copy(calendarIds = ids, app = chosenApp))
                    else app.store.addTile(CalendarTile(calendarIds = ids, app = chosenApp))
                    nav.pop()
                }
            )
            if (!granted) {
                TextRow(stringResource(R.string.calendar_permission)) { permLauncher.launch(Manifest.permission.READ_CALENDAR) }
                Rule()
            }
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                // Which app opens when the tile or an event is tapped: Reader's Calendar first when installed.
                item { Small(stringResource(R.string.calendar_opens_with), Modifier.padding(horizontal = rowPadH).padding(top = 10.dp, bottom = 4.dp), color = colors.dim) }
                items(apps, key = { "app-" + it.first }) { (pkg, label) ->
                    TextRow(label, inverted = pkg == chosenApp) { chosenApp = pkg }
                }
                item { TextRow(stringResource(R.string.calendar_app_default), inverted = chosenApp == "") { chosenApp = "" } }
                item { Small(stringResource(R.string.calendar_choose), Modifier.padding(horizontal = rowPadH).padding(top = 22.dp, bottom = 4.dp), color = colors.dim) }
                items(calendars, key = { it.id }) { c ->
                    TextRow(c.name, inverted = c.id in selected, secondary = c.account) {
                        selected = if (c.id in selected) selected - c.id else selected + c.id
                    }
                }
            }
        }
    }
}
