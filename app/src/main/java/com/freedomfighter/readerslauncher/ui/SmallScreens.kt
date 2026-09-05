package com.freedomfighter.readerslauncher.ui

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.apps.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hidden apps: tap one to show it again. */
@Composable
fun HiddenScreen(nav: Nav, app: App) {
    val home by app.store.state.collectAsState()
    BackHandler { nav.pop() }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.hidden_title), onBack = { nav.pop() })
            if (home.hidden.isEmpty()) Small(stringResource(R.string.no_apps), Modifier.padding(rowPadH))
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                items(home.hidden, key = { it.key }) { ref ->
                    TextRow(home.labelFor(app, ref), secondary = stringResource(R.string.menu_unhide)) {
                        app.store.setHidden(ref, false)
                    }
                }
            }
        }
    }
}

/**
 * Text list of recently used apps ("open apps"), from UsageStatsManager.
 * Android gives no public API for the real recents stack, so this is the closest a
 * third-party launcher can get.
 */
@Composable
fun RecentsScreen(nav: Nav, app: App) {
    val context = LocalContext.current
    BackHandler { nav.pop() }
    val allowed = hasUsageAccess(context)
    val recents by produceState<List<AppEntry>?>(null, allowed) {
        value = if (allowed) withContext(Dispatchers.Default) { recentApps(context, app) } else emptyList()
    }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.recents_title), onBack = { nav.pop() })
            if (!allowed) {
                TextRow(stringResource(R.string.recents_permission), size = LocalTypo.current.title) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            } else if (recents?.isEmpty() == true) {
                Small(stringResource(R.string.recents_empty), Modifier.padding(rowPadH))
            }
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                items(recents ?: emptyList(), key = { it.ref.key }) { e ->
                    TextTile(e.label, onClick = { app.apps.launch(e.ref); nav.home() }, onLongPress = { app.apps.openAppInfo(e.ref) })
                }
            }
        }
    }
}

fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    else @Suppress("DEPRECATION") appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun recentApps(context: Context, app: App, max: Int = 12): List<AppEntry> {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = System.currentTimeMillis()
    val events = usm.queryEvents(now - 6 * 60 * 60 * 1000L, now)
    val last = HashMap<String, Long>()
    val ev = UsageEvents.Event()
    val fg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) UsageEvents.Event.ACTIVITY_RESUMED
    else @Suppress("DEPRECATION") UsageEvents.Event.MOVE_TO_FOREGROUND
    while (events.hasNextEvent()) {
        events.getNextEvent(ev)
        if (ev.eventType == fg) last[ev.packageName] = ev.timeStamp
    }
    last.remove(context.packageName)
    val byPackage = app.apps.apps.value.filter { !it.isWorkProfile }.groupBy { it.ref.packageName }
    return last.entries.sortedByDescending { it.value }
        .mapNotNull { byPackage[it.key]?.firstOrNull() }
        .take(max)
}
