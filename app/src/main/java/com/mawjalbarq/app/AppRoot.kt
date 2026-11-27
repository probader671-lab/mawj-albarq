package com.mawjalbarq.app
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
        var aiChat by remember { mutableStateOf(false) }
        var notifs by remember { mutableStateOf(false) }
        val appCtx = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(Unit) { if (App.loggedIn && registerDevice()) App.logout(); Feed.init(appCtx); if (Feed.videos.isEmpty() && !Feed.loading) Feed.load() }
        UpdatePrompt()
        ShareProgressHost()
        // Back stack for tabs: back returns to the previous tab; the app exits only when there is nowhere left to go.
        val tabHistory = remember { mutableStateListOf<Int>() }
        var lastSel by remember { mutableIntStateOf(sel) }
        var popping by remember { mutableStateOf(false) }
        LaunchedEffect(sel) {
            if (sel != lastSel) { if (!popping) { tabHistory.remove(lastSel); tabHistory.add(lastSel) }; lastSel = sel }
            popping = false
        }
        androidx.activity.compose.BackHandler(enabled = notifs || suggestions || aiChat || tabHistory.isNotEmpty() || sel != 0) {
            when {
                notifs -> notifs = false
                suggestions -> suggestions = false
                aiChat -> aiChat = false
                tabHistory.isNotEmpty() -> { popping = true; sel = tabHistory.removeAt(tabHistory.size - 1) }
                sel != 0 -> { popping = true; sel = 0 }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(PageBg)) {
            val isWide = maxWidth >= 600.dp
            val showNav = Ui.barVisible && !(sel == 1 && Ui.deep) && !suggestions && !notifs && !aiChat && Feed.activeVideo == null
            val visited = remember { mutableStateListOf(sel) }
            if (sel !in visited) visited.add(sel)
            @Composable fun Keep(i: Int, content: @Composable () -> Unit) {
                if (i in visited) {
                    val alpha by animateFloatAsState(if (sel == i) 1f else 0f, tween(160), label = "tabAlpha_$i")
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { this.alpha = alpha }
                            .layout { m, c ->
                                val p = m.measure(c)
                                if (sel == i) layout(p.width, p.height) { p.place(0, 0) } else layout(0, 0) {}
                            }
                    ) { content() }
                }
            }
            Row(Modifier.fillMaxSize()) {
                if (isWide && showNav) {
                    MawjNavRail(sel = sel, onSel = { sel = it; Ui.barVisible = true })
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Keep(0) { HomeScreen(onOpen = { sel = 1 }, onNeedLogin = { sel = 4 }, onSuggestions = { suggestions = true }, onNotifs = { notifs = true }, onAi = { aiChat = true }) }
                    if (sel == 1) ClipsScreen(onNeedLogin = { sel = 4 }, onExit = { sel = 0 })
                    if (sel == 2) UploadScreen(onNeedLogin = { sel = 4 }, onDone = { sel = 1 })
                    Keep(3) { PostsScreen(onNeedLogin = { sel = 4 }) }
                    Keep(4) { ProfileScreen(prefs, onOpenFeed = { sel = 1 }, onSuggestions = { suggestions = true }, onGoUpload = { sel = 2 }) }

                    // Floating Mini-Player when minimized from FullPlayer
                    val mini = Feed.minimizedVideo
                    val showMini = mini != null && Feed.activeVideo == null && !suggestions && !notifs && !aiChat
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(
                                start = 10.dp,
                                end = 10.dp,
                                bottom = if (!isWide && showNav) 62.dp else 10.dp
                            )
                    ) {
                        OverlayAnimatedVisibility(
                            visible = showMini,
                            enter = slideInVertically(tween(180)) { it } + fadeIn(tween(150)),
                            exit = slideOutVertically(tween(120)) { it } + fadeOut(tween(100))
                        ) {
                            if (mini != null) {
                                MiniPlayerBar(
                                    v = mini,
                                    onResume = {
                                        Feed.minimizedVideo = null
                                        Feed.activeVideo = mini
                                    },
                                    onClose = { Feed.minimizedVideo = null }
                                )
                            }
                        }
                    }

                    if (!isWide) {
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            OverlayAnimatedVisibility(
                                visible = showNav,
                                enter = slideInVertically(tween(140)) { it } + fadeIn(tween(120)),
                                exit = slideOutVertically(tween(90)) { it } + fadeOut(tween(80))
                            ) { PillBar(sel, { sel = it; Ui.barVisible = true }, Modifier) }
                        }
                    }
                }
            }
            if (notifs) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { NotificationsScreen({ notifs = false }) { vid -> notifs = false; Feed.videos.firstOrNull { it.id == vid }?.let { Feed.open(it) { sel = 1 } } } }
            if (aiChat) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { AiChatScreen({ aiChat = false }, { sel = 4 }) }
            if (suggestions) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { SuggestionsScreen { suggestions = false } }
            Feed.activeVideo?.let { v -> FullPlayer(v) { Feed.activeVideo = null } }
        }
    }
}

