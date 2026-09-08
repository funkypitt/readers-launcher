package com.freedomfighter.readerslauncher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.MainActivity
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.data.AppWidgetTile
import com.freedomfighter.readerslauncher.ui.MenuItem
import com.freedomfighter.readerslauncher.ui.TextTile
import com.freedomfighter.readerslauncher.ui.findActivity
import com.freedomfighter.readerslauncher.ui.rowPadH


/**
 * Wraps an AppWidgetHostView and steals a long press for the launcher's tile menu,
 * the same way stock launchers do (CheckLongPressHelper).
 */
private class LongPressHost(context: Context, private val onLongPress: () -> Unit) : FrameLayout(context) {
    private var downX = 0f
    private var downY = 0f
    private var pending = false
    /** Once the long press fired, the rest of the gesture is ours: the widget must not see a click. */
    private var swallowing = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val trigger = Runnable {
        if (pending) {
            pending = false
            swallowing = true
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPress()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; pending = true; swallowing = false
                postDelayed(trigger, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (pending && (Math.abs(ev.x - downX) > slop || Math.abs(ev.y - downY) > slop)) cancel()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancel()
        }
        return swallowing
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) swallowing = false
        return true
    }

    private fun cancel() { pending = false; removeCallbacks(trigger) }
}

@Composable
fun AppWidgetTileView(tile: AppWidgetTile, app: App, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val info = remember(tile.appWidgetId) { app.widgetManager.getAppWidgetInfo(tile.appWidgetId) }
    if (info == null) {
        TextTile(stringResource(R.string.widget_not_installed) + " · " + tile.label, onClick = {}, onLongPress = onLongPress)
        return
    }
    val heightDp = com.freedomfighter.readerslauncher.ui.cellHeight() * tile.cellCount()
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().height(heightDp).padding(horizontal = rowPadH / 2)) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(heightDp),
            factory = { ctx ->
                val host = LongPressHost(ctx, onLongPress)
                val view: AppWidgetHostView = app.widgetHost.createView(ctx.applicationContext, tile.appWidgetId, info)
                view.setPadding(0, 0, 0, 0)
                host.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                host
            },
            update = { host ->
                val view = host.getChildAt(0) as? AppWidgetHostView ?: return@AndroidView
                val wDp = context.resources.displayMetrics.widthPixels / density.density - rowPadH.value
                val hDp = heightDp.value
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    view.updateAppWidgetSize(Bundle.EMPTY, listOf(SizeF(wDp, hDp)))
                } else {
                    @Suppress("DEPRECATION")
                    view.updateAppWidgetSize(Bundle.EMPTY, wDp.toInt(), hDp.toInt(), wDp.toInt(), hDp.toInt())
                }
            }
        )
    }
}

@Composable
fun appWidgetMenuItems(tile: AppWidgetTile, app: App, activity: Activity?): List<MenuItem> = buildList {
    val info = app.widgetManager.getAppWidgetInfo(tile.appWidgetId)
    val next = if (tile.height >= 4) 1 else tile.height + 1
    add(MenuItem(stringResource(R.string.menu_height, "${tile.height}×"), "→ $next×") {
        app.store.replaceTile(tile.copy(height = next))
    })
    if (info?.configure != null && activity is MainActivity &&
        (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P ||
            info.widgetFeatures and android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0)
    ) {
        add(MenuItem(stringResource(R.string.menu_configure)) { activity.configureWidget(tile.appWidgetId) { } })
    }
}
