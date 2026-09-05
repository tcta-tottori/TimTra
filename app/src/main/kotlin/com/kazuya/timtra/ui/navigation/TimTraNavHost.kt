package com.kazuya.timtra.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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

object Routes {
    const val HOME = "home"
    const val TIMETABLE = "timetable"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

private data class BottomItem(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
)

private val bottomItems =
    listOf(
        BottomItem(Routes.HOME, R.string.nav_home, R.drawable.ic_home),
        BottomItem(Routes.TIMETABLE, R.string.nav_timetable, R.drawable.ic_list),
        BottomItem(Routes.SETTINGS, R.string.nav_settings, R.drawable.ic_settings),
    )

@Composable
fun TimTraNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) { HomeScreen() }
            composable(Routes.TIMETABLE) { TimetableScreen() }
            composable(Routes.SETTINGS) { SettingsScreen(onOpenAbout = { navController.navigate(Routes.ABOUT) }) }
            composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
