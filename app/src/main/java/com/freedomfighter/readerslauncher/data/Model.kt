package com.freedomfighter.readerslauncher.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** Identifies one launchable activity, possibly in another user profile (work profile). */
@Serializable
data class AppRef(
    val packageName: String,
    val activity: String,
    /** UserManager serial number; -1 means the main user. */
    val user: Long = -1L
) {
    val key: String get() = "$packageName/$activity/$user"
}

/** Everything that can sit in the single column of the home screen. */
@Serializable
sealed class Tile {
    abstract val id: String
}

@Serializable
@SerialName("app")
data class AppTile(override val id: String = newId(), val app: AppRef) : Tile()

@Serializable
@SerialName("category")
data class CategoryTile(
    override val id: String = newId(),
    val name: String,
    val apps: List<AppRef>
) : Tile()

@Serializable
@SerialName("clock")
data class ClockTile(override val id: String = newId()) : Tile()

/** The Littré's word of the day (read from the dictionary app). */
@Serializable
@SerialName("word")
data class WordTile(override val id: String = newId()) : Tile()

/** The book being read (from Reader's Books' provider). */
@Serializable
@SerialName("book")
data class BookTile(override val id: String = newId()) : Tile()

/** The latest notes (from Reader's Notes' provider), swipe for the next. */
@Serializable
@SerialName("notes")
data class NotesTile(override val id: String = newId()) : Tile()

/** Reader's Mindful Tool: a bowl every X minutes and/or a timed meditation, started from the tile. */
@Serializable
@SerialName("mindful")
data class MindfulTile(
    override val id: String = newId(),
    /** "both" (swipe between the two), "interval" or "period". */
    val mode: String = "both",
    val intervalMin: Int = 10,
    val durationMin: Int = 20
) : Tile()

@Serializable
data class Place(val name: String, val latitude: Double, val longitude: Double)

@Serializable
@SerialName("weather")
data class WeatherTile(
    override val id: String = newId(),
    /** null = automatic location. */
    val place: Place? = null,
    val fahrenheit: Boolean = false,
    val fiveDays: Boolean = false
) : Tile()

@Serializable
@SerialName("calendar")
data class CalendarTile(
    override val id: String = newId(),
    val calendarIds: List<Long>,
    /** Package of the calendar app the tile opens; "" = whatever the system picks. */
    val app: String = ""
) : Tile()

@Serializable
@SerialName("tasks")
data class TasksTile(
    override val id: String = newId(),
    val listId: String,
    val listTitle: String,
    /** "tasksorg" (default, older tiles) or "readers". */
    val source: String = "tasksorg"
) : Tile()

@Serializable
@SerialName("appwidget")
data class AppWidgetTile(
    override val id: String = newId(),
    val appWidgetId: Int,
    /** Legacy height in 72 dp units; superseded by [cells]. */
    val height: Int = 2,
    val label: String = "",
    /** Height in page cells (half text rows); 0 = derive from the legacy [height]. */
    val cells: Int = 0
) : Tile() {
    fun cellCount(): Int = if (cells > 0) cells else (height * 2).coerceIn(2, 12)
}

/** The "key apps" grid pinned at the bottom: 3 to 5 squares, each optionally holding an app. */
@Serializable
data class Grid(
    val columns: Int = 5,
    val slots: List<AppRef?> = List(5) { null }
) {
    fun slot(i: Int): AppRef? = slots.getOrNull(i)
    fun withSlot(i: Int, app: AppRef?): Grid {
        val s = slots.toMutableList()
        while (s.size < 5) s.add(null)
        s[i] = app
        return copy(slots = s)
    }
}

/** A further home page, to the right of the first; the right-hand book slot sits after the last one. */
@Serializable
data class HomePage(val id: String = newId(), val tiles: List<Tile> = emptyList())

@Serializable
data class HomeState(
    /** The first home page. */
    val tiles: List<Tile> = emptyList(),
    /** The pages after it, in order. */
    val morePages: List<HomePage> = emptyList(),
    val grid: Grid? = null,
    val hidden: List<AppRef> = emptyList(),
    /** Custom labels keyed by AppRef.key. */
    val renames: Map<String, String> = emptyMap(),
    val version: Int = 1
) {
    val pageCount: Int get() = 1 + morePages.size
    fun page(i: Int): List<Tile> = if (i <= 0) tiles else morePages.getOrNull(i - 1)?.tiles ?: emptyList()
    val allTiles: List<Tile> get() = tiles + morePages.flatMap { it.tiles }
    fun withPage(i: Int, list: List<Tile>): HomeState =
        if (i <= 0) copy(tiles = list) else copy(morePages = morePages.mapIndexed { k, p -> if (k == i - 1) p.copy(tiles = list) else p })
    /** Index of the page holding this tile (0 when unknown). */
    fun pageOf(id: String): Int {
        if (tiles.any { it.id == id }) return 0
        val k = morePages.indexOfFirst { p -> p.tiles.any { it.id == id } }
        return if (k < 0) 0 else k + 1
    }
}

fun newId(): String = UUID.randomUUID().toString()
