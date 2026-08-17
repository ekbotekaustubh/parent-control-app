package com.familyguard.child.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familyguard.child.ui.home.HomeScreen
import com.familyguard.child.ui.pairing.PairingEntryScreen
import com.familyguard.child.ui.pairing.PairingViewModel
import com.familyguard.child.ui.pairing.WaitingForApprovalScreen
import com.familyguard.child.ui.permission.UsageAccessPermissionScreen

/**
 * The story this app implements (see task spec): Pairing -> WaitingForApproval ->
 * UsageAccessPermission -> Home. Each step only advances forward; there is no "back" out
 * of pairing once claimed, matching the one-way state machine on the backend
 * (`docs/pairing-security.md`).
 */
object ChildRoutes {
    const val PAIRING = "pairing"
    const val WAITING_FOR_APPROVAL = "waiting_for_approval"
    const val USAGE_ACCESS_PERMISSION = "usage_access_permission"
    const val HOME = "home"
}

@Composable
fun ChildNavGraph(
    startDestination: String,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(ChildRoutes.PAIRING) {
            // Each destination gets its own default (per-back-stack-entry) PairingViewModel
            // instance. That's fine here: the state that actually needs to survive the
            // Pairing -> WaitingForApproval navigation (deviceId, claimTicket) is already
            // persisted in TokenStore by PairingRepository.claim(), so WaitingForApproval's
            // own ViewModel instance can resume polling without any shared instance state.
            val pairingViewModel: PairingViewModel = hiltViewModel()
            PairingEntryScreen(
                viewModel = pairingViewModel,
                onClaimed = { navController.navigate(ChildRoutes.WAITING_FOR_APPROVAL) },
            )
        }
        composable(ChildRoutes.WAITING_FOR_APPROVAL) {
            val pairingViewModel: PairingViewModel = hiltViewModel()
            WaitingForApprovalScreen(
                viewModel = pairingViewModel,
                onApprovedAndTokensExchanged = {
                    navController.navigate(ChildRoutes.USAGE_ACCESS_PERMISSION) {
                        popUpTo(ChildRoutes.PAIRING) { inclusive = true }
                    }
                },
            )
        }
        composable(ChildRoutes.USAGE_ACCESS_PERMISSION) {
            UsageAccessPermissionScreen(
                onGranted = {
                    navController.navigate(ChildRoutes.HOME) {
                        popUpTo(ChildRoutes.USAGE_ACCESS_PERMISSION) { inclusive = true }
                    }
                },
            )
        }
        composable(ChildRoutes.HOME) {
            HomeScreen()
        }
    }
}
