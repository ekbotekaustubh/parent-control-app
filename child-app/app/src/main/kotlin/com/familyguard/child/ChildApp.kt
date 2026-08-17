package com.familyguard.child

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.familyguard.child.data.local.TokenStore
import com.familyguard.child.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ChildApp : Application(), Configuration.Provider {

    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory
    @Inject lateinit var workScheduler: WorkScheduler
    @Inject lateinit var tokenStore: TokenStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // If the app process was restarted while already paired (e.g. after an update, or
        // a cold start that isn't a boot — WorkManager jobs otherwise persist across process
        // restarts on their own, but this is a cheap, idempotent safety net to make sure
        // periodic work is always scheduled for an already-paired device).
        if (tokenStore.isPaired()) {
            workScheduler.scheduleAll()
        }
    }
}
