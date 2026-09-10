package com.freedomfighter.readerslauncher.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.data.MindfulTile
import com.freedomfighter.readerslauncher.ui.LocalColors
import com.freedomfighter.readerslauncher.ui.LocalTypo
import com.freedomfighter.readerslauncher.ui.Nav
import com.freedomfighter.readerslauncher.ui.Page
import com.freedomfighter.readerslauncher.ui.ScreenTitle
import com.freedomfighter.readerslauncher.ui.Small
import com.freedomfighter.readerslauncher.ui.T
import com.freedomfighter.readerslauncher.ui.TextRow
import com.freedomfighter.readerslauncher.ui.noRippleClickable
import com.freedomfighter.readerslauncher.ui.rememberTick
import com.freedomfighter.readerslauncher.ui.rowPadH
import com.freedomfighter.readerslauncher.ui.rowPadV
import com.freedomfighter.readerslauncher.ui.widgetTwoLineHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.abs

/** Reader's Mindful Tool: its state provider and its service, both signature-protected. */
object ReadersMindful {
    const val PACKAGE = "com.freedomfighter.readersmindful"
    val URI: Uri = Uri.parse("content://$PACKAGE/state")

    data class State(
        val intervalRunning: Boolean, val intervalMin: Int, val nextBellAt: Long, val rang: Int,
        val periodRunning: Boolean, val periodMin: Int, val periodStartedAt: Long, val periodEndsAt: Long,
        val lastInterval: Int, val lastDuration: Int
    )

