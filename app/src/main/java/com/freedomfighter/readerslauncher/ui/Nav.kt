package com.freedomfighter.readerslauncher.ui

import androidx.compose.runtime.mutableStateListOf
import com.freedomfighter.readerslauncher.data.AppRef

/** How the app list is being used. */
sealed class PickMode {
    /** Regular app drawer (swipe up). */
    data object Drawer : PickMode()
    /** Select one app (grid slot, change app…). */
    data class Single(val onPicked: (AppRef) -> Unit) : PickMode()
    /** Select several apps (new tile or add to a category). */
    data class Multi(val preselected: List<AppRef> = emptyList(), val onPicked: (List<AppRef>) -> Unit) : PickMode()
}

sealed class Screen {
    data object Home : Screen()
    data class Apps(val mode: PickMode) : Screen()
    data class Category(val tileId: String) : Screen()
    data class CategoryEdit(val tileId: String) : Screen()
    data object Settings : Screen()
    data object Arrange : Screen()
    data object Hidden : Screen()
    data object WidgetPicker : Screen()
    data class CalendarSetup(val tileId: String?) : Screen()
    data class TasksSetup(val tileId: String?) : Screen()
    data class MindfulSetup(val tileId: String?) : Screen()
    data object Recents : Screen()
    /** slot 0 = left of home (swipe right), 1 = right of home (swipe left). */
    data class Book(val slot: Int) : Screen()
    data class BookChapters(val slot: Int) : Screen()
    data object Word : Screen()
    data class Agenda(val tileId: String) : Screen()
}

/** Tiny back stack. The home screen is always at the bottom. */
class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun home() { while (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun replace(s: Screen) { pop(); push(s) }
}
