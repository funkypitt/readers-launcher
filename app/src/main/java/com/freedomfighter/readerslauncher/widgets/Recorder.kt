package com.freedomfighter.readerslauncher.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlin.math.abs
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.ui.LocalColors
import com.freedomfighter.readerslauncher.ui.LocalTypo
import com.freedomfighter.readerslauncher.ui.Small
import com.freedomfighter.readerslauncher.ui.T
import com.freedomfighter.readerslauncher.ui.noRippleClickable
import com.freedomfighter.readerslauncher.ui.rememberTick
import com.freedomfighter.readerslauncher.ui.rowPadH
import com.freedomfighter.readerslauncher.ui.rowPadV
import com.freedomfighter.readerslauncher.ui.widgetTwoLineHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Reader's Recorder: its state provider and its recording service, both signature-protected. */
object ReadersRecorder {
    const val PACKAGE = "com.freedomfighter.readersrecorder"
    val URI: Uri = Uri.parse("content://$PACKAGE/state")

    data class State(val recording: Boolean, val paused: Boolean, val elapsedMs: Long, val lastId: String, val lastTitle: String, val lastDurationMs: Long, val lastStatus: String, val count: Int)

    val RECORDINGS: Uri = Uri.parse("content://$PACKAGE/recordings")
    data class Rec(val id: String, val title: String, val whenLabel: String, val durationMs: Long, val status: String)
    /** What the app's player is doing; [at] is when [positionMs] was read. */
    data class Playback(val id: String, val playing: Boolean, val positionMs: Long, val durationMs: Long, val at: Long)

    fun isInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /** Finished recordings, newest first. */
    fun recordings(context: Context): List<Rec> = runCatching {
        val out = ArrayList<Rec>()
        context.contentResolver.query(RECORDINGS, null, null, null, null)?.use { c ->
            val id = c.getColumnIndexOrThrow("id"); val title = c.getColumnIndexOrThrow("title"); val w = c.getColumnIndexOrThrow("when")
            val d = c.getColumnIndexOrThrow("duration_ms"); val st = c.getColumnIndexOrThrow("status")
            while (c.moveToNext()) out += Rec(c.getString(id), c.getString(title) ?: "", c.getString(w) ?: "", c.getLong(d), c.getString(st) ?: "")
        }
        out
    }.getOrDefault(emptyList())

