package com.kazuya.timtra.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

/**
 * 画面の行き来。左メニューはやめ、ホーム右下のボタン（[com.kazuya.timtra.ui.common.ActionMenuFab]）に
 * 集約した。ホーム以外は左上の戻るで 1 つ前へ帰る。
 */
@Composable
fun TimTraNavHost() {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenTimetable = { navController.navigate(Routes.TIMETABLE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.TIMETABLE) { TimetableScreen(onBack = back) }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = back, onOpenAbout = { navController.navigate(Routes.ABOUT) })
        }
        composable(Routes.ABOUT) { AboutScreen(onBack = back) }
    }
}
