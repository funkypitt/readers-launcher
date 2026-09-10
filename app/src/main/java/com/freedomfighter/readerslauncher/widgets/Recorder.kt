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

    fun isInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

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
