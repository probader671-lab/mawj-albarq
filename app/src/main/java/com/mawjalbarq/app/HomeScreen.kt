package com.mawjalbarq.app

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
private fun RoundBtn(
    icon: ImageVector,
    contentDesc: String,
    size: Int = 44,
    tint: Color = WatchTextMain,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .sizeIn(minWidth = 42.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(WatchPillBg)
                .border(1.dp, WatchCardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDesc, tint = tint, modifier = Modifier.size((size * 0.50f).dp))
        }
    }
}

@Composable
private fun WatchFeedVideoCard(
    v: Video,
    ratio: Float,
    hero: Boolean,
    onOpen: () -> Unit,
    onCreator: () -> Unit,
    onNeedLogin: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val saved = Feed.saved[v.id] == true
    val borderColor = if (hero) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else WatchCardBorder
    Column(
        modifier
            .clip(RoundedCornerShape(if (hero) 16.dp else 12.dp))
            .background(WatchCardBg)
            .border(if (hero) 1.25.dp else 0.75.dp, borderColor, RoundedCornerShape(if (hero) 16.dp else 12.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(Color.Black)
                .clickable(onClick = onOpen)
                .testTag("video_card_${v.id}")
        ) {
            if (v.portrait == (ratio < 1f)) {
                VideoThumb(v, Modifier.fillMaxSize(), ContentScale.Crop)
            } else {
                VideoThumb(v, Modifier.fillMaxSize().alpha(0.32f), ContentScale.Crop)
                VideoThumb(v, Modifier.fillMaxSize(), ContentScale.Fit)
            }
            if (hero) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color.Black, modifier = Modifier.size(12.dp))
                    Text("مميز • HD", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = Cairo)
                }
            }
            if (v.duration > 0) {
                Text(
                    formatDuration(v.duration),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    v.title,
                    color = WatchTextMain,
                    fontFamily = Cairo,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (hero) 14.5.sp else 12.5.sp,
                    lineHeight = if (hero) 20.sp else 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable(enabled = v.userId.isNotBlank(), onClick = onCreator)
                ) {
                    Text(
                        v.author,
                        color = WatchTextSec,
                        fontSize = 11.5.sp,
                        fontFamily = Cairo,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (v.verified) VerifiedBadge(12)
                    Text(
                        "• ${formatCount(v.views)} مشاهدة",
                        color = WatchTextSec,
                        fontSize = 11.sp,
                        fontFamily = Tajawal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch { Feed.toggleSave(v.id) }
                    },
                    modifier = Modifier.minimumInteractiveComponentSize().size(40.dp).testTag("home_save_${v.id}")
                ) {
                    Icon(
                        if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        "حفظ",
                        tint = if (saved) MaterialTheme.colorScheme.primary else WatchTextSec,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = onReport,
                    modifier = Modifier.minimumInteractiveComponentSize().size(40.dp).testTag("home_report_${v.id}")
                ) {
                    Icon(
                        Icons.Outlined.Flag,
                        "إبلاغ",
                        tint = WatchTextSec,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/** Two compact cards per row so more videos fit on the home page. */
/** Home layout: false = two cards per row, true = YouTube-style, one big card per row. Toggled by tapping the app icon in the header. */
object HomePrefs {
    private var sp: android.content.SharedPreferences? = null
    var big by androidx.compose.runtime.mutableStateOf(false)
        private set
    fun init(ctx: android.content.Context) { sp = ctx.applicationContext.getSharedPreferences("home", android.content.Context.MODE_PRIVATE); big = sp?.getBoolean("big", false) ?: false }
    fun toggle() { big = !big; sp?.edit()?.putBoolean("big", big)?.apply() }
}

@Composable
private fun CardRow(
    pair: List<Video>, cols: Int, open: (Video) -> Unit, onCreator: (Video) -> Unit, onNeedLogin: () -> Unit, onReport: (Video) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = if (cols == 1) 12.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pair.forEach { v ->
            WatchFeedVideoCard(v, if (cols == 1) 16f / 9f else 0.78f, false, { open(v) }, { onCreator(v) }, onNeedLogin, { onReport(v) }, Modifier.weight(1f))
        }
        repeat((cols - pair.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
fun HomeScreen(
    onOpen: () -> Unit,
    onNeedLogin: () -> Unit = {},
    onSuggestions: () -> Unit = {},
    onNotifs: () -> Unit = {},
    onAi: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var searching by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var reportFor by remember { mutableStateOf<Video?>(null) }
    var creatorId by remember { mutableStateOf<String?>(null) }

    val conn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -12f) Ui.barVisible = false else if (available.y > 12f) Ui.barVisible = true
                return Offset.Zero
            }
        }
    }

    // Only horizontal videos appear in Home; portrait clips belong exclusively to ClipsScreen
    val all = Feed.videos.toList()
    // debounce: filter once typing pauses (400 ms) instead of on every keystroke
    var dq by remember { mutableStateOf("") }
    LaunchedEffect(q) { if (q.isBlank()) dq = "" else { kotlinx.coroutines.delay(400); dq = q } }
    val vids = if (dq.isBlank()) all else all.filter { it.title.contains(dq, true) || it.author.contains(dq, true) }

    var promptVid by remember { mutableStateOf<Video?>(null) }
    LaunchedEffect(vids.size, App.userId) { if (promptVid == null) promptVid = Reco.promptCandidate(all) }

    val lastVisible by remember {
        derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
    }
    val totalItems by remember {
        derivedStateOf { list.layoutInfo.totalItemsCount }
    }
    LaunchedEffect(lastVisible, totalItems, vids.size, Feed.loading, Feed.loadingMore, Feed.hasMore, q) {
        if (q.isBlank() && !Feed.loading && !Feed.loadingMore && Feed.hasMore) {
            val remaining = (totalItems - 1 - lastVisible).coerceAtLeast(0)
            if (vids.size < 10 || remaining < 10) {
                Feed.loadMore(ensureWide = true)
            }
        }
    }

    fun open(v: Video) {
        Feed.open(v, onOpen)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(WatchBg), contentAlignment = Alignment.TopCenter) {
        val cols = if (HomePrefs.big) 1 else if (maxWidth >= 600.dp) 3 else 2
        Column(Modifier.fillMaxSize().statusBarsPadding().widthIn(max = 920.dp)) {
            val haptic = LocalHapticFeedback.current
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(WatchBg)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tctx = androidx.compose.ui.platform.LocalContext.current
                Box(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            HomePrefs.toggle()
                            android.widget.Toast.makeText(tctx, if (HomePrefs.big) "عرض كبير (صف واحد)" else "عرض الشبكة", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) { Image38() }
                Spacer(Modifier.width(4.dp))
                Text(
                    "موج البرق",
                    color = WatchTextMain,
                    fontFamily = Cairo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .sizeIn(minWidth = 42.dp, minHeight = 48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !Feed.loading) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { Feed.load() }
                        }
                        .testTag("home_refresh_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(WatchPillBg)
                            .border(1.dp, WatchCardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (Feed.loading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Filled.Refresh, "تحديث", tint = WatchTextMain, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                BadgedBox(badge = { if (Notifs.unread > 0) Badge { Text(Notifs.unread.toString()) } }) {
                    RoundBtn(Icons.Filled.Notifications, "الإشعارات", 34, WatchTextMain, onClick = onNotifs)
                }
                RoundBtn(Icons.Filled.SmartToy, "المساعد الذكي", 34, MaterialTheme.colorScheme.primary, onClick = onAi)
                RoundBtn(Icons.Filled.Lightbulb, "الاقتراحات", 34, MaterialTheme.colorScheme.secondary, onClick = onSuggestions)
                RoundBtn(
                    if (searching) Icons.Filled.Close else Icons.Filled.Search,
                    "بحث",
                    34
                ) { searching = !searching; if (!searching) q = "" }
            }

            AnimatedVisibility(searching) {
                val spinTransition = rememberInfiniteTransition(label = "searchBorderSpin")
                val spinAngle by spinTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
                    label = "angle"
                )
                val ringColors = remember {
                    listOf(
                        Color(0xFFFF7A1A),
                        Color(0xFF10B981),
                        Color(0xFF2EA8FF),
                        Color(0xFFFFD21F),
                        Color(0xFFFF5CA8),
                        Color(0xFFFF7A1A)
                    )
                }
                Box(
                    Modifier
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .drawBehind {
                            val r = maxOf(size.width, size.height) * 1.4f
                            rotate(spinAngle) {
                                drawRect(
                                    brush = Brush.sweepGradient(ringColors, center = center),
                                    topLeft = Offset(center.x - r, center.y - r),
                                    size = Size(r * 2f, r * 2f)
                                )
                            }
                        }
                        .padding(2.5.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = WatchCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f)) {
                                if (q.isEmpty()) Text("ابحث عن فيديوهات أو صناع محتوى...", color = WatchTextSec, fontSize = 14.sp)
                                BasicTextField(
                                    q,
                                    { q = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = WatchTextMain, fontSize = 14.sp),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth().testTag("home_search_input")
                                )
                            }
                            if (q.isNotEmpty()) {
                                Icon(Icons.Filled.Close, "مسح البحث", tint = WatchTextMain, modifier = Modifier.size(18.dp).clickable { q = "" })
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(Feed.isOffline) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(WatchCardBg)
                        .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("home_offline_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.CloudOff, "وضع عدم الاتصال", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Text(
                        "أنت غير متصل • يتم عرض البيانات المحفوظة محلياً",
                        color = WatchTextMain,
                        fontSize = 12.sp,
                        fontFamily = Cairo,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "تحديث",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Cairo,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(WatchPillBg)
                            .clickable(enabled = !Feed.loading) { scope.launch { Feed.load() } }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(thickness = 0.75.dp, color = Color(0x10FFFFFF))

            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding().nestedScroll(conn),
                state = list,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 84.dp)
            ) {
                if (q.isBlank()) item { Box(Modifier.padding(horizontal = 14.dp)) { StoriesStrip(onNeedLogin) } }
                when {
                    vids.isEmpty() && Feed.loading -> {
                        item(key = "sk0") { HomeVideoSkeletonCard(0.78f) }
                        item(key = "sk1") { HomeVideoSkeletonCard(0.78f) }
                    }
                    vids.isEmpty() -> item {
                        Column(
                            Modifier.fillMaxWidth().padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (Feed.error) {
                                Text("تعذر تحميل الفيديوهات", color = WatchTextMain, fontFamily = Cairo, fontSize = 15.sp)
                                Button(onClick = { scope.launch { Feed.load() } }, shape = RoundedCornerShape(50)) {
                                    Text("إعادة المحاولة", fontFamily = Cairo)
                                }
                            } else {
                                Icon(Icons.Filled.VideoLibrary, null, tint = WatchTextSec, modifier = Modifier.size(44.dp))
                                Text(
                                    if (q.isNotBlank()) "لا توجد نتائج مطابقة" else "لا توجد فيديوهات بعد",
                                    color = WatchTextSec,
                                    fontFamily = Cairo
                                )
                            }
                        }
                    }
                    else -> {
                        val hasHero = false
                        if (hasHero) {
                            val heroVid = vids.first()
                            item(key = "hero_${heroVid.id}") {
                                WatchFeedVideoCard(
                                    v = heroVid,
                                    ratio = 2.0f,
                                    hero = true,
                                    onOpen = { open(heroVid) },
                                    onCreator = { if (heroVid.userId.isNotBlank()) creatorId = heroVid.userId },
                                    onNeedLogin = onNeedLogin,
                                    onReport = { reportFor = heroVid },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                                )
                            }
                        }
                        val restAfterHero = if (hasHero) vids.drop(1) else vids
                        val featuredCount = if (q.isBlank()) minOf(4, restAfterHero.size) else restAfterHero.size
                        itemsIndexed(restAfterHero.take(featuredCount).chunked(cols), key = { _, pr -> "top_${pr.first().id}" }) { _, pr ->
                            CardRow(pr, cols, { open(it) }, { if (it.userId.isNotBlank()) creatorId = it.userId }, onNeedLogin, { reportFor = it })
                        }
                    }
                }

                if (q.isBlank() && vids.size > 5) {
                    val afterFive = vids.drop(5)
                    promptVid?.let { pv -> item(key = "reco_prompt") { InterestCard(pv) { yes -> Reco.answerPrompt(pv, yes); promptVid = null } } }
                    itemsIndexed(afterFive.chunked(cols), key = { _, pr -> "feed_${pr.first().id}" }) { _, pr ->
                        CardRow(pr, cols, { open(it) }, { if (it.userId.isNotBlank()) creatorId = it.userId }, onNeedLogin, { reportFor = it })
                    }
                }

                if (Feed.loadingMore) {
                    item(key = "home_loading_more") {
                        HomeVideoSkeletonCard(0.78f)
                    }
                }
            }
        }
    }
    reportFor?.let { v -> ReportSheet("video", v.id) { reportFor = null } }
    creatorId?.let { cid ->
        CreatorSheet(
            id = cid,
            onPlay = { vid ->
                creatorId = null
                Feed.videos.firstOrNull { it.id == vid }?.let { open(it) }
            },
            onClose = { creatorId = null }
        )
    }
}

@Composable
private fun Image38() {
    androidx.compose.foundation.Image(painterResource(R.drawable.logo_mark), "شعار موج البرق", Modifier.size(30.dp).clip(CircleShape))
}
