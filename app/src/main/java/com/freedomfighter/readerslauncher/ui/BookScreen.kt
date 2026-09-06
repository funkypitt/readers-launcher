package com.freedomfighter.readerslauncher.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.books.Book
import com.freedomfighter.readerslauncher.books.BookSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val BOOK_MIME = arrayOf(
    "application/epub+zip", "application/x-mobipocket-ebook", "application/vnd.amazon.ebook",
    "application/x-fictionbook+xml", "text/plain", "application/octet-stream", "*/*"
)

/** Default reading size follows the screen: about one twentieth of its width, clamped. */
@Composable
fun defaultReaderSp(): Int {
    val w = LocalConfiguration.current.smallestScreenWidthDp
    return (w / 20).coerceIn(17, 24)
}

/**
 * One of the two book slots. Empty → an invitation to open a book. Otherwise a paginated
 * plain-text reader: tap right half = next page, left half = previous, long press = menu,
 * horizontal swipe = back to the home screen.
 */
@Composable
fun BookScreen(nav: Nav, app: App, slotIndex: Int) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val scope = rememberCoroutineScope()
    val books by app.books.state.collectAsState()
    val slot = books.slots.getOrNull(slotIndex)
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    BackHandler { nav.pop() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true; importError = false
            scope.launch {
                val r = withContext(Dispatchers.IO) { app.books.importInto(slotIndex, uri) }
                importing = false
                importError = r.isFailure
            }
        }
    }
    fun pick() = runCatching { picker.launch(BOOK_MIME) }

    val swipeBack = Modifier.pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (abs(total) > 80.dp.toPx()) nav.pop() },
            onHorizontalDrag = { change, delta -> total += delta; change.consume() }
        )
    }

    Page {
        when {
            importing -> Box(Modifier.fillMaxSize().then(swipeBack), contentAlignment = Alignment.Center) {
                T(stringResource(R.string.reader_loading), color = colors.dim, align = TextAlign.Center)
            }
            slot == null -> Box(
                Modifier.fillMaxSize().then(swipeBack).noRippleClickable { pick() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(rowPadH)) {
                    T(stringResource(R.string.reader_open_book), align = TextAlign.Center)
                    VSpace(12.dp)
                    Small(
                        if (importError) stringResource(R.string.reader_unsupported) else stringResource(R.string.reader_hint_formats),
                        align = TextAlign.Center, maxLines = 3
                    )
                    VSpace(40.dp)
                    Small(stringResource(R.string.reader_hint_back), align = TextAlign.Center)
                }
            }
            else -> Reader(app, slotIndex, slot, swipeBack, onMenu = { menu = true }, onOpen = { pick() })
        }
        if (menu && slot != null) {
            val settings by app.prefs.settings.collectAsState()
            val current = if (settings.readerSp > 0) settings.readerSp else defaultReaderSp()
            TextMenu(
                title = slot.title,
                items = buildList {
                    add(MenuItem(stringResource(R.string.reader_menu_chapters)) { nav.push(Screen.BookChapters(slotIndex)) })
                    add(MenuItem(stringResource(R.string.reader_menu_larger), "$current → ${(current + 2).coerceAtMost(40)}") { app.prefs.setReaderSp((current + 2).coerceAtMost(40)) })
                    add(MenuItem(stringResource(R.string.reader_menu_smaller), "$current → ${(current - 2).coerceAtLeast(12)}") { app.prefs.setReaderSp((current - 2).coerceAtLeast(12)) })
                    add(MenuItem(stringResource(R.string.reader_menu_open)) { pick() })
                    add(MenuItem(stringResource(R.string.reader_menu_remove)) { app.books.clear(slotIndex) })
                },
                footer = listOf(MenuItem(stringResource(R.string.reader_menu_home)) { nav.pop() }),
                onDismiss = { menu = false }
            )
        }
    }
}

/**
 * Text layout of one chapter at the current width/size, plus its page boundaries (line indices).
 * Built on android.text.StaticLayout, the same engine used by working open-source readers:
 * getLineTop/getLineBottom describe exactly what StaticLayout.draw paints, so a page holds
 * only whole lines and nothing is clipped or repeated.
 */
private class ChapterLayout(val layout: StaticLayout, val pageStarts: List<Int>, val pageHeight: Int) {
    val pageCount: Int get() = pageStarts.size
    fun pageTop(page: Int): Int = layout.getLineTop(pageStarts[page])
    fun pageBottom(page: Int): Int {
        val lastLine = if (page + 1 < pageStarts.size) pageStarts[page + 1] - 1 else layout.lineCount - 1
        return layout.getLineBottom(lastLine)
    }
    fun pageStartChar(page: Int): Int = layout.getLineStart(pageStarts[page])
    fun pageFor(charOffset: Int): Int {
        var p = 0
        for (i in pageStarts.indices) if (pageStartChar(i) <= charOffset) p = i else break
        return p
    }
}

