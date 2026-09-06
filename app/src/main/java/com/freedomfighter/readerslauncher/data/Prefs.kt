package com.freedomfighter.readerslauncher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class FontChoice { SERIF, SANS, MONO }
enum class TextSize { SMALL, MEDIUM, LARGE }
enum class Align { LEFT, CENTER }

data class Settings(
    val theme: ThemeMode = ThemeMode.DARK,
    val font: FontChoice = FontChoice.SANS,
    val textSize: TextSize = TextSize.MEDIUM,
    val align: Align = Align.LEFT,
    val doubleTapTheme: Boolean = true,
    val swipeDownNotifications: Boolean = true,
    val showStatusBar: Boolean = true,
    val haptics: Boolean = true,
    val autoRotate: Boolean = false,
    /** Double tap on an app tile or grid square lists its shortcuts (costs ~300 ms on a single tap). */
    val doubleTapShortcuts: Boolean = true,
    /** Reader text size in sp; 0 = automatic from screen size. */
    val readerSp: Int = 0
)

/**
 * Small settings live in SharedPreferences so the quick-settings tile service
 * can flip the theme without touching the home JSON.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = read()
    }

    init {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun read() = Settings(
        theme = enumOr(sp.getString(K_THEME, null), ThemeMode.DARK),
        font = enumOr(sp.getString(K_FONT, null), FontChoice.SANS),
        textSize = enumOr(sp.getString(K_SIZE, null), TextSize.MEDIUM),
        align = enumOr(sp.getString(K_ALIGN, null), Align.LEFT),
        doubleTapTheme = sp.getBoolean(K_DOUBLE_TAP, true),
        swipeDownNotifications = sp.getBoolean(K_SWIPE_DOWN, true),
        showStatusBar = sp.getBoolean(K_STATUS_BAR, true),
        haptics = sp.getBoolean(K_HAPTICS, true),
        autoRotate = sp.getBoolean(K_ROTATE, false),
        doubleTapShortcuts = sp.getBoolean(K_DT_SHORTCUTS, true),
        readerSp = sp.getInt(K_READER_SP, 0)
    )

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    fun setTheme(mode: ThemeMode) = sp.edit().putString(K_THEME, mode.name).apply()
    fun setFont(font: FontChoice) = sp.edit().putString(K_FONT, font.name).apply()
    fun setTextSize(size: TextSize) = sp.edit().putString(K_SIZE, size.name).apply()
    fun setAlign(align: Align) = sp.edit().putString(K_ALIGN, align.name).apply()
    fun setDoubleTapTheme(v: Boolean) = sp.edit().putBoolean(K_DOUBLE_TAP, v).apply()
    fun setSwipeDownNotifications(v: Boolean) = sp.edit().putBoolean(K_SWIPE_DOWN, v).apply()
    fun setShowStatusBar(v: Boolean) = sp.edit().putBoolean(K_STATUS_BAR, v).apply()
    fun setHaptics(v: Boolean) = sp.edit().putBoolean(K_HAPTICS, v).apply()
    fun setAutoRotate(v: Boolean) = sp.edit().putBoolean(K_ROTATE, v).apply()
    fun setDoubleTapShortcuts(v: Boolean) = sp.edit().putBoolean(K_DT_SHORTCUTS, v).apply()
    fun setReaderSp(v: Int) = sp.edit().putInt(K_READER_SP, v).apply()

    /** Flip between the two monochrome palettes (SYSTEM resolves to whatever is showing now). */
    fun toggleTheme(systemIsDark: Boolean) {
        val current = _settings.value.theme
        val dark = when (current) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> systemIsDark
        }
        setTheme(if (dark) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    // Opaque blobs used by widgets.
    fun getString(key: String): String? = sp.getString(key, null)
    fun putString(key: String, value: String?) = sp.edit().putString(key, value).apply()

    companion object {
        private const val K_THEME = "theme"
        private const val K_FONT = "font"
        private const val K_SIZE = "text_size"
        private const val K_ALIGN = "align"
        private const val K_DOUBLE_TAP = "double_tap_theme"
        private const val K_SWIPE_DOWN = "swipe_down_notifications"
        private const val K_STATUS_BAR = "show_status_bar"
        private const val K_HAPTICS = "haptics"
        private const val K_ROTATE = "auto_rotate"
        private const val K_DT_SHORTCUTS = "double_tap_shortcuts"
        private const val K_READER_SP = "reader_sp"
    }
}
