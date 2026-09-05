package com.freedomfighter.readerslauncher.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Single source of truth for the home screen. Kept as one JSON file so that
 * export / import is trivially the same file.
 */
class HomeStore(context: Context) {
    private val file = File(context.filesDir, "home.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<HomeState> = _state

    private fun load(): HomeState {
        if (!file.exists()) return HomeState()
        return try {
            json.decodeFromString(HomeState.serializer(), file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "home.json unreadable, starting fresh", e)
            file.copyTo(File(file.parentFile, "home.broken.json"), overwrite = true)
            HomeState()
        }
    }

    @Synchronized
    fun update(transform: (HomeState) -> HomeState) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        save(next)
    }

    private fun save(state: HomeState) {
        try {
            val tmp = File(file.parentFile, "home.json.tmp")
            tmp.writeText(json.encodeToString(HomeState.serializer(), state))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "cannot save home.json", e)
        }
    }

    fun exportJson(): String = json.encodeToString(HomeState.serializer(), _state.value)

    fun importJson(text: String): Boolean {
        return try {
            val parsed = json.decodeFromString(HomeState.serializer(), text)
            update { parsed }
            true
        } catch (e: Exception) {
            Log.w(TAG, "import failed", e)
            false
        }
    }

    // ---- convenience mutations -------------------------------------------------

    fun addTile(tile: Tile) = update { it.copy(tiles = it.tiles + tile) }

    fun removeTile(id: String) = update { s -> s.copy(tiles = s.tiles.filterNot { it.id == id }) }

    fun replaceTile(tile: Tile) = update { s ->
        s.copy(tiles = s.tiles.map { if (it.id == tile.id) tile else it })
    }

    fun moveTile(id: String, delta: Int) = update { s ->
        val list = s.tiles.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return@update s
        val t = list.removeAt(i)
        list.add(j, t)
        s.copy(tiles = list)
    }

    fun reorder(from: Int, to: Int) = update { s ->
        if (from == to || from !in s.tiles.indices || to !in s.tiles.indices) return@update s
        val list = s.tiles.toMutableList()
        val t = list.removeAt(from)
        list.add(to, t)
        s.copy(tiles = list)
    }

    fun setGrid(grid: Grid?) = update { it.copy(grid = grid) }

    fun setHidden(app: AppRef, hidden: Boolean) = update { s ->
        val h = s.hidden.filterNot { it == app }
        s.copy(hidden = if (hidden) h + app else h)
    }

    fun rename(app: AppRef, label: String?) = update { s ->
        val m = s.renames.toMutableMap()
        if (label.isNullOrBlank()) m.remove(app.key) else m[app.key] = label.trim()
        s.copy(renames = m)
    }

    /** Drop references to apps that no longer exist. Categories keep their name even if emptied. */
    fun purge(missing: (AppRef) -> Boolean) = update { s ->
        s.copy(
            tiles = s.tiles.mapNotNull { t ->
                when (t) {
                    is AppTile -> if (missing(t.app)) null else t
                    is CategoryTile -> t.copy(apps = t.apps.filterNot(missing))
                    else -> t
                }
            },
            grid = s.grid?.let { g -> g.copy(slots = g.slots.map { if (it != null && missing(it)) null else it }) },
            hidden = s.hidden.filterNot(missing)
        )
    }

    companion object {
        private const val TAG = "HomeStore"
    }
}
