package io.github.dansparker.coasthint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.dansparker.coasthint.ui.CoastHintTheme
import io.github.dansparker.coasthint.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoastHintTheme {
                MainScreen()
            }
        }
    }
}
