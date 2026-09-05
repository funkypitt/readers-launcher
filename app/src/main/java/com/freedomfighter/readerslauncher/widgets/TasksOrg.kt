package com.freedomfighter.readerslauncher.widgets

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Tasks.org (org.tasks, open source, F-Droid) integration through its content providers.
 * No account, no OAuth: two runtime permissions granted once.
 *
 *  - Primary: the public API provider `content://org.tasks.api/v0/…` (Tasks.org ≥ 15.11):
 *    read lists and open tasks, complete a task, insert a task.
 *  - Fallback for older versions: the read-only provider `content://org.tasks/todoagenda`
 *    (used by the Todo Agenda widget for years). Completing then opens the task in Tasks.org
 *    and adding hands the title to Tasks.org's editor.
 */
class TasksOrg(private val context: Context) {

    data class TaskList(val id: Long, val title: String, val account: String)
    data class Task(val id: Long, val title: String)

    val isInstalled: Boolean
        get() = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    val hasPermissions: Boolean
        get() = PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** True when the installed Tasks.org exposes the writable API provider. */
    val apiAvailable: Boolean
        get() = context.packageManager.resolveContentProvider(API_AUTHORITY, 0) != null

    val ready: Boolean get() = isInstalled && hasPermissions

    // ---- lists -------------------------------------------------------------------------

    fun lists(): List<TaskList> = if (apiAvailable) apiLists() else legacyLists()

    private fun apiLists(): List<TaskList> {
        val accounts = HashMap<Long, String>()
        query(Uri.parse("$API/accounts"), arrayOf("_id", "name")) { c ->
            accounts[c.getLong(0)] = c.getString(1) ?: ""
        }
        val out = ArrayList<TaskList>()
        query(Uri.parse("$API/lists"), arrayOf("_id", "title", "account_id", "access")) { c ->
            if (c.getString(3) == "read_only") return@query
            out += TaskList(c.getLong(0), c.getString(1) ?: "", accounts[c.getLong(2)] ?: "")
        }
        return out
    }

    private fun legacyLists(): List<TaskList> {
        val seen = LinkedHashMap<Long, TaskList>()
        query(LEGACY_TODO_AGENDA, null, "cdl_id IS NOT NULL") { c ->
            val id = c.long("cdl_id") ?: return@query
            if (id !in seen) seen[id] = TaskList(id, c.string("cdl_name") ?: "", "") // account names are not part of this join
        }
        return seen.values.toList()
    }

    // ---- tasks -------------------------------------------------------------------------

    /** Open top-level tasks of a list, the way Tasks.org orders them by default (due date, then id). */
    fun openTasks(listId: Long, limit: Int = 50): List<Task> =
        if (apiAvailable) apiOpenTasks(listId, limit) else legacyOpenTasks(listId, limit)

    private fun apiOpenTasks(listId: Long, limit: Int): List<Task> {
        val uri = Uri.parse("$API/tasks").buildUpon()
            .appendQueryParameter("list_id", listId.toString())
            .appendQueryParameter("completed", "0")
            .appendQueryParameter("sort", "due")
            .appendQueryParameter("limit", "100")
            .build()
        val withDue = ArrayList<Task>()
        val noDue = ArrayList<Task>()
        query(uri, arrayOf("_id", "title", "due_date", "parent_id")) { c ->
            if (!c.isNull(3) && c.getLong(3) > 0) return@query
            val due = if (c.isNull(2)) 0L else c.getLong(2)
            val t = Task(c.getLong(0), c.getString(1) ?: "")
            if (due > 0) withDue += t else noDue += t
        }
        return (withDue + noDue).take(limit)
    }

    private fun legacyOpenTasks(listId: Long, limit: Int): List<Task> {
        val withDue = ArrayList<Pair<Long, Task>>()
        val noDue = ArrayList<Task>()
        query(
            LEGACY_TODO_AGENDA, null,
            "completed = 0 AND deleted = 0 AND cd_deleted = 0 AND cdl_id = $listId"
        ) { c ->
            if ((c.long("parent") ?: 0L) > 0) return@query
            val t = Task(c.long("_id") ?: return@query, c.string("title") ?: "")
            val due = c.long("dueDate") ?: 0L
            if (due > 0) withDue += due to t else noDue += t
        }
        return (withDue.sortedBy { it.first }.map { it.second } + noDue).take(limit)
    }

    /** Returns true when completed through the API; false when the caller should open the task instead. */
    fun complete(taskId: Long): Boolean {
        if (!apiAvailable) return false
        return runCatching {
            val values = ContentValues().apply { put("completed_at", System.currentTimeMillis()) }
            context.contentResolver.update(Uri.parse("$API/tasks/$taskId"), values, null, null) > 0
        }.onFailure { Log.w(TAG, "complete failed", it) }.getOrDefault(false)
    }

    /** Returns true when inserted through the API; false when the caller should use [newTaskInApp]. */
    fun insert(listId: Long, title: String): Boolean {
        if (!apiAvailable) return false
        return runCatching {
            val values = ContentValues().apply { put("title", title); put("list_id", listId) }
            context.contentResolver.insert(Uri.parse("$API/tasks"), values) != null
        }.onFailure { Log.w(TAG, "insert failed", it) }.getOrDefault(false)
    }

    // ---- intents -----------------------------------------------------------------------

    fun openTask(taskId: Long) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://$PACKAGE/tasks/$taskId"))
            .setPackage(PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { openApp() }
    }

    fun openApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Hands a title to Tasks.org's own "new task" editor. */
    fun newTaskInApp(title: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(PACKAGE)
            .putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { openApp() }
    }

    fun openStore() {
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("fdroid.app:$PACKAGE")),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/$PACKAGE/"))
        )
        for (i in intents) if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }

    // ---- helpers -----------------------------------------------------------------------

    private fun android.database.Cursor.long(name: String): Long? =
        getColumnIndex(name).let { if (it < 0 || isNull(it)) null else getLong(it) }

    private fun android.database.Cursor.string(name: String): String? =
        getColumnIndex(name).let { if (it < 0 || isNull(it)) null else getString(it) }

    private inline fun query(uri: Uri, projection: Array<String>?, selection: String? = null, each: (android.database.Cursor) -> Unit) {
        try {
            context.contentResolver.query(uri, projection, selection, null, null)?.use { c ->
                while (c.moveToNext()) each(c)
            }
        } catch (e: Exception) {
            Log.w(TAG, "query $uri failed", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "TasksOrg"
        const val PACKAGE = "org.tasks"
        const val API_AUTHORITY = "org.tasks.api"
        private const val API = "content://$API_AUTHORITY/v0"
        private val LEGACY_TODO_AGENDA: Uri = Uri.parse("content://org.tasks/todoagenda")
        val PERMISSIONS = arrayOf("org.tasks.permission.READ_TASKS", "org.tasks.permission.WRITE_TASKS")
        val API_TASKS_URI: Uri = Uri.parse("$API/tasks")
        val LEGACY_URI: Uri = Uri.parse("content://org.tasks/")

        @Volatile private var instance: TasksOrg? = null
        fun get(context: Context): TasksOrg =
            instance ?: synchronized(this) { instance ?: TasksOrg(context.applicationContext).also { instance = it } }
    }
}
