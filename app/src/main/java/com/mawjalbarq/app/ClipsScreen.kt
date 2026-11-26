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
    val videos = Feed.videos
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
    LaunchedEffect(Feed.jumpTo) { Feed.jumpTo?.let { pager.scrollToPage(it.coerceAtMost(videos.size - 1)); Feed.jumpTo = null } }
    val cur = pager.settledPage.coerceAtMost(videos.size - 1)
    val curId = videos[cur].id
    LaunchedEffect(curId) {
        player.setMediaItem(MediaItem.fromUri(Uri.parse(videos[cur].url))); player.prepare(); player.playWhenReady = true
        MediaCache.prefetch(ctx, videos.drop(cur + 1).take(2).map { it.url })
    }
    var commentsFor by remember { mutableStateOf<Video?>(null) }
    VerticalPager(pager, Modifier.fillMaxSize()) { page ->
        val v = videos[page]
        val liked = Feed.liked[v.id] == true
        val saved = Feed.saved[v.id] == true
        var burst by remember { mutableIntStateOf(0) }
        val burstAnim = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(burst) { if (burst > 0) { burstAnim.snapTo(0f); burstAnim.animateTo(1f, androidx.compose.animation.core.tween(700)) } }
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(v.id) {
            detectTapGestures(onTap = { player.playWhenReady = !player.playWhenReady }, onDoubleTap = {
                if (!App.loggedIn) onNeedLogin() else { burst++; if (Feed.liked[v.id] != true) scope.launch { Feed.toggleLike(v.id) } }
            })
        }) {
            if (burstAnim.value > 0f && burstAnim.value < 1f) Icon(Icons.Filled.Favorite, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center).size(110.dp).scale(0.6f + 0.7f * burstAnim.value).alpha(1f - burstAnim.value))
            if (page == pager.settledPage) {
                AndroidView({ c -> PlayerView(c).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; this.player = player } },
                    Modifier.fillMaxSize(), update = { it.player = player })
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 120.dp, end = 90.dp)) {
                Text("@" + v.author, color = Color.White, fontSize = 16.sp, fontFamily = Cairo)
                Text(v.title, color = Color.White, fontSize = 14.sp)
                if (v.desc.isNotBlank()) Text(v.desc, color = Color(0xFFCCCCCC), fontSize = 12.sp, maxLines = 2)
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Action(Icons.Filled.Favorite, v.likes.toString(), if (liked) MaterialTheme.colorScheme.primary else Color.White) {
                    if (!App.loggedIn) onNeedLogin() else scope.launch { Feed.toggleLike(v.id) }
                }
                Action(Icons.Filled.Comment, v.comments.toString(), Color.White) { commentsFor = v }
                Action(Icons.Filled.Bookmark, "حفظ", if (saved) MaterialTheme.colorScheme.secondary else Color.White) {
                    if (!App.loggedIn) onNeedLogin() else scope.launch { Feed.toggleSave(v.id) }
                }
                Action(Icons.Filled.Share, "مشاركة", Color.White) {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "${v.title} - موج البرق\n${v.url}")
                    ctx.startActivity(Intent.createChooser(i, "مشاركة"))
                }
            }
        }
    }
    commentsFor?.let { v ->
        ModalBottomSheet(onDismissRequest = { commentsFor = null }, containerColor = Color(0xFF12141A)) {
            CommentsSheet(v, onNeedLogin = { commentsFor = null; onNeedLogin() })
        }
    }
}

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(34.dp))
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