    fun isInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    fun state(context: Context): State? = runCatching {
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            fun i(n: String) = c.getInt(c.getColumnIndexOrThrow(n))
            fun l(n: String) = c.getLong(c.getColumnIndexOrThrow(n))
            State(i("interval_running") == 1, i("interval_min"), l("next_bell_at"), i("rang"), i("period_running") == 1, i("period_min"), l("period_started_at"), l("period_ends_at"), i("last_interval"), i("last_duration"))
        }
    }.getOrNull()

    /** The app's foreground service, started from here: the launcher is in the foreground, so the OS allows it. */
    private fun service(context: Context, action: String, minutes: Int = 0) {
        val i = Intent("$PACKAGE.$action").setClassName(PACKAGE, "$PACKAGE.BellService").putExtra("minutes", minutes)
        runCatching { ContextCompat.startForegroundService(context, i) }
    }
    fun startInterval(context: Context, minutes: Int) = service(context, "START_INTERVAL", minutes)
    fun stopInterval(context: Context) = service(context, "STOP_INTERVAL")
    fun startPeriod(context: Context, minutes: Int) = service(context, "START_PERIOD", minutes)
    fun stopPeriod(context: Context) = service(context, "STOP_PERIOD")

    fun open(context: Context) {
        val intent = if (isInstalled(context)) context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return
        else Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/funkypitt/readers-mindful"))
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

private fun clock(millis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

private fun countdown(at: Long, now: Long): String {
    val s = ((at - now).coerceAtLeast(0L) + 999) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

/**
 * The mindful tile: "a bowl every 10 min ▶" or "meditate 20 min ▶", one at a time. In
 * "both" mode a sideways swipe flips between them. Tap the text to change the minutes,
 * ▶ starts, ■ stops; while a period runs a hairline shows how far it has come.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MindfulTileView(tile: MindfulTile, app: App, onLongPress: () -> Unit, onMinutes: (String) -> Unit) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val tick = rememberTick()
    val installed = ReadersMindful.isInstalled(context)
    val generation = rememberProviderGeneration(ReadersMindful.URI)
    var poll by remember { mutableIntStateOf(0) }
    val st by produceState<ReadersMindful.State?>(null, generation, poll) {
        value = withContext(Dispatchers.IO) { ReadersMindful.state(context) }
    }
    val running = st?.intervalRunning == true || st?.periodRunning == true
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (running) { now = System.currentTimeMillis(); delay(1000L - now % 1000) }
        now = System.currentTimeMillis()
    }
    // A bowl that has just rung changes the state without a provider notification reaching us in time; look again every few seconds.
    LaunchedEffect(running) { while (running) { delay(5_000); poll++ } }

    var which by remember(tile.id) { mutableIntStateOf(if (tile.mode == "period") 1 else 0) }
    if (tile.mode == "interval") which = 0 else if (tile.mode == "period") which = 1
    val isInterval = which == 0
    val active = if (isInterval) st?.intervalRunning == true else st?.periodRunning == true
    val minutes = if (isInterval) (if (active) st!!.intervalMin else tile.intervalMin) else (if (active) st!!.periodMin else tile.durationMin)
    val title = stringResource(if (isInterval) R.string.mindful_interval_title else R.string.mindful_period_title, minutes)
    val caption = when {
        !installed -> "Reader's Mindful Tool"
        isInterval && active -> stringResource(R.string.mindful_next, clock(st!!.nextBellAt), countdown(st!!.nextBellAt, now)) + (if (st!!.rang > 0) " · " + stringResource(R.string.mindful_rung, st!!.rang) else "")
        !isInterval && active -> stringResource(R.string.mindful_until, clock(st!!.periodEndsAt), countdown(st!!.periodEndsAt, now))
        else -> stringResource(R.string.widget_mindful) + (if (tile.mode == "both") " · " + stringResource(R.string.mindful_swipe) else "")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(widgetTwoLineHeight())
            .pointerInput(tile.mode) {
                if (tile.mode != "both") return@pointerInput
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (abs(total) > 60.dp.toPx()) { which = 1 - which; tick() } },
                    onHorizontalDrag = { change, delta -> total += delta; change.consume() }
                )
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!installed || active) ReadersMindful.open(context) else onMinutes(if (isInterval) "interval" else "period") },
                onLongClick = onLongPress
            )
            .padding(horizontal = rowPadH, vertical = rowPadV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            T(if (installed) title else stringResource(R.string.mindful_not_installed), maxLines = 1, color = if (installed) colors.fg else colors.dim)
            Small(caption, maxLines = 1)
            if (!isInterval && active) {
                val f = ((now - st!!.periodStartedAt).toFloat() / (st!!.periodMin * 60_000f)).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp)) {
                    drawRect(colors.rule, topLeft = Offset(0f, size.height / 3), size = Size(size.width, size.height / 3))
                    drawRect(colors.fg, size = Size(size.width * f, size.height))
                }
            }
        }
        if (installed) {
            Box(
                Modifier.padding(start = 16.dp).background(if (active) colors.fg else Color.Transparent)
                    .noRippleClickable {
                        tick()
                        when {
                            isInterval && active -> ReadersMindful.stopInterval(context)
                            isInterval -> ReadersMindful.startInterval(context, tile.intervalMin)
                            active -> ReadersMindful.stopPeriod(context)
                            else -> ReadersMindful.startPeriod(context, tile.durationMin)
                        }
                        poll++
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                T(if (active) "■" else "▶", size = typo.title, color = if (active) colors.bg else colors.fg, align = TextAlign.Center)
            }
        }
    }
}

/** Chosen when the tile is placed (and from its menu): both functions, or only one. */
@Composable
fun MindfulSetupScreen(nav: Nav, app: App, tileId: String?) {
    val context = LocalContext.current
    val typo = LocalTypo.current
    val existing = tileId?.let { id -> app.store.state.value.allTiles.firstOrNull { it.id == id } as? MindfulTile }
    val installed = ReadersMindful.isInstalled(context)
    BackHandler { nav.pop() }
    fun choose(mode: String) {
        if (existing != null) app.store.replaceTile(existing.copy(mode = mode)) else app.store.addTile(MindfulTile(mode = mode))
        nav.pop()
    }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.widget_mindful), onBack = { nav.pop() })
            Small(stringResource(R.string.mindful_setup_help), Modifier.padding(horizontal = rowPadH, vertical = 12.dp), maxLines = 6)
            if (!installed) TextRow(stringResource(R.string.mindful_not_installed), secondary = "github.com/funkypitt/readers-mindful", size = typo.title) { ReadersMindful.open(context) }
            TextRow(stringResource(R.string.mindful_both), inverted = existing?.mode == "both", secondary = stringResource(R.string.mindful_both_desc)) { choose("both") }
            TextRow(stringResource(R.string.mindful_interval_only), inverted = existing?.mode == "interval") { choose("interval") }
            TextRow(stringResource(R.string.mindful_period_only), inverted = existing?.mode == "period") { choose("period") }
        }
    }
}