    fun playback(context: Context): Playback? = runCatching {
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            val idx = c.getColumnIndex("play_id"); if (idx < 0) return null
            Playback(c.getString(idx) ?: "", c.getString(c.getColumnIndexOrThrow("play_state")) == "playing",
                c.getLong(c.getColumnIndexOrThrow("play_pos_ms")), c.getLong(c.getColumnIndexOrThrow("play_dur_ms")), c.getLong(c.getColumnIndexOrThrow("play_at")))
        }
    }.getOrNull()

    /** Play this recording, or pause / resume it if it is the one playing. */
    fun togglePlay(context: Context, id: String) {
        val i = Intent("$PACKAGE.PLAY_TOGGLE").setClassName(PACKAGE, "$PACKAGE.PlayerService").putExtra("id", id)
        runCatching { ContextCompat.startForegroundService(context, i) }
    }

    fun state(context: Context): State? = runCatching {
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            fun i(n: String) = c.getInt(c.getColumnIndexOrThrow(n))
            fun l(n: String) = c.getLong(c.getColumnIndexOrThrow(n))
            fun s(n: String) = c.getString(c.getColumnIndexOrThrow(n)) ?: ""
            State(i("recording") == 1, i("paused") == 1, l("elapsed_ms"), s("last_id"), s("last_title"), l("last_duration_ms"), s("last_status"), i("count"))
        }
    }.getOrNull()

    /** One tap starts, the next stops: the app's exported foreground service, allowed because the launcher is in front. */
    fun toggle(context: Context) {
        val i = Intent("$PACKAGE.TOGGLE").setClassName(PACKAGE, "$PACKAGE.RecordService")
        runCatching { ContextCompat.startForegroundService(context, i) }
    }

    fun open(context: Context, id: String? = null) {
        val intent = when {
            !isInstalled(context) -> Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/funkypitt/readers-recorder"))
            id != null -> Intent(Intent.ACTION_VIEW, Uri.parse("content://$PACKAGE/recordings/$id")).setClassName(PACKAGE, "$PACKAGE.MainActivity")
            else -> context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

/**
 * The recorder tile: "● record" with the latest recording under it; while recording, the
 * running time and ■. One tap on the mark records, the next stops; the text opens the app
 * (the latest recording, so its transcript is one tap away).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecorderTileView(onLongPress: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val tick = rememberTick()
    val installed = ReadersRecorder.isInstalled(context)
    val generation = rememberProviderGeneration(ReadersRecorder.URI)
    var poll by remember { mutableIntStateOf(0) }
    val st by produceState<ReadersRecorder.State?>(null, generation, poll) { value = withContext(Dispatchers.IO) { ReadersRecorder.state(context) } }
    val recording = st?.recording == true
    // The provider's elapsed time is a snapshot; count on from it once a second while recording.
    var base by remember { mutableLongStateOf(0L) }
    var shown by remember { mutableLongStateOf(0L) }
    LaunchedEffect(st) { base = System.currentTimeMillis() - (st?.elapsedMs ?: 0L); shown = st?.elapsedMs ?: 0L }
    LaunchedEffect(recording, st?.paused) {
        while (recording && st?.paused != true) { shown = System.currentTimeMillis() - base; delay(1000L - System.currentTimeMillis() % 1000) }
    }
    LaunchedEffect(recording) { while (recording) { delay(5_000); poll++ } }
    val title = when {
        !installed -> "Reader's Recorder"
        recording -> stringResource(if (st?.paused == true) R.string.recorder_paused else R.string.recorder_recording) + " · " + clock(shown)
        else -> stringResource(R.string.recorder_record)
    }
    val caption = when {
        !installed -> stringResource(R.string.widget_recorder)
        recording -> stringResource(R.string.recorder_tap_stop)
        st == null || st!!.count == 0 -> stringResource(R.string.widget_recorder)
        else -> st!!.lastTitle + " · " + clock(st!!.lastDurationMs)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(widgetTwoLineHeight())
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!installed || recording) ReadersRecorder.open(context) else ReadersRecorder.open(context, st?.lastId?.takeIf { it.isNotBlank() }) },
                onLongClick = onLongPress
            )
            .padding(horizontal = rowPadH, vertical = rowPadV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            T(title, maxLines = 1, color = if (installed) colors.fg else colors.dim)
            Small(caption, maxLines = 1)
        }
        if (installed) {
            Box(
                Modifier.padding(start = 16.dp).background(if (recording) colors.fg else Color.Transparent)
                    .noRippleClickable { tick(); ReadersRecorder.toggle(context); poll++ }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                T(if (recording) "■" else "●", size = typo.title, color = if (recording) colors.bg else colors.fg, align = TextAlign.Center)
            }
        }
    }
}


/**
 * The listen tile: one recording, newest first; swipe left for the older ones, right to come
 * back. ▶ plays it through Reader's Recorder's player (❚❚ pauses), the text opens it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListenTileView(onLongPress: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val tick = rememberTick()
    val installed = ReadersRecorder.isInstalled(context)
    val genList = rememberProviderGeneration(ReadersRecorder.RECORDINGS)
    val genState = rememberProviderGeneration(ReadersRecorder.URI)
    val recs by produceState<List<ReadersRecorder.Rec>>(emptyList(), genList) { value = withContext(Dispatchers.IO) { ReadersRecorder.recordings(context) } }
    val pb by produceState<ReadersRecorder.Playback?>(null, genState) { value = withContext(Dispatchers.IO) { ReadersRecorder.playback(context) } }
    var index by remember { mutableIntStateOf(0) }
    if (index >= recs.size) index = maxOf(0, recs.size - 1)
    val r = recs.getOrNull(index)
    val p = pb
    val isThis = r != null && p != null && p.id == r.id
    val playing = isThis && p!!.playing
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playing) { while (playing) { now = System.currentTimeMillis(); delay(1000L - now % 1000) } }
    val position = when { !isThis -> 0L; playing -> p!!.positionMs + (now - p.at); else -> p!!.positionMs }
    val title = when { !installed -> "Reader's Recorder"; r == null -> stringResource(R.string.listen_none); else -> r.title }
    val caption = when {
        !installed || r == null -> stringResource(R.string.widget_listen)
        isThis -> clock(position.coerceIn(0L, p!!.durationMs)) + " / " + clock(p.durationMs)
        else -> stringResource(R.string.widget_listen) + (if (recs.size > 1) " ${index + 1}/${recs.size}" else "") + " · " +
            (if (r.title.trim() != r.whenLabel) r.whenLabel + " · " else "") + clock(r.durationMs)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(widgetTwoLineHeight())
            // Swipe left → the recording before this one, right → the more recent; consumed here so the
            // home screen does not read it as a page or book-slot swipe.
            .pointerInput(recs.size) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (abs(total) > 60.dp.toPx()) {
                            val next = if (total < 0) index + 1 else index - 1
                            if (next in recs.indices) { index = next; tick() }
                        }
                    },
                    onHorizontalDrag = { change, delta -> total += delta; change.consume() }
                )
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { ReadersRecorder.open(context, r?.id) },
                onLongClick = onLongPress
            )
            .padding(horizontal = rowPadH, vertical = rowPadV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            T(title, maxLines = 1, color = if (r != null) colors.fg else colors.dim)
            Small(caption, maxLines = 1)
        }
        if (installed && r != null) {
            Box(
                Modifier.padding(start = 16.dp).background(if (playing) colors.fg else Color.Transparent)
                    .noRippleClickable { tick(); ReadersRecorder.togglePlay(context, r.id) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                T(if (playing) "❚❚" else "▶", size = typo.title, color = if (playing) colors.bg else colors.fg, align = TextAlign.Center)
            }
        }
    }
}
