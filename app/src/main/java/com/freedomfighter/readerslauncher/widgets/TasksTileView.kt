package com.freedomfighter.readerslauncher.widgets

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.data.TasksTile
import com.freedomfighter.readerslauncher.ui.LocalColors
import com.freedomfighter.readerslauncher.ui.LocalTypo
import com.freedomfighter.readerslauncher.ui.Nav
import com.freedomfighter.readerslauncher.ui.Page
import com.freedomfighter.readerslauncher.ui.Rule
import com.freedomfighter.readerslauncher.ui.ScreenTitle
import com.freedomfighter.readerslauncher.ui.Small
import com.freedomfighter.readerslauncher.ui.T
import com.freedomfighter.readerslauncher.ui.TextPrompt
import com.freedomfighter.readerslauncher.ui.TextRow
import com.freedomfighter.readerslauncher.ui.noRippleClickable
import com.freedomfighter.readerslauncher.ui.rememberTick
import com.freedomfighter.readerslauncher.ui.rowPadH
import com.freedomfighter.readerslauncher.ui.rowPadV
import com.freedomfighter.readerslauncher.ui.widgetTwoLineHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Tasks tile: "☐ first task   +". The box completes it, + adds one, the text opens Tasks.org.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TasksTileView(tile: TasksTile, app: App, onLongPress: () -> Unit, onSetup: () -> Unit, onAdd: () -> Unit = {}) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val repo = remember(tile.source) { TaskSource.of(context, tile.source) }
    val listId = tile.listId
    val now = rememberNow()
    var tasks by remember(tile.id) { mutableStateOf<List<TaskSource.Task>?>(null) }
    var index by remember(tile.id) { mutableIntStateOf(0) }
    var error by remember { mutableStateOf(false) }
    val changed by app.tasksChanged.collectAsState()
    var generation by remember { mutableIntStateOf(0) }

    fun reload() {
        if (!repo.ready) return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { repo.openTasks(listId) } }
            r.onSuccess { tasks = it; error = false }.onFailure { error = true }
        }
    }
    LaunchedEffect(listId, now / (10 * 60_000), generation, repo.ready, changed) { reload() }

    // Tasks.org notifies its provider URIs on every change; follow them.
    DisposableEffect(listId) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { generation++ }
        }
        val cr = context.contentResolver
        repo.observedUris.forEach { uri -> runCatching { cr.registerContentObserver(uri, true, observer) } }
        onDispose { runCatching { cr.unregisterContentObserver(observer) } }
    }

    val list = tasks ?: emptyList()
    if (index >= list.size) index = maxOf(0, list.size - 1)
    val first = list.getOrNull(index)
    val ready = repo.ready
    Row(
        Modifier
            .fillMaxWidth()
            .height(widgetTwoLineHeight())
            // Swipe left → next task, right → previous, exactly like the agenda tile. The drag is
            // consumed here, so the home screen does not read it as a book-slot swipe.
            .pointerInput(list.size) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (abs(total) > 60.dp.toPx()) {
                            val next = if (total < 0) index + 1 else index - 1
                            if (next in list.indices) { index = next; tick() }
                        }
                    },
                    onHorizontalDrag = { change, delta -> total += delta; change.consume() }
                )
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!ready) onSetup() else repo.openApp() },
                onLongClick = onLongPress
            )
            .padding(horizontal = rowPadH, vertical = rowPadV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            !ready -> T(stringResource(R.string.tasks_setup_needed), Modifier.weight(1f), size = typo.title, color = colors.dim)
            error && tasks == null -> T(stringResource(R.string.tasks_offline), Modifier.weight(1f), size = typo.title, color = colors.dim)
            tasks == null -> T(stringResource(R.string.tasks_loading), Modifier.weight(1f), color = colors.dim)
            first == null -> T(stringResource(R.string.tasks_none), Modifier.weight(1f), size = typo.title, color = colors.dim)
            else -> {
                T("☐", Modifier.noRippleClickable {
                    tick()
                    val t = first
                    scope.launch {
                        val done = withContext(Dispatchers.IO) { repo.complete(listId, t) }
                        if (done) { tasks = tasks?.filterNot { it.id == t.id }; reload() } else repo.openTask(listId, t)
                    }
                }, align = TextAlign.Start)
                Box(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    T(first.title.ifBlank { "…" }, maxLines = 1)
                    Small(tile.listTitle.lowercase() + (if (list.size > 1) "   ${index + 1}/${list.size}" else ""), maxLines = 1)
                }
            }
        }
        if (ready) {
            Box(Modifier.width(16.dp))
            T("+", Modifier.noRippleClickable { onAdd() }, align = TextAlign.End)
        }
    }
}

