package com.mawjalbarq.app
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ClipsScreen(onNeedLogin: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val videos = Feed.clips
    if (videos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                Feed.loading -> CircularProgressIndicator()
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (Feed.error) "تعذر تحميل المقاطع" else "لا توجد مقاطع بعد", color = Color.White, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { scope.launch { Feed.load() } }) { Text("إعادة المحاولة") }
                }
            }
        }
        return
    }
    val pager = rememberPagerState { videos.size }
    val player = remember { ExoPlayer.Builder(ctx).setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(MediaCache.factory(ctx))).build().apply { repeatMode = Player.REPEAT_MODE_ONE; playWhenReady = true } }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var landscapeFor by remember { mutableStateOf<Video?>(null) }
    LaunchedEffect(Feed.jumpTo) { Feed.jumpTo?.let { j ->
        val tv = Feed.videos.getOrNull(j); Feed.jumpTo = null
        if (tv != null) { val k = videos.indexOfFirst { it.id == tv.id }; if (k >= 0) pager.scrollToPage(k) else landscapeFor = tv }
    } }
    landscapeFor?.let { FullPlayer(it) { landscapeFor = null } }
    val cur = pager.settledPage.coerceAtMost(videos.size - 1)
    val curId = videos[cur].id
    LaunchedEffect(curId) { Watch.save(ctx, curId) }
    LaunchedEffect(curId) {
        player.setMediaItem(MediaItem.fromUri(Uri.parse(videos[cur].url))); player.prepare(); player.playWhenReady = true
        MediaCache.prefetch(ctx, videos.drop(cur + 1).take(2).map { it.url })
    }
    LaunchedEffect(curId) {
        while (true) {
            kotlinx.coroutines.delay(1500)
            val d = player.duration
            if (d > 0) Watch.savePos(ctx, curId, (player.currentPosition.toFloat() / d).coerceIn(0f, 1f))
        }
    }
    LaunchedEffect(cur) { Ui.deep = cur >= 3 }
    DisposableEffect(Unit) { onDispose { Ui.deep = false } }
    var speed by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(speed, curId) { player.setPlaybackSpeed(speed) }
    var cinema by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    val saveAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    var saveTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(saveTick) { if (saveTick > 0) { saveAnim.snapTo(0f); saveAnim.animateTo(1f, androidx.compose.animation.core.tween(1200)) } }
    var fullFor by remember { mutableStateOf<Video?>(null) }
    var commentsFor by remember { mutableStateOf<Video?>(null) }
    var reportFor by remember { mutableStateOf<Video?>(null) }
    var creatorId by remember { mutableStateOf<String?>(null) }
    val barShown = Ui.barVisible && !Ui.deep
    val bpad by androidx.compose.animation.core.animateDpAsState(if (barShown) 120.dp else 44.dp, label = "bpad")
    VerticalPager(pager, Modifier.fillMaxSize()) { page ->
        val v = videos[page]
        val liked = Feed.liked[v.id] == true
        val saved = Feed.saved[v.id] == true
        var burst by remember { mutableIntStateOf(0) }
        val burstAnim = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(burst) { if (burst > 0) { burstAnim.snapTo(0f); burstAnim.animateTo(1f, androidx.compose.animation.core.tween(700)) } }
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(v.id) {
            detectTapGestures(onTap = { if (hidden) hidden = false else player.playWhenReady = !player.playWhenReady }, onDoubleTap = {
                if (!App.loggedIn) onNeedLogin() else { burst++; if (Feed.liked[v.id] != true) scope.launch { Feed.toggleLike(v.id) } }
            })
        }) {
            if (burstAnim.value > 0f && burstAnim.value < 1f) Icon(Icons.Filled.Favorite, null, tint = Color(0xFFFF2D55),
                modifier = Modifier.align(Alignment.Center).size(150.dp).scale(0.6f + 0.7f * burstAnim.value).alpha(1f - burstAnim.value))
            if (page == pager.settledPage) {
                val r = if (v.w > 0 && v.h > 0) v.w.toFloat() / v.h else 0f
                AndroidView({ c -> PlayerView(c).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; this.player = player } },
                    (if (r > 0f) Modifier.align(Alignment.TopCenter).fillMaxWidth().aspectRatio(r) else Modifier.fillMaxSize()), update = { it.player = player })
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = bpad, end = 90.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable { creatorId = v.userId }) { Avatar(v.avatar, v.author, 40) }
                    Spacer(Modifier.width(8.dp))
                    Text(v.author, color = Color.White, fontSize = 15.sp, fontFamily = Cairo, maxLines = 1, modifier = Modifier.clickable { creatorId = v.userId }.weight(1f, false))
                    if (v.userId.isNotBlank() && v.userId != App.userId) {
                        val f = Feed.following[v.userId] == true
                        Spacer(Modifier.width(8.dp))
                        Text(if (f) "تتابعه" else "متابعة", color = if (f) Color.White else Color.Black, fontSize = 12.sp, fontFamily = Cairo,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (f) Color(0x44FFFFFF) else MaterialTheme.colorScheme.primary)
                                .clickable { if (!App.loggedIn) onNeedLogin() else scope.launch { Feed.toggleFollow(v.userId) } }.padding(horizontal = 12.dp, vertical = 5.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(v.title, color = Color.White, fontSize = 14.sp, fontFamily = Cairo)
                if (v.desc.isNotBlank()) Text(v.desc, color = Color(0xFFCCCCCC), fontSize = 12.sp, maxLines = 2)
            }
            if (page == pager.settledPage) {
                var prog by remember { mutableFloatStateOf(0f) }
                LaunchedEffect(curId) { while (true) { kotlinx.coroutines.delay(250); val d = player.duration; prog = if (d > 0) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f } }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(prog).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
            if (!cinema) Column(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = bpad + 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Action(Icons.Filled.Favorite, v.likes.toString(), if (liked) Color(0xFFFF2D55) else Color.White) {
                    if (!App.loggedIn) onNeedLogin() else scope.launch { Feed.toggleLike(v.id) }
                }
                Action(Icons.Filled.Comment, v.comments.toString(), Color.White) { commentsFor = v }
                Action(Icons.Filled.Bookmark, "حفظ", if (saved) Color(0xFFFFD21F) else Color.White) {
                    if (!saved) saveTick++
                    scope.launch { Feed.toggleSave(v.id) }
                }
                Action(Icons.Filled.Share, "مشاركة", Color.White) {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "${v.title} - موج البرق\n${v.url}")
                    ctx.startActivity(Intent.createChooser(i, "مشاركة"))
                }
            }
            Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) {
                var menu by remember { mutableStateOf(false) }
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0x66000000)).clickable { menu = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Settings, "الإعدادات", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (cinema) "الوضع العادي" else "وضع السينما") }, onClick = { cinema = !cinema; hidden = false; menu = false })
                    DropdownMenuItem(text = { Text("السرعة: " + (if (speed == 1f) "1x" else "${speed}x")) }, onClick = { speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f }; menu = false })
                    DropdownMenuItem(text = { Text(if (muted) "تشغيل الصوت" else "كتم الصوت") }, onClick = { muted = !muted; player.volume = if (muted) 0f else 1f; menu = false })
                    DropdownMenuItem(text = { Text(if (saved) "إزالة من المحفوظات" else "حفظ") }, onClick = { if (!saved) saveTick++; scope.launch { Feed.toggleSave(v.id) }; menu = false })
                    DropdownMenuItem(text = { Text("إبلاغ") }, onClick = { reportFor = v; menu = false })
                }
            }
            if (saveAnim.value > 0f && saveAnim.value < 1f) Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
                val f = saveAnim.value
                val fill = (if (f < 0.7f) f / 0.7f else 1f)
                Icon(Icons.Filled.Bookmark, null, tint = Color(0x55FFFFFF), modifier = Modifier.size(130.dp).alpha(1f - (if (f > 0.85f) (f - 0.85f) / 0.15f else 0f)))
                Icon(Icons.Filled.Bookmark, null, tint = Color(0xFFFFD21F), modifier = Modifier.size(130.dp)
                    .alpha(1f - (if (f > 0.85f) (f - 0.85f) / 0.15f else 0f))
                    .drawWithContent {
                        val wave = (kotlin.math.sin(f * 24f) * 4.dp.toPx())
                        clipRect(top = size.height * (1f - fill) + wave) { this@drawWithContent.drawContent() }
                    })
            }
            if (cinema && page == pager.settledPage && !hidden) {
                var playing by remember { mutableStateOf(true) }
                var left by remember { mutableStateOf("") }
                LaunchedEffect(curId) { while (true) { kotlinx.coroutines.delay(500); playing = player.isPlaying; val d = player.duration
                    left = if (d > 0) { val r = ((d - player.currentPosition) / 1000).coerceAtLeast(0); "%d:%02d".format(r / 60, r % 60) } else "" } }
                Column(Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = bpad - 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        CapBtn(Icons.Filled.Favorite, v.likes.toString(), if (liked) Color(0xFFFF2D55) else Color.White) { if (!App.loggedIn) onNeedLogin() else scope.launch { Feed.toggleLike(v.id) } }
                        CapBtn(Icons.Filled.Comment, v.comments.toString(), Color.White) { commentsFor = v }
                        CapBtn(Icons.Filled.Bookmark, "", if (saved) MaterialTheme.colorScheme.secondary else Color.White) { scope.launch { Feed.toggleSave(v.id) } }
                        CapBtn(Icons.Filled.Share, "", Color.White) {
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${v.title} - موج البرق\n${v.url}"), "مشاركة"))
                        }
                        CapBtn(Icons.Filled.Flag, "", Color.White) { reportFor = v }
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xCC0E131B)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { player.playWhenReady = !player.playWhenReady }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White) }
                        Text(left, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(if (speed == 1f) "1x" else "${speed}x", color = Color.White, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))
                            .clickable { speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f } }.padding(horizontal = 12.dp, vertical = 6.dp))
                        IconButton(onClick = { muted = !muted; player.volume = if (muted) 0f else 1f }) { Icon(if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, null, tint = Color.White) }
                        IconButton(onClick = { hidden = true }) { Icon(Icons.Filled.VisibilityOff, null, tint = Color.White) }
                    }
                }
            }
        }
    }
    creatorId?.let { id -> if (id.isNotBlank()) CreatorSheet(id, onPlay = { vid -> creatorId = null; Feed.videos.indexOfFirst { it.id == vid }.let { if (it >= 0) Feed.jumpTo = it } }) { creatorId = null } }
    reportFor?.let { v -> ReportSheet("video", v.id) { reportFor = null } }
    commentsFor?.let { v ->
        ModalBottomSheet(sheetState = fullSheet(), onDismissRequest = { commentsFor = null }, containerColor = Color(0xFF141A24)) {
            CommentsSheet(v, onNeedLogin = { commentsFor = null; onNeedLogin() })
        }
    }
}

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    val sc = remember { androidx.compose.animation.core.Animatable(1f) }
    val scope = rememberCoroutineScope()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(CircleShape).clickable {
        scope.launch { sc.animateTo(1.35f, androidx.compose.animation.core.tween(110)); sc.animateTo(1f, androidx.compose.animation.core.spring()) }
        onClick()
    }.padding(horizontal = 8.dp, vertical = 6.dp)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(28.dp).scale(sc.value))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun CommentsSheet(v: Video, onNeedLogin: () -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Comment>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(v.id) { val r = Feed.comments(v.id); if (r == null) failed = true else list = r }
    Column(Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 560.dp).padding(horizontal = 16.dp).navigationBarsPadding().imePadding()) {
        Text("التعليقات", color = Color.White, fontSize = 18.sp, fontFamily = Cairo)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f, fill = true).fillMaxWidth()) {
            val l = list
            when {
                failed -> Text("تعذر تحميل التعليقات", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                l == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                l.isEmpty() -> Text("كن أول من يعلّق", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(l) { c ->
                        Column {
                            Text(c.author, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontFamily = Cairo)
                            Text(c.text, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        if (!App.loggedIn) {
            Button(onClick = onNeedLogin, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("سجّل الدخول للتعليق") }
        } else Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("اكتب تعليقاً...") }, maxLines = 3)
            Spacer(Modifier.width(8.dp))
            IconButton(enabled = text.isNotBlank() && !sending, onClick = {
                val t = text.trim(); sending = true
                scope.launch {
                    if (Feed.addComment(v.id, t)) { text = ""; list = Feed.comments(v.id) ?: list }
                    sending = false
                }
            }) { Icon(Icons.Filled.Send, "إرسال", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun CapBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0x990E131B)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        if (label.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Text(label, color = Color.White, fontSize = 12.sp) }
    }
}
