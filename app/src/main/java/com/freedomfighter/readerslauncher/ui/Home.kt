package com.freedomfighter.readerslauncher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.data.AppRef
import com.freedomfighter.readerslauncher.data.AppTile
import com.freedomfighter.readerslauncher.data.AppWidgetTile
import com.freedomfighter.readerslauncher.data.CalendarTile
import com.freedomfighter.readerslauncher.data.CategoryTile
import com.freedomfighter.readerslauncher.data.ClockTile
import com.freedomfighter.readerslauncher.data.Grid
import com.freedomfighter.readerslauncher.data.HomeState
import com.freedomfighter.readerslauncher.data.TasksTile
import com.freedomfighter.readerslauncher.data.Tile
import com.freedomfighter.readerslauncher.data.WeatherTile
import com.freedomfighter.readerslauncher.widgets.AppWidgetTileView
import com.freedomfighter.readerslauncher.widgets.CalendarTileView
import com.freedomfighter.readerslauncher.widgets.ClockTileView
import com.freedomfighter.readerslauncher.widgets.TasksTileView
import com.freedomfighter.readerslauncher.widgets.WeatherTileView
import com.freedomfighter.readerslauncher.widgets.appWidgetMenuItems
import com.freedomfighter.readerslauncher.widgets.weatherMenuItems
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Label for an app, honouring user renames; falls back to the package name if the app vanished. */
fun HomeState.labelFor(app: App, ref: AppRef): String =
    renames[ref.key] ?: app.apps.entry(ref)?.label ?: ref.packageName.substringAfterLast('.')

internal sealed class HomeMenu {
    data object Empty : HomeMenu()
    data class ForTile(val tile: Tile) : HomeMenu()
    data class GridSlot(val index: Int) : HomeMenu()
}

internal sealed class HomePrompt {
    data class Rename(val ref: AppRef, val current: String) : HomePrompt()
    data class RenameCategory(val tile: CategoryTile) : HomePrompt()
    data class NewCategory(val apps: List<AppRef>) : HomePrompt()
    data class WeatherCity(val tile: WeatherTile) : HomePrompt()
}

/**
 * Transient home UI state. Lives above the screen switch so a prompt requested from another
 * screen (e.g. "name this category" after the picker) survives the navigation.
 */
class HomeUi {
    internal var menu by mutableStateOf<HomeMenu?>(null)
    internal var prompt by mutableStateOf<HomePrompt?>(null)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(nav: Nav, app: App, ui: HomeUi) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val state by app.store.state.collectAsState()
    val settings by app.prefs.settings.collectAsState()
    val colors = LocalColors.current
    val systemDark = isSystemInDarkTheme()
    val tick = rememberTick()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var menu by ui::menu
    var prompt by ui::prompt
    var isDefault by remember { mutableStateOf(isDefaultLauncher(context)) }

    // Re-check the default launcher and refresh widget data whenever we come back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) isDefault = isDefaultLauncher(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    fun openDrawer() = nav.push(Screen.Apps(PickMode.Drawer))
    fun openRecents() = nav.push(Screen.Recents)

    fun addAppsFlow() {
        nav.push(Screen.Apps(PickMode.Multi { picked ->
            nav.pop()
            when (picked.size) {
                0 -> Unit
                1 -> app.store.addTile(AppTile(app = picked[0]))
                else -> prompt = HomePrompt.NewCategory(picked)
            }
        }))
    }

