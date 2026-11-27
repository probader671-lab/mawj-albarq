package com.mawjalbarq.app

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FullPlayer(start: Video, onClose0: () -> Unit) {
    DarkLock()
    val onClose = { Ui.barVisible = true; Ui.deep = false; onClose0() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cur by remember { mutableStateOf(start) }
    var fs by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var resizeBanner by remember { mutableStateOf<String?>(null) }
    var showDesc by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var muted by remember { mutableStateOf(false) }
    var disliked by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<List<Comment>?>(null) }
    val tracker = remember { Reco.Tracker() }
    DisposableEffect(Unit) { onDispose { tracker.stop() } }
    var noMoreComments by remember(cur.id) { mutableStateOf(false) }
    var cText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var creatorOpenId by remember { mutableStateOf<String?>(null) }

    // Playback state
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isEnded by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var currentMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var scrubFrac by remember { mutableFloatStateOf(0f) }
    var detectedRatio by remember { mutableFloatStateOf(0f) }
    var seekBurst by remember { mutableStateOf<Pair<Boolean, Int>?>(null) } // true = forward, count
    var autoPlayCancelled by remember { mutableStateOf(false) }
    var autoPlaySec by remember { mutableIntStateOf(4) }
    var heartTick by remember { mutableIntStateOf(0) }
    val heartAnim = remember { Animatable(0f) }
    LaunchedEffect(heartTick) {
        if (heartTick > 0) {
            heartAnim.snapTo(0f)
            heartAnim.animateTo(1f, tween(1350, easing = LinearEasing))
        }
    }
    val liveCur = Feed.videos.firstOrNull { it.id == cur.id } ?: cur
    val shareThumb = cur.thumb // keep reference clear

    val playlist = remember(cur.id, Feed.videos.size) {
        val wide = Feed.videos.filter { !it.portrait }
        if (wide.any { it.id == cur.id }) wide else Feed.videos.toList()
    }
    val curIdx = playlist.indexOfFirst { it.id == cur.id }
    val prevVideo = if (curIdx > 0) playlist[curIdx - 1] else null
    val nextVideo = if (curIdx in 0 until playlist.lastIndex) playlist[curIdx + 1] else null
    val upNext = remember(cur.id, Feed.videos.size) {
        Feed.videos.filter { it.id != cur.id && !it.portrait }
            .ifEmpty { Feed.videos.filter { it.id != cur.id } }
    }

    val player = remember {
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(MediaCache.factory(ctx)))
            .build().also { Media.withMp4Fallback(it) }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                isEnded = state == Player.STATE_ENDED
                if (state == Player.STATE_READY) {
                    hasError = false
                    durationMs = player.duration.coerceAtLeast(0L)
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                isBuffering = false
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    detectedRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            QuranBg.release(ctx, player)
            player.clearVideoSurface()
            player.release()
        }
    }

    LaunchedEffect(cur.id) {
        hasError = false
        isEnded = false
        isBuffering = true
        autoPlayCancelled = false
        detectedRatio = 0f
        currentMs = 0L
        durationMs = (cur.duration * 1000L).coerceAtLeast(0L)
        showDesc = false
        controlsVisible = true
        Watch.save(ctx, cur.id)
        tracker.switchTo(cur)
        player.setMediaItem(MediaItem.fromUri(Uri.parse(Media.playUrl(cur.url, cur.duration))))
        player.prepare()
        QuranBg.sync(ctx, player, cur.isQuran)
        player.setPlaybackSpeed(speed)
        player.volume = if (muted) 0f else 1f
        player.playWhenReady = true
        val savedFrac = Watch.pos(ctx, cur.id)
        if (savedFrac in 0.02f..0.94f) {
            var waited = 0
            while (player.duration <= 0L && waited < 20) {
                delay(150); waited++
            }
            if (player.duration > 5_000L) {
                player.seekTo((savedFrac * player.duration).toLong())
            }
        }
        comments = null
        comments = Feed.comments(cur.id) ?: emptyList()
    }

    // Position polling & watch-progress saving
    LaunchedEffect(cur.id) {
        var tick = 0
        while (true) {
            delay(250)
            val d = player.duration
            if (d > 0) {
                durationMs = d
                if (!dragging) currentMs = player.currentPosition.coerceIn(0L, d)
                bufferedMs = player.bufferedPosition.coerceIn(0L, d)
                tick++
                if (tick % 6 == 0) {
                    Watch.savePos(ctx, cur.id, (player.currentPosition.toFloat() / d).coerceIn(0f, 1f))
                }
            }
        }
    }

    // Auto-hide controls while playing
    LaunchedEffect(controlsVisible, isPlaying, dragging, locked, isEnded, hasError) {
        if (controlsVisible && isPlaying && !dragging && !locked && !isEnded && !hasError) {
            delay(3200)
            controlsVisible = false
        }
    }

    // Auto-hide seek burst indicator
    LaunchedEffect(seekBurst) {
        if (seekBurst != null) {
            delay(650)
            seekBurst = null
        }
    }

    // Auto-hide resize mode banner
    LaunchedEffect(resizeBanner) {
        if (resizeBanner != null) {
            delay(1300)
            resizeBanner = null
        }
    }

    // Auto-play countdown on video end
    LaunchedEffect(isEnded, autoPlayCancelled, nextVideo?.id) {
        if (isEnded && !autoPlayCancelled && nextVideo != null) {
            for (s in 4 downTo 1) {
                autoPlaySec = s
                delay(1000)
            }
            cur = nextVideo
        }
    }

    val ratio = when {
        cur.w > 0 && cur.h > 0 -> (cur.w.toFloat() / cur.h).coerceIn(1f, 2.4f)
        detectedRatio > 0f -> detectedRatio.coerceIn(1f, 2.4f)
        else -> 16f / 9f
    }

    val white = WatchTextMain
    val grey = WatchTextSec
    val chip = WatchPillBg

    fun needLogin(): Boolean {
        if (!App.loggedIn) {
            Toast.makeText(ctx, "سجّل الدخول أولاً", Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    }

    fun cycleResizeMode() {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> { resizeBanner = "ملء الشاشة"; AspectRatioFrameLayout.RESIZE_MODE_ZOOM }
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> { resizeBanner = "تمديد"; AspectRatioFrameLayout.RESIZE_MODE_FILL }
            else -> { resizeBanner = "ملائم للأبعاد"; AspectRatioFrameLayout.RESIZE_MODE_FIT }
        }
    }

    fun cycleSpeed() {
        speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; 0.5f -> 0.75f; else -> 1f }
        player.setPlaybackSpeed(speed)
    }

    Dialog(
        onDismissRequest = { if (fs) { fs = false; locked = false } else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        val targetWin = (view.parent as? DialogWindowProvider)?.window ?: (ctx as? Activity)?.window
        LaunchedEffect(fs) {
            (ctx as? Activity)?.requestedOrientation =
                if (fs) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (targetWin != null) {
                val ctrl = WindowCompat.getInsetsController(targetWin, view)
                ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fs) ctrl.hide(WindowInsetsCompat.Type.systemBars())
                else ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                (ctx as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                if (targetWin != null) {
                    WindowCompat.getInsetsController(targetWin, view).show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        BackHandler {
            when {
                locked -> { locked = false; controlsVisible = true }
                fs -> fs = false
                else -> onClose()
            }
        }

        @Composable
        fun PlayerSurface(boxModifier: Modifier) {
            Box(
                boxModifier
                    .background(Color.Black)
                    .pointerInput(cur.id, locked, durationMs) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { offset ->
                                if (locked) return@detectTapGestures
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                when {
                                    // RTL playback: left side seeks forward, right side rewinds
                                    offset.x < w * 0.4f -> {
                                        val maxDur = if (durationMs > 0L) durationMs else player.duration.coerceAtLeast(0L)
                                        val target = (player.currentPosition + 10_000L).let { if (maxDur > 0L) it.coerceAtMost(maxDur) else it }
                                        player.seekTo(target)
                                        currentMs = target
                                        seekBurst = true to ((seekBurst?.second ?: 0) + 10)
                                    }
                                    offset.x > w * 0.6f -> {
                                        val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                                        player.seekTo(target)
                                        currentMs = target
                                        seekBurst = false to ((seekBurst?.second ?: 0) + 10)
                                    }
                                    else -> {
                                        player.playWhenReady = !player.playWhenReady
                                    }
                                }
                            }
                        )
                    }
            ) {
                AndroidView(
                    factory = { c ->
                        (LayoutInflater.from(c).inflate(R.layout.player_texture_view, null, false) as PlayerView).apply {
                            useController = false
                            keepScreenOn = true
                            this.resizeMode = resizeMode
                            this.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { pv ->
                        if (pv.player !== player) pv.player = player
                        if (pv.resizeMode != resizeMode) pv.resizeMode = resizeMode
                    },
                    onRelease = { pv ->
                        pv.player = null
                    }
                )

                // Water heart burst animation on like
                if (heartAnim.value > 0f && heartAnim.value < 1f) {
                    WaterHeartBurst(
                        progress = heartAnim.value,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Double-tap seek visual feedback
                seekBurst?.let { (forward, secs) ->
                    Box(
                        Modifier
                            .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                            .padding(horizontal = 28.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xAA0E131B))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(if (forward) Icons.Filled.Forward10 else Icons.Filled.Replay10, null, tint = white, modifier = Modifier.size(22.dp))
                            Text(if (forward) "+${secs} ث" else "-${secs} ث", color = white, fontSize = 13.sp, fontFamily = Cairo)
                        }
                    }
                }

                // Resize mode banner
                resizeBanner?.let { label ->
                    Text(
                        label,
                        color = white,
                        fontSize = 12.sp,
                        fontFamily = Cairo,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = if (fs) 20.dp else 14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xB30E131B))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // Buffering indicator
                if (isBuffering && !hasError) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center).size(42.dp),
                        strokeWidth = 3.dp
                    )
                }

                // Error retry state
                if (hasError) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xCC0E131B))
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("تعذر تشغيل الفيديو", color = white, fontSize = 14.sp, fontFamily = Cairo)
                        Button(
                            onClick = { hasError = false; player.prepare(); player.playWhenReady = true },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("player_retry_button")
                        ) {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("إعادة المحاولة", fontFamily = Cairo)
                        }
                    }
                }

                // Auto-play next video overlay when ended
                if (isEnded && !autoPlayCancelled && nextVideo != null && !hasError) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                            .widthIn(max = 320.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xE60E131B))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("التالي خلال $autoPlaySec ثوانٍ", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontFamily = Cairo)
                        Text(nextVideo.title, color = white, fontSize = 14.sp, fontFamily = Cairo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { autoPlayCancelled = true }, shape = RoundedCornerShape(50)) {
                                Text("إلغاء", color = white, fontFamily = Cairo)
                            }
                            Button(onClick = { cur = nextVideo }, shape = RoundedCornerShape(50)) {
                                Text("تشغيل الآن", fontFamily = Cairo)
                            }
                        }
                    }
                }

                // Lock screen pill when locked
                AnimatedVisibility(
                    visible = locked && controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart).padding(20.dp)
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xCC0E131B))
                            .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(50))
                            .clickable { locked = false; controlsVisible = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Lock, "فتح قفل الشاشة", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("فتح القفل", color = white, fontSize = 12.sp, fontFamily = Cairo)
                    }
                }

                // Main Player Controls HUD
                AnimatedVisibility(
                    visible = controlsVisible && !locked,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(Modifier.fillMaxSize()) {
                        // Top gradient scrim
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(84.dp)
                                .background(Brush.verticalGradient(listOf(Color(0xBB000000), Color.Transparent)))
                        )
                        // Bottom gradient scrim
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(104.dp)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        )

                        // Top controls row
                        Row(
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .then(if (fs) Modifier.safeDrawingPadding() else Modifier)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (fs) fs = false else onClose() },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("player_close_button")
                            ) {
                                Icon(
                                    if (fs) Icons.Filled.FullscreenExit else Icons.Filled.Close,
                                    contentDescription = if (fs) "تصغير" else "إغلاق المشغل",
                                    tint = white
                                )
                            }
                            if (fs) {
                                Text(
                                    cur.title,
                                    color = white,
                                    fontSize = 14.sp,
                                    fontFamily = Cairo,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            IconButton(
                                onClick = { muted = !muted; player.volume = if (muted) 0f else 1f },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("player_mute_button")
                            ) {
                                Icon(if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, "الصوت", tint = white)
                            }
                            IconButton(
                                onClick = { cycleResizeMode() },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("player_resize_button")
                            ) {
                                Icon(Icons.Filled.AspectRatio, "تبديل حجم العرض", tint = white)
                            }
                            if (fs) {
                                IconButton(
                                    onClick = { locked = true; controlsVisible = true },
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("player_lock_button")
                                ) {
                                    Icon(Icons.Filled.LockOpen, "قفل الشاشة", tint = white)
                                }
                            }
                        }

                        // Center transport row
                        if (!hasError && !(isEnded && !autoPlayCancelled && nextVideo != null)) {
                            Row(
                                Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                IconButton(
                                    enabled = prevVideo != null,
                                    onClick = { prevVideo?.let { cur = it } },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x66000000))
                                        .testTag("player_prev_button")
                                ) {
                                    Icon(
                                        Icons.Filled.SkipPrevious,
                                        "الفيديو السابق",
                                        tint = if (prevVideo != null) white else white.copy(alpha = 0.3f),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                if (!isBuffering) {
                                    Box(
                                        Modifier
                                            .size(58.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x990E131B))
                                            .border(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), CircleShape)
                                            .clickable {
                                                if (isEnded) {
                                                    player.seekTo(0)
                                                    player.playWhenReady = true
                                                } else {
                                                    player.playWhenReady = !player.playWhenReady
                                                }
                                            }
                                            .testTag("player_play_pause_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            when {
                                                isEnded -> Icons.Filled.Replay
                                                isPlaying -> Icons.Filled.Pause
                                                else -> Icons.Filled.PlayArrow
                                            },
                                            contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                                            tint = white,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.size(58.dp))
                                }
                                IconButton(
                                    enabled = nextVideo != null,
                                    onClick = { nextVideo?.let { cur = it } },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x66000000))
                                        .testTag("player_next_button")
                                ) {
                                    Icon(
                                        Icons.Filled.SkipNext,
                                        "الفيديو التالي",
                                        tint = if (nextVideo != null) white else white.copy(alpha = 0.3f),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        // Bottom scrubber & action row
                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .then(if (fs) Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp) else Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                val shownMs = if (dragging && durationMs > 0L) (scrubFrac * durationMs).toLong() else currentMs
                                val frac = if (dragging) scrubFrac else if (durationMs > 0L) (currentMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                                val bufFrac = if (durationMs > 0L) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

                                if (dragging && durationMs > 0L) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text(
                                            formatDurationMs(shownMs),
                                            color = Color.Black,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                                .padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                                    // Buffered background bar filling Right-to-Left
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp)
                                            .height(3.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x3DFFFFFF))
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(bufFrac)
                                                .fillMaxHeight()
                                                .background(Color(0x66FFFFFF))
                                        )
                                    }
                                    Slider(
                                        value = frac,
                                        onValueChange = { v -> dragging = true; scrubFrac = v },
                                        onValueChangeFinished = {
                                            if (durationMs > 0L) {
                                                val target = (scrubFrac * durationMs).toLong().coerceIn(0L, durationMs)
                                                player.seekTo(target)
                                                currentMs = target
                                            }
                                            dragging = false
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = ProgressThumbGreen,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = Color.Transparent
                                        ),
                                        modifier = Modifier.fillMaxWidth().testTag("player_seek_slider")
                                    )
                                }

                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            "${formatDurationMs(shownMs)} / ${formatDurationMs(durationMs)}",
                                            color = white,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Text(
                                        if (speed == 1f) "1x" else "${speed}x",
                                        color = white,
                                        fontSize = 12.sp,
                                        fontFamily = Cairo,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Color(0x44FFFFFF))
                                            .clickable { cycleSpeed() }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                            .testTag("player_speed_chip")
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { fs = !fs; if (!fs) locked = false },
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("player_fullscreen_button")
                                    ) {
                                        Icon(
                                            if (fs) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                            contentDescription = if (fs) "خروج من ملء الشاشة" else "ملء الشاشة",
                                            tint = white
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(if (fs) Color.Black else WatchBg)
                .then(if (fs) Modifier else Modifier.statusBarsPadding())
        ) {
            PlayerSurface(
                if (fs) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth().heightIn(min = 256.dp).aspectRatio(ratio.coerceIn(1.35f, 1.62f))
            )
            if (!fs) {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "watch_main_card") {
                        val liked = Feed.liked[cur.id] == true
                        val saved = Feed.saved[cur.id] == true
                        val following = Feed.following[cur.userId] == true
                        val dateText = formatVideoDate(cur.createdAt)
                        val stats = buildList {
                            add("${formatCount(liveCur.views)} مشاهدة")
                            if (dateText.isNotBlank()) add(dateText) else if (cur.duration > 0) add(formatDuration(cur.duration))
                        }.joinToString(" • ")

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(WatchCardBg)
                                .border(1.dp, WatchCardBorder, RoundedCornerShape(18.dp))
                                .padding(horizontal = 13.dp, vertical = 12.dp)
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { if (cur.desc.isNotBlank()) showDesc = !showDesc }
                                    .testTag("player_title_toggle"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    cur.title,
                                    color = white,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 22.sp,
                                    fontFamily = Cairo,
                                    maxLines = if (showDesc) 8 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (cur.desc.isNotBlank()) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        if (showDesc) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "عرض الوصف",
                                        tint = grey,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(3.dp))
                            Text(stats, color = grey, fontSize = 11.5.sp, fontFamily = Cairo)

                            // Compact, professional Creator + Follow row
                            Row(
                                Modifier.fillMaxWidth().padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = cur.userId.isNotBlank()) { creatorOpenId = cur.userId }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                            .padding(1.5.dp)
                                    ) {
                                        Avatar(cur.avatar, cur.author, 29)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                cur.author,
                                                color = white,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = Cairo,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (liveCur.verified) VerifiedBadge(13)
                                        }
                                        Text(
                                            "${formatCount(liveCur.followers)} منضم",
                                            color = grey,
                                            fontSize = 11.sp,
                                            fontFamily = Cairo
                                        )
                                    }
                                }
                                if (cur.userId.isNotBlank() && cur.userId != App.userId) {
                                    Spacer(Modifier.width(6.dp))
                                    WatchJoinButton(following = following, compact = true) {
                                        if (!needLogin()) {
                                            val joining = !following
                                            scope.launch {
                                                Feed.toggleFollow(cur.userId)
                                                if (joining) {
                                                    Toast.makeText(ctx, "مرحباً بك في ${cur.author}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            LazyRow(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                item {
                                    WatchActionPill(
                                        icon = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        label = "${liveCur.likes}",
                                        active = liked,
                                        activeBg = MaterialTheme.colorScheme.primary,
                                        compact = true
                                    ) {
                                        if (!needLogin()) {
                                            if (!liked) heartTick++
                                            scope.launch { Feed.toggleLike(cur.id) }
                                        }
                                    }
                                }
                                item {
                                    WatchActionPill(
                                        icon = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        label = if (saved) "محفوظ" else "حفظ",
                                        active = saved,
                                        activeBg = MaterialTheme.colorScheme.primary,
                                        compact = true
                                    ) {
                                        scope.launch { Feed.toggleSave(cur.id) }
                                    }
                                }
                                item {
                                    WatchActionPill(icon = Icons.Outlined.Share, label = "مشاركة", compact = true) {
                                        ShareVideo.share(ctx, cur)
                                    }
                                }
                                item {
                                    WatchActionPill(
                                        icon = Icons.Outlined.Speed,
                                        label = if (speed == 1f) "السرعة" else "${speed}x",
                                        compact = true
                                    ) { cycleSpeed() }
                                }
                                item {
                                    WatchActionPill(icon = Icons.Outlined.Fullscreen, label = "ملء الشاشة", compact = true) { fs = true }
                                }
                                item {
                                    WatchActionPill(icon = Icons.Outlined.Flag, label = "إبلاغ", compact = true) { reportOpen = true }
                                }
                            }

                            HorizontalDivider(Modifier.padding(vertical = 10.dp), thickness = 0.75.dp, color = WatchCardBorder)

                            AnimatedVisibility(visible = showDesc && cur.desc.isNotBlank()) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(Color(0x0DFFFFFF))
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        cur.desc,
                                        color = white.copy(alpha = 0.94f),
                                        fontSize = 13.5.sp,
                                        lineHeight = 21.sp,
                                        fontFamily = Cairo
                                    )
                                }
                            }

                            // Comments Section matching watchorview.html
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "التعليقات",
                                    color = white,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = Cairo
                                )
                                Text(
                                    "(${comments?.size ?: liveCur.comments})",
                                    color = grey,
                                    fontSize = 15.sp,
                                    fontFamily = Cairo
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Avatar(App.avatar, App.name.ifBlank { "م" }, 40)
                                Row(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(25.dp))
                                        .background(Color(0x14FFFFFF))
                                        .border(0.75.dp, WatchCardBorder, RoundedCornerShape(25.dp))
                                        .padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                        if (cText.isEmpty()) {
                                            Text("أضف تعليقاً...", color = grey, fontSize = 13.5.sp, fontFamily = Cairo)
                                        }
                                        BasicTextField(
                                            value = cText,
                                            onValueChange = { cText = it },
                                            maxLines = 3,
                                            textStyle = TextStyle(color = white, fontSize = 13.5.sp, fontFamily = Cairo),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.fillMaxWidth().testTag("player_comment_input")
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    val canSend = cText.isNotBlank() && !sending
                                    Text(
                                        if (sending) "..." else "إرسال",
                                        color = if (canSend) Color.White else grey,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = Cairo,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (canSend) MaterialTheme.colorScheme.primary else Color(0x1EFFFFFF))
                                            .clickable(enabled = canSend) {
                                                if (needLogin()) return@clickable
                                                val t = cText.trim()
                                                sending = true
                                                scope.launch {
                                                    if (Feed.addComment(cur.id, t)) {
                                                        cText = ""
                                                        comments = Feed.comments(cur.id) ?: comments
                                                    }
                                                    sending = false
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 7.dp)
                                            .testTag("player_comment_send")
                                    )
                                }
                            }

                            val l = comments
                            if (l == null) {
                                ListSkeleton(3, Modifier.padding(16.dp))
                            } else if (l.isEmpty()) {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.ChatBubbleOutline, null, tint = grey.copy(alpha = 0.65f), modifier = Modifier.size(34.dp))
                                    Text("لا توجد تعليقات بعد. كن أول من يعلق!", color = grey, fontSize = 13.sp, fontFamily = Cairo)
                                }
                            } else {
                                Column(Modifier.padding(top = 14.dp)) {
                                    l.forEachIndexed { idx, c ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Avatar(c.avatar, c.author, 38)
                                            Column(Modifier.weight(1f)) {
                                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                    Row(
                                                        Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(
                                                            c.author,
                                                            color = white,
                                                            fontSize = 13.5.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontFamily = Cairo,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (c.verified) VerifiedBadge(13)
                                                    }
                                                    if (c.at.isNotBlank()) {
                                                        Text(c.at, color = grey, fontSize = 11.5.sp)
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(c.text, color = white.copy(alpha = 0.92f), fontSize = 13.5.sp, lineHeight = 20.sp)
                                            }
                                        }
                                        if (idx < l.size - 1) {
                                            HorizontalDivider(thickness = 0.75.dp, color = Color(0x10FFFFFF))
                                        }
                                    }
                                    if (l.size >= Feed.COMMENTS_PAGE && l.size % Feed.COMMENTS_PAGE == 0 && !noMoreComments) {
                                        TextButton(onClick = {
                                            scope.launch {
                                                val r = Feed.comments(cur.id, l.lastOrNull())
                                                if (r == null) return@launch
                                                val seen = l.mapTo(HashSet()) { it.id }
                                                val add = r.filter { seen.add(it.id) }
                                                comments = l + add
                                                if (r.size < Feed.COMMENTS_PAGE) noMoreComments = true
                                            }
                                        }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("عرض المزيد", fontFamily = Cairo, color = MaterialTheme.colorScheme.primary) }
                                    }
                                }
                            }
                        }
                    }

                    if (cur.isQuran) item(key = "quran_bg_toggle") {
                        var bg by remember { mutableStateOf(QuranPrefs.enabled(ctx)) }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("تشغيل التلاوة في الخلفية", color = white, fontFamily = Cairo, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Switch(checked = bg, onCheckedChange = { bg = it; QuranPrefs.set(ctx, it); QuranBg.sync(ctx, player, cur.isQuran) })
                        }
                    }
                    if (upNext.isNotEmpty()) {
                        item(key = "watch_rail") {
                            WatchRailSection(
                                title = "فيديوهات قد تعجبك",
                                videos = upNext.take(10),
                                onSelect = { cur = it }
                            )
                        }
                        item(key = "watch_suggestions_header") {
                            Text(
                                "المقترحات",
                                color = white,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = Cairo,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                            )
                        }
                        items(upNext, key = { "watch_sug_${it.id}" }) { v ->
                            WatchSuggestionRowCard(v = v, onClick = { cur = v })
                        }
                    }
                }
            }
        }
        if (reportOpen) ReportSheet("video", cur.id) { reportOpen = false }
        creatorOpenId?.let { cid ->
            CreatorSheet(
                id = cid,
                onPlay = { vid ->
                    creatorOpenId = null
                    Feed.videos.firstOrNull { it.id == vid }?.let { cur = it }
                },
                onClose = { creatorOpenId = null }
            )
        }
    }
}

