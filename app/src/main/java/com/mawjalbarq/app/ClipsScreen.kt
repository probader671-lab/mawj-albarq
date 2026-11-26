package com.mawjalbarq.app
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

data class Clip(val url: String, val title: String, val author: String)
val SampleClips = listOf(
    Clip("https://media.w3.org/2010/05/bunny/trailer.mp4", "الأرنب الكبير", "@mawj"),
    Clip("https://media.w3.org/2010/05/sintel/trailer.mp4", "سينتل", "@barq"),
    Clip("https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4", "زهرة", "@mawj"),
    Clip("https://media.w3.org/2010/05/video/movie_300.mp4", "مقطع تجريبي", "@barq"),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipsScreen() {
    val pager = rememberPagerState { SampleClips.size }
    val ctx = LocalContext.current
    val player = remember { ExoPlayer.Builder(ctx).build().apply { repeatMode = Player.REPEAT_MODE_ONE; playWhenReady = true } }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(pager.settledPage) {
        player.setMediaItem(MediaItem.fromUri(Uri.parse(SampleClips[pager.settledPage].url))); player.prepare()
    }
    VerticalPager(pager, Modifier.fillMaxSize()) { page ->
        var liked by remember { mutableStateOf(false) }
        var saved by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (page == pager.settledPage) {
                AndroidView({ c -> PlayerView(c).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; this.player = player } },
                    Modifier.fillMaxSize(), update = { it.player = player })
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 120.dp, end = 90.dp)) {
                Text(SampleClips[page].author, color = Color.White, fontSize = 16.sp, fontFamily = Cairo)
                Text(SampleClips[page].title, color = Color.White, fontSize = 14.sp)
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Favorite, "إعجاب", tint = if (liked) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(34.dp).clickable { liked = !liked })
                Icon(Icons.Filled.Comment, "تعليقات", tint = Color.White, modifier = Modifier.size(32.dp))
                Icon(Icons.Filled.Bookmark, "حفظ", tint = if (saved) MaterialTheme.colorScheme.secondary else Color.White,
                    modifier = Modifier.size(32.dp).clickable { saved = !saved })
                Icon(Icons.Filled.Share, "مشاركة", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}
