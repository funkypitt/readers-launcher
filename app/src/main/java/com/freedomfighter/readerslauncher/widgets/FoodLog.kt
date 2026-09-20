package com.freedomfighter.readerslauncher.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.ui.LocalColors
import com.freedomfighter.readerslauncher.ui.Small
import com.freedomfighter.readerslauncher.ui.T
import com.freedomfighter.readerslauncher.ui.rememberTick
import com.freedomfighter.readerslauncher.ui.rowPadH
import com.freedomfighter.readerslauncher.ui.rowPadV
import com.freedomfighter.readerslauncher.ui.widgetTwoLineHeight

/** Reader's Food Log: the launcher only offers its one gesture, a new photo. */
object ReadersFoodLog {
    const val PACKAGE = "com.freedomfighter.readersfoodlog"

    fun isInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /** Straight to the app's viewfinder; the project page when the app is not installed. */
    fun photograph(context: Context) {
        val i = if (isInstalled(context)) Intent().setClassName(PACKAGE, "$PACKAGE.CaptureActivity")
        else Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/funkypitt/readers-foodlog"))
        runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** The food log tile is a shortcut and nothing else: touch it, the viewfinder is open. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodLogTileView(onLongPress: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val tick = rememberTick()
    val installed = ReadersFoodLog.isInstalled(context)
    Row(
        Modifier
            .fillMaxWidth()
            .height(widgetTwoLineHeight())
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { tick(); ReadersFoodLog.photograph(context) },
                onLongClick = onLongPress
            )
            .padding(horizontal = rowPadH, vertical = rowPadV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            T(if (installed) stringResource(R.string.food_photograph) else "Reader's Food Log", maxLines = 1, color = if (installed) colors.fg else colors.dim)
            Small(stringResource(R.string.widget_food), maxLines = 1)
        }
        if (installed) T("◉", align = TextAlign.Center)
    }
}
