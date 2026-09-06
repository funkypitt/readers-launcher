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
fun TasksTileView(tile: TasksTile, app: App, onLongPress: () -> Unit, onSetup: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val repo = remember { TasksOrg.get(context) }
    val listId = tile.listId.toLongOrNull() ?: -1L
    val now = rememberNow()
    var tasks by remember(tile.id) { mutableStateOf<List<TasksOrg.Task>?>(null) }
    var index by remember(tile.id) { mutableIntStateOf(0) }
    var error by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }

    fun reload() {
        if (!repo.ready) return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { repo.openTasks(listId) } }
            r.onSuccess { tasks = it; error = false }.onFailure { error = true }
        }
    }
    LaunchedEffect(listId, now / (10 * 60_000), generation, repo.ready) { reload() }

    // Tasks.org notifies its provider URIs on every change; follow them.
    DisposableEffect(listId) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { generation++ }
        }
        val cr = context.contentResolver
        runCatching { cr.registerContentObserver(TasksOrg.API_TASKS_URI, true, observer) }
        runCatching { cr.registerContentObserver(TasksOrg.LEGACY_URI, true, observer) }
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
                        val done = withContext(Dispatchers.IO) { repo.complete(t.id) }
                        if (done) { tasks = tasks?.filterNot { it.id == t.id }; reload() } else repo.openTask(t.id)
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
            T("+", Modifier.noRippleClickable { adding = true }, align = TextAlign.End)
        }
    }
    if (adding) TextPrompt(
        title = stringResource(R.string.tasks_new_prompt),
        confirm = stringResource(R.string.action_done),
        onDone = { title ->
            adding = false
            scope.launch {
                val done = withContext(Dispatchers.IO) { repo.insert(listId, title) }
                if (done) reload() else repo.newTaskInApp(title)
            }
        },
        onCancel = { adding = false }
    )
}

/**
 * Setup: Tasks.org installed? → permissions → choose a list.
 * Creates a tile when [tileId] is null, otherwise re-targets the existing tile.
 */
@Composable
fun TasksSetupScreen(nav: Nav, app: App, tileId: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { TasksOrg.get(context) }
    val typo = LocalTypo.current
    val colors = LocalColors.current
    var installed by remember { mutableStateOf(repo.isInstalled) }
    var granted by remember { mutableStateOf(repo.hasPermissions) }
    var lists by remember { mutableStateOf<List<TasksOrg.TaskList>?>(null) }
    var error by remember { mutableStateOf(false) }
    BackHandler { nav.pop() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = repo.hasPermissions
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { installed = repo.isInstalled; granted = repo.hasPermissions }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(installed, granted) {
        if (installed && !granted) permLauncher.launch(TasksOrg.PERMISSIONS)
        if (installed && granted) {
            val r = withContext(Dispatchers.IO) { runCatching { repo.lists() } }
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
                when {
                    !installed -> item {
                        TextRow(stringResource(R.string.tasks_install), secondary = "F-Droid · org.tasks", size = typo.title) { repo.openStore() }
                    }
                    !granted -> item {
                        TextRow(stringResource(R.string.tasks_grant), size = typo.title) { permLauncher.launch(TasksOrg.PERMISSIONS) }
                    }
                    else -> {
                        item {
                            Small(
                                if (repo.apiAvailable) stringResource(R.string.tasks_choose_list) else stringResource(R.string.tasks_choose_list_legacy),
                                Modifier.padding(horizontal = rowPadH, vertical = 10.dp), maxLines = 4
                            )
                            if (error) Small(stringResource(R.string.tasks_offline), Modifier.padding(horizontal = rowPadH, vertical = 6.dp), color = colors.dim)
                            if (lists?.isEmpty() == true) Small(stringResource(R.string.tasks_no_lists), Modifier.padding(horizontal = rowPadH, vertical = 6.dp), maxLines = 3)
                        }
                        items(lists ?: emptyList(), key = { it.id }) { l ->
                            TextRow(l.title, secondary = l.account.ifBlank { null }) {
                                val existing = tileId?.let { id -> app.store.state.value.tiles.firstOrNull { it.id == id } as? TasksTile }
                                if (existing != null) app.store.replaceTile(existing.copy(listId = l.id.toString(), listTitle = l.title))
                                else app.store.addTile(TasksTile(listId = l.id.toString(), listTitle = l.title))
                                nav.pop()
                            }
                        }
                    }
                }
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = scope
}
