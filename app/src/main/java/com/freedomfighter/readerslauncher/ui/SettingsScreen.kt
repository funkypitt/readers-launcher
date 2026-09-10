package com.freedomfighter.readerslauncher.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.freedomfighter.readerslauncher.BuildConfig
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.data.Align
import com.freedomfighter.readerslauncher.data.FontChoice
import com.freedomfighter.readerslauncher.data.TextSize
import com.freedomfighter.readerslauncher.data.ThemeMode

/** Every setting is a line of text; tapping it cycles to the next value. */
@Composable
fun SettingsScreen(nav: Nav, app: App) {
    val context = LocalContext.current
    val s by app.prefs.settings.collectAsState()
    val typo = LocalTypo.current
    BackHandler { nav.pop() }
    var about by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(app.store.exportJson().toByteArray()) }
                Toast.makeText(context, R.string.export_done, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()?.let { app.store.importJson(it) } ?: false
            Toast.makeText(context, if (ok) R.string.import_done else R.string.import_failed, Toast.LENGTH_SHORT).show()
        }
    }

    val on = stringResource(R.string.on)
    val off = stringResource(R.string.off)
    fun onOff(b: Boolean) = if (b) on else off

    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.settings_title), onBack = { nav.pop() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 32.dp)) {
                val themeName = when (s.theme) {
                    ThemeMode.DARK -> stringResource(R.string.theme_dark)
                    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                }
                TextRow(stringResource(R.string.settings_theme, themeName), size = typo.title) {
                    app.prefs.setTheme(next(s.theme))
                }
                val fontName = when (s.font) {
                    FontChoice.SERIF -> stringResource(R.string.font_serif)
                    FontChoice.SANS -> stringResource(R.string.font_sans)
                    FontChoice.MONO -> stringResource(R.string.font_mono)
                }
                TextRow(stringResource(R.string.settings_font, fontName), size = typo.title) { app.prefs.setFont(next(s.font)) }
                val sizeName = when (s.textSize) {
                    TextSize.SMALL -> stringResource(R.string.size_small)
                    TextSize.MEDIUM -> stringResource(R.string.size_medium)
                    TextSize.LARGE -> stringResource(R.string.size_large)
                }
                TextRow(stringResource(R.string.settings_text_size, sizeName), size = typo.title) { app.prefs.setTextSize(next(s.textSize)) }
                val alignName = if (s.align == Align.LEFT) stringResource(R.string.align_left) else stringResource(R.string.align_center)
                TextRow(stringResource(R.string.settings_align, alignName), size = typo.title) { app.prefs.setAlign(next(s.align)) }
                TextRow(stringResource(R.string.settings_double_tap, onOff(s.doubleTapTheme)), size = typo.title) { app.prefs.setDoubleTapTheme(!s.doubleTapTheme) }
                TextRow(stringResource(R.string.settings_swipe_down, onOff(s.swipeDownNotifications)), size = typo.title) { app.prefs.setSwipeDownNotifications(!s.swipeDownNotifications) }
                TextRow(stringResource(R.string.settings_status_bar, onOff(s.showStatusBar)), size = typo.title) { app.prefs.setShowStatusBar(!s.showStatusBar) }
                TextRow(stringResource(R.string.settings_haptics, onOff(s.haptics)), size = typo.title) { app.prefs.setHaptics(!s.haptics) }
                TextRow(stringResource(R.string.settings_rotation, onOff(s.autoRotate)), size = typo.title) { app.prefs.setAutoRotate(!s.autoRotate) }
                TextRow(stringResource(R.string.settings_double_tap_app, onOff(s.doubleTapShortcuts)), size = typo.title) { app.prefs.setDoubleTapShortcuts(!s.doubleTapShortcuts) }
                Rule(Modifier.padding(vertical = 8.dp))
                TextRow(stringResource(R.string.settings_arrange), size = typo.title) { nav.push(Screen.Arrange) }
                TextRow(stringResource(R.string.settings_hidden), size = typo.title) { nav.push(Screen.Hidden) }
                TextRow(stringResource(R.string.settings_tasks), size = typo.title) { nav.push(Screen.TasksSetup(null)) }
                Rule(Modifier.padding(vertical = 8.dp))
                TextRow(stringResource(R.string.settings_default_launcher), size = typo.title) { openHomeSettings(context) }
                TextRow(stringResource(R.string.settings_usage_access), size = typo.title) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                Rule(Modifier.padding(vertical = 8.dp))
                TextRow(stringResource(R.string.settings_export), size = typo.title) { exportLauncher.launch("readers-launcher.json") }
                TextRow(stringResource(R.string.settings_import), size = typo.title) { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                Rule(Modifier.padding(vertical = 8.dp))
                TextRow(
                    stringResource(R.string.settings_about, BuildConfig.VERSION_NAME),
                    size = typo.title,
                    secondary = if (about) stringResource(R.string.settings_about_line) else null
                ) { about = !about }
                TextRow(stringResource(R.string.credits), size = typo.title) { }
            }
        }
    }
}

private inline fun <reified E : Enum<E>> next(e: E): E {
    val all = enumValues<E>()
    return all[(e.ordinal + 1) % all.size]
}
