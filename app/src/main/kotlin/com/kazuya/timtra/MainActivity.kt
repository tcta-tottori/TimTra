package com.kazuya.timtra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kazuya.timtra.ui.navigation.TimTraNavHost
import com.kazuya.timtra.ui.theme.TimTraTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimTraTheme {
                TimTraNavHost()
            }
        }
    }
}
