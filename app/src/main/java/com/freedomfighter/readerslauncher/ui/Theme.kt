package com.freedomfighter.readerslauncher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.data.Align
import com.freedomfighter.readerslauncher.data.FontChoice
import com.freedomfighter.readerslauncher.data.Palettes
import com.freedomfighter.readerslauncher.data.Settings
import com.freedomfighter.readerslauncher.data.TextSize
import com.freedomfighter.readerslauncher.data.ThemeMode

/**
 * The colours of a theme: a background, a text colour, and the accent that fills the inverted
 * rows (the text colour itself in the two inks). `dim` is the text at reduced opacity for
 * secondary lines and rules.
 */
data class ReaderColors(val bg: Color, val fg: Color, val accent: Color = fg, val onAccent: Color = bg) {
    val dim: Color get() = fg.copy(alpha = 0.55f)
    val rule: Color get() = fg.copy(alpha = 0.25f)
    val isDark: Boolean get() = bg.luminance() < 0.5f
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

data class ReaderTypography(
    val family: FontFamily,
    val weight: FontWeight,
    val tile: TextUnit,
    val alignLeft: Boolean
) {
    /** App and category names: one step above the widgets, they are where the page leads. */
    val nav: TextUnit get() = tile * 1.15f
    val small: TextUnit get() = tile * 0.62f
    val title: TextUnit get() = tile * 0.8f
    val big: TextUnit get() = tile * 2.2f
    val textAlign: TextAlign get() = if (alignLeft) TextAlign.Start else TextAlign.Center
}

/**
 * The faces travel with the app (res/font), so every device draws the same text whatever font
 * it has set — a device that replaces its system fonts (Hisense) used to give its own face and
 * weight to "sans-serif". Roboto Light for sans (the Light Phone look), Literata for serif,
 * Roboto Mono for mono; bold is only drawn in the pages (headings, leads).
 */
val RobotoFamily = FontFamily(Font(R.font.roboto_light, FontWeight.Light), Font(R.font.roboto_bold, FontWeight.Bold))
val LiterataFamily = FontFamily(Font(R.font.literata_regular, FontWeight.Normal), Font(R.font.literata_bold, FontWeight.Bold))
val RobotoMonoFamily = FontFamily(Font(R.font.roboto_mono_regular, FontWeight.Normal), Font(R.font.roboto_mono_bold, FontWeight.Bold))

val LocalColors = compositionLocalOf { ReaderColors(Color.Black, Color.White) }
val LocalTypo = compositionLocalOf { ReaderTypography(RobotoFamily, FontWeight.Light, 28.sp, true) }
val LocalHaptics = compositionLocalOf { true }

@Composable
fun ReaderTheme(settings: Settings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val p = Palettes.of(settings.theme, systemDark)
    val colors = ReaderColors(Color(p.bg), Color(p.fg), Color(p.accent), Color(p.onAccent))
    val family = when (settings.font) {
        FontChoice.SERIF -> LiterataFamily
        FontChoice.SANS -> RobotoFamily
        FontChoice.MONO -> RobotoMonoFamily
    }
    // Roboto Light for sans (the Light Phone look); Literata and Roboto Mono in regular weight.
    val weight = if (settings.font == FontChoice.SANS) FontWeight.Light else FontWeight.Normal
    val size = when (settings.textSize) {
        TextSize.SMALL -> 24.sp
        TextSize.MEDIUM -> 28.sp
        TextSize.LARGE -> 32.sp
    }
    val typo = ReaderTypography(family, weight, size, settings.align == Align.LEFT)
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypo provides typo,
        LocalHaptics provides settings.haptics,
        content = content
    )
}

/** The name of a theme, as the settings row and the menu show it. */
@Composable
fun themeName(m: ThemeMode): String = stringResource(when (m) {
    ThemeMode.DARK -> R.string.theme_dark; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LAVANDE -> R.string.theme_lavande; ThemeMode.SAUGE -> R.string.theme_sauge; ThemeMode.NORDIQUE -> R.string.theme_nordique
    ThemeMode.TERRACOTTA -> R.string.theme_terracotta; ThemeMode.COQUETTE -> R.string.theme_coquette; ThemeMode.MILITANTE -> R.string.theme_militante
    ThemeMode.TORRIDE -> R.string.theme_torride; ThemeMode.NATURE -> R.string.theme_nature
})

@Composable
fun tileTextStyle(): TextStyle {
    val t = LocalTypo.current
    return TextStyle(
        color = LocalColors.current.fg,
        fontFamily = t.family,
        fontWeight = t.weight,
        fontSize = t.tile,
        lineHeight = t.tile * 1.25f,
        textAlign = t.textAlign
    )
}
