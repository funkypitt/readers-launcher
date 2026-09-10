package com.freedomfighter.readerslauncher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.MainActivity
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.data.AppWidgetTile
import com.freedomfighter.readerslauncher.data.ClockTile
import com.freedomfighter.readerslauncher.data.WeatherTile
import com.freedomfighter.readerslauncher.data.WordTile
import com.freedomfighter.readerslauncher.data.BookTile
import com.freedomfighter.readerslauncher.data.NotesTile
import com.freedomfighter.readerslauncher.widgets.WeatherRepo

private data class ProviderRow(val appLabel: String, val info: AppWidgetProviderInfo) {
    val widgetLabel: String get() = info.label
}

/** "add widget": the four built-in text widgets first, then every app widget on the device. */
@Composable
fun WidgetPickerScreen(nav: Nav, app: App) {
    val context = LocalContext.current
    val activity = context.findActivity() as? MainActivity
    val typo = LocalTypo.current
    BackHandler { nav.pop() }
    var query by remember { mutableStateOf("") }
    var pendingBind by remember { mutableStateOf<Pair<Int, AppWidgetProviderInfo>?>(null) }

    val providers = remember {
        val pm = context.packageManager
        app.widgetManager.installedProviders.map { info ->
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }
                .getOrDefault(info.provider.packageName)
            ProviderRow(label, info)
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.widgetLabel.lowercase() }))
    }
    val filtered = remember(query, providers) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) providers else providers.filter { it.appLabel.lowercase().contains(q) || it.widgetLabel.lowercase().contains(q) }
    }

    val cellDp = cellHeight().value
    fun finishAdd(id: Int, info: AppWidgetProviderInfo) {
        val label = runCatching { info.loadLabel(context.packageManager) }.getOrDefault("widget")
        val minDp = info.minHeight / context.resources.displayMetrics.density
        val cells = kotlin.math.ceil(minDp / cellDp).toInt().coerceIn(2, 12)
        val used = app.store.state.value.page(app.store.currentPage.value).sumOf { tileCells(it) }
        if (app.pageCells > 0 && used + cells > app.pageCells) {
            app.widgetHost.deleteAppWidgetId(id)
            Toast.makeText(context, R.string.hint_full, Toast.LENGTH_SHORT).show()
            nav.pop(); return
        }
        app.store.addTile(AppWidgetTile(appWidgetId = id, label = label, cells = cells))
        nav.pop()
    }

    fun configureOrAdd(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null && activity != null) {
            activity.configureWidget(id) { ok ->
                if (ok) finishAdd(id, info) else app.widgetHost.deleteAppWidgetId(id)
            }
        } else finishAdd(id, info)
    }

    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val p = pendingBind ?: return@rememberLauncherForActivityResult
        pendingBind = null
        if (result.resultCode == android.app.Activity.RESULT_OK) configureOrAdd(p.first, p.second)
        else app.widgetHost.deleteAppWidgetId(p.first)
    }

    fun addAppWidget(info: AppWidgetProviderInfo) {
        val id = app.widgetHost.allocateAppWidgetId()
        val bound = runCatching {
            app.widgetManager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
        }.getOrDefault(false)
        if (bound) {
            configureOrAdd(id, info)
        } else {
            pendingBind = id to info
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            }
            runCatching { bindLauncher.launch(intent) }.onFailure {
                app.widgetHost.deleteAppWidgetId(id)
                Toast.makeText(context, R.string.widget_bind_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.menu_add_widget), onBack = { nav.pop() })
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Small(stringResource(R.string.widgets_builtin), Modifier.padding(horizontal = rowPadH, vertical = 10.dp)) }
                item {
                    TextRow(stringResource(R.string.widget_clock), secondary = stringResource(R.string.widget_clock_desc)) {
                        app.store.addTile(ClockTile()); nav.pop()
                    }
                }
                item {
                    TextRow(stringResource(R.string.widget_weather), secondary = stringResource(R.string.widget_weather_desc)) {
                        app.store.addTile(WeatherTile())
                        WeatherRepo.get(context).requestLocationPermission(activity)
                        nav.pop()
                    }
                }
                item {
                    TextRow(stringResource(R.string.widget_word), secondary = stringResource(R.string.widget_word_desc)) {
                        app.store.addTile(WordTile()); nav.pop()
                    }
                }
                item {
                    TextRow(stringResource(R.string.widget_book), secondary = stringResource(R.string.widget_book_desc)) {
                        app.store.addTile(BookTile()); nav.pop()
                    }
                }
                item {
                    TextRow(stringResource(R.string.widget_notes), secondary = stringResource(R.string.widget_notes_desc)) {
                        app.store.addTile(NotesTile()); nav.pop()
                    }
                }
                item {
                    TextRow(stringResource(R.string.widget_recorder), secondary = stringResource(R.string.widget_recorder_desc)) {
                        app.store.addTile(com.freedomfighter.readerslauncher.data.RecorderTile()); nav.pop()
                    }
                }
                item {
                    TextRow(stringResource(R.string.widget_mindful), secondary = stringResource(R.string.widget_mindful_desc)) {
                        nav.replace(Screen.MindfulSetup(null))
                    }
                }
                item {
                    TextRow(stringResource(R.string.widget_calendar), secondary = stringResource(R.string.widget_calendar_desc)) {
                        nav.replace(Screen.CalendarSetup(null))
                    }
                }
                item {
                    TextRow(stringResource(R.string.widget_tasks), secondary = stringResource(R.string.widget_tasks_desc)) {
                        nav.replace(Screen.TasksSetup(null))
                    }
                }
                item { Rule(Modifier.padding(top = 8.dp)) }
                item { Small(stringResource(R.string.widgets_system), Modifier.padding(horizontal = rowPadH, vertical = 10.dp)) }
                item {
                    ReaderTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.padding(horizontal = rowPadH, vertical = 6.dp),
                        placeholder = stringResource(R.string.search)
                    )
                }
                items(filtered, key = { it.info.provider.flattenToString() + it.info.profile.hashCode() }) { row ->
                    TextRow(row.widgetLabel, secondary = row.appLabel, size = typo.title) { addAppWidget(row.info) }
                }
            }
        }
    }
}
