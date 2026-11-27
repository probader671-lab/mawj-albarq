package com.mawjalbarq.app

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.LayoutInflater
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ClipsScreen(onNeedLogin: () -> Unit, onExit: () -> Unit = {}) {
    DarkLock()
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val videos = Feed.clips
    if (videos.isEmpty()) {
        if (Feed.loading) {
            ClipStageSkeleton()
        } else {
            Box(
                Modifier.fillMaxSize().background(Color(0xFF0A0D13)).statusBarsPadding().navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (Feed.error) "تعذر تحميل المقاطع" else "لا توجد مقاطع بعد", color = Color.White, fontSize = 16.sp, fontFamily = Cairo)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { scope.launch { Feed.load() } }, shape = RoundedCornerShape(50)) { Text("إعادة المحاولة", fontFamily = Cairo) }
                }
            }
        }
        return
    }

    val pager = rememberPagerState { videos.size }
    val player = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 35_000, 250, 500)
            .build()
        ExoPlayer.Builder(ctx)
            .setTrackSelector(Media.highQualityTrackSelector(ctx))
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(MediaCache.factory(ctx)))
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_ONE; playWhenReady = true; Media.withMp4Fallback(this) }
    }
    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) { isBuffering = state == Player.STATE_BUFFERING }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.clearVideoSurface()
            QuranBg.release(ctx, player)
            player.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE && !(videos.getOrNull(pager.currentPage)?.isQuran == true && QuranPrefs.enabled(ctx))) player.playWhenReady = false
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Feed.activeVideo) {
        if (Feed.activeVideo != null) player.playWhenReady = false
    }

    LaunchedEffect(Feed.jumpTo) {
        Feed.jumpTo?.let { j ->
            val tv = Feed.videos.getOrNull(j)
            Feed.jumpTo = null
            if (tv != null) {
                val k = videos.indexOfFirst { it.id == tv.id }
                if (k >= 0) pager.scrollToPage(k) else Feed.activeVideo = tv
            }
        }
    }

    val cur = pager.settledPage.coerceAtMost(videos.size - 1)
    val curId = videos[cur].id
    var speed by remember { mutableFloatStateOf(1f) }
    var uiStyle by remember { mutableStateOf(ClipStylePrefs.load(ctx)) }
    var hidden by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var fillZoom by remember { mutableStateOf(true) }

    fun cycleStyle() {
        val next = uiStyle.next()
        uiStyle = next
        hidden = false
        ClipStylePrefs.save(ctx, next)
    }

    val tracker = remember { Reco.Tracker() }
    DisposableEffect(Unit) { onDispose { tracker.stop() } }
    LaunchedEffect(curId) { Watch.save(ctx, curId); tracker.switchTo(videos.getOrNull(cur)) }
    LaunchedEffect(curId) {
        isBuffering = true
        player.setMediaItem(MediaItem.fromUri(Uri.parse(Media.playUrl(videos[cur].url, videos[cur].duration))))
        player.prepare()
        QuranBg.sync(ctx, player, videos[cur].isQuran)
        player.setPlaybackSpeed(speed)
        player.volume = if (muted) 0f else 1f
        player.playWhenReady = Feed.activeVideo == null
        if (cur >= videos.size - 10 && Feed.hasMore && !Feed.loadingMore) {
            Feed.loadMore(ensureWide = false)
        }
    }
    LaunchedEffect(curId, videos.size) {
        MediaCache.smartPrefetchNext(ctx, videos, cur, lookahead = 10)
    }
    LaunchedEffect(curId) {
        while (true) {
            delay(1500)
            val d = player.duration
            if (d > 0) Watch.savePos(ctx, curId, (player.currentPosition.toFloat() / d).coerceIn(0f, 1f))
        }
    }
    var hapticPageArmed by remember { mutableStateOf(false) }
    LaunchedEffect(cur) {
        Ui.deep = cur >= 5
        if (hapticPageArmed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        else hapticPageArmed = true
    }
    DisposableEffect(Unit) { onDispose { Ui.deep = false } }
    LaunchedEffect(speed, curId) { player.setPlaybackSpeed(speed) }

    val saveAnim = remember { Animatable(0f) }
    var saveTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(saveTick) {
        if (saveTick > 0) {
            saveAnim.snapTo(0f)
            saveAnim.animateTo(1f, tween(1350, easing = LinearEasing))
        }
    }

    var commentsFor by remember { mutableStateOf<Video?>(null) }
    var reportFor by remember { mutableStateOf<Video?>(null) }
    var creatorId by remember { mutableStateOf<String?>(null) }

    val barShown = Ui.barVisible && !Ui.deep
    // Keep the video viewport full-width, cleanly above the docked 56dp bar when shown and flush to bottom when hidden
    val stageBottomPad by animateDpAsState(if (barShown) 56.dp else 0.dp, tween(100), label = "stageBottom")

    val pagerFling = PagerDefaults.flingBehavior(
        state = pager,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapPositionalThreshold = 0.16f,
        lowVelocityAnimationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f)
    )
    val swipeGuard = remember(pager) {
        object : NestedScrollConnection {
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (kotlin.math.abs(available.y) > 5200f) {
                    val capped = available.y.coerceIn(-5200f, 5200f)
                    return Velocity(0f, available.y - capped)
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = if (barShown) 4.dp else 0.dp, bottom = stageBottomPad)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(if (barShown) 16.dp else 0.dp))
                .background(Color.Black)
                .nestedScroll(swipeGuard)
        ) {
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                flingBehavior = pagerFling
            ) { page ->
                val v = videos[page]
                val liked = Feed.liked[v.id] == true
                val saved = Feed.saved[v.id] == true
                var burst by remember { mutableIntStateOf(0) }
                val burstAnim = remember { Animatable(0f) }
                LaunchedEffect(burst) {
                    if (burst > 0) {
                        burstAnim.snapTo(0f)
                        burstAnim.animateTo(1f, tween(1350, easing = LinearEasing))
                    }
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .pointerInput(v.id, hidden) {
                            detectTapGestures(
                                onTap = {
                                    if (hidden) hidden = false
                                    else player.playWhenReady = !player.playWhenReady
                                },
                                onDoubleTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    burst++
                                    if (!App.loggedIn) {
                                        Toast.makeText(ctx, "سجّل الدخول لحفظ الإعجاب", Toast.LENGTH_SHORT).show()
                                    } else if (Feed.liked[v.id] != true) {
                                        scope.launch { Feed.toggleLike(v.id) }
                                    }
                                }
                            )
                        }
                ) {
                    if (page == pager.settledPage && Feed.activeVideo == null) {
                        val targetMode = if (fillZoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AndroidView(
                            factory = { c ->
                                (LayoutInflater.from(c).inflate(R.layout.player_texture_view, null, false) as PlayerView).apply {
                                    useController = false
                                    keepScreenOn = true
                                    resizeMode = targetMode
                                    this.player = player
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { pv ->
                                if (pv.player !== player) pv.player = player
                                if (pv.resizeMode != targetMode) pv.resizeMode = targetMode
                            },
                            onRelease = { pv ->
                                pv.player = null
                            }
                        )
                        if (isBuffering) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.Center).size(38.dp),
                                strokeWidth = 3.dp
                            )
                        } else if (!isPlaying && !player.playWhenReady) {
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x990E131B))
                                    .border(1.dp, Color(0x44FFFFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.PlayArrow, "تشغيل", tint = Color.White, modifier = Modifier.size(38.dp))
                            }
                        }
                    }

                    // Top & bottom subtle gradient scrims so text & actions stay crisp and comfortable
                    if (!hidden) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(64.dp)
                                .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                        )
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(155.dp)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                        )
                    }

                    // Custom water-filling glowing heart + dual wrapping light beams + glowing circles
                    if (burstAnim.value > 0f && burstAnim.value < 1f) {
                        WaterHeartBurst(
                            progress = burstAnim.value,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // Overlay layout based on selected style (Classic, Reels, TikTok, or X-Pill)
                    if (!hidden) {
                        val following = Feed.following[v.userId] == true
                        val doLike: () -> Unit = {
                            if (!App.loggedIn) {
                                onNeedLogin()
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (!liked) burst++
                                scope.launch { Feed.toggleLike(v.id) }
                            }
                        }
                        val doSave: () -> Unit = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!saved) saveTick++
                            scope.launch { Feed.toggleSave(v.id) }
                        }
                        val doShare: () -> Unit = {
                            ShareVideo.share(ctx, v)
                        }
                        val doFollow: () -> Unit = {
                            if (!App.loggedIn) onNeedLogin() else scope.launch { Feed.toggleFollow(v.userId) }
                        }
                        val settingsInStack: (@Composable () -> Unit)? = if (!barShown) {
                            {
                                ClipSettingsControl(
                                    v = v,
                                    saved = saved,
                                    uiStyle = uiStyle,
                                    fillZoom = fillZoom,
                                    speed = speed,
                                    muted = muted,
                                    inStack = true,
                                    onCycleStyle = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        cycleStyle()
                                    },
                                    onToggleZoom = { fillZoom = !fillZoom },
                                    onCycleSpeed = { speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f } },
                                    onToggleMute = { muted = !muted; player.volume = if (muted) 0f else 1f },
                                    onToggleSave = doSave,
                                    onReport = { reportFor = v }
                                )
                            }
                        } else null

                        when (uiStyle) {
                            ClipUiStyle.CLASSIC -> {
                                var descOpen by remember(v.id) { mutableStateOf(false) }
                                val bPad = if (barShown) 14.dp else 6.dp
                                Column(
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 14.dp, bottom = bPad, end = 82.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.clickable { creatorId = v.userId }) { Avatar(v.avatar, v.author, 34) }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            v.author,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontFamily = Cairo,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable { creatorId = v.userId }.weight(1f, false)
                                        )
                                        if (v.userId.isNotBlank() && v.userId != App.userId) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                if (following) "تتابعه" else "متابعة",
                                                color = if (following) Color.White else Color.Black,
                                                fontSize = 11.sp,
                                                fontFamily = Cairo,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(if (following) Color(0x44FFFFFF) else MaterialTheme.colorScheme.primary)
                                                    .clickable(onClick = doFollow)
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    Row(
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { descOpen = !descOpen }
                                            .padding(vertical = 2.dp)
                                            .testTag("clip_title_toggle"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            v.title,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontFamily = Cairo,
                                            maxLines = if (descOpen) 4 else 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (v.desc.isNotBlank()) {
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                if (descOpen) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                                                contentDescription = "عرض الوصف",
                                                tint = Color(0xFFCCCCCC),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    androidx.compose.animation.AnimatedVisibility(visible = descOpen && v.desc.isNotBlank()) {
                                        Text(
                                            v.desc,
                                            color = Color(0xFFE0E4EC),
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0x990E131B))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                                Column(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 10.dp, bottom = if (barShown) 16.dp else 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Action(
                                        icon = Icons.Filled.Favorite,
                                        label = v.likes.toString(),
                                        tint = if (liked) Color(0xFFFF2D55) else Color.White,
                                        modifier = Modifier.testTag("clip_like_button"),
                                        onClick = doLike
                                    )
                                    Action(
                                        icon = Icons.Filled.Comment,
                                        label = v.comments.toString(),
                                        tint = Color.White,
                                        modifier = Modifier.testTag("clip_comment_button")
                                    ) { commentsFor = v }
                                    Action(
                                        icon = Icons.Filled.Bookmark,
                                        label = "حفظ",
                                        tint = if (saved) Color(0xFFFFD21F) else Color.White,
                                        modifier = Modifier.testTag("clip_save_button"),
                                        onClick = doSave
                                    )
                                    Action(
                                        icon = Icons.Filled.Share,
                                        label = "مشاركة",
                                        tint = Color.White,
                                        modifier = Modifier.testTag("clip_share_button"),
                                        onClick = doShare
                                    )
                                    settingsInStack?.invoke()
                                }
                            }
                            ClipUiStyle.REELS -> {
                                ReelsClipOverlay(
                                    v = v,
                                    liked = liked,
                                    saved = saved,
                                    following = following,
                                    tightBottom = !barShown,
                                    extraAction = settingsInStack,
                                    onLike = doLike,
                                    onComment = { commentsFor = v },
                                    onSave = doSave,
                                    onShare = doShare,
                                    onReport = { reportFor = v },
                                    onFollow = doFollow,
                                    onCreator = { creatorId = v.userId }
                                )
                            }
                            ClipUiStyle.TIKTOK -> {
                                TikTokClipOverlay(
                                    v = v,
                                    liked = liked,
                                    saved = saved,
                                    following = following,
                                    isPlaying = isPlaying && page == pager.settledPage,
                                    tightBottom = !barShown,
                                    extraAction = settingsInStack,
                                    onLike = doLike,
                                    onComment = { commentsFor = v },
                                    onSave = doSave,
                                    onShare = doShare,
                                    onFollow = doFollow,
                                    onCreator = { creatorId = v.userId }
                                )
                            }
                            ClipUiStyle.X_PILL -> {
                                var posMs by remember { mutableLongStateOf(0L) }
                                var durMs by remember { mutableLongStateOf(0L) }
                                LaunchedEffect(curId, page == pager.settledPage) {
                                    if (page == pager.settledPage) {
                                        while (true) {
                                            val d = player.duration
                                            if (d > 0) {
                                                durMs = d
                                                posMs = player.currentPosition.coerceIn(0L, d)
                                            }
                                            delay(250)
                                        }
                                    }
                                }
                                XPillClipOverlay(
                                    v = v,
                                    liked = liked,
                                    saved = saved,
                                    following = following,
                                    isPlaying = isPlaying && page == pager.settledPage,
                                    posMs = posMs,
                                    durMs = durMs,
                                    speed = speed,
                                    muted = muted,
                                    fillZoom = fillZoom,
                                    extraAction = settingsInStack,
                                    onLike = doLike,
                                    onComment = { commentsFor = v },
                                    onSave = doSave,
                                    onShare = doShare,
                                    onReport = { reportFor = v },
                                    onFollow = doFollow,
                                    onCreator = { creatorId = v.userId },
                                    onPlayPause = { player.playWhenReady = !player.playWhenReady },
                                    onSeek = { target -> player.seekTo(target); posMs = target },
                                    onCycleSpeed = { speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f } },
                                    onToggleMute = { muted = !muted; player.volume = if (muted) 0f else 1f },
                                    onToggleZoom = { fillZoom = !fillZoom }
                                )
                            }
                        }
                    }

                    // Thin bottom progress line for Classic, Reels, and TikTok styles
                    if (page == pager.settledPage && uiStyle != ClipUiStyle.X_PILL) {
                        var prog by remember { mutableFloatStateOf(0f) }
                        LaunchedEffect(curId) {
                            while (true) {
                                delay(250)
                                val d = player.duration
                                prog = if (d > 0) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
                            }
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color(0x33FFFFFF))
                        ) {
                            Box(Modifier.fillMaxWidth(prog).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                        }
                    }

                    // Top-Start (physical top-right in RTL):
                    // - When bar is shown: App Logo style-cycler
                    // - When bar is hidden: Screen icon to show the bar (in the App Logo's spot) + Exit button beside it
                    if (!hidden) {
                        if (barShown) {
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                                    .minimumInteractiveComponentSize()
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x990D131E))
                                    .border(
                                        width = if (uiStyle != ClipUiStyle.CLASSIC) 1.75.dp else 1.dp,
                                        color = if (uiStyle != ClipUiStyle.CLASSIC) MaterialTheme.colorScheme.primary else Color(0x55FFFFFF),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        cycleStyle()
                                    }
                                    .testTag("clip_style_logo_toggle"),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.logo_mark),
                                    contentDescription = "تغيير شكل العرض (${uiStyle.label})",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(28.dp).clip(CircleShape)
                                )
                            }
                        } else {
                            // Screen button to restore the bottom bar (placed where the App Logo sits)
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                                    .minimumInteractiveComponentSize()
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x990D131E))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        Ui.deep = false
                                        Ui.barVisible = true
                                    }
                                    .testTag("clip_show_bar_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.CallToAction, "إظهار الشريط السفلي", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            // Exit button beside it
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 66.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                                    .minimumInteractiveComponentSize()
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x990D131E))
                                    .border(1.dp, Color(0x55FFFFFF), CircleShape)
                                    .clickable { onExit() }
                                    .testTag("clip_exit_button"),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.Close, "خروج", tint = Color.White, modifier = Modifier.size(20.dp)) }
                        }
                    }

                    if (!hidden && Feed.isOffline) {
                        Row(
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xB30D131E))
                                .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("clip_offline_chip"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Filled.CloudOff, "وضع عدم الاتصال", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(13.dp))
                            Text("محفوظ مؤقتاً", color = Color.White, fontSize = 11.sp, fontFamily = Cairo)
                        }
                    }

                    // Top-End (physical top-left in RTL):
                    // - When bar is shown: Settings button & menu
                    // - When bar is hidden: App Logo style-cycler moves here (while Settings moves to end of side icons)
                    if (!hidden) {
                        if (barShown) {
                            Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                                ClipSettingsControl(
                                    v = v,
                                    saved = saved,
                                    uiStyle = uiStyle,
                                    fillZoom = fillZoom,
                                    speed = speed,
                                    muted = muted,
                                    inStack = false,
                                    onCycleStyle = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        cycleStyle()
                                    },
                                    onToggleZoom = { fillZoom = !fillZoom },
                                    onCycleSpeed = { speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f } },
                                    onToggleMute = { muted = !muted; player.volume = if (muted) 0f else 1f },
                                    onToggleSave = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!saved) saveTick++
                                        scope.launch { Feed.toggleSave(v.id) }
                                    },
                                    onReport = { reportFor = v }
                                )
                            }
                        } else {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .minimumInteractiveComponentSize()
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x990D131E))
                                    .border(
                                        width = if (uiStyle != ClipUiStyle.CLASSIC) 1.75.dp else 1.dp,
                                        color = if (uiStyle != ClipUiStyle.CLASSIC) MaterialTheme.colorScheme.primary else Color(0x55FFFFFF),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        cycleStyle()
                                    }
                                    .testTag("clip_style_logo_toggle"),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.logo_mark),
                                    contentDescription = "تغيير شكل العرض (${uiStyle.label})",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(28.dp).clip(CircleShape)
                                )
                            }
                        }
                    }

                    // Custom yellow water-filling bookmark + two glowing expanding rings
                    if (saveAnim.value > 0f && saveAnim.value < 1f) {
                        WaterSaveBurst(
                            progress = saveAnim.value,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }

    creatorId?.let { id ->
        if (id.isNotBlank()) CreatorSheet(id, onPlay = { vid ->
            creatorId = null
            Feed.videos.firstOrNull { it.id == vid }?.let { tv ->
                Feed.open(tv) { val k = videos.indexOfFirst { it.id == tv.id }; if (k >= 0) scope.launch { pager.scrollToPage(k) } }
            }
        }) { creatorId = null }
    }
    reportFor?.let { v -> ReportSheet("video", v.id) { reportFor = null } }
    commentsFor?.let { v ->
        ModalBottomSheet(
            sheetState = fullSheet(),
            onDismissRequest = { commentsFor = null },
            containerColor = Color(0xFF141A24),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0x55FFFFFF)) }
        ) {
            CommentsSheet(v, onNeedLogin = { commentsFor = null; onNeedLogin() }, onClose = { commentsFor = null })
        }
    }
}