/**
 * Setup: which task app (Reader's Tasks or Tasks.org), then its lists.
 * Creates a tile when [tileId] is null, otherwise re-targets the existing tile.
 */
@Composable
fun TasksSetupScreen(nav: Nav, app: App, tileId: String?) {
    val context = LocalContext.current
    val typo = LocalTypo.current
    val colors = LocalColors.current
    val sources = remember { TaskSource.all(context) }
    val existing = tileId?.let { id -> app.store.state.value.allTiles.firstOrNull { it.id == id } as? TasksTile }
    var chosen by remember { mutableStateOf<TaskSource?>(existing?.let { e -> sources.firstOrNull { it.id == e.source } }
        ?: sources.filter { it.isInstalled }.singleOrNull()) }
    var installed by remember { mutableStateOf(sources.map { it.id to it.isInstalled }) }
    var granted by remember { mutableStateOf(chosen?.ready ?: false) }
    var lists by remember { mutableStateOf<List<TaskSource.TaskList>?>(null) }
    var error by remember { mutableStateOf(false) }
    BackHandler { nav.pop() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = chosen?.ready ?: false }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { installed = sources.map { it.id to it.isInstalled }; granted = chosen?.ready ?: false }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(chosen, granted) {
        val src = chosen ?: return@LaunchedEffect
        lists = null
        if (src.isInstalled && !src.ready && src.permissions.isNotEmpty()) permLauncher.launch(src.permissions)
        if (src.ready) {
            val r = withContext(Dispatchers.IO) { runCatching { src.lists() } }
            r.onSuccess { lists = it; error = false }.onFailure { error = true }
        }
    }

    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.tasks_setup_title), onBack = { nav.pop() })
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Small(stringResource(R.string.tasks_setup_help), Modifier.padding(horizontal = rowPadH, vertical = 12.dp), maxLines = 8)
                    Rule()
                }
                // Source choice: one line per app, the chosen one inverted.
                items(sources, key = { it.id }) { src ->
                    val isInstalled = installed.firstOrNull { it.first == src.id }?.second ?: false
                    TextRow(
                        src.label,
                        inverted = src == chosen,
                        secondary = if (isInstalled) null else stringResource(R.string.tasks_not_installed),
                        size = typo.title
                    ) {
                        if (isInstalled) { chosen = src; granted = src.ready }
                        else if (src is TasksOrgSource) src.openStore()
                        else runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/funkypitt/readers-tasks-android")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
                item { Rule(Modifier.padding(top = 8.dp)) }
                val src = chosen
                when {
                    src == null -> Unit
                    !src.ready -> item {
                        TextRow(stringResource(R.string.tasks_grant), size = typo.title) { if (src.permissions.isNotEmpty()) permLauncher.launch(src.permissions) else src.openApp() }
                    }
                    else -> {
                        item {
                            Small(
                                if (src is TasksOrgSource && !src.apiAvailable) stringResource(R.string.tasks_choose_list_legacy) else stringResource(R.string.tasks_choose_list),
                                Modifier.padding(horizontal = rowPadH, vertical = 10.dp), maxLines = 4
                            )
                            if (error) Small(stringResource(R.string.tasks_offline), Modifier.padding(horizontal = rowPadH, vertical = 6.dp), color = colors.dim)
                            if (lists?.isEmpty() == true) Small(stringResource(R.string.tasks_no_lists), Modifier.padding(horizontal = rowPadH, vertical = 6.dp), maxLines = 3)
                        }
                        items(lists ?: emptyList(), key = { it.id }) { l ->
                            TextRow(l.title, secondary = l.account.ifBlank { null }) {
                                val current = tileId?.let { id -> app.store.state.value.allTiles.firstOrNull { it.id == id } as? TasksTile }
                                if (current != null) app.store.replaceTile(current.copy(listId = l.id, listTitle = l.title, source = src.id))
                                else app.store.addTile(TasksTile(listId = l.id, listTitle = l.title, source = src.id))
                                nav.pop()
                            }
                        }
                    }
                }
            }
        }
    }
}
