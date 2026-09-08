package com.freedomfighter.readerslauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.data.AppTile
import com.freedomfighter.readerslauncher.data.AppWidgetTile
import com.freedomfighter.readerslauncher.data.CalendarTile
import com.freedomfighter.readerslauncher.data.CategoryTile
import com.freedomfighter.readerslauncher.data.ClockTile
import com.freedomfighter.readerslauncher.data.TasksTile
import com.freedomfighter.readerslauncher.data.Tile
import com.freedomfighter.readerslauncher.data.WeatherTile
import com.freedomfighter.readerslauncher.data.WordTile
import com.freedomfighter.readerslauncher.data.BookTile
import com.freedomfighter.readerslauncher.data.NotesTile

/** Drag tiles to reorder the home column. Widgets are shown by name here. */
@Composable
fun ArrangeScreen(nav: Nav, app: App) {
    val home by app.store.state.collectAsState()
    BackHandler { nav.pop() }
    val names = mapOf(
        "clock" to stringResource(R.string.widget_clock),
        "weather" to stringResource(R.string.widget_weather),
        "calendar" to stringResource(R.string.widget_calendar),
        "tasks" to stringResource(R.string.widget_tasks),
        "word" to stringResource(R.string.widget_word),
        "book" to stringResource(R.string.widget_book),
        "notes" to stringResource(R.string.widget_notes)
    )
    fun label(t: Tile): String = when (t) {
        is AppTile -> home.labelFor(app, t.app)
        is CategoryTile -> t.name
        is ClockTile -> "— " + names["clock"]
        is WordTile -> "— " + names["word"]
        is BookTile -> "— " + names["book"]
        is NotesTile -> "— " + names["notes"]
        is WeatherTile -> "— " + names["weather"]
        is CalendarTile -> "— " + names["calendar"]
        is TasksTile -> "— " + names["tasks"] + " · " + t.listTitle
        is AppWidgetTile -> "— " + t.label
    }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.menu_arrange), onBack = { nav.pop() })
            Small(stringResource(R.string.arrange_hint), Modifier.padding(horizontal = rowPadH, vertical = 10.dp))
            ReorderableList(
                items = home.tiles,
                key = { it.id },
                label = { label(it) },
                onReorder = { app.store.update { s -> s.copy(tiles = it) } },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
