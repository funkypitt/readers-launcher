package com.freedomfighter.readerslauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.freedomfighter.readerslauncher.R
import androidx.compose.ui.res.stringResource

// ---------------------------------------------------------------------------------------------
// Text primitives. Everything on screen goes through these so the look stays uniform.
// ---------------------------------------------------------------------------------------------

@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = LocalTypo.current.tile,
    color: Color = LocalColors.current.fg,
    align: TextAlign = LocalTypo.current.textAlign,
    maxLines: Int = Int.MAX_VALUE,
    lineHeightMul: Float = 1.25f
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontFamily = LocalTypo.current.family,
            fontWeight = LocalTypo.current.weight,
            fontSize = size,
            lineHeight = size * lineHeightMul,
            textAlign = align
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun Small(text: String, modifier: Modifier = Modifier, color: Color = LocalColors.current.dim, maxLines: Int = 2, align: TextAlign = LocalTypo.current.textAlign) =
    T(text, modifier, size = LocalTypo.current.small, color = color, maxLines = maxLines, align = align)

/** Hairline rule in the foreground colour. */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = LocalColors.current.rule) {
    val c = color
    Canvas(modifier.fillMaxWidth().height(1.dp)) { drawRect(c) }
}

/** Padding used by every text row. */
val rowPadH = 28.dp
val rowPadV = 20.dp

fun Modifier.noRippleClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
)

/** A tappable line of text — the universal control of this launcher. */
@Composable
fun TextRow(
    text: String,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
    secondary: String? = null,
    size: TextUnit = LocalTypo.current.tile,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalColors.current
    val bg = if (inverted) colors.fg else Color.Transparent
    val fg = if (inverted) colors.bg else colors.fg
    val dim = if (inverted) colors.bg.copy(alpha = 0.6f) else colors.dim
    Column(
        modifier
            .fillMaxWidth()
            .background(bg)
            .then(if (onClick != null) Modifier.noRippleClickable(onClick = onClick) else Modifier)
            .padding(horizontal = rowPadH, vertical = rowPadV * 0.7f)
    ) {
        T(text, size = size, color = fg, maxLines = 1)
        if (secondary != null) Small(secondary, color = dim, maxLines = 1)
    }
}

/** Title line at the top of a screen. Tapping it goes back. */
@Composable
fun ScreenTitle(title: String, onBack: (() -> Unit)?, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    val colors = LocalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = rowPadH, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).then(if (onBack != null) Modifier.noRippleClickable(onClick = onBack) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                T("←", size = LocalTypo.current.title, color = colors.dim, align = TextAlign.Start)
                Spacer(Modifier.height(0.dp).padding(horizontal = 8.dp))
            }
            T(title, size = LocalTypo.current.title, color = colors.dim, maxLines = 1, align = TextAlign.Start)
        }
        if (trailing != null) {
            T(
                trailing,
                Modifier.then(if (onTrailing != null) Modifier.noRippleClickable(onClick = onTrailing) else Modifier),
                size = LocalTypo.current.title,
                align = TextAlign.End
            )
        }
    }
    Rule()
}

/** Full-screen page frame with the theme background. */
@Composable
fun Page(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(LocalColors.current.bg)) { content() }
}

// ---------------------------------------------------------------------------------------------
// Menus and prompts: text-only bottom sheets.
// ---------------------------------------------------------------------------------------------

data class MenuItem(val label: String, val secondary: String? = null, val action: () -> Unit)

/**
 * A menu is a sheet of text lines anchored at the bottom, above a scrim.
 * Tapping outside or pressing back dismisses it.
 */
