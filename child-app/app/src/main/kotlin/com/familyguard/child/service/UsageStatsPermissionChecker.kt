package com.familyguard.child.service

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `PACKAGE_USAGE_STATS` is a special-access permission (see AndroidManifest.xml's comment
 * on it) — there is no runtime-permission callback for it, so the only way to know whether
 * it's granted is to ask `AppOpsManager` directly. Used by
 * `ui/permission/UsageAccessPermissionScreen.kt` to decide whether to advance past the
 * permission step, and re-checked defensively by `MonitorForegroundService` since the user
 * can revoke it from Settings at any time while the service is running.
 */
@Singleton
class UsageStatsPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
