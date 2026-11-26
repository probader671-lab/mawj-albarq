package com.mawjalbarq.app
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(prefs: ThemePrefs, onOpenFeed: () -> Unit = {}, onSuggestions: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    if (!App.loggedIn) {
        AuthPortalScreen(prefs = prefs, onSuggestions = onSuggestions)
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 100.dp)) {
        var settings by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("حسابي", color = Ink, fontSize = 24.sp, fontFamily = Cairo, modifier = Modifier.weight(1f))
            IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Settings, "الإعدادات", tint = Color.White) }
        }
        if (settings) SettingsSheet(prefs) { settings = false }
        Spacer(Modifier.height(16.dp))
        var me by remember { mutableStateOf<ProfileData?>(null) }
        var edit by remember { mutableStateOf(false) }
        LaunchedEffect(App.userId) { me = App.userId?.let { loadProfile(it) } }
        me?.let { d ->
            ProfileHeader(d, isMe = true, onPlay = { vid ->
                val target = d.videos.firstOrNull { it.id == vid } ?: Feed.videos.firstOrNull { it.id == vid }
                if (target != null) Feed.open(target, onOpenFeed)
            }) {
                Text(App.email, color = Color(0xFF8A8F9C), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { edit = true }) {
                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تعديل الملف")
                    }
                    OutlinedButton(onClick = { App.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تسجيل الخروج")
                    }
                }
            }
            if (edit) EditProfileSheet(d) { ok -> edit = false; if (ok) scope.launch { me = App.userId?.let { loadProfile(it) } } }
        } ?: Text(App.name, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontFamily = Cairo)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onSuggestions) {
            Icon(Icons.Filled.Lightbulb, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("لوحة الاقتراحات")
        }

    }
}

object GoogleAuth {
    var enabled = false
    var start: (() -> Unit)? = null
}