private fun buildLayout(text: CharSequence, paint: TextPaint, widthPx: Int, spacingMul: Float): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx.coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, spacingMul)
        .setIncludePad(false)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
        .build()

private fun paginate(layout: StaticLayout, pageHeight: Int): ChapterLayout {
    val starts = ArrayList<Int>()
    var line = 0
    while (line < layout.lineCount) {
        starts += line
        val top = layout.getLineTop(line)
        var l = line
        // A line belongs to the page only if its whole box fits.
        while (l < layout.lineCount && layout.getLineBottom(l) - top <= pageHeight) l++
        if (l == line) l++ // a single line taller than the page
        line = l
    }
    if (starts.isEmpty()) starts += 0
    return ChapterLayout(layout, starts, pageHeight)
}

@Composable
private fun Reader(app: App, slotIndex: Int, slot: BookSlot, swipeBack: Modifier, onMenu: () -> Unit, onOpen: () -> Unit) {
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val settings by app.prefs.settings.collectAsState()
    val tick = rememberTick()
    val readerSp = if (settings.readerSp > 0) settings.readerSp else defaultReaderSp()

    val book by produceState<Result<Book>?>(null, slot.fileName) {
        value = withContext(Dispatchers.IO) { runCatching { app.books.book(slot) } }
    }
    val loaded = book
    if (loaded == null) {
        Box(Modifier.fillMaxSize().then(swipeBack), contentAlignment = Alignment.Center) {
            T(stringResource(R.string.reader_loading), color = colors.dim, align = TextAlign.Center)
        }
        return
    }
    val b = loaded.getOrNull()
    if (b == null) {
        Box(Modifier.fillMaxSize().then(swipeBack).noRippleClickable { onOpen() }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(rowPadH)) {
                T(stringResource(R.string.reader_unsupported), align = TextAlign.Center)
                VSpace(12.dp)
                Small(stringResource(R.string.reader_open_book), align = TextAlign.Center)
            }
        }
        return
    }

    // Keep the screen on while a page is open: a slow reader must never be locked out mid-page.
    // Released when leaving the book, and after 15 minutes without a page turn.
    var lastTurn by remember { mutableStateOf(System.currentTimeMillis()) }
    KeepScreenOn(lastTurn)

    var chapter by remember(slot.fileName) { mutableStateOf(slot.chapter.coerceIn(0, b.chapters.size - 1)) }
    // The character we want at the top of the page; re-resolved whenever the layout changes.
    var wantedChar by remember(slot.fileName) { mutableStateOf(slot.charOffset) }
    var page by remember(slot.fileName) { mutableStateOf(0) }

    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().then(swipeBack)) {
        val hPad = 24.dp
        val topPad = 20.dp
        val bottomPad = 20.dp
        val statusBar = WindowInsets.statusBars.asPaddingValuesTop()
        val navBar = WindowInsets.navigationBars.asPaddingValuesBottom()
        val headerHeight = 22.dp
        val footerHeight = 22.dp
        val widthPx = with(density) { (maxWidth - hPad * 2).roundToPx() }
        // The page is paginated against the text area's *measured* height; the estimate below only
        // serves the first frame. Any drift (hidden status bar, rounding) would otherwise clip the
        // last line and repeat it on the next page.
        val estimatedHeightPx = with(density) {
            (maxHeight - statusBar - navBar - topPad - bottomPad - headerHeight - footerHeight).toPx()
        }
        var canvasHeight by remember { mutableStateOf(0) }
        val pageHeightPx = if (canvasHeight > 0) canvasHeight.toFloat() else estimatedHeightPx
        val fgArgb = colors.fg.toArgb()
        val paint = remember(readerSp, typo.family, typo.weight, fgArgb, density) {
            TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = fgArgb
                textSize = with(density) { readerSp.sp.toPx() }
                typeface = when (typo.family) {
                    FontFamily.Serif -> Typeface.SERIF
                    FontFamily.Monospace -> Typeface.MONOSPACE
                    else -> if (typo.weight == FontWeight.Light) Typeface.create("sans-serif-light", Typeface.NORMAL) else Typeface.SANS_SERIF
                }
            }
        }
        val ch = b.chapters[chapter]
        val text = remember(ch) { chapterText(ch) }
        val layout = remember(text, widthPx, paint, pageHeightPx) {
            paginate(buildLayout(text, paint, widthPx, 1.45f), pageHeightPx.toInt())
        }
        // Resolve the wanted character into a page whenever the layout (size, width) changes.
        LaunchedEffect(layout) { page = layout.pageFor(wantedChar).coerceIn(0, layout.pageCount - 1) }
        LaunchedEffect(chapter, page, layout) {
            if (page in 0 until layout.pageCount) app.books.savePosition(slotIndex, chapter, layout.pageStartChar(page))
        }

        fun goTo(c: Int, charOffset: Int) { chapter = c; wantedChar = charOffset }
        fun next() {
            lastTurn = System.currentTimeMillis()
            if (page + 1 < layout.pageCount) { page++; wantedChar = layout.pageStartChar(page) }
            else if (chapter + 1 < b.chapters.size) goTo(chapter + 1, 0)
        }
        fun prev() {
            lastTurn = System.currentTimeMillis()
            if (page > 0) { page--; wantedChar = layout.pageStartChar(page) }
            else if (chapter > 0) goTo(chapter - 1, Int.MAX_VALUE)
        }

        val safePage = page.coerceIn(0, layout.pageCount - 1)
        val progress = ((b.charsBefore(chapter) + layout.pageStartChar(safePage)).toFloat() / b.totalLength.coerceAtLeast(1) * 100).toInt()

        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(layout, chapter) {
                    detectTapGestures(
                        onTap = { pos -> if (pos.x > size.width / 2) next() else prev() },
                        onLongPress = { tick(); onMenu() }
                    )
                }
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = hPad)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = topPad).height(headerHeight)) {
                Small(b.title, Modifier.weight(1f), maxLines = 1, align = TextAlign.Start)
            }
            Canvas(Modifier.weight(1f).fillMaxWidth().onSizeChanged { canvasHeight = it.height }) {
                drawIntoCanvas { c ->
                    val n = c.nativeCanvas
                    val top = layout.pageTop(safePage)
                    // Clip to the bottom of this page's last whole line, not to the canvas: the
                    // layout paints every line that intersects the clip, so clipping to the canvas
                    // would show a sliver of the next page's first line under the last one.
                    val bottom = layout.pageBottom(safePage)
                    n.save()
                    n.clipRect(0f, 0f, size.width, (bottom - top).toFloat().coerceAtMost(size.height))
                    n.translate(0f, -top.toFloat())
                    layout.layout.draw(n)
                    n.restore()
                }
            }
            Row(Modifier.fillMaxWidth().height(footerHeight).padding(bottom = 0.dp), verticalAlignment = Alignment.Bottom) {
                // A bare number means the format had no chapter heading: not worth a label.
                Small(if (ch.title.substringBefore(" · ").toIntOrNull() == null) ch.title else "", Modifier.weight(1f), maxLines = 1, align = TextAlign.Start)
                Small("${safePage + 1}/${layout.pageCount} · $progress%", maxLines = 1, align = TextAlign.End)
            }
            VSpace(bottomPad)
        }
    }
}

