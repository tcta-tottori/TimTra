package com.kazuya.timtra.ui.navigation

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kazuya.timtra.R
import com.kazuya.timtra.ui.about.AboutScreen
import com.kazuya.timtra.ui.home.HomeScreen
import com.kazuya.timtra.ui.settings.SettingsScreen
import com.kazuya.timtra.ui.timetable.TimetableScreen
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val TIMETABLE = "timetable"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

private val drawerItems =
    listOf(
        DrawerItemSpec(Routes.HOME, R.string.nav_home, R.drawable.ic_home),
        DrawerItemSpec(Routes.TIMETABLE, R.string.nav_timetable, R.drawable.ic_schedule),
        DrawerItemSpec(Routes.SETTINGS, R.string.nav_settings, R.drawable.ic_settings),
        DrawerItemSpec(Routes.ABOUT, R.string.nav_about, R.drawable.ic_info),
    )

@Composable
fun TimTraNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            TimTraDrawer(
                currentRoute = currentRoute,
                items = drawerItems,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) { HomeScreen(onOpenDrawer = openDrawer) }
            composable(Routes.TIMETABLE) { TimetableScreen(onOpenDrawer = openDrawer) }
            composable(
                Routes.SETTINGS,
            ) { SettingsScreen(onOpenDrawer = openDrawer, onOpenAbout = { navController.navigate(Routes.ABOUT) }) }
            composable(Routes.ABOUT) { AboutScreen(onOpenDrawer = openDrawer) }
        }
    }
}
