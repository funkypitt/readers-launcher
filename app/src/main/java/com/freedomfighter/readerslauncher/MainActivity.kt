package com.freedomfighter.readerslauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.freedomfighter.readerslauncher.ui.AppsScreen
import com.freedomfighter.readerslauncher.ui.ArrangeScreen
import com.freedomfighter.readerslauncher.ui.BookChaptersScreen
import com.freedomfighter.readerslauncher.ui.BookScreen
import com.freedomfighter.readerslauncher.ui.CategoryEditScreen
import com.freedomfighter.readerslauncher.ui.CategoryScreen
import com.freedomfighter.readerslauncher.ui.HiddenScreen
import com.freedomfighter.readerslauncher.ui.HomeScreen
import com.freedomfighter.readerslauncher.ui.HomeUi
import com.freedomfighter.readerslauncher.ui.LocalColors
import com.freedomfighter.readerslauncher.ui.Nav
import com.freedomfighter.readerslauncher.ui.ReaderTheme
import com.freedomfighter.readerslauncher.ui.RecentsScreen
import com.freedomfighter.readerslauncher.ui.Screen
import com.freedomfighter.readerslauncher.ui.SettingsScreen
import com.freedomfighter.readerslauncher.ui.WidgetPickerScreen
import com.freedomfighter.readerslauncher.widgets.CalendarSetupScreen
import com.freedomfighter.readerslauncher.widgets.TasksSetupScreen

class MainActivity : ComponentActivity() {
    private val app get() = application as App
    private val nav = Nav()
    private var widgetConfigCallback: ((Boolean) -> Unit)? = null
    /** A book handed over by "open with" / "share", waiting for the user to pick a side. */
    val incomingBook = androidx.compose.runtime.mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Drop tiles whose app disappeared while we were not running.
        app.apps.apps.value.takeIf { it.isNotEmpty() }?.let { purgeMissing() }
        takeBook(intent)

        setContent {
            val settings by app.prefs.settings.collectAsState()
            LaunchedEffect(settings.autoRotate) {
                requestedOrientation = if (settings.autoRotate) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            ReaderTheme(settings) {
                SystemBars(settings.showStatusBar)
                Root(nav, app)
                IncomingBook(this, app, nav)
            }
        }
    }

    private fun purgeMissing() {
        val known = app.apps.apps.value.map { it.ref }.toSet()
        app.store.purge { ref -> ref !in known && ref.user < 0 }
    }

    override fun onStart() {
        super.onStart()
        runCatching { app.widgetHost.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { app.widgetHost.stopListening() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Home button: always return to the top of the home screen.
        nav.home()
        takeBook(intent)
    }

    private fun takeBook(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            else -> null
        } ?: return
        incomingBook.value = uri
        intent?.action = null
    }

    /** Launch a widget's configuration activity; [onDone] receives whether it succeeded. */
    fun configureWidget(appWidgetId: Int, onDone: (Boolean) -> Unit) {
        widgetConfigCallback = onDone
        try {
            app.widgetHost.startAppWidgetConfigureActivityForResult(this, appWidgetId, 0, REQ_WIDGET_CONFIG, null)
        } catch (e: Exception) {
            widgetConfigCallback = null
            onDone(false)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_WIDGET_CONFIG) {
            widgetConfigCallback?.invoke(resultCode == RESULT_OK)
            widgetConfigCallback = null
        }
    }

    companion object {
        private const val REQ_WIDGET_CONFIG = 7001
    }
}

@Composable
private fun SystemBars(show: Boolean) {
    val view = LocalView.current
    val colors = LocalColors.current
    LaunchedEffect(show, colors.isDark) {
        val window = (view.context as? ComponentActivity)?.window ?: return@LaunchedEffect
        val c = WindowInsetsControllerCompat(window, view)
        c.isAppearanceLightStatusBars = !colors.isDark
        c.isAppearanceLightNavigationBars = !colors.isDark
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (show) c.show(WindowInsetsCompat.Type.statusBars()) else c.hide(WindowInsetsCompat.Type.statusBars())
    }
}

/** "open with" a book: ask which side, import, open it. */
@Composable
private fun IncomingBook(activity: MainActivity, app: App, nav: Nav) {
    val uri = activity.incomingBook.value ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    fun into(slot: Int) {
        activity.incomingBook.value = null
        // Clearing the uri removes this composable, so the work runs in the activity's scope,
        // not in a composition scope that would be cancelled with it.
        activity.lifecycleScope.launch {
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.books.importInto(slot, uri) }
            if (r.isSuccess) { nav.home(); nav.push(Screen.Book(slot)) }
            else {
                android.util.Log.w("IncomingBook", "import failed", r.exceptionOrNull())
                android.widget.Toast.makeText(context, R.string.reader_unsupported, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    com.freedomfighter.readerslauncher.ui.TextMenu(
        title = androidx.compose.ui.res.stringResource(R.string.reader_open_book),
        items = listOf(
            com.freedomfighter.readerslauncher.ui.MenuItem(androidx.compose.ui.res.stringResource(R.string.book_slot_left)) { into(0) },
            com.freedomfighter.readerslauncher.ui.MenuItem(androidx.compose.ui.res.stringResource(R.string.book_slot_right)) { into(1) }
        ),
        onDismiss = { activity.incomingBook.value = null }
    )
}

@Composable
private fun Root(nav: Nav, app: App) {
    val screen = nav.current
    val homeUi = remember { HomeUi() }
    when (screen) {
        Screen.Home -> HomeScreen(nav, app, homeUi)
        is Screen.Apps -> AppsScreen(nav, app, screen.mode)
        is Screen.Category -> CategoryScreen(nav, app, screen.tileId)
        is Screen.CategoryEdit -> CategoryEditScreen(nav, app, screen.tileId)
        Screen.Settings -> SettingsScreen(nav, app)
        Screen.Arrange -> ArrangeScreen(nav, app)
        Screen.Hidden -> HiddenScreen(nav, app)
        Screen.WidgetPicker -> WidgetPickerScreen(nav, app)
        is Screen.CalendarSetup -> CalendarSetupScreen(nav, app, screen.tileId)
        is Screen.TasksSetup -> TasksSetupScreen(nav, app, screen.tileId)
        Screen.Recents -> RecentsScreen(nav, app)
        is Screen.Book -> BookScreen(nav, app, screen.slot)
        is Screen.BookChapters -> BookChaptersScreen(nav, app, screen.slot)
    }
}
