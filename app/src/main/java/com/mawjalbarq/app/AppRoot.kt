package com.mawjalbarq.app
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Tab(val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("الرئيسية", Icons.Filled.Home), Tab("المقاطع", Icons.Filled.PlayArrow),
    Tab("رفع", Icons.Filled.Add), Tab("المنشورات", Icons.Filled.Article), Tab("حسابي", Icons.Filled.Person))

@Composable
fun AppRoot(prefs: ThemePrefs) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        var sel by remember { mutableIntStateOf(1) }
        var suggestions by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { Feed.load() }
        UpdatePrompt()
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when (sel) {
                0 -> HomeScreen(onOpen = { sel = 1 }, onNeedLogin = { sel = 4 })
                1 -> ClipsScreen(onNeedLogin = { sel = 4 })
                2 -> UploadScreen(onNeedLogin = { sel = 4 }, onDone = { sel = 1 })
                3 -> PostsScreen(onNeedLogin = { sel = 4 })
                else -> if (suggestions) SuggestionsScreen { suggestions = false } else ProfileScreen(prefs, onOpenFeed = { sel = 1 }, onSuggestions = { suggestions = true })
            }
            PillBar(sel, { sel = it }, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun PillBar(sel: Int, onSel: (Int) -> Unit, modifier: Modifier) {
    Row(modifier.navigationBarsPadding().padding(bottom = 12.dp).clip(RoundedCornerShape(32.dp))
        .background(Color(0xE60E1015)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(32.dp))
        .padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        tabs.forEachIndexed { i, t ->
            if (i == 2) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { onSel(i) },
                    contentAlignment = Alignment.Center) { Icon(t.icon, t.label, tint = Color.Black) }
            } else {
                val on = i == sel
                Column(Modifier.clip(RoundedCornerShape(20.dp)).clickable { onSel(i) }.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(t.icon, t.label, tint = if (on) MaterialTheme.colorScheme.primary else Color(0xFF8A8F9C))
                    Text(t.label, fontSize = 10.sp, color = if (on) MaterialTheme.colorScheme.primary else Color(0xFF8A8F9C))
                }
            }
        }
    }
}

@Composable
fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$name\nقريباً", color = Color.White, fontSize = 20.sp, fontFamily = Cairo)
    }
}