    val swipeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 90.dp.toPx() }
    val stillPx = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.toPx() }

    Page {
        Box(
            Modifier
                .fillMaxSize()
                // Vertical swipes observed in the Initial pass so the tile list still scrolls normally.
                .pointerInput(settings.swipeDownNotifications) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val startY = down.position.y
                        val couldScrollDown = listState.canScrollForward
                        val couldScrollUp = listState.canScrollBackward
                        var lastY = startY
                        var lastMove = down.uptimeMillis
                        var fired = false
                        while (true) {
                            val event = withTimeoutOrNull(120L) { awaitPointerEvent(PointerEventPass.Initial) }
                            val now = System.currentTimeMillis()
                            if (event == null) {
                                // No movement for a while: "swipe up and hold" → open apps list.
                                if (!fired && startY - lastY > swipeThresholdPx * 0.6f &&
                                    android.os.SystemClock.uptimeMillis() - lastMove > 350
                                ) {
                                    fired = true
                                    tick()
                                    openRecents()
                                }
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                val dy = change.position.y - startY
                                if (!fired) {
                                    if (dy < -swipeThresholdPx && !couldScrollDown) openDrawer()
                                    else if (dy > swipeThresholdPx && !couldScrollUp && settings.swipeDownNotifications)
                                        expandNotifications(context)
                                }
                                break
                            }
                            if (abs(change.position.y - lastY) > stillPx) {
                                lastY = change.position.y
                                lastMove = change.uptimeMillis
                            }
                            if (fired) change.consume()
                            @Suppress("UNUSED_VARIABLE") val unused = now
                        }
                    }
                }
                // Horizontal swipe → the book on that side. Observed in the Final pass so a tile
                // that consumed the drag (the agenda) keeps it.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                        val slop = viewConfiguration.touchSlop
                        var childHasIt = false
                        var horizontal = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (!horizontal && change.isConsumed) childHasIt = true
                            if (!childHasIt && !horizontal && abs(dx) > slop && abs(dx) > 2 * abs(dy)) horizontal = true
                            // Consuming here (Final pass) cancels the tiles' pending clicks.
                            if (horizontal) change.consume()
                            if (!change.pressed) {
                                if (horizontal && abs(dx) > swipeThresholdPx) {
                                    tick()
                                    nav.push(Screen.Book(if (dx > 0) 0 else 1))
                                }
                                break
                            }
                        }
                    }
                }
                .pointerInput(settings.doubleTapTheme) {
                    detectTapGestures(
                        onLongPress = { tick(); menu = HomeMenu.Empty },
                        onDoubleTap = { if (settings.doubleTapTheme) { tick(); app.prefs.toggleTheme(systemDark) } }
                    )
                }
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.windowInsetsPadding(WindowInsets.statusBars))
                if (!isDefault) {
                    TextRow(
                        stringResource(R.string.hint_set_default),
                        size = LocalTypo.current.small,
                        onClick = { openHomeSettings(context) }
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    // Generous bottom padding keeps a long-pressable empty zone under the last tile.
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 96.dp)
                ) {
                    items(state.tiles, key = { it.id }) { tile ->
                        TileView(
                            tile = tile,
                            app = app,
                            state = state,
                            nav = nav,
                            onLongPress = { tick(); menu = HomeMenu.ForTile(tile) },
                            onNeedCity = { prompt = HomePrompt.WeatherCity(it) }
                        )
                    }
                }
                state.grid?.let { grid ->
                    GridBar(
                        grid = grid,
                        app = app,
                        onLongPress = { i -> tick(); menu = HomeMenu.GridSlot(i) }
                    )
                }
                Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }

            if (state.tiles.isEmpty() && state.grid == null) {
                Small(
                    stringResource(R.string.hint_empty),
                    Modifier.align(Alignment.Center),
                    align = TextAlign.Center
                )
            }
        }

        // ---- menus ----------------------------------------------------------------------
        val homeItems = buildList {
            add(MenuItem(stringResource(R.string.menu_add_app)) { addAppsFlow() })
            if (state.grid == null) add(MenuItem(stringResource(R.string.menu_add_grid)) { app.store.setGrid(Grid()) })
            add(MenuItem(stringResource(R.string.menu_add_widget)) { nav.push(Screen.WidgetPicker) })
            add(MenuItem(stringResource(R.string.menu_settings)) { nav.push(Screen.Settings) })
        }
        when (val m = menu) {
            null -> Unit
            HomeMenu.Empty -> TextMenu(
                title = null,
                items = homeItems,
                onDismiss = { menu = null }
            )
            is HomeMenu.ForTile -> TextMenu(
                title = tileTitle(m.tile, state, app),
                items = tileMenuItems(
                    tile = m.tile, app = app, state = state, nav = nav, activity = activity,
                    onPrompt = { prompt = it }
                ),
                onDismiss = { menu = null },
                // When the column fills the screen there is no empty space left to long-press.
                footer = homeItems
            )
            is HomeMenu.GridSlot -> {
                val grid = state.grid ?: Grid()
                val slot = grid.slot(m.index)
                TextMenu(
                    title = slot?.let { state.labelFor(app, it) },
                    items = buildList {
                        if (slot == null) {
                            add(MenuItem(stringResource(R.string.menu_add_app)) {
                                nav.push(Screen.Apps(PickMode.Single { ref ->
                                    nav.pop(); app.store.setGrid((app.store.state.value.grid ?: Grid()).withSlot(m.index, ref))
                                }))
                            })
                        } else {
                            add(MenuItem(stringResource(R.string.menu_open)) { app.apps.launch(slot) })
                            add(MenuItem(stringResource(R.string.menu_change_app)) {
                                nav.push(Screen.Apps(PickMode.Single { ref ->
                                    nav.pop(); app.store.setGrid((app.store.state.value.grid ?: Grid()).withSlot(m.index, ref))
                                }))
                            })
                            add(MenuItem(stringResource(R.string.menu_clear_slot)) { app.store.setGrid(grid.withSlot(m.index, null)) })
                            add(MenuItem(stringResource(R.string.menu_app_info)) { app.apps.openAppInfo(slot) })
                        }
                        val next = if (grid.columns >= 5) 3 else grid.columns + 1
                        add(MenuItem(stringResource(R.string.menu_columns, grid.columns), "→ $next") {
                            app.store.setGrid(grid.copy(columns = next))
                        })
                        add(MenuItem(stringResource(R.string.menu_remove)) { app.store.setGrid(null) })
                    },
                    onDismiss = { menu = null }
                )
            }
        }

        // ---- prompts --------------------------------------------------------------------
        when (val p = prompt) {
            null -> Unit
            is HomePrompt.Rename -> TextPrompt(
                title = stringResource(R.string.menu_rename), initial = p.current,
                onDone = { app.store.rename(p.ref, it); prompt = null }, onCancel = { prompt = null }
            )
            is HomePrompt.RenameCategory -> TextPrompt(
                title = stringResource(R.string.category_name_prompt), initial = p.tile.name,
                onDone = { app.store.replaceTile(p.tile.copy(name = it)); prompt = null }, onCancel = { prompt = null }
            )
            is HomePrompt.NewCategory -> TextPrompt(
                title = stringResource(R.string.category_name_prompt),
                confirm = stringResource(R.string.action_done),
                onDone = { app.store.addTile(CategoryTile(name = it, apps = p.apps)); prompt = null },
                onCancel = { prompt = null }
            )
            is HomePrompt.WeatherCity -> TextPrompt(
                title = stringResource(R.string.weather_city_prompt), initial = p.tile.place?.name ?: "",
                onDone = { query ->
                    prompt = null
                    com.freedomfighter.readerslauncher.widgets.WeatherRepo.get(context).chooseCity(scope, query) { place ->
                        if (place != null) app.store.replaceTile(p.tile.copy(place = place))
                    }
                },
                onCancel = { prompt = null }
            )
        }
    }
}

