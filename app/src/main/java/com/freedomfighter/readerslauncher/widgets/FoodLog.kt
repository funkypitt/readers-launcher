package com.freedomfighter.readerslauncher.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.withContext

/** Reader's Food Log: today's counts (signature-protected provider) and its three gestures. */
object ReadersFoodLog {
    const val PACKAGE = "com.freedomfighter.readersfoodlog"
    val URI: Uri = Uri.parse("content://$PACKAGE/today")

    data class Today(val photos: String, val activities: String, val weight: String)

    fun isInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /** Null when the app is absent or refuses the read (different signing key): the gestures still work. */
    fun today(context: Context): Today? = runCatching {
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            fun s(n: String) = c.getString(c.getColumnIndexOrThrow(n)) ?: ""
            Today(s("photos_label"), s("activities_label"), s("weight_label"))
        }
    }.getOrNull()

    private fun start(context: Context, intent: Intent) {
        val i = if (isInstalled(context)) intent else Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/funkypitt/readers-foodlog"))
        runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun open(context: Context) = start(context, context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: Intent())
    fun photograph(context: Context) = start(context, Intent().setClassName(PACKAGE, "$PACKAGE.CaptureActivity"))
    fun activity(context: Context) = start(context, Intent("$PACKAGE.ADD_ACTIVITY").setClassName(PACKAGE, "$PACKAGE.MainActivity"))
    fun weight(context: Context) = start(context, Intent("$PACKAGE.WEIGHT").setClassName(PACKAGE, "$PACKAGE.MainActivity"))
}

/**
 * The food log tile: today's photos on the first line, activities and weight under it; on the
 * right ✎ notes an activity, ⚖ asks for the weight and ◉ opens the viewfinder. The text opens the app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodLogTileView(onLongPress: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val tick = rememberTick()
    val installed = ReadersFoodLog.isInstalled(context)
    val generation = rememberProviderGeneration(ReadersFoodLog.URI)
    // Coming back from the viewfinder: the provider's notification can lag by seconds while the
    // launcher was cached, so every return to the home screen reads again; the minute covers midnight.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val minute = rememberNow() / 60_000
    val today by produceState<ReadersFoodLog.Today?>(null, generation, resumes, minute) { value = withContext(Dispatchers.IO) { ReadersFoodLog.today(context) } }
    val t = today
    val title = when {
        !installed -> "Reader's Food Log"
        t == null || t.photos.isEmpty() -> stringResource(R.string.food_nothing)
        else -> t.photos
    }
    val caption = listOfNotNull(stringResource(R.string.widget_food), t?.activities?.takeIf { it.isNotEmpty() }, t?.weight?.takeIf { it.isNotEmpty() }).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .height(widgetTwoLineHeight())
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { ReadersFoodLog.open(context) },
                onLongClick = onLongPress
            )
            .padding(start = rowPadH, end = rowPadH - 10.dp, top = rowPadV, bottom = rowPadV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            T(title, maxLines = 1, color = if (installed) colors.fg else colors.dim)
            Small(caption, maxLines = 1)
        }
        if (installed) listOf<Pair<String, () -> Unit>>(
            "✎" to { ReadersFoodLog.activity(context) },
            "⚖" to { ReadersFoodLog.weight(context) },
            "◉" to { ReadersFoodLog.photograph(context) }
        ).forEach { (glyph, action) ->
            Box(Modifier.noRippleClickable { tick(); action() }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                T(glyph, size = if (glyph == "◉") typo.tile else typo.title, align = TextAlign.Center)
            }
        }
    }
}
