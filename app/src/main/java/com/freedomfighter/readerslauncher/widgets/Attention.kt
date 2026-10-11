package com.freedomfighter.readerslauncher.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.ui.LocalColors
import com.freedomfighter.readerslauncher.ui.Small
import com.freedomfighter.readerslauncher.ui.T
import com.freedomfighter.readerslauncher.ui.rowPadH
import com.freedomfighter.readerslauncher.ui.rowPadV
import com.freedomfighter.readerslauncher.ui.widgetTwoLineHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reader's Attention: today in two lines, through the app's provider. */
object ReadersAttention {
    const val PACKAGE = "com.freedomfighter.readersattention"
    val URI: Uri = Uri.parse("content://$PACKAGE/today")

    /**
     * [allowed] is false until the app has been given "usage access"; then [line1] is the
     * reflex openings and time ("reflex 23 · 1 h 12") and [line2] the tools' time, both in
     * the app's words.
     */
    data class State(val allowed: Boolean, val line1: String, val line2: String)

    fun isInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    fun state(context: Context): State? = runCatching {
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null
            else State(
                c.getInt(c.getColumnIndexOrThrow("allowed")) == 1,
                c.getString(c.getColumnIndexOrThrow("line1")) ?: "",
                c.getString(c.getColumnIndexOrThrow("line2")) ?: ""
            )
        }
    }.getOrNull()

    /** The app; the project page when it is not installed. */
    fun open(context: Context) {
        val i = if (isInstalled(context)) Intent().setClassName(PACKAGE, "$PACKAGE.MainActivity")
        else Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/funkypitt/readers-attention"))
        runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/**
 * The attention tile: today's reflex openings and time on the first line, the tools' time
 * under it, as the app's own widget says them; a tap opens the app. Read again when the app
 * announces a change, when the home screen comes back, and every ten minutes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttentionTileView(onLongPress: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val installed = ReadersAttention.isInstalled(context)
    val generation = rememberProviderGeneration(ReadersAttention.URI)
    val now = rememberNow()
    // the day most often changed while another app was in front: read again when the home comes back
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumed++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val st by produceState<ReadersAttention.State?>(null, generation, resumed, now / (10 * 60_000)) {
        value = withContext(Dispatchers.IO) { ReadersAttention.state(context) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(widgetTwoLineHeight())
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { ReadersAttention.open(context) },
                onLongClick = onLongPress
            )
            .padding(horizontal = rowPadH, vertical = rowPadV),
        verticalArrangement = Arrangement.Center
    ) {
        val s = st
        when {
            !installed -> { T("Reader's Attention", maxLines = 1, color = colors.dim); Small(stringResource(R.string.widget_attention), maxLines = 1) }
            s == null -> { T(stringResource(R.string.widget_attention), maxLines = 1, color = colors.dim); Small("", maxLines = 1) }
            !s.allowed -> { T(s.line1, maxLines = 1, color = colors.dim); Small(stringResource(R.string.attention_setup), maxLines = 1) }
            else -> { T(s.line1, maxLines = 1); Small(s.line2 + " · " + stringResource(R.string.widget_attention), maxLines = 1) }
        }
    }
}
