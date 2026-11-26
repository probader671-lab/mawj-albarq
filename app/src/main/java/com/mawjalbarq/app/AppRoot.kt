package com.mawjalbarq.app
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow

private data class Tab(val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("الرئيسية", Icons.Filled.Home), Tab("المقاطع", Icons.Filled.PlayArrow),
    Tab("رفع", Icons.Filled.Add), Tab("المنشورات", Icons.Filled.Article), Tab("حسابي", Icons.Filled.Person))

@Composable
fun AppRoot(prefs: ThemePrefs) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        var sel by remember { mutableIntStateOf(1) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when (sel) {
                1 -> ClipsScreen()
                4 -> ProfileScreen(prefs)
                else -> Placeholder(tabs[sel].label)
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
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$name\nقريباً", color = Color.White, fontSize = 20.sp, fontFamily = Cairo)
    }
}

@Composable
private fun ProfileScreen(prefs: ThemePrefs) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
        Text("حسابي", color = Color.White, fontSize = 24.sp, fontFamily = Cairo)
        Spacer(Modifier.height(24.dp))
        Text("لون التطبيق", color = Color.White, fontSize = 16.sp, fontFamily = Cairo)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Accents.forEachIndexed { i, a ->
                Box(Modifier.size(44.dp).clip(CircleShape).background(a.main)
                    .border(if (i == prefs.index) 3.dp else 0.dp, Color.White, CircleShape).clickable { prefs.set(i) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(Accents[prefs.index].name, color = Color(0xFF8A8F9C), fontSize = 13.sp)
    }
}
