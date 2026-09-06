package com.freedomfighter.readerslauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.data.AppRef
import com.freedomfighter.readerslauncher.data.CategoryTile

/** The list of apps behind a category tile. Same look as the home screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryScreen(nav: Nav, app: App, tileId: String) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val home by app.store.state.collectAsState()
    val tile = home.tiles.firstOrNull { it.id == tileId } as? CategoryTile
    val tick = rememberTick()
    val settings by app.prefs.settings.collectAsState()
    var menuFor by remember { mutableStateOf<AppRef?>(null) }
    var shortcutsFor by remember { mutableStateOf<AppRef?>(null) }
    BackHandler { nav.pop() }
    if (tile == null) { nav.pop(); return }

    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(tile.name, onBack = { nav.pop() }, trailing = stringResource(R.string.menu_edit), onTrailing = { nav.push(Screen.CategoryEdit(tileId)) })
            if (tile.apps.isEmpty()) {
                TextRow(stringResource(R.string.category_add_apps), onClick = { nav.push(Screen.CategoryEdit(tileId)) })
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                itemsIndexed(tile.apps, key = { _, r -> r.key }) { _, ref ->
                    // Same gestures as an app tile on the home screen: tap, long press, double tap.
                    TextTile(
                        home.labelFor(app, ref),
                        onClick = { app.apps.launch(ref); nav.home() },
                        onLongPress = { tick(); menuFor = ref },
                        onDoubleTap = if (settings.doubleTapShortcuts) ({ tick(); shortcutsFor = ref }) else null
                    )
                }
            }
        }
        menuFor?.let { ref ->
            TextMenu(
                title = home.labelFor(app, ref),
                items = buildList {
                    add(MenuItem(stringResource(R.string.menu_open)) { app.apps.launch(ref); nav.home() })
                    add(MenuItem(stringResource(R.string.menu_shortcuts)) { shortcutsFor = ref })
                    add(MenuItem(stringResource(R.string.menu_remove_from_category)) {
                        app.store.replaceTile(tile.copy(apps = tile.apps - ref))
                    })
                    add(MenuItem(stringResource(R.string.menu_app_info)) { app.apps.openAppInfo(ref) })
                    if (app.apps.entry(ref)?.isSystem == false && activity != null)
                        add(MenuItem(stringResource(R.string.menu_uninstall)) { app.apps.uninstall(activity, ref) })
                },
                onDismiss = { menuFor = null }
            )
        }
        shortcutsFor?.let { ref ->
            ShortcutsMenu(app, ref, home.labelFor(app, ref), onDismiss = { shortcutsFor = null })
        }
    }
}

/** Edit a category: rename, add apps, remove, drag to reorder. */
@Composable
fun CategoryEditScreen(nav: Nav, app: App, tileId: String) {
    val home by app.store.state.collectAsState()
    val tile = home.tiles.firstOrNull { it.id == tileId } as? CategoryTile
    var rename by remember { mutableStateOf(false) }
    var removeFor by remember { mutableStateOf<AppRef?>(null) }
    BackHandler { nav.pop() }
    if (tile == null) { nav.pop(); return }

    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(tile.name, onBack = { nav.pop() })
            TextRow(stringResource(R.string.menu_rename), size = LocalTypo.current.title, onClick = { rename = true })
            TextRow(stringResource(R.string.category_add_apps), size = LocalTypo.current.title, onClick = {
                nav.push(Screen.Apps(PickMode.Multi(preselected = tile.apps) { picked ->
                    nav.pop()
                    val current = app.store.state.value.tiles.firstOrNull { it.id == tileId } as? CategoryTile ?: return@Multi
                    // Keep existing order for kept apps, append newly chosen ones.
                    val kept = current.apps.filter { it in picked }
                    val added = picked.filter { it !in kept }
                    app.store.replaceTile(current.copy(apps = kept + added))
                }))
            })
            Rule()
            Small(stringResource(R.string.arrange_hint), Modifier.padding(horizontal = rowPadH, vertical = 10.dp))
            ReorderableList(
                items = tile.apps,
                key = { it.key },
                label = { home.labelFor(app, it) },
                onReorder = { app.store.replaceTile(tile.copy(apps = it)) },
                onTap = { removeFor = it },
                modifier = Modifier.weight(1f)
            )
        }
        if (rename) TextPrompt(
            title = stringResource(R.string.category_name_prompt), initial = tile.name,
            onDone = { app.store.replaceTile(tile.copy(name = it)); rename = false }, onCancel = { rename = false }
        )
        removeFor?.let { ref ->
            TextMenu(
                title = home.labelFor(app, ref),
                items = listOf(MenuItem(stringResource(R.string.menu_remove_from_category)) {
                    app.store.replaceTile(tile.copy(apps = tile.apps - ref))
                }),
                onDismiss = { removeFor = null }
            )
        }
    }
}
