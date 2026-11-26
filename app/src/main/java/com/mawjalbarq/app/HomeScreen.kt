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
            .minimumInteractiveComponentSize()
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
    onReport: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val liked = Feed.liked[v.id] == true
    val saved = Feed.saved[v.id] == true
    val following = Feed.following[v.userId] == true
    var showDesc by remember(v.id) { mutableStateOf(false) }

    val cardBorder = if (hero) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
    } else {
        WatchCardBorder
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(WatchCardBg)
            .border(if (hero) 1.25.dp else 1.dp, cardBorder, RoundedCornerShape(20.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(Color.Black)
                .clickable(onClick = onOpen)
                .testTag("video_card_${v.id}")
        ) {
            if (v.thumb != null) {
                if (!v.portrait) {
                    AsyncImage(v.thumb, v.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    AsyncImage(
                        v.thumb,
                        null,
                        Modifier.fillMaxSize().alpha(0.32f),
                        contentScale = ContentScale.Crop
                    )
                    Box(Modifier.fillMaxSize().background(Color(0x55000000)))
                    AsyncImage(v.thumb, v.title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(68.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                        )
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.72f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, "تشغيل", tint = Color.White, modifier = Modifier.size(30.dp))
            }

            if (hero) {
                Text(
                    "مميز",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Cairo,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }

            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xB30B1720))
                    .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    if (!v.portrait) Icons.Filled.StayCurrentLandscape else Icons.Filled.Smartphone,
                    contentDescription = null,
                    tint = if (!v.portrait) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    if (!v.portrait) "عريض" else "مقطع",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = Cairo
                )
            }

            if (v.duration > 0) {
                Text(
                    formatDuration(v.duration),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (v.desc.isNotBlank()) showDesc = !showDesc else onOpen()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    v.title,
                    color = WatchTextMain,
                    fontFamily = Cairo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    maxLines = if (showDesc) 5 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (v.desc.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (showDesc) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = "عرض الوصف",
                        tint = WatchTextSec,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            val dateText = formatVideoDate(v.createdAt)
            val meta = buildList {
                add("${formatCount(v.views)} مشاهدة")
                if (dateText.isNotBlank()) add(dateText)
                if (v.comments > 0) add("${v.comments} تعليق")
            }.joinToString(" • ")
            Text(
                meta,
                color = WatchTextSec,
                fontSize = 12.5.sp,
                fontFamily = Cairo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            AnimatedVisibility(visible = showDesc && v.desc.isNotBlank()) {
                Text(
                    v.desc,
                    color = WatchTextMain.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    fontFamily = Cairo,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x0DFFFFFF))
                        .padding(12.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    WatchActionPill(
                        icon = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        label = "${v.likes}",
                        active = liked,
                        activeBg = MaterialTheme.colorScheme.primary
                    ) {
                        if (!App.loggedIn) onNeedLogin() else scope.launch { Feed.toggleLike(v.id) }
                    }
                }
                item {
                    WatchActionPill(
                        icon = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        label = if (saved) "محفوظ" else "حفظ",
                        active = saved,
                        activeBg = MaterialTheme.colorScheme.primary
                    ) {
                        scope.launch { Feed.toggleSave(v.id) }
                    }
                }
                item {
                    WatchActionPill(
                        icon = Icons.Outlined.Share,
                        label = "مشاركة"
                    ) {
                        ctx.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, "${v.title} - موج البرق\n${v.url}"),
                                "مشاركة"
                            )
                        )
                    }
                }
                item {
                    WatchActionPill(
                        icon = Icons.Outlined.Flag,
                        label = "إبلاغ",
                        onClick = onReport
                    )
                }
            }

            HorizontalDivider(
                Modifier.padding(vertical = 12.dp),
                thickness = 0.75.dp,
                color = Color(0x14FFFFFF)
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = v.userId.isNotBlank()) { onCreator() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(2.dp)
                    ) {
                        Avatar(v.avatar, v.author, 40)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                v.author,
                                color = WatchTextMain,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = Cairo,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (v.verified) VerifiedBadge(14)
                        }
                        Text(
                            "${formatCount(v.followers)} منضم",
                            color = WatchTextSec,
                            fontSize = 12.sp,
                            fontFamily = Cairo
                        )
                    }
                }
                if (v.userId.isNotBlank() && v.userId != App.userId) {
                    Spacer(Modifier.width(8.dp))
                    WatchJoinButton(following = following) {
                        if (!App.loggedIn) {
                            onNeedLogin()
                        } else {
                            val joining = !following
                            scope.launch {
                                Feed.toggleFollow(v.userId)
                                if (joining) {
                                    Toast.makeText(ctx, "مرحباً بك في ${v.author}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    onOpen: () -> Unit,
    onNeedLogin: () -> Unit = {},
    onSuggestions: () -> Unit = {},
    onNotifs: () -> Unit = {}
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
                    .background(Color(0xA60C1218))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image38()
                Spacer(Modifier.width(10.dp))
                Text(
                    "موج البرق",
                    color = WatchTextMain,
                    fontFamily = Cairo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(WatchPillBg)
                        .border(1.dp, WatchCardBorder, CircleShape)
                        .clickable(enabled = !Feed.loading) { scope.launch { Feed.load() } }
                        .testTag("home_refresh_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (Feed.loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.Refresh, "تحديث", tint = WatchTextMain, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(6.dp))
                BadgedBox(badge = { if (Notifs.unread > 0) Badge { Text(Notifs.unread.toString()) } }) {
                    RoundBtn(Icons.Filled.Notifications, "الإشعارات", 42, WatchTextMain, onClick = onNotifs)
                }
                Spacer(Modifier.width(6.dp))
                RoundBtn(Icons.Filled.Lightbulb, "الاقتراحات", 42, MaterialTheme.colorScheme.secondary, onClick = onSuggestions)
                Spacer(Modifier.width(6.dp))
                RoundBtn(
                    if (searching) Icons.Filled.Close else Icons.Filled.Search,
                    "بحث",
                    42
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 84.dp)
            ) {
                if (q.isBlank()) item { Box(Modifier.padding(horizontal = 14.dp)) { StoriesStrip(onNeedLogin) } }
                when {
                    vids.isEmpty() && Feed.loading -> {
                        item(key = "sk0") { HomeVideoSkeletonCard(16f / 9f) }
                        item(key = "sk1") { HomeVideoSkeletonCard(16f / 9f) }
                        item(key = "sk2") { HomeSuggestionSkeletonRow() }
                        item(key = "sk3") { HomeSuggestionSkeletonRow() }
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
                        val featuredCount = if (q.isBlank()) 3 else vids.size
                        itemsIndexed(vids.take(featuredCount), key = { _, v -> v.id }) { i, v ->
                            WatchFeedVideoCard(
                                v = v,
                                ratio = 16f / 9f,
                                hero = i == 0 && q.isBlank(),
                                onOpen = { open(v) },
                                onCreator = { if (v.userId.isNotBlank()) creatorId = v.userId },
                                onNeedLogin = onNeedLogin,
                                onReport = { reportFor = v }
                            )
                        }
                    }
                }

                if (q.isBlank() && vids.size > 3) {
                    val afterThree = vids.drop(3)
                    item(key = "home_rail") {
                        WatchRailSection(
                            title = "اقتراحات قد تعجبك",
                            videos = afterThree.take(10),
                            onSelect = { open(it) }
                        )
                    }
                    item(key = "home_more_header") {
                        Text(
                            "المزيد من الفيديوهات",
                            color = WatchTextMain,
                            fontFamily = Cairo,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                        )
                    }
                    itemsIndexed(afterThree, key = { _, v -> "feed_${v.id}" }) { _, v ->
                        WatchFeedVideoCard(
                            v = v,
                            ratio = 16f / 9f,
                            hero = false,
                            onOpen = { open(v) },
                            onCreator = { if (v.userId.isNotBlank()) creatorId = v.userId },
                            onNeedLogin = onNeedLogin,
                            onReport = { reportFor = v }
                        )
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
    androidx.compose.foundation.Image(painterResource(R.drawable.logo_mark), "شعار موج البرق", Modifier.size(38.dp).clip(CircleShape))
}
