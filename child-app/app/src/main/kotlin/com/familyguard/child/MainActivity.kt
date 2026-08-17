package com.familyguard.child

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.familyguard.child.data.local.TokenStore
import com.familyguard.child.navigation.ChildNavGraph
import com.familyguard.child.navigation.ChildRoutes
import com.familyguard.child.service.MonitorForegroundService
import com.familyguard.child.service.UsageStatsPermissionChecker
import com.familyguard.child.ui.theme.FamilyGuardChildTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host. Chooses the nav graph's start destination from local state
 * (paired? usage-access granted?) — see task spec's story:
 * Pairing -> WaitingForApproval -> UsageAccessPermission -> Home.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var tokenStore: TokenStore
    @Inject lateinit var usageStatsPermissionChecker: UsageStatsPermissionChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startDestination = when {
            !tokenStore.isPaired() -> ChildRoutes.PAIRING
            !usageStatsPermissionChecker.isGranted() -> ChildRoutes.USAGE_ACCESS_PERMISSION
            else -> ChildRoutes.HOME
        }

        if (startDestination == ChildRoutes.HOME) {
            MonitorForegroundService.start(this)
        }

        setContent {
            FamilyGuardChildTheme {
                ChildNavGraph(startDestination = startDestination)
            }
        }
    }
}