@Composable
fun TextMenu(title: String?, items: List<MenuItem>, onDismiss: () -> Unit, footer: List<MenuItem> = emptyList()) {
    val colors = LocalColors.current
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg.copy(alpha = 0.6f))
            .noRippleClickable(onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colors.bg)
                .noRippleClickable { }
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Rule(color = colors.fg)
            if (title != null) {
                Small(title, Modifier.padding(horizontal = rowPadH).padding(top = 14.dp, bottom = 2.dp), maxLines = 1)
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { item ->
                    TextRow(item.label, secondary = item.secondary, onClick = {
                        onDismiss()
                        item.action()
                    })
                }
                if (footer.isNotEmpty()) {
                    Rule(Modifier.padding(vertical = 6.dp))
                    footer.forEach { item ->
                        TextRow(item.label, secondary = item.secondary, size = LocalTypo.current.title, onClick = {
                            onDismiss()
                            item.action()
                        })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Single-line text prompt (category name, city, task…). */
@Composable
fun TextPrompt(
    title: String,
    initial: String = "",
    confirm: String = stringResource(R.string.action_ok),
    onDone: (String) -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalColors.current
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    BackHandler(onBack = onCancel)
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg.copy(alpha = 0.6f))
            .noRippleClickable(onClick = onCancel)
            .imePadding()
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colors.bg)
                .noRippleClickable { }
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Rule(color = colors.fg)
            Small(title, Modifier.padding(horizontal = rowPadH).padding(top = 14.dp))
            ReaderTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = rowPadH, vertical = 10.dp).focusRequester(focus),
                imeAction = ImeAction.Done,
                onImeAction = { if (value.isNotBlank()) onDone(value.trim()) }
            )
            Rule()
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { TextRow(stringResource(R.string.action_cancel), onClick = onCancel) }
                Box(Modifier.weight(1f)) {
                    TextRow(confirm, inverted = value.isNotBlank(), onClick = { if (value.isNotBlank()) onDone(value.trim()) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ReaderTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    imeAction: ImeAction = ImeAction.Search,
    onImeAction: () -> Unit = {}
) {
    val colors = LocalColors.current
    val typo = LocalTypo.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = TextStyle(color = colors.fg, fontFamily = typo.family, fontWeight = typo.weight, fontSize = typo.tile),
        cursorBrush = SolidColor(colors.fg),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) T(placeholder, color = colors.dim, align = TextAlign.Start)
                inner()
            }
        }
    )
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

/**
 * Built-in widgets have a rigid height derived from the text size, never from their content,
 * so that swiping to another event or switching the weather to five days moves nothing else
 * on the home screen. Two flavours: a two-line text widget, and the tall clock/weather one.
 */
/**
 * The page grid. A cell is half a text row (half a line of tile-size text plus one row
 * padding), and every tile occupies a whole number of cells: a text row 2, a two-line
 * widget 3, the clock and the weather 4, an app widget its declared height rounded up.
 * The page holds a fixed number of cells, so "full" is exact and the leftover is always
 * smaller than one cell.
 */
@Composable
fun cellHeight(): Dp {
    val t = LocalTypo.current
    return with(LocalDensity.current) { (t.tile * 1.25f).toDp() } / 2 + rowPadV
}

fun tileCells(tile: com.freedomfighter.readerslauncher.data.Tile): Int = when (tile) {
    is com.freedomfighter.readerslauncher.data.AppTile, is com.freedomfighter.readerslauncher.data.CategoryTile -> 2
    is com.freedomfighter.readerslauncher.data.WordTile, is com.freedomfighter.readerslauncher.data.CalendarTile,
    is com.freedomfighter.readerslauncher.data.TasksTile, is com.freedomfighter.readerslauncher.data.BookTile,
    is com.freedomfighter.readerslauncher.data.NotesTile -> 3
    is com.freedomfighter.readerslauncher.data.ClockTile, is com.freedomfighter.readerslauncher.data.WeatherTile -> 4
    is com.freedomfighter.readerslauncher.data.AppWidgetTile -> tile.cellCount()
}

const val TEXT_ROW_CELLS = 2

@Composable
fun widgetTwoLineHeight(): Dp = cellHeight() * 3

@Composable
fun widgetTallHeight(): Dp = cellHeight() * 4

/** Text menu of an app's shortcuts (static + dynamic). */
@Composable
fun ShortcutsMenu(app: com.freedomfighter.readerslauncher.App, ref: com.freedomfighter.readerslauncher.data.AppRef, title: String, onDismiss: () -> Unit) {
    val shortcuts = remember(ref) { app.apps.shortcuts(ref) }
    TextMenu(
        title = title,
        items = if (shortcuts.isEmpty()) listOf(MenuItem(stringResource(R.string.shortcuts_none)) { })
        else shortcuts.map { info ->
            MenuItem((info.shortLabel ?: info.longLabel ?: info.id).toString(), info.longLabel?.toString()?.takeIf { it != info.shortLabel?.toString() }) {
                app.apps.startShortcut(info)
            }
        },
        footer = listOf(MenuItem(stringResource(R.string.menu_open)) { app.apps.launch(ref) }),
        onDismiss = onDismiss
    )
}

/** One haptic tick, if enabled. */
@Composable
fun rememberTick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    val enabled = LocalHaptics.current
    return remember(enabled) { { if (enabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }
}
