package com.freedomfighter.readerslauncher

import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import com.freedomfighter.readerslauncher.apps.AppRepository
import com.freedomfighter.readerslauncher.books.BookStore
import com.freedomfighter.readerslauncher.data.HomeStore
import com.freedomfighter.readerslauncher.data.Prefs

class App : Application() {
    /** Cells the home page holds, as last measured (0 = unknown). */
    var pageCells: Int = 0
    /** Bumped after a task is added from the home prompt, so the tasks tile reloads. */
    val tasksChanged = kotlinx.coroutines.flow.MutableStateFlow(0)
    lateinit var prefs: Prefs
    lateinit var store: HomeStore
    lateinit var apps: AppRepository
    lateinit var books: BookStore
    lateinit var widgetHost: AppWidgetHost
    lateinit var widgetManager: AppWidgetManager

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        store = HomeStore(this)
        apps = AppRepository(this)
        books = BookStore(this)
        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(this, WIDGET_HOST_ID)
    }

    companion object {
        const val WIDGET_HOST_ID = 0x52454144 // "READ"
    }
}
