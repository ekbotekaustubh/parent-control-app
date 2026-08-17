package com.familyguard.parent.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.familyguard.parent.ui.accessrequests.AccessRequestsScreen
import com.familyguard.parent.ui.auth.AuthScreen
import com.familyguard.parent.ui.auth.AuthViewModel
import com.familyguard.parent.ui.children.ChildListScreen
import com.familyguard.parent.ui.children.CreateChildScreen
import com.familyguard.parent.ui.dashboard.DashboardScreen
import com.familyguard.parent.ui.pairing.DeviceApprovalScreen
import com.familyguard.parent.ui.pairing.PairingScreen
import com.familyguard.parent.ui.rules.RuleEditorScreen

/**
 * Auth -> ChildList -> {CreateChild, ChildDetail{Pairing, DeviceApproval, RuleEditor,
 * Dashboard}} per the required structure. "ChildDetail" isn't a single composable — it's
 * the family of childId-scoped routes below (Pairing/DeviceApproval/RuleEditor/Dashboard),
 * all reachable from a ChildList row tap (-> Dashboard) or from each other as the pairing
 * story progresses: CreateChild -> Pairing -> DeviceApproval -> RuleEditor -> Dashboard.
 */
object ParentRoutes {
    const val AUTH = "auth"
    const val CHILD_LIST = "childList"
    const val CREATE_CHILD = "createChild"
    const val ACCESS_REQUESTS = "accessRequests"

    private const val CHILD_ID_ARG = "childId"
    const val PAIRING = "pairing/{$CHILD_ID_ARG}"
    const val DEVICE_APPROVAL = "deviceApproval/{$CHILD_ID_ARG}"
    const val RULE_EDITOR = "ruleEditor/{$CHILD_ID_ARG}"
    const val DASHBOARD = "dashboard/{$CHILD_ID_ARG}"

    fun pairing(childId: String) = "pairing/$childId"
    fun deviceApproval(childId: String) = "deviceApproval/$childId"
    fun ruleEditor(childId: String) = "ruleEditor/$childId"
    fun dashboard(childId: String) = "dashboard/$childId"
}

@Composable
fun ParentNavGraph(navController: NavHostController) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val startDestination = if (authViewModel.isLoggedIn()) ParentRoutes.CHILD_LIST else ParentRoutes.AUTH

    NavHost(navController = navController, startDestination = startDestination) {

        composable(ParentRoutes.AUTH) {
            AuthScreen(
                onAuthenticated = {
                    navController.navigate(ParentRoutes.CHILD_LIST) {
                        popUpTo(ParentRoutes.AUTH) { inclusive = true }
                    }
                },
            )
        }

        composable(ParentRoutes.CHILD_LIST) {
            ChildListScreen(
                onAddChild = { navController.navigate(ParentRoutes.CREATE_CHILD) },
                onOpenChild = { childId -> navController.navigate(ParentRoutes.dashboard(childId)) },
                onOpenAccessRequests = { navController.navigate(ParentRoutes.ACCESS_REQUESTS) },
                onLoggedOut = {
                    navController.navigate(ParentRoutes.AUTH) {
                        popUpTo(ParentRoutes.CHILD_LIST) { inclusive = true }
                    }
                },
            )
        }

        composable(ParentRoutes.ACCESS_REQUESTS) {
            AccessRequestsScreen(onBack = { navController.popBackStack() })
        }

        composable(ParentRoutes.CREATE_CHILD) {
            CreateChildScreen(
                onChildCreated = { childId ->
                    // Natural next step per the product story: right after creating a
                    // profile, generate that child's pairing code.
                    navController.navigate(ParentRoutes.pairing(childId)) {
                        popUpTo(ParentRoutes.CHILD_LIST)
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(
            route = ParentRoutes.PAIRING,
            arguments = listOf(navArgument("childId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            PairingScreen(
                childId = childId,
                onDevicePendingApproval = {
                    navController.navigate(ParentRoutes.deviceApproval(childId)) {
                        popUpTo(ParentRoutes.PAIRING) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(
            route = ParentRoutes.DEVICE_APPROVAL,
            arguments = listOf(navArgument("childId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            DeviceApprovalScreen(
                childId = childId,
                onApproved = {
                    // Next natural step: set the first rule for this newly-paired device.
                    navController.navigate(ParentRoutes.ruleEditor(childId)) {
                        popUpTo(ParentRoutes.CHILD_LIST)
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(
            route = ParentRoutes.RULE_EDITOR,
            arguments = listOf(navArgument("childId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            RuleEditorScreen(
                childId = childId,
                onDone = {
                    navController.navigate(ParentRoutes.dashboard(childId)) {
                        popUpTo(ParentRoutes.CHILD_LIST)
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(
            route = ParentRoutes.DASHBOARD,
            arguments = listOf(navArgument("childId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getString("childId").orEmpty()
            DashboardScreen(
                childId = childId,
                onPairDevice = { navController.navigate(ParentRoutes.pairing(childId)) },
                onEditRules = { navController.navigate(ParentRoutes.ruleEditor(childId)) },
                onBackToChildList = {
                    navController.navigate(ParentRoutes.CHILD_LIST) {
                        popUpTo(ParentRoutes.CHILD_LIST) { inclusive = true }
                    }
                },
            )
        }
    }
}
