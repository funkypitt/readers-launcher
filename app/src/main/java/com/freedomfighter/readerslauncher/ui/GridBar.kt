package com.freedomfighter.readerslauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.apps.MonoIcon
import com.freedomfighter.readerslauncher.data.AppRef
import com.freedomfighter.readerslauncher.data.Grid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The "key apps" tile pinned at the bottom: 3–5 squares, each holding an app icon rendered
 * as a monochrome glyph in the foreground colour.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridBar(grid: Grid, app: App, onLongPress: (Int) -> Unit, onDoubleTap: ((AppRef) -> Unit)? = null) {
    val colors = LocalColors.current
    Column(Modifier.fillMaxWidth()) {
        Rule()
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            for (i in 0 until grid.columns) {
                val ref = grid.slot(i)
                val gestures = if (onDoubleTap != null && ref != null) Modifier.pointerInput(ref, i) {
                    detectTapGestures(
                        onTap = { app.apps.launch(ref) },
                        onLongPress = { onLongPress(i) },
                        onDoubleTap = { onDoubleTap(ref) }
                    )
                } else Modifier.combinedClickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = { if (ref != null) app.apps.launch(ref) else onLongPress(i) },
                    onLongClick = { onLongPress(i) }
                )
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .then(gestures),
                    contentAlignment = Alignment.Center
                ) {
                    if (ref == null) {
                        T("·", color = colors.rule, align = TextAlign.Center)
                    } else {
                        MonoAppIcon(ref, app, 40.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun MonoAppIcon(ref: AppRef, app: App, size: androidx.compose.ui.unit.Dp) {
    val colors = LocalColors.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val fg = colors.fg.toArgb()
    val bg = colors.bg.toArgb()
    val bitmap by produceState<ImageBitmap?>(null, ref, fg, px) {
        value = withContext(Dispatchers.Default) {
            app.apps.icon(ref)?.let { runCatching { MonoIcon.render(it, px, fg, bg) }.getOrNull() }
        }
    }
    val b = bitmap
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size).height(size))
    }
}
