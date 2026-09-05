package com.freedomfighter.readerslauncher

import android.content.res.Configuration
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.freedomfighter.readerslauncher.data.ThemeMode

/** Quick-settings tile: flips the launcher between white-on-black and black-on-white. */
class ThemeTileService : TileService() {

    private val prefs get() = (application as App).prefs

    private fun systemIsDark(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun isDark(): Boolean = when (prefs.settings.value.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemIsDark()
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        prefs.toggleTheme(systemIsDark())
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.state = if (isDark()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.qs_theme_label)
        tile.updateTile()
    }
}
