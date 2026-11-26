package com.mawjalbarq.app
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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

private data class Tab(val label: String, val activeIcon: ImageVector, val idleIcon: ImageVector)
private val tabs = listOf(
    Tab("الرئيسية", Icons.Filled.Home, Icons.Outlined.Home),
    Tab("المقاطع", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
    Tab("رفع", Icons.Filled.Add, Icons.Filled.Add),
    Tab("المنشورات", Icons.Filled.Article, Icons.Outlined.Article),
    Tab("حسابي", Icons.Filled.Person, Icons.Outlined.Person)
)

@Composable
fun AppRoot(prefs: ThemePrefs) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        var sel by remember { mutableIntStateOf(1) }
        var suggestions by remember { mutableStateOf(false) }
        var notifs by remember { mutableStateOf(false) }
        val appCtx = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(Unit) { Feed.init(appCtx); if (Feed.videos.isEmpty() && !Feed.loading) Feed.load() }
        UpdatePrompt()
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when (sel) {
                0 -> HomeScreen(onOpen = { sel = 1 }, onNeedLogin = { sel = 4 }, onSuggestions = { suggestions = true }, onNotifs = { notifs = true })
                1 -> ClipsScreen(onNeedLogin = { sel = 4 })
                2 -> UploadScreen(onNeedLogin = { sel = 4 }, onDone = { sel = 1 })
                3 -> PostsScreen(onNeedLogin = { sel = 4 })
                else -> ProfileScreen(prefs, onOpenFeed = { sel = 1 }, onSuggestions = { suggestions = true })
            }
            androidx.compose.animation.AnimatedVisibility(visible = Ui.barVisible && !(sel == 1 && Ui.deep) && !suggestions && !notifs && Feed.activeVideo == null,
                enter = androidx.compose.animation.slideInVertically { it * 2 }, exit = androidx.compose.animation.slideOutVertically { it * 2 },
                modifier = Modifier.align(Alignment.BottomCenter)) { PillBar(sel, { sel = it; Ui.barVisible = true }, Modifier) }
            if (notifs) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { NotificationsScreen({ notifs = false }) { vid -> notifs = false; Feed.videos.firstOrNull { it.id == vid }?.let { Feed.open(it) { sel = 1 } } } }
            if (suggestions) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { SuggestionsScreen { suggestions = false } }
            Feed.activeVideo?.let { v -> FullPlayer(v) { Feed.activeVideo = null } }
        }
    }
}

@Composable
private fun PillBar(sel: Int, onSel: (Int) -> Unit, modifier: Modifier) {
    val barShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(barShape)
            .background(Color(0xF50B0F17))
            .border(0.75.dp, Color(0x1EFFFFFF), barShape)
            .navigationBarsPadding()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { i, t ->
            if (i == 2) {
                Box(
                    Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                        .clickable { onSel(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(44.dp, 36.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(t.activeIcon, t.label, tint = Color.Black, modifier = Modifier.size(22.dp))
                    }
                }
            } else {
                val on = i == sel
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSel(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .width(22.dp)
                            .height(2.5.dp)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    )
                    Spacer(Modifier.height(4.dp))
                    Icon(
                        if (on) t.activeIcon else t.idleIcon,
                        t.label,
                        tint = if (on) MaterialTheme.colorScheme.primary else Color(0xFF8A92A2),
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        t.label,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        fontFamily = Cairo,
                        color = if (on) MaterialTheme.colorScheme.primary else Color(0xFF8A92A2)
                    )
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
