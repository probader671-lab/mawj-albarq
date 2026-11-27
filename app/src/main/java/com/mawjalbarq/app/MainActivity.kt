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
        App.init(applicationContext); Media.init(applicationContext); ProfileCache.init(applicationContext); HomePrefs.init(applicationContext)
        Feed.init(applicationContext)
        PushWorker.ensureChannel(applicationContext); PushWorker.schedule(applicationContext); PushService.start(applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
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
    override fun onStart() { super.onStart(); Fg.on = true }
    override fun onStop() { Fg.on = false; super.onStop() }
}
