package com.mawjalbarq.app
import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

@Composable
fun FullPlayer(start: Video, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var cur by remember { mutableStateOf(start) }
    var landscape by remember { mutableStateOf(false) }
    val player = remember { ExoPlayer.Builder(ctx).setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(MediaCache.factory(ctx))).build() }
    LaunchedEffect(cur.id) { player.setMediaItem(MediaItem.fromUri(Uri.parse(cur.url))); player.prepare(); player.playWhenReady = true }
    LaunchedEffect(landscape) {
        (ctx as? Activity)?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    DisposableEffect(Unit) { onDispose { player.release(); (ctx as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(cur.title, color = Color.White, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                TextButton(onClick = { landscape = !landscape }) { Text(if (landscape) "عمودي" else "أفقي") }
                TextButton(onClick = onClose) { Text("إغلاق") }
            }
            AndroidView({ c -> PlayerView(c).apply { useController = true; this.player = player } }, Modifier.fillMaxWidth().weight(1f))
            if (!landscape) {
                Text("مقترحات", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                LazyRow(Modifier.padding(bottom = 16.dp), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Feed.videos.filter { it.id != cur.id }) { v ->
                        VideoCard(v) { cur = v }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoCard(v: Video, onClick: () -> Unit) {
    Column(Modifier.width(160.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF12141A)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(100.dp).background(Color(0xFF1E222B))) {
            if (v.thumb != null) AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(v.title, color = Color.White, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(8.dp, 6.dp, 8.dp, 0.dp))
        Text(v.author, color = Color(0xFF8A8F9C), fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(8.dp, 0.dp, 8.dp, 8.dp))
    }
}
