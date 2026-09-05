package com.freedomfighter.readerslauncher.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.freedomfighter.readerslauncher.ui.LocalTypo
import com.freedomfighter.readerslauncher.ui.Small
import com.freedomfighter.readerslauncher.ui.T
import com.freedomfighter.readerslauncher.ui.rowPadH
import com.freedomfighter.readerslauncher.ui.rowPadV
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import android.provider.AlarmClock

/** Emits the current time every time the minute changes. */
@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            val t = System.currentTimeMillis()
            value = t
            delay(60_000 - t % 60_000 + 50)
        }
    }
    return now
}

@Composable
fun timeString(now: Long): String {
    val ctx = LocalContext.current
    val fmt = remember(android.text.format.DateFormat.is24HourFormat(ctx)) {
        if (android.text.format.DateFormat.is24HourFormat(ctx)) SimpleDateFormat("HH:mm", Locale.getDefault())
        else SimpleDateFormat("h:mm", Locale.getDefault())
    }
    return fmt.format(Date(now))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClockTileView(onLongPress: () -> Unit) {
    val context = LocalContext.current
    val now = rememberNow()
    val date = remember(now / 60_000) { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(now)) }
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
                onLongClick = onLongPress
            )
            .padding(horizontal = rowPadH, vertical = rowPadV)
    ) {
        T(timeString(now), size = LocalTypo.current.big, lineHeightMul = 1.05f)
        Small(date.lowercase(), maxLines = 1)
    }
}
