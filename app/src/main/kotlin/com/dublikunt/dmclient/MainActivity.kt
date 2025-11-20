package com.dublikunt.dmclient

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.dublikunt.dmclient.component.AppUpdateChecker
import com.dublikunt.dmclient.database.PreferenceHelper
import com.dublikunt.dmclient.screen.GalleryScreen
import com.dublikunt.dmclient.screen.HistoryScreen
import com.dublikunt.dmclient.screen.HomeScreen
import com.dublikunt.dmclient.screen.LockScreen
import com.dublikunt.dmclient.screen.SearchResultScreen
import com.dublikunt.dmclient.screen.SearchScreen
import com.dublikunt.dmclient.screen.SettingsScreen
import com.dublikunt.dmclient.screen.StatusesScreen
import com.dublikunt.dmclient.ui.theme.DMClientTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DMClientTheme {
                var isUnlocked by remember { mutableStateOf(false) }
                var pinCode by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    PreferenceHelper.getPinCode(this@MainActivity).collect {
                        if (it == null) {
                            isUnlocked = true
                        } else {
                            pinCode = it
                        }
                    }
                }

                if (isUnlocked) {
                    setSingletonImageLoaderFactory { context ->
                        ImageLoader.Builder(context)
                            .crossfade(true)
                            .memoryCache {
                                MemoryCache.Builder()
                                    .maxSizePercent(context, 0.3)
                                    .build()
                            }
                            .diskCache {
                                DiskCache.Builder()
                                    .directory(cacheDir.resolve("image_cache"))
                                    .maxSizeBytes(1 * 1024 * 1024 * 1024)
                                    .build()
                            }
                            .build()
                    }
                    MainScreen()
                } else {
                    if (pinCode != null) {
                        LockScreen(
                            { isUnlocked = true },
                            pinCode!!
                        )
                    }
                }
            }
        }
    }
}

enum class Screen(val route: String, val title: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Rounded.Home),
    Search("search", "Search", Icons.Rounded.Search),
    History("history", "History", Icons.Rounded.Menu),
    Statuses("statuses", "Statuses", Icons.Rounded.Star),
    Settings("settings", "Settings", Icons.Rounded.Settings),
}

@Composable
fun AppDrawer(navController: NavController, closeDrawer: () -> Unit) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Branding Icon",
                modifier = Modifier
                    .align(Alignment.Start)
                    .height(40.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge
            )

            HorizontalDivider()
            Screen.entries.forEach { screen ->
                NavigationDrawerItem(
                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                    label = { Text(screen.title) },
                    selected = false,
                    onClick = {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                        closeDrawer()
                    },
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(openDrawer: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        navigationIcon = {
            IconButton(onClick = openDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        }
    )
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) { HomeScreen(navController) }
        composable(Screen.History.route) { HistoryScreen(navController) }
        composable(Screen.Statuses.route) { StatusesScreen(navController) }
        composable(Screen.Search.route) { SearchScreen(navController) }
        composable(Screen.Settings.route) { SettingsScreen() }
        composable(
            route = "gallery?id={id}",
            arguments = listOf(
                navArgument("id") {
                    type = NavType.IntType
                    nullable = false
                }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id")!!
            GalleryScreen(id, navController)
        }
        composable(
            route = "search?query={query}",
            arguments = listOf(
                navArgument("query") {
                    type = NavType.StringType
                    nullable = false
                }
            )
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query")!!
            SearchResultScreen(query, navController)
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    AppUpdateChecker()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(navController) {
                scope.launch { drawerState.close() }
            }
        }
    ) {
        Scaffold(
            topBar = { TopBar { scope.launch { drawerState.open() } } }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                AppNavHost(navController)
            }
        }
    }
}