/**
 * One single paragraph for the whole chapter. Multi-paragraph layouts report line tops and
 * bottoms that do not match what is drawn once a line height is set (paragraph boundaries
 * trim differently), which clipped the last line of a page and repeated it on the next.
 * Indentation is therefore made of em spaces instead of a paragraph style.
 */
private fun chapterText(ch: Book.Chapter): String = buildString {
    ch.paragraphs.forEachIndexed { i, p ->
        if (i > 0) append("\n\u2003\u2003")
        append(p.replace("\n", "\n\u2003\u2003"))
    }
}

@Composable
private fun WindowInsets.asPaddingValuesTop(): androidx.compose.ui.unit.Dp =
    with(LocalDensity.current) { getTop(this).toDp() }

@Composable
private fun WindowInsets.asPaddingValuesBottom(): androidx.compose.ui.unit.Dp =
    with(LocalDensity.current) { getBottom(this).toDp() }

@Composable
private fun KeepScreenOn(lastTurn: Long) {
    val view = androidx.compose.ui.platform.LocalView.current
    val window = (view.context.findActivity())?.window
    androidx.compose.runtime.DisposableEffect(window) {
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(lastTurn, window) {
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        kotlinx.coroutines.delay(15 * 60_000L)
        window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

/** Table of contents: tap a chapter to jump there. */
@Composable
fun BookChaptersScreen(nav: Nav, app: App, slotIndex: Int) {
    val books by app.books.state.collectAsState()
    val slot = books.slots.getOrNull(slotIndex)
    BackHandler { nav.pop() }
    if (slot == null) { nav.pop(); return }
    val book by produceState<Book?>(null, slot.fileName) { value = withContext(Dispatchers.IO) { runCatching { app.books.book(slot) }.getOrNull() } }
    val listState = rememberLazyListState()
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(slot.title, onBack = { nav.pop() })
            // Long chapters are split into parts for pagination; the table of contents lists
            // only the real chapters (the current one is inverted, whichever part is open).
            val chapters = book?.chapters ?: emptyList()
            val entries = chapters.withIndex().filter { (i, ch) -> i == 0 || !ch.title.startsWith(chapters[i - 1].title.substringBefore(" · ") + " · ") }
            val currentEntry = entries.lastOrNull { it.index <= slot.chapter }?.index
            LazyColumn(state = listState, contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                items(entries, key = { it.index }) { (i, ch) ->
                    TextRow(ch.title, inverted = i == currentEntry, size = LocalTypo.current.title) {
                        app.books.savePosition(slotIndex, i, 0)
                        nav.pop()
                    }
                }
            }
        }
    }
}
