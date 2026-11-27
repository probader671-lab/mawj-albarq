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
        App.init(applicationContext); Media.init(applicationContext)
        Feed.init(applicationContext)
        val prefs = ThemePrefs(applicationContext)
        Theme.prefs = prefs
        setContent {
            MawjTheme(Accents[prefs.index]) {
                LaunchedEffect(Unit) { Feed.load() }
                var splash by remember { mutableStateOf(true) }
                if (splash) SplashScreen { splash = false } else AppRoot(prefs)
            }
        }
    }
}
