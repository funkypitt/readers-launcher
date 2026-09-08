package com.freedomfighter.readerslauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.apps.AppEntry
import com.freedomfighter.readerslauncher.data.AppRef
import com.freedomfighter.readerslauncher.data.AppTile
import com.freedomfighter.readerslauncher.data.CategoryTile
import com.freedomfighter.readerslauncher.data.Grid
import java.text.Normalizer

private fun String.fold(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/**
 * The alphabetical text list of every app. Used as the drawer (swipe up) and as the picker
 * for tiles, categories and grid squares.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(nav: Nav, app: App, mode: PickMode) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val all by app.apps.apps.collectAsState()
    val home by app.store.state.collectAsState()
    val tick = rememberTick()
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateOf((mode as? PickMode.Multi)?.preselected?.toSet() ?: emptySet()) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var renameFor by remember { mutableStateOf<AppEntry?>(null) }
    var addToCategoryFor by remember { mutableStateOf<AppEntry?>(null) }
    var shortcutsFor by remember { mutableStateOf<AppEntry?>(null) }
    val focus = remember { FocusRequester() }

    val hiddenKeys = remember(home.hidden) { home.hidden.map { it.key }.toSet() }
    val visible = remember(all, hiddenKeys, query, home.renames, mode) {
        val q = query.fold().trim()
        all.asSequence()
            .filter { mode !is PickMode.Drawer || it.ref.key !in hiddenKeys }
            .map { it to (home.renames[it.ref.key] ?: it.label) }
            .filter { (entry, label) -> q.isEmpty() || label.fold().contains(q) || entry.ref.packageName.lowercase().contains(q) }
            .sortedBy { (_, label) -> label.lowercase() }
            .toList()
    }

    BackHandler { nav.pop() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun activate(entry: AppEntry) {
        when (mode) {
            is PickMode.Drawer -> { app.apps.launch(entry.ref); nav.home() }
            is PickMode.Single -> mode.onPicked(entry.ref)
            is PickMode.Multi -> {
                selected.value = if (entry.ref in selected.value) selected.value - entry.ref else selected.value + entry.ref
            }
        }
    }

    Page {
        Column(Modifier.fillMaxSize()) {
            val title = when (mode) {
                is PickMode.Drawer -> stringResource(R.string.drawer_title)
                is PickMode.Single -> stringResource(R.string.picker_title_single)
                is PickMode.Multi -> stringResource(R.string.picker_title_multi)
            }
            val multi = mode as? PickMode.Multi
            ScreenTitle(
                title = if (multi != null && selected.value.isNotEmpty())
                    stringResource(R.string.picker_hint_multi, selected.value.size) else title,
                onBack = { nav.pop() },
                trailing = if (multi != null) stringResource(R.string.action_done) else null,
                onTrailing = if (multi != null) ({
                    if (selected.value.isNotEmpty()) {
                        // Keep alphabetical order of selection for a predictable category.
                        val ordered = visibleOrderOf(all, selected.value)
                        multi.onPicked(ordered)
                    }
                }) else null
            )
            ReaderTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = rowPadH, vertical = 12.dp).focusRequester(focus),
                placeholder = stringResource(R.string.search),
                imeAction = ImeAction.Go,
                onImeAction = { visible.firstOrNull()?.let { activate(it.first) } }
            )
            Rule()
            if (visible.isEmpty()) {
                Small(stringResource(R.string.no_apps), Modifier.padding(rowPadH))
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
            ) {
                items(visible, key = { it.first.ref.key }) { (entry, label) ->
                    val isSel = entry.ref in selected.value
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { activate(entry) },
                                onLongClick = {
                                    tick()
                                    if (mode is PickMode.Multi) activate(entry) else menuFor = entry
                                }
                            )
                    ) {
                        TextRow(
                            text = label,
                            inverted = isSel,
                            secondary = if (entry.isWorkProfile) "work" else null,
                            size = LocalTypo.current.nav
                        )
                    }
                }
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).windowInsetsPadding(WindowInsets.ime))
        }

        // Long-press menu (drawer only).
        menuFor?.let { entry ->
            val label = home.renames[entry.ref.key] ?: entry.label
            val onHome = home.tiles.any { it is AppTile && it.app == entry.ref }
            val hidden = entry.ref.key in hiddenKeys
            TextMenu(
                title = label,
                items = buildList {
                    add(MenuItem(stringResource(R.string.menu_open)) { app.apps.launch(entry.ref); nav.home() })
                    add(MenuItem(stringResource(R.string.menu_shortcuts)) { shortcutsFor = entry })
                    if (!onHome) add(MenuItem(stringResource(R.string.menu_add_to_home)) { app.store.addTile(AppTile(app = entry.ref)) })
                    if (home.tiles.any { it is CategoryTile })
                        add(MenuItem(stringResource(R.string.menu_add_to_category)) { addToCategoryFor = entry })
                    if (home.grid != null && home.grid!!.slots.take(home.grid!!.columns).any { it == null }) {
                        add(MenuItem(stringResource(R.string.menu_add_grid)) {
                            val g = app.store.state.value.grid ?: Grid()
                            val free = (0 until g.columns).firstOrNull { g.slot(it) == null }
                            if (free != null) app.store.setGrid(g.withSlot(free, entry.ref))
                        })
                    }
                    add(MenuItem(stringResource(R.string.menu_rename)) { renameFor = entry })
                    add(MenuItem(if (hidden) stringResource(R.string.menu_unhide) else stringResource(R.string.menu_hide)) {
                        app.store.setHidden(entry.ref, !hidden)
                    })
                    add(MenuItem(stringResource(R.string.menu_app_info)) { app.apps.openAppInfo(entry.ref) })
                    if (!entry.isSystem && activity != null)
                        add(MenuItem(stringResource(R.string.menu_uninstall)) { app.apps.uninstall(activity, entry.ref) })
                },
                onDismiss = { menuFor = null }
            )
        }
        shortcutsFor?.let { entry ->
            ShortcutsMenu(app, entry.ref, home.renames[entry.ref.key] ?: entry.label, onDismiss = { shortcutsFor = null })
        }
        renameFor?.let { entry ->
            TextPrompt(
                title = stringResource(R.string.menu_rename),
                initial = home.renames[entry.ref.key] ?: entry.label,
                onDone = { app.store.rename(entry.ref, if (it == entry.label) null else it); renameFor = null },
                onCancel = { renameFor = null }
            )
        }
        addToCategoryFor?.let { entry ->
            TextMenu(
                title = stringResource(R.string.menu_add_to_category),
                items = home.tiles.filterIsInstance<CategoryTile>().map { cat ->
                    MenuItem(cat.name) {
                        if (entry.ref !in cat.apps) app.store.replaceTile(cat.copy(apps = cat.apps + entry.ref))
                    }
                },
                onDismiss = { addToCategoryFor = null }
            )
        }
    }
}

private fun visibleOrderOf(all: List<AppEntry>, selected: Set<AppRef>): List<AppRef> {
    val order = all.map { it.ref }
    return selected.sortedBy { r -> order.indexOf(r).let { if (it < 0) Int.MAX_VALUE else it } }
}
