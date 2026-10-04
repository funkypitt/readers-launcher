package com.freedomfighter.readerslauncher.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reader's Night Filter: its state, and its switch, through the app's provider. */
object ReadersNight {
    const val PACKAGE = "com.freedomfighter.readersnight"
    val URI: Uri = Uri.parse("content://$PACKAGE/state")

    /**
     * [allowed] is false until the app has received its permission from a computer. [dim] is
     * the dimming (0 = off, then 30, 60, 90); [canDim] is false when the phone has no dimming
     * or the app is a version that cannot change it from here.
     */
    data class State(val on: Boolean, val allowed: Boolean, val dim: Int = 0, val canDim: Boolean = false)

    fun isInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    fun state(context: Context): State? = runCatching {
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null
            else {
                val canDim = c.getColumnIndex("can_dim")
                State(
                    c.getInt(c.getColumnIndexOrThrow("on")) == 1, c.getInt(c.getColumnIndexOrThrow("allowed")) == 1,
                    c.getInt(c.getColumnIndexOrThrow("dim")), canDim >= 0 && c.getInt(canDim) == 1
                )
            }
        }
    }.getOrNull()

    /** False when the app could not switch: it is opened instead, where the missing step is explained. */
    fun toggle(context: Context): Boolean =
        runCatching { context.contentResolver.call(URI, "toggle", null, null)?.getBoolean("done") == true }.getOrDefault(false)

    /** The dimming moved one step (off, light, medium, strong). False when the app could not. */
    fun cycleDim(context: Context): Boolean =
        runCatching { context.contentResolver.call(URI, "dim", null, null)?.getBoolean("done") == true }.getOrDefault(false)

    /** The app; the project page when it is not installed. */
    fun open(context: Context) {
        val i = if (isInstalled(context)) Intent().setClassName(PACKAGE, "$PACKAGE.MainActivity")
        else Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/funkypitt/readers-night"))
        runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/**
 * The night filter tile: the state in words, then the dimming, which a tap moves one step,
 * then the switch; the words open the app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NightTileView(onLongPress: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val installed = ReadersNight.isInstalled(context)
    val generation = rememberProviderGeneration(ReadersNight.URI)
    var poll by remember { mutableIntStateOf(0) }
    val st by produceState<ReadersNight.State?>(null, generation, poll) { value = withContext(Dispatchers.IO) { ReadersNight.state(context) } }
    val on = st?.on == true
    Row(
        Modifier
            .fillMaxWidth()
            .height(widgetTwoLineHeight())
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { ReadersNight.open(context) },
                onLongClick = onLongPress
            )
            .padding(horizontal = rowPadH, vertical = rowPadV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            T(
                if (!installed) "Reader's Night Filter" else stringResource(if (on) R.string.night_on else R.string.night_off),
                maxLines = 1, color = if (installed) colors.fg else colors.dim
            )
            Small(stringResource(if (st?.allowed == false) R.string.night_setup else R.string.widget_night), maxLines = 1)
        }
        if (installed && st?.canDim == true) {
            val dim = st?.dim ?: 0
            Column(
                Modifier.padding(start = 16.dp).border(1.dp, colors.fg)
                    .noRippleClickable {
                        tick()
                        scope.launch {
                            val done = withContext(Dispatchers.IO) { ReadersNight.cycleDim(context) }
                            if (done) poll++ else ReadersNight.open(context)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Small(stringResource(R.string.night_dim), maxLines = 1, align = TextAlign.Center)
                Small(
                    stringResource(when {
                        dim <= 0 -> R.string.night_dim_off
                        dim <= 30 -> R.string.night_dim_light
                        dim <= 60 -> R.string.night_dim_medium
                        else -> R.string.night_dim_strong
                    }),
                    color = colors.fg, maxLines = 1, align = TextAlign.Center
                )
            }
        }
        if (installed) {
            Box(
                Modifier.padding(start = 16.dp).background(if (on) colors.fg else Color.Transparent)
                    .noRippleClickable {
                        tick()
                        scope.launch {
                            val done = withContext(Dispatchers.IO) { ReadersNight.toggle(context) }
                            if (done) poll++ else ReadersNight.open(context)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                T(if (on) "●" else "○", size = typo.title, color = if (on) colors.bg else colors.fg, align = TextAlign.Center)
            }
        }
    }
}
