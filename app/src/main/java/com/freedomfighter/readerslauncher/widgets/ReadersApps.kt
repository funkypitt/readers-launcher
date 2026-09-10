package com.freedomfighter.readerslauncher.widgets

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Reader's Books and Reader's Notes: their signature-protected providers, read without a prompt. */
object ReadersBooks {
    const val PACKAGE = "com.freedomfighter.readersbooks"
    val URI: Uri = Uri.parse("content://$PACKAGE/books")
    data class Book(val id: String, val title: String, val progress: Int, val opened: Long)

    fun isInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /** Every book on the shelf, the most recently opened first. */
    fun all(context: Context): List<Book> = runCatching {
        val out = ArrayList<Book>()
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            val id = c.getColumnIndex("id"); val title = c.getColumnIndex("title"); val progress = c.getColumnIndex("progress"); val opened = c.getColumnIndex("opened")
            while (c.moveToNext()) out += Book(c.getString(id), c.getString(title), c.getInt(progress), c.getLong(opened))
        }
        out.sortedByDescending { it.opened }
    }.getOrDefault(emptyList())

    /** The book most recently opened, or null. */
    fun current(context: Context): Book? = all(context).firstOrNull()

    fun open(context: Context, book: Book?) {
        // explicit component: a content:// URI gets a MIME type from the provider, which no plain scheme/host filter matches
        val intent = if (book != null) Intent(Intent.ACTION_VIEW, Uri.parse("content://$PACKAGE/books/${book.id}")).setClassName(PACKAGE, "$PACKAGE.MainActivity")
        else context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/funkypitt/readers-books"))
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

object ReadersNotes {
    const val PACKAGE = "com.freedomfighter.readersnotes"
    val URI: Uri = Uri.parse("content://$PACKAGE/notes")
    data class Note(val id: String, val title: String, val preview: String, val modified: Long)

    fun isInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /** Newest first. */
    fun notes(context: Context, limit: Int = 30): List<Note> = runCatching {
        val out = ArrayList<Note>()
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            val id = c.getColumnIndex("id"); val title = c.getColumnIndex("title"); val preview = c.getColumnIndex("preview"); val modified = c.getColumnIndex("modified")
            while (c.moveToNext() && out.size < limit) out += Note(c.getString(id), c.getString(title) ?: "", c.getString(preview) ?: "", c.getLong(modified))
        }
        out
    }.getOrDefault(emptyList())

    fun open(context: Context, id: String?) {
        val intent = if (isInstalled(context)) Intent(Intent.ACTION_VIEW, Uri.parse("content://$PACKAGE/notes/${id ?: "new"}")).setClassName(PACKAGE, "$PACKAGE.MainActivity")
        else Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/funkypitt/readers-notes"))
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Shared text straight into a note, without opening the app. */
    fun create(context: Context, text: String): Boolean = runCatching {
        context.contentResolver.insert(URI, ContentValues().apply { put("text", text) }) != null
    }.getOrDefault(false)
}
