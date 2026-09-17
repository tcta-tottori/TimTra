package com.kazuya.timtra.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kazuya.timtra.ui.about.AboutScreen
import com.kazuya.timtra.ui.home.HomeScreen
import com.kazuya.timtra.ui.settings.SettingsScreen
import com.kazuya.timtra.ui.timetable.TimetableFocus
import com.kazuya.timtra.ui.timetable.TimetableScreen

object Routes {
    const val HOME = "home"
    const val TIMETABLE = "timetable"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    /** 時刻表は「どの駅・バス停で開くか」を付けて呼ぶ（ホームの区間タップから）。 */
    const val TIMETABLE_ROUTE = "$TIMETABLE?${TimetableFocus.ARG}={${TimetableFocus.ARG}}"

    fun timetable(focus: TimetableFocus): String = "$TIMETABLE?${TimetableFocus.ARG}=${focus.name}"
}

/**
 * 画面の行き来。時刻表はホームの主役表示（バス / JR の発時刻）をタップして開き、
 * どの駅・バス停で開くかを [TimetableFocus] で渡す。右下のボタンは設定だけ。
 * 「このアプリについて」は設定の中に置く。ホーム以外は右下の戻るで 1 つ前へ帰る。
 */
@Composable
fun TimTraNavHost() {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenTimetable = { focus -> navController.navigate(Routes.timetable(focus)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.TIMETABLE_ROUTE,
            arguments =
                listOf(
                    navArgument(TimetableFocus.ARG) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
        ) { TimetableScreen(onBack = back) }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = back, onOpenAbout = { navController.navigate(Routes.ABOUT) })
        }
        composable(Routes.ABOUT) { AboutScreen(onBack = back) }
    }
}
