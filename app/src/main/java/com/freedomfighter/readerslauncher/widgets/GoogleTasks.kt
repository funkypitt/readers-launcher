package com.freedomfighter.readerslauncher.widgets

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.net.Http
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable data class TaskList(val id: String, val title: String)
@Serializable data class Task(val id: String, val title: String = "", val status: String = "needsAction", val position: String = "", val due: String? = null)
@Serializable private data class TaskLists(val items: List<TaskList> = emptyList())
@Serializable private data class Tasks(val items: List<Task> = emptyList())

/**
 * Google Tasks through OAuth 2.0 (AppAuth, PKCE, no client secret) and the REST API.
 * The OAuth client id is provided by the user in settings, since it is tied to the
 * package name and signing certificate of each build.
 */
class GoogleTasks(private val context: Context) {
    private val app = context.applicationContext as App
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val config = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token")
    )

    val clientId: String get() = app.prefs.settings.value.tasksClientId
    val redirectUri: Uri get() = Uri.parse("${context.packageName}:/oauth2redirect")

    private var authState: AuthState = app.prefs.getString(KEY_AUTH)?.let { runCatching { AuthState.jsonDeserialize(it) }.getOrNull() } ?: AuthState(config)

    val isSignedIn: Boolean get() = authState.isAuthorized && clientId.isNotBlank()

    private fun persist() = app.prefs.putString(KEY_AUTH, authState.jsonSerializeString())

    fun signOut() {
        authState = AuthState(config)
        app.prefs.putString(KEY_AUTH, null)
    }

    /** Build the browser sign-in intent; the result comes back to [handleAuthResult]. */
    fun authIntent(): Intent {
        val request = AuthorizationRequest.Builder(config, clientId, ResponseTypeValues.CODE, redirectUri)
            .setScope("https://www.googleapis.com/auth/tasks")
            .setAdditionalParameters(mapOf("access_type" to "offline", "prompt" to "consent"))
            .build()
        val service = AuthorizationService(context)
        return service.getAuthorizationRequestIntent(request).also { service.dispose() }
    }

    suspend fun handleAuthResult(data: Intent?): Boolean {
        data ?: return false
        val resp = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        authState.update(resp, ex)
        if (resp == null) { Log.w(TAG, "auth failed: $ex"); return false }
        val service = AuthorizationService(context)
        return try {
            suspendCancellableCoroutine { cont ->
                service.performTokenRequest(resp.createTokenExchangeRequest()) { tokenResp, tokenEx ->
                    authState.update(tokenResp, tokenEx)
                    persist()
                    cont.resume(tokenResp != null)
                }
            }
        } finally { service.dispose() }
    }

    private suspend fun freshToken(): String {
        val service = AuthorizationService(context)
        try {
            return suspendCancellableCoroutine { cont ->
                authState.performActionWithFreshTokens(service) { access, _, ex ->
                    persist()
                    if (access != null) cont.resume(access) else cont.resumeWithException(ex ?: IllegalStateException("no token"))
                }
            }
        } finally { service.dispose() }
    }

    private suspend fun call(method: String, path: String, body: String? = null): String {
        val token = freshToken()
        return Http.request(method, "https://tasks.googleapis.com/tasks/v1$path", body, mapOf("Authorization" to "Bearer $token"))
    }

    suspend fun lists(): List<TaskList> =
        json.decodeFromString(TaskLists.serializer(), call("GET", "/users/@me/lists?maxResults=100")).items

    /** Open tasks in list order (position), top-level only. */
    suspend fun openTasks(listId: String): List<Task> {
        val enc = URLEncoder.encode(listId, "UTF-8")
        val items = json.decodeFromString(Tasks.serializer(), call("GET", "/lists/$enc/tasks?showCompleted=false&showHidden=false&maxResults=100")).items
        return items.filter { it.status != "completed" }.sortedBy { it.position }
    }

    suspend fun complete(listId: String, taskId: String) {
        val enc = URLEncoder.encode(listId, "UTF-8")
        call("PATCH", "/lists/$enc/tasks/${URLEncoder.encode(taskId, "UTF-8")}", """{"status":"completed"}""")
    }

    suspend fun insert(listId: String, title: String): Task {
        val enc = URLEncoder.encode(listId, "UTF-8")
        val body = json.encodeToString(mapOf("title" to title))
        return json.decodeFromString(Task.serializer(), call("POST", "/lists/$enc/tasks", body))
    }

    fun openTasksApp(activity: Activity?) {
        val ctx: Context = activity ?: context
        val launch = ctx.packageManager.getLaunchIntentForPackage("com.google.android.apps.tasks")
        val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://tasks.google.com/"))
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    companion object {
        private const val TAG = "GoogleTasks"
        private const val KEY_AUTH = "tasks_auth_state"
        @Volatile private var instance: GoogleTasks? = null
        fun get(context: Context): GoogleTasks =
            instance ?: synchronized(this) { instance ?: GoogleTasks(context.applicationContext).also { instance = it } }
    }
}
