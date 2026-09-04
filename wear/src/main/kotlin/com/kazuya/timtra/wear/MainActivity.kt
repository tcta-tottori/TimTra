package com.kazuya.timtra.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kazuya.timtra.wear.ui.WearHomeScreen
import com.kazuya.timtra.wear.ui.WearTheme
import dagger.hilt.android.AndroidEntryPoint

/** タイル・コンプリケーションのタップ先。バス / JR の発時刻詳細を出す。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearTheme {
                WearHomeScreen()
            }
        }
    }
}
