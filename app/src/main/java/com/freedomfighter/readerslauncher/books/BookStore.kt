package com.freedomfighter.readerslauncher.books

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** One of the two book slots: left of the home screen (0) or right of it (1). */
@Serializable
data class BookSlot(
    val fileName: String,
    val title: String,
    val format: BookFormat,
    val chapter: Int = 0,
    val charOffset: Int = 0
)

@Serializable
data class BooksState(val slots: List<BookSlot?> = listOf(null, null))

/**
 * Books are copied into app storage when opened (no lingering document permissions), and
 * the reading position is one (chapter, character offset) pair per slot — independent of
 * screen size and text size.
 */
class BookStore(private val context: Context) {
    private val file = File(context.filesDir, "books.json")
    private val dir = File(context.filesDir, "books").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<BooksState> = _state

    /** Parsed books kept in memory per slot; invalidated when the slot changes file. */
    private val cache = HashMap<String, Book>()

    private fun load(): BooksState = try {
        if (file.exists()) json.decodeFromString(BooksState.serializer(), file.readText()) else BooksState()
    } catch (e: Exception) {
        Log.w(TAG, "books.json unreadable", e); BooksState()
    }

    @Synchronized
    private fun update(transform: (BooksState) -> BooksState) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        runCatching { file.writeText(json.encodeToString(BooksState.serializer(), next)) }
    }

    fun slot(i: Int): BookSlot? = _state.value.slots.getOrNull(i)

    fun savePosition(i: Int, chapter: Int, charOffset: Int) = update { s ->
        val slots = s.slots.toMutableList()
        val cur = slots.getOrNull(i) ?: return@update s
        slots[i] = cur.copy(chapter = chapter, charOffset = charOffset)
        s.copy(slots = slots)
    }

    fun clear(i: Int) = update { s ->
        val slots = s.slots.toMutableList()
        slots.getOrNull(i)?.let { File(dir, it.fileName).delete(); cache.remove(it.fileName) }
        slots[i] = null
        s.copy(slots = slots)
    }

    /** Copy the chosen document into the slot, parse it once to validate, and remember it. Blocking. */
    fun importInto(i: Int, uri: Uri): Result<BookSlot> = runCatching {
        val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "book"
        val tmp = File(dir, "import.tmp")
        context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            ?: throw IllegalStateException("cannot read")
        val format = BookParser.detect(tmp, displayName)
        val fallbackTitle = displayName.substringBeforeLast('.').replace('_', ' ')
        val book = BookParser.parse(tmp, format, fallbackTitle)
        val fileName = (if (i == 0) "left" else "right") + "." + format.name.lowercase()
        val target = File(dir, fileName)
        slot(i)?.let { File(dir, it.fileName).delete(); cache.remove(it.fileName) }
        if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
        cache[fileName] = book
        val slot = BookSlot(fileName, book.title, format)
        update { s -> s.copy(slots = s.slots.toMutableList().also { it[i] = slot }) }
        slot
    }

    /** Parse (or fetch from cache) the book of a slot. Blocking; call off the main thread. */
    fun book(slot: BookSlot): Book {
        cache[slot.fileName]?.let { return it }
        val book = BookParser.parse(File(dir, slot.fileName), slot.format, slot.title)
        cache[slot.fileName] = book
        return book
    }

    companion object { private const val TAG = "BookStore" }
}
