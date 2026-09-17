package com.freedomfighter.readerslauncher.backup

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
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
import androidx.lifecycle.lifecycleScope
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.MainActivity
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.ui.LocalTypo
import com.freedomfighter.readerslauncher.ui.Nav
import com.freedomfighter.readerslauncher.ui.Page
import com.freedomfighter.readerslauncher.ui.Rule
import com.freedomfighter.readerslauncher.ui.ScreenTitle
import com.freedomfighter.readerslauncher.ui.Small
import com.freedomfighter.readerslauncher.ui.TextRow
import com.freedomfighter.readerslauncher.ui.findActivity
import com.freedomfighter.readerslauncher.ui.rowPadH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Before a backup replaces the home screen: what it holds, and what this phone lacks — each
 * missing app a tap away from its store page. "restore anyway" / "cancel".
 */
@Composable
fun RestoreScreen(nav: Nav, app: App) {
    val context = LocalContext.current
    val typo = LocalTypo.current
    val backup = app.pendingRestore
    BackHandler { app.pendingRestore = null; nav.pop() }
    if (backup == null) { nav.pop(); return }
    val missing = remember(backup) { BackupIo.missing(app, backup) }
    var busy by remember { mutableStateOf(false) }
    val activity = context.findActivity() as? MainActivity
    val placeHome = stringResource(R.string.restore_place_home)
    val placeGrid = stringResource(R.string.restore_place_grid)
    val placeWidget = stringResource(R.string.restore_place_widget)
    val placeCategory = stringResource(R.string.restore_place_category, "%s")
    fun places(list: List<String>) = list.joinToString(" · ") { p ->
        when {
            p == "home" -> placeHome
            p == "grid" -> placeGrid
            p == "widget" -> placeWidget
            p.startsWith("category:") -> placeCategory.replace("%s", p.removePrefix("category:"))
            else -> p
        }
    }
    val done = stringResource(R.string.restore_done)

    fun restore() {
        if (busy) return
        busy = true
        val scope = activity?.lifecycleScope ?: return
        // the activity's scope: this screen leaves the composition as soon as the home returns
        scope.launch {
            withContext(Dispatchers.IO) { BackupIo.apply(context, app, backup) }
            app.pendingRestore = null
            nav.home()
            Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
            activity.bindRestoredWidgets()
        }
    }

    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.restore_backup), onBack = { app.pendingRestore = null; nav.pop() })
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
                item {
                    val h = backup.home
                    val date = backup.exported.take(10).ifBlank { "—" }
                    Small(stringResource(R.string.restore_summary, date, h.pageCount, h.allTiles.size) + "\n" + stringResource(R.string.restore_replaces),
                        Modifier.padding(horizontal = rowPadH, vertical = 10.dp), maxLines = 4)
                }
                if (missing.apps.isNotEmpty()) {
                    item { Small(stringResource(R.string.restore_missing), Modifier.padding(horizontal = rowPadH).padding(top = 14.dp, bottom = 2.dp)) }
                    items(missing.apps, key = { "app-" + it.packageName }) { m ->
                        TextRow(m.label, secondary = m.packageName + (if (m.places.isNotEmpty()) " · " + places(m.places) else ""), size = typo.title) {
                            BackupIo.openStore(context, m.packageName)
                        }
                    }
                }
                val gone = missing.widgets.filter { it.packageInstalled }
                if (gone.isNotEmpty()) {
                    item { Small(stringResource(R.string.restore_widgets_gone), Modifier.padding(horizontal = rowPadH).padding(top = 14.dp, bottom = 2.dp)) }
                    items(gone, key = { "w-" + it.provider }) { w ->
                        TextRow(w.label.ifBlank { w.provider.substringAfterLast('.') }, secondary = w.provider.substringBefore('/'), size = typo.title) {
                            BackupIo.openStore(context, w.provider.substringBefore('/'))
                        }
                    }
                }
                if (missing.isEmpty) item { Small(stringResource(R.string.restore_all_there), Modifier.padding(horizontal = rowPadH, vertical = 10.dp)) }
                item { Small(stringResource(R.string.restore_explain), Modifier.padding(horizontal = rowPadH).padding(top = 18.dp), maxLines = 12) }
            }
            Rule()
            TextRow(stringResource(if (missing.isEmpty) R.string.restore_do else R.string.restore_anyway), size = typo.title) { restore() }
            TextRow(stringResource(R.string.restore_cancel), size = typo.title) { app.pendingRestore = null; nav.pop() }
            androidx.compose.foundation.layout.Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 8.dp))
        }
    }
}