@Composable
private fun ClipSettingsControl(
    v: Video,
    saved: Boolean,
    uiStyle: ClipUiStyle,
    fillZoom: Boolean,
    speed: Float,
    muted: Boolean,
    inStack: Boolean,
    onCycleStyle: () -> Unit,
    onToggleZoom: () -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSave: () -> Unit,
    onReport: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        if (inStack) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { menu = true }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("clip_settings_button")
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x7A0B0F17))
                        .border(0.85.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Tune, "خيارات العرض", tint = Color.White, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "الإعدادات",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontFamily = Cairo,
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0xCC000000), blurRadius = 6f))
                )
            }
        } else {
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0x990D131E))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape)
                    .clickable { menu = true }
                    .testTag("clip_settings_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Tune, "خيارات العرض", tint = Color.White, modifier = Modifier.size(21.dp))
            }
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            modifier = Modifier.background(Color(0xFF151C28))
        ) {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.Filled.DashboardCustomize, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                },
                text = { Text("تغيير شكل العرض", color = Color.White, fontFamily = Cairo) },
                trailingIcon = {
                    Text(
                        uiStyle.label,
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontFamily = Cairo,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                },
                onClick = { onCycleStyle(); menu = false }
            )
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        if (fillZoom) Icons.Filled.FitScreen else Icons.Filled.Fullscreen,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = { Text(if (fillZoom) "عرض الإطار كاملاً" else "ملء الشاشة", color = Color.White, fontFamily = Cairo) },
                onClick = { onToggleZoom(); menu = false }
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.Speed, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp)) },
                text = { Text("سرعة التشغيل", color = Color.White, fontFamily = Cairo) },
                trailingIcon = {
                    Text(
                        if (speed == 1f) "1x" else "${speed}x",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontFamily = Cairo,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.secondary)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                },
                onClick = { onCycleSpeed(); menu = false }
            )
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        null,
                        tint = if (muted) Color(0xFFFF6B6B) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = { Text(if (muted) "تشغيل الصوت" else "كتم الصوت", color = Color.White, fontFamily = Cairo) },
                onClick = { onToggleMute(); menu = false }
            )
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        null,
                        tint = if (saved) Color(0xFFFFD21F) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = { Text(if (saved) "إزالة من المحفوظات" else "حفظ المقطع", color = Color.White, fontFamily = Cairo) },
                onClick = { onToggleSave(); menu = false }
            )
            HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.Flag, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp)) },
                text = { Text("إبلاغ عن المقطع", color = Color(0xFFFF8A8A), fontFamily = Cairo) },
                onClick = { onReport(); menu = false }
            )
        }
    }
}

