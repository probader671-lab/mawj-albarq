package com.mawjalbarq.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = ThemePrefs(applicationContext)
        setContent {
            MawjTheme(Accents[prefs.index]) {
                var splash by remember { mutableStateOf(true) }
                if (splash) SplashScreen { splash = false } else AppRoot(prefs)
            }
        }
    }
}
