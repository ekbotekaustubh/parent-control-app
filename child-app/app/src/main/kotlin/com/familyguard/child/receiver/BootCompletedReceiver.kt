package com.familyguard.child.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familyguard.child.data.local.TokenStore
import com.familyguard.child.service.MonitorForegroundService
import com.familyguard.child.work.WorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Restarts enforcement + sync after a device reboot. Without this, a paired device would
 * silently stop being monitored until the child happened to reopen the app themselves —
 * which defeats the purpose of background enforcement.
 *
 * Only acts if the device is actually paired (has stored tokens); an unpaired device has
 * nothing to enforce or sync yet, and starting the foreground service would just show a
 * confusing "active" notification with no rules loaded.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var tokenStore: TokenStore
    @Inject lateinit var workScheduler: WorkScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!tokenStore.isPaired()) return

        MonitorForegroundService.start(context)
        workScheduler.scheduleAll(expediteRuleSync = true)
    }
}
