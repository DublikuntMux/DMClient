package com.dublikunt.dmclient.ui.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dublikunt.dmclient.crash.CrashReportPrompt
import com.dublikunt.dmclient.data.lock.AppLockManager
import com.dublikunt.dmclient.data.lock.LockState
import com.dublikunt.dmclient.data.settings.AppSettings
import com.dublikunt.dmclient.data.settings.SettingsRepository
import com.dublikunt.dmclient.data.settings.ThemeMode
import com.dublikunt.dmclient.ui.lock.LockScreen
import com.dublikunt.dmclient.ui.theme.DMClientTheme
import com.dublikunt.dmclient.ui.update.UpdatePrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    lockManager: AppLockManager
) : ViewModel() {
    val settings = settingsRepository.settings
    val lockState = lockManager.state
}

@Composable
fun DMClientApp(viewModel: AppViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    DMClientTheme(
        darkTheme = settings.isDark(),
        dynamicColor = settings.dynamicColor,
        pureBlack = settings.pureBlack
    ) {
        when (val state = lockState) {
            LockState.Loading -> Surface(Modifier.fillMaxSize()) {}
            is LockState.Locked -> LockScreen(state)
            LockState.Unlocked -> AppContent(navController, checkUpdates = settings.checkUpdates)
        }
    }
}

@Composable
private fun AppSettings.isDark(): Boolean = when (themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
private fun AppContent(navController: NavHostController, checkUpdates: Boolean) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showNavigation = destination == null ||
            TopLevelDestination.entries.any { destination.hasRoute(it.routeClass) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                if (showNavigation) AppNavigationRail(navController, destination)
                AppNavHost(navController, Modifier.weight(1f))
            }
        } else {
            Scaffold(
                bottomBar = { if (showNavigation) AppNavigationBar(navController, destination) },
                contentWindowInsets = WindowInsets(0)
            ) { padding ->
                AppNavHost(
                    navController,
                    Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding)
                )
            }
        }
    }

    if (checkUpdates) UpdatePrompt()
    CrashReportPrompt()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppNavigationBar(navController: NavHostController, destination: NavDestination?) {
    ShortNavigationBar {
        TopLevelDestination.entries.forEach { item ->
            val selected = destination.isOn(item)
            ShortNavigationBarItem(
                selected = selected,
                onClick = { navController.navigateTopLevel(item) },
                icon = { Icon(if (selected) item.selectedIcon else item.icon, null) },
                label = { Text(item.label) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppNavigationRail(navController: NavHostController, destination: NavDestination?) {
    WideNavigationRail(
        colors = WideNavigationRailDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        TopLevelDestination.entries.forEach { item ->
            val selected = destination.isOn(item)
            WideNavigationRailItem(
                selected = selected,
                onClick = { navController.navigateTopLevel(item) },
                icon = { Icon(if (selected) item.selectedIcon else item.icon, null) },
                label = { Text(item.label) },
                railExpanded = false
            )
        }
    }
}

private fun NavDestination?.isOn(item: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(item.routeClass) } == true

private fun NavHostController.navigateTopLevel(item: TopLevelDestination) {
    navigate(item.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
