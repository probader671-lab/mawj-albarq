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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
            .size(size.dp)
            .clip(CircleShape)
            .background(WatchPillBg)
            .border(1.dp, WatchCardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDesc, tint = tint, modifier = Modifier.size((size * 0.48f).dp))
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
    val saved = Feed.saved[v.id] == true
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(WatchCardBg)
            .border(0.75.dp, WatchCardBorder, RoundedCornerShape(12.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
                .clickable(onClick = onOpen)
                .testTag("video_card_${v.id}")
        ) {
            if (!v.portrait) {
                VideoThumb(v, Modifier.fillMaxSize(), ContentScale.Crop)
            } else {
                VideoThumb(v, Modifier.fillMaxSize().alpha(0.32f), ContentScale.Crop)
                VideoThumb(v, Modifier.fillMaxSize(), ContentScale.Fit)
            }
            if (v.duration > 0) {
                Text(
                    formatDuration(v.duration),
                    color = Color.White,
                    fontSize = 10.sp,
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
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 2.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(
                    v.title,
                    color = WatchTextMain,
                    fontFamily = Cairo,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    v.author + " • " + formatCount(v.views) + " مشاهدة",
                    color = WatchTextSec,
                    fontSize = 10.5.sp,
                    fontFamily = Cairo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = v.userId.isNotBlank(), onClick = onCreator)
                )
            }
            Row {
                Icon(
                    if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "حفظ",
                    tint = if (saved) MaterialTheme.colorScheme.primary else WatchTextSec,
                    modifier = Modifier.size(28.dp).clip(CircleShape).clickable { scope.launch { Feed.toggleSave(v.id) } }.padding(5.dp)
                )
                Icon(
                    Icons.Outlined.Flag, "إبلاغ", tint = WatchTextSec,
                    modifier = Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onReport).padding(5.dp)
                )
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
    pair: List<Video>, open: (Video) -> Unit, onCreator: (Video) -> Unit, onNeedLogin: () -> Unit, onReport: (Video) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = if (HomePrefs.big) 4.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pair.forEach { v ->
            WatchFeedVideoCard(v, 16f / 9f, false, { open(v) }, { onCreator(v) }, onNeedLogin, { onReport(v) }, Modifier.weight(1f))
        }
        if (pair.size == 1 && !HomePrefs.big) Spacer(Modifier.weight(1f))
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
    val all = Feed.wideVideos
    val vids = if (q.isBlank()) all else all.filter { it.title.contains(q, true) || it.author.contains(q, true) }

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

    Box(Modifier.fillMaxSize().background(WatchBg), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().statusBarsPadding().widthIn(max = 760.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(WatchBg)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tctx = androidx.compose.ui.platform.LocalContext.current
                Box(Modifier.clip(CircleShape).clickable {
                    HomePrefs.toggle()
                    android.widget.Toast.makeText(tctx, if (HomePrefs.big) "عرض كبير (صف واحد)" else "عرض الشبكة", android.widget.Toast.LENGTH_SHORT).show()
                }) { Image38() }
                Spacer(Modifier.width(10.dp))
                Text(
                    "موج البرق",
                    color = WatchTextMain,
                    fontFamily = Cairo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(WatchPillBg)
                        .border(1.dp, WatchCardBorder, CircleShape)
                        .clickable(enabled = !Feed.loading) { scope.launch { Feed.load() } }
                        .testTag("home_refresh_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (Feed.loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.Refresh, "تحديث", tint = WatchTextMain, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(6.dp))
                BadgedBox(badge = { if (Notifs.unread > 0) Badge { Text(Notifs.unread.toString()) } }) {
                    RoundBtn(Icons.Filled.Notifications, "الإشعارات", 34, WatchTextMain, onClick = onNotifs)
                }
                Spacer(Modifier.width(6.dp))
                RoundBtn(Icons.Filled.AutoAwesome, "المساعد الذكي", 34, MaterialTheme.colorScheme.primary, onClick = onAi)
                RoundBtn(Icons.Filled.Lightbulb, "الاقتراحات", 34, MaterialTheme.colorScheme.secondary, onClick = onSuggestions)
                Spacer(Modifier.width(6.dp))
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
                        item(key = "sk0") { HomeVideoSkeletonCard(16f / 9f) }
                        item(key = "sk1") { HomeVideoSkeletonCard(16f / 9f) }
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
                        val featuredCount = if (q.isBlank()) minOf(4, vids.size) else vids.size
                        itemsIndexed(vids.take(featuredCount).chunked(if (HomePrefs.big) 1 else 2), key = { _, pr -> "top_${pr.first().id}" }) { _, pr ->
                            CardRow(pr, { open(it) }, { if (it.userId.isNotBlank()) creatorId = it.userId }, onNeedLogin, { reportFor = it })
                        }
                    }
                }

                if (q.isBlank() && vids.size > 4) {
                    val afterThree = vids.drop(4)
                    promptVid?.let { pv -> item(key = "reco_prompt") { InterestCard(pv) { yes -> Reco.answerPrompt(pv, yes); promptVid = null } } }
                    itemsIndexed(afterThree.chunked(if (HomePrefs.big) 1 else 2), key = { _, pr -> "feed_${pr.first().id}" }) { _, pr ->
                        CardRow(pr, { open(it) }, { if (it.userId.isNotBlank()) creatorId = it.userId }, onNeedLogin, { reportFor = it })
                    }
                }

                if (Feed.loadingMore) {
                    item(key = "home_loading_more") {
                        HomeVideoSkeletonCard(16f / 9f)
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
