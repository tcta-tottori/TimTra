package com.kazuya.timtra.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kazuya.timtra.wear.ui.WearBoardScreen
import com.kazuya.timtra.wear.ui.WearTheme
import dagger.hilt.android.AndroidEntryPoint

/** 時計アプリ本体。ホーム（発車標）1 画面から、時刻表とメニューへ広げる。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearTheme {
                WearBoardScreen()
            }
        }
    }
}
