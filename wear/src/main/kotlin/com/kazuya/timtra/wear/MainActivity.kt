package com.kazuya.timtra.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kazuya.timtra.wear.ui.WearBoardScreen
import com.kazuya.timtra.wear.ui.WearHomeScreen
import com.kazuya.timtra.wear.ui.WearTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 時計アプリ本体。既定は発車標（添付デザインの画面）で、そこから出発時刻の詳細へ移れる。
 * 出発タイル・コンプリケーションからは出発時刻の画面で開く。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startOnJourney = intent?.getStringExtra(EXTRA_SCREEN) == SCREEN_JOURNEY
        setContent {
            WearTheme {
                var showJourney by remember { mutableStateOf(startOnJourney) }
                if (showJourney) {
                    WearHomeScreen(onOpenBoard = { showJourney = false })
                } else {
                    WearBoardScreen(onOpenJourney = { showJourney = true })
                }
            }
        }
    }

    companion object {
        /** どの画面で開くか。出発タイルからは出発時刻の画面を直接開く。 */
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_JOURNEY = "journey"
    }
}
