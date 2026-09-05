package com.freedomfighter.readerslauncher.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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

/** Text layout of one chapter at the current width/size, plus its page boundaries (line indices). */
private class ChapterLayout(val layout: TextLayoutResult, val pageStarts: List<Int>, val pageHeight: Float) {
    val pageCount: Int get() = pageStarts.size
    fun pageTop(page: Int): Float = layout.getLineTop(pageStarts[page])
    fun pageStartChar(page: Int): Int = layout.getLineStart(pageStarts[page])
    fun pageFor(charOffset: Int): Int {
        var p = 0
        for (i in pageStarts.indices) if (pageStartChar(i) <= charOffset) p = i else break
        return p
    }
}

private fun paginate(layout: TextLayoutResult, pageHeight: Float): ChapterLayout {
    val starts = ArrayList<Int>()
    var top = 0f
    var line = 0
    while (line < layout.lineCount) {
        starts += line
        top = layout.getLineTop(line)
        var l = line
        while (l < layout.lineCount && layout.getLineBottom(l) - top <= pageHeight + 0.5f) l++
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

    var chapter by remember(slot.fileName) { mutableStateOf(slot.chapter.coerceIn(0, b.chapters.size - 1)) }
    // The character we want at the top of the page; re-resolved whenever the layout changes.
    var wantedChar by remember(slot.fileName) { mutableStateOf(slot.charOffset) }
    var page by remember(slot.fileName) { mutableStateOf(0) }

    val measurer = rememberTextMeasurer()
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
        val pageHeightPx = with(density) {
            (maxHeight - statusBar - navBar - topPad - bottomPad - headerHeight - footerHeight).toPx()
        }
        val style = TextStyle(
            color = colors.fg,
            fontFamily = typo.family,
            fontWeight = typo.weight,
            fontSize = readerSp.sp,
            lineHeight = (readerSp * 1.45f).sp,
            textAlign = TextAlign.Start
        )
        val ch = b.chapters[chapter]
        val text = remember(ch) { chapterText(ch) }
        val layout = remember(text, widthPx, readerSp, typo.family, typo.weight, pageHeightPx, colors.fg) {
            paginate(measurer.measure(text, style, constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1))), pageHeightPx)
        }
        // Resolve the wanted character into a page whenever the layout (size, width) changes.
        LaunchedEffect(layout) { page = layout.pageFor(wantedChar).coerceIn(0, layout.pageCount - 1) }
        LaunchedEffect(chapter, page, layout) {
            if (page in 0 until layout.pageCount) app.books.savePosition(slotIndex, chapter, layout.pageStartChar(page))
        }

        fun goTo(c: Int, charOffset: Int) { chapter = c; wantedChar = charOffset }
        fun next() {
            if (page + 1 < layout.pageCount) { page++; wantedChar = layout.pageStartChar(page) }
            else if (chapter + 1 < b.chapters.size) goTo(chapter + 1, 0)
        }
        fun prev() {
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
            Canvas(Modifier.weight(1f).fillMaxWidth()) {
                clipRect {
                    drawText(layout.layout, topLeft = Offset(0f, -layout.pageTop(safePage)))
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

private fun chapterText(ch: Book.Chapter): AnnotatedString = buildAnnotatedString {
    ch.paragraphs.forEachIndexed { i, p ->
        withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = if (i == 0) 0.em else 1.4.em))) {
            append(p)
            if (i < ch.paragraphs.size - 1) append("\n")
        }
    }
}

@Composable
private fun WindowInsets.asPaddingValuesTop(): androidx.compose.ui.unit.Dp =
    with(LocalDensity.current) { getTop(this).toDp() }

@Composable
private fun WindowInsets.asPaddingValuesBottom(): androidx.compose.ui.unit.Dp =
    with(LocalDensity.current) { getBottom(this).toDp() }

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