@Composable
private fun MawjNavRail(sel: Int, onSel: (Int) -> Unit) {
    NavigationRail(
        containerColor = if (Pal.isDark) Color(0xFF0E131B) else Color(0xFFFFFFFF),
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()
    ) {
        Spacer(Modifier.height(12.dp))
        tabs.forEachIndexed { i, t ->
            if (i == 2) {
                FloatingActionButton(
                    onClick = { onSel(i) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black,
                    modifier = Modifier.padding(vertical = 8.dp).size(48.dp).testTag("nav_tab_$i")
                ) {
                    Icon(t.activeIcon, t.label)
                }
            } else {
                val on = i == sel
                NavigationRailItem(
                    selected = on,
                    onClick = { onSel(i) },
                    icon = { Icon(if (on) t.activeIcon else t.idleIcon, t.label) },
                    label = { Text(t.label, fontFamily = Cairo, fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.testTag("nav_tab_$i")
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(v: Video, onResume: () -> Unit, onClose: () -> Unit) {
    val bg = if (Pal.isDark) Color(0xEB131924) else Color(0xF5FFFFFF)
    val borderCol = if (Pal.isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.40f) else Color(0x22000000)
    val textMain = if (Pal.isDark) Color.White else Color(0xFF0F172A)
    val textSec = if (Pal.isDark) Color(0xFFB2BBCB) else Color(0xFF475569)
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(16.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onResume()
            }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("mini_player_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(74.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
        ) {
            VideoThumb(v, Modifier.fillMaxSize(), ContentScale.Crop)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                v.title.ifBlank { "فيديو" },
                color = textMain,
                fontFamily = Cairo,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${v.author} • متابعة المشاهدة",
                color = textSec,
                fontFamily = Tajawal,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onResume()
            },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("mini_player_resume")
        ) {
            Icon(Icons.Filled.PlayArrow, "متابعة المشاهدة", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("mini_player_close")
        ) {
            Icon(Icons.Filled.Close, "إغلاق المشغل المصغر", tint = textSec, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PillBar(sel: Int, onSel: (Int) -> Unit, modifier: Modifier) {
    val barShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    val barBg = if (Pal.isDark) Color(0x9E0B0F17) else Color(0xCCFFFFFF)
    val barBorder = if (Pal.isDark) Color(0x28FFFFFF) else Color(0x18000000)
    val idleTint = if (Pal.isDark) Color(0xFFB4BDCB) else Color(0xFF4B5563)
    Box(
        modifier
            .fillMaxWidth()
            .clip(barShape)
            .background(barBg)
            .border(0.75.dp, barBorder, barShape)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
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
                            .clickable { onSel(i) }
                            .testTag("nav_tab_$i"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(42.dp, 34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(t.activeIcon, t.label, tint = Color.Black, modifier = Modifier.size(22.dp))
                        }
                    }
                } else {
                    val on = i == sel
                    val indicatorW by animateDpAsState(if (on) 22.dp else 0.dp, tween(180), label = "navInd")
                    val tint by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else idleTint, tween(160), label = "navTint")
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSel(i) }
                            .testTag("nav_tab_$i"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier
                                .width(indicatorW)
                                .height(2.5.dp)
                                .clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                        )
                        Spacer(Modifier.height(3.dp))
                        Icon(
                            if (on) t.activeIcon else t.idleIcon,
                            t.label,
                            tint = tint,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            t.label,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            fontFamily = Cairo,
                            color = tint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayAnimatedVisibility(
    visible: Boolean,
    enter: androidx.compose.animation.EnterTransition,
    exit: androidx.compose.animation.ExitTransition,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(visible = visible, enter = enter, exit = exit) {
        content()
    }
}

@Composable
fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$name\nقريباً", color = Color.White, fontSize = 20.sp, fontFamily = Cairo)
    }
}
