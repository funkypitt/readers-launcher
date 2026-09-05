package com.freedomfighter.readerslauncher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.freedomfighter.readerslauncher.data.Align
import com.freedomfighter.readerslauncher.data.FontChoice
import com.freedomfighter.readerslauncher.data.Settings
import com.freedomfighter.readerslauncher.data.TextSize
import com.freedomfighter.readerslauncher.data.ThemeMode

/** Two colours only. `dim` is the foreground at reduced opacity for secondary lines and rules. */
data class ReaderColors(val bg: Color, val fg: Color) {
    val dim: Color get() = fg.copy(alpha = 0.55f)
    val rule: Color get() = fg.copy(alpha = 0.25f)
    val isDark: Boolean get() = bg.luminance() < 0.5f
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

data class ReaderTypography(
    val family: FontFamily,
    val tile: TextUnit,
    val alignLeft: Boolean
) {
    val small: TextUnit get() = tile * 0.62f
    val title: TextUnit get() = tile * 0.8f
    val big: TextUnit get() = tile * 2.2f
    val textAlign: TextAlign get() = if (alignLeft) TextAlign.Start else TextAlign.Center
}

val LocalColors = compositionLocalOf { ReaderColors(Color.Black, Color.White) }
val LocalTypo = compositionLocalOf { ReaderTypography(FontFamily.Serif, 24.sp, true) }
val LocalHaptics = compositionLocalOf { true }

@Composable
fun ReaderTheme(settings: Settings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val colors = if (dark) ReaderColors(Color.Black, Color.White) else ReaderColors(Color.White, Color.Black)
    val family = when (settings.font) {
        FontChoice.SERIF -> FontFamily.Serif
        FontChoice.SANS -> FontFamily.SansSerif
        FontChoice.MONO -> FontFamily.Monospace
    }
    val size = when (settings.textSize) {
        TextSize.SMALL -> 20.sp
        TextSize.MEDIUM -> 24.sp
        TextSize.LARGE -> 28.sp
    }
    val typo = ReaderTypography(family, size, settings.align == Align.LEFT)
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypo provides typo,
        LocalHaptics provides settings.haptics,
        content = content
    )
}

@Composable
fun tileTextStyle(): TextStyle {
    val t = LocalTypo.current
    return TextStyle(
        color = LocalColors.current.fg,
        fontFamily = t.family,
        fontSize = t.tile,
        lineHeight = t.tile * 1.25f,
        textAlign = t.textAlign
    )
}