@Composable
private fun tileTitle(tile: Tile, state: HomeState, app: App): String? = when (tile) {
    is AppTile -> state.labelFor(app, tile.app)
    is CategoryTile -> tile.name
    is ClockTile -> stringResource(R.string.widget_clock)
    is WeatherTile -> stringResource(R.string.widget_weather)
    is CalendarTile -> stringResource(R.string.widget_calendar)
    is TasksTile -> stringResource(R.string.widget_tasks) + " · " + tile.listTitle
    is AppWidgetTile -> tile.label.ifBlank { null }
}

@Composable
private fun tileMenuItems(
    tile: Tile,
    app: App,
    state: HomeState,
    nav: Nav,
    activity: Activity?,
    onPrompt: (HomePrompt) -> Unit
): List<MenuItem> {
    val context = LocalContext.current
    val index = state.tiles.indexOfFirst { it.id == tile.id }
    val moveItems = buildList {
        if (index > 0) add(MenuItem(stringResource(R.string.menu_move_up)) { app.store.moveTile(tile.id, -1) })
        if (index < state.tiles.size - 1) add(MenuItem(stringResource(R.string.menu_move_down)) { app.store.moveTile(tile.id, 1) })
        if (state.tiles.size > 2) add(MenuItem(stringResource(R.string.menu_arrange)) { nav.push(Screen.Arrange) })
    }
    val remove = MenuItem(stringResource(R.string.menu_remove)) {
        if (tile is AppWidgetTile) app.widgetHost.deleteAppWidgetId(tile.appWidgetId)
        app.store.removeTile(tile.id)
    }
    return when (tile) {
        is AppTile -> buildList {
            add(MenuItem(stringResource(R.string.menu_open)) { app.apps.launch(tile.app) })
            add(MenuItem(stringResource(R.string.menu_rename)) { onPrompt(HomePrompt.Rename(tile.app, state.labelFor(app, tile.app))) })
            addAll(moveItems)
            add(MenuItem(stringResource(R.string.menu_app_info)) { app.apps.openAppInfo(tile.app) })
            if (app.apps.entry(tile.app)?.isSystem == false && activity != null)
                add(MenuItem(stringResource(R.string.menu_uninstall)) { app.apps.uninstall(activity, tile.app) })
            add(remove)
        }
        is CategoryTile -> buildList {
            add(MenuItem(stringResource(R.string.menu_open)) { nav.push(Screen.Category(tile.id)) })
            add(MenuItem(stringResource(R.string.menu_edit)) { nav.push(Screen.CategoryEdit(tile.id)) })
            add(MenuItem(stringResource(R.string.menu_rename)) { onPrompt(HomePrompt.RenameCategory(tile)) })
            addAll(moveItems)
            add(remove)
        }
        is ClockTile -> moveItems + remove
        is WeatherTile -> buildList {
            addAll(weatherMenuItems(tile, app, context, onChooseCity = { onPrompt(HomePrompt.WeatherCity(tile)) }))
            addAll(moveItems)
            add(remove)
        }
        is CalendarTile -> buildList {
            add(MenuItem(stringResource(R.string.menu_calendars)) { nav.push(Screen.CalendarSetup(tile.id)) })
            addAll(moveItems)
            add(remove)
        }
        is TasksTile -> buildList {
            add(MenuItem(stringResource(R.string.menu_configure)) { nav.push(Screen.TasksSetup(tile.id)) })
            addAll(moveItems)
            add(remove)
        }
        is AppWidgetTile -> buildList {
            addAll(appWidgetMenuItems(tile, app, activity))
            addAll(moveItems)
            add(remove)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileView(
    tile: Tile,
    app: App,
    state: HomeState,
    nav: Nav,
    onLongPress: () -> Unit,
    onNeedCity: (WeatherTile) -> Unit
) {
    when (tile) {
        is AppTile -> TextTile(state.labelFor(app, tile.app), onClick = { app.apps.launch(tile.app) }, onLongPress = onLongPress)
        is CategoryTile -> TextTile(tile.name, onClick = { nav.push(Screen.Category(tile.id)) }, onLongPress = onLongPress)
        is ClockTile -> ClockTileView(onLongPress)
        is WeatherTile -> WeatherTileView(tile, app, onLongPress, onNeedCity)
        is CalendarTile -> CalendarTileView(tile, app, onLongPress)
        is TasksTile -> TasksTileView(tile, app, onLongPress, onSetup = { nav.push(Screen.TasksSetup(tile.id)) })
        is AppWidgetTile -> AppWidgetTileView(tile, app, onLongPress)
    }
}

/** A plain word. This is what most of the home screen is made of. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextTile(label: String, onClick: () -> Unit, onLongPress: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(horizontal = rowPadH, vertical = rowPadV)
    ) {
        T(label, Modifier.fillMaxWidth(), maxLines = 1)
    }
}

fun isDefaultLauncher(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val res = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
    return res?.activityInfo?.packageName == context.packageName
}

fun openHomeSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_HOME_SETTINGS),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (i in intents) {
        try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        } catch (_: Exception) { }
    }
}

@Suppress("DEPRECATION")
fun expandNotifications(context: Context) {
    try {
        @android.annotation.SuppressLint("WrongConstant")
        val service = context.getSystemService("statusbar") ?: return
        val cls = Class.forName("android.app.StatusBarManager")
        cls.getMethod("expandNotificationsPanel").invoke(service)
    } catch (_: Exception) { }
}
