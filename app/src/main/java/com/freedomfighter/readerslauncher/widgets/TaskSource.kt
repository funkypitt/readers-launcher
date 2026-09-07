package com.freedomfighter.readerslauncher.widgets

import android.content.Context
import android.net.Uri

/** What the tasks tile needs from a task app. Two implementations: Reader's Tasks and Tasks.org. */
interface TaskSource {
    data class TaskList(val id: String, val title: String, val account: String)
    data class Task(val id: String, val title: String)

    val id: String
    val label: String
    val isInstalled: Boolean
    val ready: Boolean
    val permissions: Array<String>
    val observedUris: List<Uri>
    fun lists(): List<TaskList>
    fun openTasks(listId: String, limit: Int = 50): List<Task>
    /** True when done in place; false when the caller should open the task in the app instead. */
    fun complete(listId: String, task: Task): Boolean
    /** True when inserted in place; false when the caller should hand the title to the app. */
    fun insert(listId: String, title: String): Boolean
    fun openTask(listId: String, task: Task)
    fun newTaskInApp(title: String)
    fun openApp()

    companion object {
        fun of(context: Context, id: String?): TaskSource =
            if (id == "readers") ReadersTasks(context.applicationContext) else TasksOrgSource(context.applicationContext)
        fun all(context: Context): List<TaskSource> = listOf(ReadersTasks(context.applicationContext), TasksOrgSource(context.applicationContext))
    }
}

/** Tasks.org behind the common interface. */
class TasksOrgSource(context: Context) : TaskSource {
    private val repo = TasksOrg.get(context)
    override val id = "tasksorg"
    override val label = "Tasks.org"
    override val isInstalled: Boolean get() = repo.isInstalled
    override val ready: Boolean get() = repo.ready
    override val permissions: Array<String> get() = TasksOrg.PERMISSIONS
    override val observedUris: List<Uri> get() = listOf(TasksOrg.API_TASKS_URI, TasksOrg.LEGACY_URI)
    override fun lists() = repo.lists().map { TaskSource.TaskList(it.id.toString(), it.title, it.account) }
    override fun openTasks(listId: String, limit: Int) = repo.openTasks(listId.toLongOrNull() ?: -1L, limit).map { TaskSource.Task(it.id.toString(), it.title) }
    override fun complete(listId: String, task: TaskSource.Task): Boolean { val id = task.id.toLongOrNull() ?: return false; return repo.complete(id) }
    override fun insert(listId: String, title: String): Boolean { val id = listId.toLongOrNull() ?: return false; return repo.insert(id, title) }
    override fun openTask(listId: String, task: TaskSource.Task) { task.id.toLongOrNull()?.let { repo.openTask(it) } ?: repo.openApp() }
    override fun newTaskInApp(title: String) = repo.newTaskInApp(title)
    override fun openApp() = repo.openApp()
    fun openStore() = repo.openStore()
    val apiAvailable: Boolean get() = repo.apiAvailable
}
