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
    val calendarIds: List<Long>
) : Tile()

@Serializable
@SerialName("tasks")
data class TasksTile(
    override val id: String = newId(),
    val listId: String,
    val listTitle: String
) : Tile()

@Serializable
@SerialName("appwidget")
data class AppWidgetTile(
    override val id: String = newId(),
    val appWidgetId: Int,
    /** Height in tile units (1 = one text tile, up to 4). */
    val height: Int = 2,
    val label: String = ""
) : Tile()

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

@Serializable
data class HomeState(
    val tiles: List<Tile> = emptyList(),
    val grid: Grid? = null,
    val hidden: List<AppRef> = emptyList(),
    /** Custom labels keyed by AppRef.key. */
    val renames: Map<String, String> = emptyMap(),
    val version: Int = 1
)

fun newId(): String = UUID.randomUUID().toString()
