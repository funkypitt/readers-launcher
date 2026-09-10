package com.freedomfighter.readerslauncher.widgets

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.ui.LocalColors
import com.freedomfighter.readerslauncher.ui.LocalTypo
import com.freedomfighter.readerslauncher.ui.Small
import com.freedomfighter.readerslauncher.ui.T
import com.freedomfighter.readerslauncher.ui.noRippleClickable
import com.freedomfighter.readerslauncher.ui.rememberTick
import com.freedomfighter.readerslauncher.ui.rowPadH
import com.freedomfighter.readerslauncher.ui.rowPadV
import com.freedomfighter.readerslauncher.ui.widgetTwoLineHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Re-query whenever the provider announces a change. */
@Composable
internal fun rememberProviderGeneration(uri: android.net.Uri): Int {
    val context = LocalContext.current
    var generation by remember { mutableIntStateOf(0) }
    DisposableEffect(uri) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { generation++ } }
        val cr = context.contentResolver
        runCatching { cr.registerContentObserver(uri, true, observer) }
        onDispose { runCatching { cr.unregisterContentObserver(observer) } }
    }
    return generation
}

/** The book being read, a dim "book" under it; swipe sideways for the ones opened before; tap carries on at the current page. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookTileView(onLongPress: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val tick = rememberTick()
    val generation = rememberProviderGeneration(ReadersBooks.URI)
    val now = rememberNow()
    val books by produceState<List<ReadersBooks.Book>?>(null, generation, now / (10 * 60_000)) {
        value = withContext(Dispatchers.IO) { ReadersBooks.all(context) }
    }
    var index by remember { mutableIntStateOf(0) }
    val list = books ?: emptyList()
    if (index >= list.size) index = maxOf(0, list.size - 1)
    val book = list.getOrNull(index)
    val installed = ReadersBooks.isInstalled(context)
    val label = book?.title ?: if (installed) stringResource(R.string.book_none) else "Reader's Books"
    val caption = stringResource(R.string.widget_book) + (if (list.size > 1) " ${index + 1}/${list.size}" else "") + (book?.takeIf { it.progress > 0 }?.let { " · ${it.progress}%" } ?: "")
    Column(
        Modifier
            .fillMaxWidth()
            .height(widgetTwoLineHeight())
            // Swipe left → the book opened before this one, right → the more recent one; the
            // drag is consumed here so the home screen does not read it as a page or book-slot swipe.
            .pointerInput(list.size) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (abs(total) > 60.dp.toPx()) {
                            val next = if (total < 0) index + 1 else index - 1
                            if (next in list.indices) { index = next; tick() }
                        }
                    },
                    onHorizontalDrag = { change, delta -> total += delta; change.consume() }
                )
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { ReadersBooks.open(context, book) },
                onLongClick = onLongPress
            )
            .padding(horizontal = rowPadH, vertical = rowPadV),
        verticalArrangement = Arrangement.Center
    ) {
        T(label, maxLines = 1, color = if (book != null) colors.fg else colors.dim)
        Small(caption, maxLines = 1)
    }
}

/** The latest note, swipe sideways for the others, + writes a new one, tap opens. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesTileView(onLongPress: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val tick = rememberTick()
    val generation = rememberProviderGeneration(ReadersNotes.URI)
    val now = rememberNow()
    val notes by produceState<List<ReadersNotes.Note>?>(null, generation, now / (10 * 60_000)) {
        value = withContext(Dispatchers.IO) { ReadersNotes.notes(context) }
    }
    var index by remember { mutableIntStateOf(0) }
    val list = notes ?: emptyList()
    if (index >= list.size) index = maxOf(0, list.size - 1)
    val note = list.getOrNull(index)
    val installed = ReadersNotes.isInstalled(context)
    Row(
        Modifier
            .fillMaxWidth()
            .height(widgetTwoLineHeight())
            // Swipe left → next note, right → previous; consumed here so the home screen does
            // not read it as a book-slot swipe (same as the agenda and tasks tiles).
            .pointerInput(list.size) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (abs(total) > 60.dp.toPx()) {
                            val next = if (total < 0) index + 1 else index - 1
                            if (next in list.indices) { index = next; tick() }
                        }
                    },
                    onHorizontalDrag = { change, delta -> total += delta; change.consume() }
                )
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { ReadersNotes.open(context, note?.id) },
                onLongClick = onLongPress
            )
            .padding(horizontal = rowPadH, vertical = rowPadV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            when {
                !installed -> { T("Reader's Notes", maxLines = 1, color = colors.dim); Small(stringResource(R.string.widget_notes), maxLines = 1) }
                note == null -> { T(stringResource(R.string.notes_none), maxLines = 1, color = colors.dim); Small(stringResource(R.string.widget_notes), maxLines = 1) }
                else -> {
                    T(note.title.ifBlank { stringResource(R.string.notes_untitled) }, maxLines = 1)
                    Small(stringResource(R.string.widget_notes) + (if (list.size > 1) " ${index + 1}/${list.size}" else "") + (if (note.preview.isNotEmpty()) " · " + note.preview else ""), maxLines = 1)
                }
            }
        }
        // a generous target: the glyph is small, the tap should not need to be precise
        if (installed) T("+", Modifier.noRippleClickable { ReadersNotes.open(context, null) }.padding(start = 24.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), size = typo.tile, align = TextAlign.End)
    }
}
