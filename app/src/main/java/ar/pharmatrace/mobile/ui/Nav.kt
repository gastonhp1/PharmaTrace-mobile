package ar.pharmatrace.mobile.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ar.pharmatrace.mobile.AppContainer
import ar.pharmatrace.mobile.ui.screens.CargoScreen
import ar.pharmatrace.mobile.ui.screens.HomeScreen
import ar.pharmatrace.mobile.ui.screens.LoginScreen
import ar.pharmatrace.mobile.ui.screens.ScanScreen
import ar.pharmatrace.mobile.ui.screens.UnlockScreen

private object Routes {
    const val HOME = "home"
    const val SCAN = "scan"
    const val UNLOCK = "unlock/{qr}"
    const val CARGO = "cargo/{cargoId}"
    fun unlock(qr: String) = "unlock/${Uri.encode(qr)}"
    fun cargo(id: String) = "cargo/${Uri.encode(id)}"
}

/** Without a session, the login screen; with one, the app. Logging out or a 401 returns here. */
@Composable
fun PharmaTraceNav(container: AppContainer) {
    val session by container.sessions.session.collectAsStateWithLifecycle()
    val current = session
    if (current == null) {
        LoginScreen(viewModel { LoginViewModel(container) })
        return
    }

    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                session = current,
                onOpenBox = { nav.navigate(Routes.SCAN) },
                onCargo = { nav.navigate(Routes.cargo(it)) },
                onLogout = { container.sessions.clear() },
            )
        }
        composable(Routes.SCAN) {
            ScanScreen(
                onQr = { qr -> nav.navigate(Routes.unlock(qr)) { popUpTo(Routes.SCAN) { inclusive = true } } },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.UNLOCK, arguments = listOf(navArgument("qr") { type = NavType.StringType })) { entry ->
            val qr = entry.arguments?.getString("qr").orEmpty()
            UnlockScreen(
                vm = viewModel(key = qr) { UnlockViewModel(container, qr) },
                ble = container.ble,
                onScanAgain = { nav.navigate(Routes.SCAN) { popUpTo(Routes.HOME) } },
                onCargo = { nav.navigate(Routes.cargo(it)) { popUpTo(Routes.HOME) } },
                onSessionExpired = { container.sessions.clear() },
                onBack = { nav.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(Routes.CARGO, arguments = listOf(navArgument("cargoId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("cargoId").orEmpty()
            CargoScreen(
                cargoId = id,
                vm = viewModel(key = id) { CargoViewModel(container.api(), id) },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
