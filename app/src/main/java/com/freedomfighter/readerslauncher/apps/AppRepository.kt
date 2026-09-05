package com.freedomfighter.readerslauncher.apps

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.widget.Toast
import com.freedomfighter.readerslauncher.data.AppRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.concurrent.ConcurrentHashMap

/** One launchable activity as shown in every list of the launcher. */
data class AppEntry(
    val ref: AppRef,
    val label: String,
    val isSystem: Boolean,
    val isWorkProfile: Boolean
) {
    val sortKey: String = label.lowercase()
}

/**
 * Enumerates launchable activities in every profile through [LauncherApps]
 * (the way µLauncher does it) and keeps the list fresh on package changes.
 */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val collator: Collator = Collator.getInstance()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    /** All launchable apps, sorted alphabetically by label (locale aware). */
    val apps: StateFlow<List<AppEntry>> = _apps

    private val iconCache = ConcurrentHashMap<String, Drawable>()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = refresh()
        override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = refresh()
    }

    init {
        launcherApps.registerCallback(callback)
        refresh()
    }

    fun refresh() {
        scope.launch {
            try {
                _apps.value = loadAll()
                iconCache.clear()
            } catch (e: Exception) {
                Log.e(TAG, "cannot list apps", e)
            }
        }
    }

    private fun loadAll(): List<AppEntry> {
        val mainSerial = userManager.getSerialNumberForUser(android.os.Process.myUserHandle())
        val result = ArrayList<AppEntry>()
        for (profile in launcherApps.profiles) {
            val serial = userManager.getSerialNumberForUser(profile)
            val isWork = serial != mainSerial
            for (info in launcherApps.getActivityList(null, profile)) {
                if (info.applicationInfo.packageName == context.packageName) continue
                result += AppEntry(
                    ref = AppRef(
                        info.applicationInfo.packageName,
                        info.name,
                        if (isWork) serial else -1L
                    ),
                    label = info.label.toString(),
                    isSystem = info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    isWorkProfile = isWork
                )
            }
        }
        return result.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun userHandle(ref: AppRef): UserHandle =
        if (ref.user < 0) android.os.Process.myUserHandle()
        else userManager.getUserForSerialNumber(ref.user) ?: android.os.Process.myUserHandle()

    fun activityInfo(ref: AppRef): LauncherActivityInfo? {
        val list = try {
            launcherApps.getActivityList(ref.packageName, userHandle(ref))
        } catch (e: Exception) {
            return null
        }
        return list.firstOrNull { it.name == ref.activity } ?: list.firstOrNull()
    }

    fun entry(ref: AppRef): AppEntry? = _apps.value.firstOrNull { it.ref == ref }
        ?: _apps.value.firstOrNull { it.ref.packageName == ref.packageName && it.ref.user == ref.user }

    fun exists(ref: AppRef): Boolean = entry(ref) != null

    fun launch(ref: AppRef, sourceBounds: Rect? = null) {
        val info = activityInfo(ref)
        if (info == null) {
            Toast.makeText(context, "app not found", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            launcherApps.startMainActivity(info.componentName, info.user, sourceBounds, null)
        } catch (e: Exception) {
            Log.w(TAG, "launch failed for $ref", e)
            Toast.makeText(context, "cannot open ${info.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppInfo(ref: AppRef) {
        val info = activityInfo(ref) ?: return
        try {
            launcherApps.startAppDetailsActivity(info.componentName, info.user, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "app info failed", e)
        }
    }

    fun uninstall(activity: Activity, ref: AppRef) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${ref.packageName}"))
        intent.putExtra(Intent.EXTRA_USER, userHandle(ref))
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no uninstaller", e)
        }
    }

    /** Full-colour icon (cached). Used only as the base for the monochrome rendering. */
    fun icon(ref: AppRef): Drawable? {
        iconCache[ref.key]?.let { return it }
        val info = activityInfo(ref) ?: return null
        val d = try {
            info.getIcon(0)
        } catch (e: Exception) {
            null
        } ?: return null
        iconCache[ref.key] = d
        return d
    }

    companion object {
        private const val TAG = "AppRepository"
    }
}