@Composable
private fun Action(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val sc = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                scope.launch { sc.animateTo(1.32f, tween(110)); sc.animateTo(1f, spring()) }
                onClick()
            }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x7A0B0F17))
                .border(0.85.dp, Color(0x33FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(23.dp).scale(sc.value))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = Cairo,
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0xCC000000), blurRadius = 6f))
        )
    }
}

@Composable
private fun CommentsSheet(v: Video, onNeedLogin: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Comment>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    var moreC by remember { mutableStateOf(false) }

    suspend fun reload() {
        failed = false
        val r = Feed.comments(v.id)
        if (r == null) failed = true else { list = r; moreC = r.size >= Feed.COMMENTS_PAGE }
    }

    suspend fun loadMoreC() {
        val cur = list ?: return
        val r = Feed.comments(v.id, cur.lastOrNull())
        if (r == null) return
        val seen = cur.mapTo(HashSet()) { it.id }
        list = cur + r.filter { seen.add(it.id) }
        moreC = r.size >= Feed.COMMENTS_PAGE
    }

    LaunchedEffect(v.id) { reload() }

    fun sendComment() {
        val t = text.trim()
        if (t.isBlank() || sending) return
        sending = true
        scope.launch {
            if (Feed.addComment(v.id, t)) {
                text = ""
                reload()
            }
            sending = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 340.dp, max = 580.dp)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Comment, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("التعليقات", color = Color.White, fontSize = 18.sp, fontFamily = Cairo)
            Spacer(Modifier.width(8.dp))
            Text(
                "${list?.size ?: v.comments}",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontFamily = Cairo,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.Filled.Close, "إغلاق", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

        Box(Modifier.weight(1f, fill = true).fillMaxWidth()) {
            val l = list
            when {
                failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("تعذر تحميل التعليقات", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Cairo)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { scope.launch { reload() } }) { Text("إعادة المحاولة", fontFamily = Cairo) }
                }
                l == null -> ListSkeleton(5, Modifier.padding(16.dp))
                l.isEmpty() -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("لا توجد تعليقات بعد", color = Color.White, fontSize = 15.sp, fontFamily = Cairo)
                    Text("كن أول من يشارك رأيه!", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(l, key = { it.id }) { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1A2230))
                                .border(0.75.dp, Color(0x18FFFFFF), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Avatar(c.avatar, c.author, 36)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.author, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontFamily = Cairo, modifier = Modifier.weight(1f, false))
                                    if (c.at.isNotBlank()) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(c.at, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(c.text, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                    if (moreC) item(key = "more_comments") {
                        LaunchedEffect(l.size) { loadMoreC() }
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        if (!App.loggedIn) {
            Button(
                onClick = onNeedLogin,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            ) { Text("سجّل الدخول للتعليق", fontFamily = Cairo) }
        } else {
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f).testTag("clip_comment_input"),
                    placeholder = { Text("اكتب تعليقاً جميلاً...") },
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendComment() })
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = text.isNotBlank() && !sending,
                    onClick = { sendComment() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (text.isNotBlank() && !sending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("clip_comment_send")
                ) {
                    if (sending) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            "إرسال",
                            tint = if (text.isNotBlank()) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

