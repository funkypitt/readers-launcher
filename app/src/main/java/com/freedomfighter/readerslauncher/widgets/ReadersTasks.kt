package com.freedomfighter.readerslauncher.widgets

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Reader's Tasks (com.freedomfighter.readerstasks), the companion CalDAV client. Its provider is
 * signature-protected, so this launcher (same signing key) reads and writes without any prompt.
 */
class ReadersTasks(private val context: Context) : TaskSource {
    override val id = "readers"
    override val label = "Reader's Tasks"
    override val isInstalled: Boolean
        get() = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess
    override val ready: Boolean get() = isInstalled

    override fun lists(): List<TaskSource.TaskList> {
        val out = ArrayList<TaskSource.TaskList>()
        context.contentResolver.query(LISTS, null, null, null, null)?.use { c ->
            val title = c.getColumnIndex("title"); val url = c.getColumnIndex("url")
            while (c.moveToNext()) out += TaskSource.TaskList(c.getString(url), c.getString(title), "")
        }
        return out
    }

    override fun openTasks(listId: String, limit: Int): List<TaskSource.Task> {
        val out = ArrayList<TaskSource.Task>()
        context.contentResolver.query(tasksUri(listId), null, null, null, null)?.use { c ->
            val href = c.getColumnIndex("href"); val title = c.getColumnIndex("title")
            while (c.moveToNext() && out.size < limit) out += TaskSource.Task(c.getString(href), c.getString(title) ?: "")
        }
        return out
    }

    override fun complete(listId: String, task: TaskSource.Task): Boolean = runCatching {
        context.contentResolver.update(tasksUri(listId), ContentValues().apply { put("href", task.id); put("completed", 1) }, null, null) > 0
    }.onFailure { Log.w(TAG, "complete failed", it) }.getOrDefault(false)

    override fun insert(listId: String, title: String): Boolean = runCatching {
        context.contentResolver.insert(tasksUri(listId), ContentValues().apply { put("title", title) }) != null
    }.onFailure { Log.w(TAG, "insert failed", it) }.getOrDefault(false)

    override fun openTask(listId: String, task: TaskSource.Task) = openApp()
    override fun newTaskInApp(title: String) = openApp()
    override fun openApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    override val observedUris: List<Uri> get() = listOf(Uri.parse("content://$AUTHORITY"))
    override val permissions: Array<String> get() = emptyArray()

    private fun tasksUri(listId: String): Uri = Uri.parse("content://$AUTHORITY/tasks").buildUpon().appendQueryParameter("list", listId).build()

    companion object {
        private const val TAG = "ReadersTasks"
        const val PACKAGE = "com.freedomfighter.readerstasks"
        const val AUTHORITY = "com.freedomfighter.readerstasks"
        val LISTS: Uri = Uri.parse("content://$AUTHORITY/lists")
    }
}
