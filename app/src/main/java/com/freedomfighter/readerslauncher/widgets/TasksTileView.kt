package com.freedomfighter.readerslauncher.widgets

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.freedomfighter.readerslauncher.ui.ReaderTextField
import com.freedomfighter.readerslauncher.ui.Rule
import com.freedomfighter.readerslauncher.ui.ScreenTitle
import com.freedomfighter.readerslauncher.ui.Small
import com.freedomfighter.readerslauncher.ui.T
import com.freedomfighter.readerslauncher.ui.TextPrompt
import com.freedomfighter.readerslauncher.ui.TextRow
import com.freedomfighter.readerslauncher.ui.findActivity
import com.freedomfighter.readerslauncher.ui.noRippleClickable
import com.freedomfighter.readerslauncher.ui.rememberTick
import com.freedomfighter.readerslauncher.ui.rowPadH
import com.freedomfighter.readerslauncher.ui.rowPadV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** In-memory cache so several tiles / recompositions don't refetch constantly. */
private object TasksCache {
    val lists = HashMap<String, androidx.compose.runtime.MutableState<List<Task>?>>()
    fun state(listId: String) = lists.getOrPut(listId) { mutableStateOf(null) }
}

/**
 * Tasks tile: "☐ first task   +". Checkbox completes it, + adds one, the title opens Google Tasks.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TasksTileView(tile: TasksTile, app: App, onLongPress: () -> Unit, onSetup: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val gt = remember { GoogleTasks.get(context) }
    val now = rememberNow()
    var tasks by TasksCache.state(tile.listId)
    var error by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { gt.openTasks(tile.listId) } }
            r.onSuccess { tasks = it; error = false }.onFailure { error = true }
        }
    }
    LaunchedEffect(tile.listId, now / (10 * 60_000), gt.isSignedIn) { if (gt.isSignedIn) reload() }

    val first = tasks?.firstOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!gt.isSignedIn) onSetup() else gt.openTasksApp(activity) },
                onLongClick = onLongPress
            )
            .padding(horizontal = rowPadH, vertical = rowPadV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            !gt.isSignedIn -> T(stringResource(R.string.tasks_sign_in), Modifier.weight(1f), size = typo.title, color = colors.dim)
            error && tasks == null -> T(stringResource(R.string.tasks_offline), Modifier.weight(1f), size = typo.title, color = colors.dim)
            tasks == null -> T(stringResource(R.string.tasks_loading), Modifier.weight(1f), color = colors.dim)
            first == null -> T(stringResource(R.string.tasks_none), Modifier.weight(1f), size = typo.title, color = colors.dim)
            else -> {
                T("☐", Modifier.noRippleClickable {
                    tick()
                    val t = first
                    tasks = tasks?.filterNot { it.id == t.id }
                    scope.launch { withContext(Dispatchers.IO) { runCatching { gt.complete(tile.listId, t.id) } }; reload() }
                }, align = TextAlign.Start)
                Box(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    T(first.title.ifBlank { "…" }, maxLines = 2)
                    Small(tile.listTitle.lowercase() + (tasks?.size?.let { if (it > 1) " · $it" else "" } ?: ""), maxLines = 1)
                }
            }
        }
        if (gt.isSignedIn) {
            Box(Modifier.width(14.dp))
            T("+", Modifier.noRippleClickable { adding = true }, align = TextAlign.End)
        }
    }
    if (adding) TextPrompt(
        title = stringResource(R.string.tasks_new_prompt),
        confirm = stringResource(R.string.action_done),
        onDone = { title ->
            adding = false
            scope.launch { withContext(Dispatchers.IO) { runCatching { gt.insert(tile.listId, title) } }; reload() }
        },
        onCancel = { adding = false }
    )
}

/**
 * Setup: client id → sign in → choose a list. Creates a tile when [tileId] is null,
 * otherwise re-targets the existing tile.
 */
@Composable
fun TasksSetupScreen(nav: Nav, app: App, tileId: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gt = remember { GoogleTasks.get(context) }
    val settings by app.prefs.settings.collectAsState()
    val typo = LocalTypo.current
    val colors = LocalColors.current
    var clientId by remember { mutableStateOf(settings.tasksClientId) }
    var signedIn by remember { mutableStateOf(gt.isSignedIn) }
    var lists by remember { mutableStateOf<List<TaskList>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler { nav.pop() }

    fun loadLists() {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { gt.lists() } }
            r.onSuccess { lists = it; error = null }.onFailure { error = it.message }
        }
    }
    LaunchedEffect(signedIn) { if (signedIn) loadLists() }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        scope.launch {
            signedIn = gt.handleAuthResult(result.data)
            if (!signedIn) error = "sign-in failed"
        }
    }

    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.tasks_client_id_title), onBack = { nav.pop() })
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Small(stringResource(R.string.tasks_client_id_help), Modifier.padding(horizontal = rowPadH, vertical = 12.dp), maxLines = 10)
                    ReaderTextField(
                        value = clientId,
                        onValueChange = { clientId = it; app.prefs.setTasksClientId(it) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = rowPadH, vertical = 8.dp),
                        placeholder = stringResource(R.string.tasks_client_id_prompt),
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    )
                    Rule()
                    if (!signedIn) {
                        TextRow(stringResource(R.string.tasks_sign_in), inverted = clientId.isNotBlank(), size = typo.title) {
                            if (clientId.isNotBlank()) runCatching { authLauncher.launch(gt.authIntent()) }.onFailure { error = it.message }
                        }
                    } else {
                        TextRow(stringResource(R.string.tasks_signed_in_as), secondary = stringResource(R.string.menu_sign_out), size = typo.title) {
                            gt.signOut(); signedIn = false; lists = null
                        }
                        Rule()
                        Small(stringResource(R.string.tasks_choose_list), Modifier.padding(horizontal = rowPadH, vertical = 10.dp))
                    }
                    error?.let { Small(it, Modifier.padding(horizontal = rowPadH, vertical = 6.dp), color = colors.dim, maxLines = 4) }
                }
                items(lists ?: emptyList(), key = { it.id }) { l ->
                    TextRow(l.title) {
                        val existing = tileId?.let { id -> app.store.state.value.tiles.firstOrNull { it.id == id } as? TasksTile }
                        if (existing != null) app.store.replaceTile(existing.copy(listId = l.id, listTitle = l.title))
                        else app.store.addTile(TasksTile(listId = l.id, listTitle = l.title))
                        nav.pop()
                    }
                }
            }
        }
    }
}
