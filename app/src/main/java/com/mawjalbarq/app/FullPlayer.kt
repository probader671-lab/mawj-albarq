package com.mawjalbarq.app
import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.launch
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
fun FullPlayer(start: Video, onClose0: () -> Unit) {
    val onClose = { Ui.barVisible = true; Ui.deep = false; onClose0() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cur by remember { mutableStateOf(start) }
    var fs by remember { mutableStateOf(false) }
    var showDesc by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1f) }
    var disliked by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<List<Comment>?>(null) }
    var cText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val player = remember { ExoPlayer.Builder(ctx).setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(MediaCache.factory(ctx))).build() }
    LaunchedEffect(cur.id) {
        player.setMediaItem(MediaItem.fromUri(Uri.parse(cur.url))); player.prepare(); player.playWhenReady = true
        showDesc = false; comments = null; comments = Feed.comments(cur.id) ?: emptyList()
    }
    LaunchedEffect(fs) {
        (ctx as? Activity)?.requestedOrientation = if (fs) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    DisposableEffect(Unit) { onDispose { player.release(); (ctx as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }
    val ratio = if (cur.w > 0 && cur.h > 0) (cur.w.toFloat() / cur.h).coerceIn(1f, 2.4f) else 16f / 9f
    val white = Color.White; val grey = Color(0xFF9AA0AC); val chip = Color(0xFF272B33)
    fun needLogin(): Boolean { if (!App.loggedIn) { android.widget.Toast.makeText(ctx, "سجّل الدخول أولاً", android.widget.Toast.LENGTH_SHORT).show(); return true }; return false }
    @Composable fun pv(m: Modifier) = AndroidView({ c -> PlayerView(c).apply { useController = true; keepScreenOn = true; controllerShowTimeoutMs = 2500; resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT; setShowNextButton(false); setShowPreviousButton(false); setFullscreenButtonClickListener { fs = !fs }; this.player = player } }, m)
    Dialog(onDismissRequest = { if (fs) fs = false else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        androidx.activity.compose.BackHandler { if (fs) fs = false else onClose() }
        if (fs) {
            Box(Modifier.fillMaxSize().background(Color.Black)) { pv(Modifier.fillMaxSize()); ExitBtn(Modifier.align(Alignment.TopStart).statusBarsPadding(), onClose) }
        } else Column(Modifier.fillMaxSize().background(Color(0xFF0F1115)).statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.Black)) {
                pv(Modifier.fillMaxSize())
                ExitBtn(Modifier.align(Alignment.TopStart), onClose)
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Column(Modifier.padding(12.dp).clickable { showDesc = !showDesc }) {
                        Text(cur.title, color = white, fontSize = 17.sp, fontFamily = Cairo, maxLines = if (showDesc) 10 else 2)
                        Text(if (showDesc) cur.desc.ifBlank { "لا يوجد وصف" } else "${cur.likes} إعجاب  •  ${cur.comments} تعليق  •  ...المزيد", color = grey, fontSize = 12.sp, fontFamily = Cairo, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(chip)) { if (cur.avatar != null) AsyncImage(cur.avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        Spacer(Modifier.width(10.dp))
                        Text(cur.author, color = white, fontSize = 15.sp, fontFamily = Cairo, maxLines = 1, modifier = Modifier.weight(1f))
                        if (cur.userId.isNotBlank() && cur.userId != App.userId) {
                            val f = Feed.following[cur.userId] == true
                            Text(if (f) "تتابعه" else "متابعة", color = if (f) white else Color.Black, fontSize = 13.sp, fontFamily = Cairo,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (f) chip else white).clickable { if (!needLogin()) scope.launch { Feed.toggleFollow(cur.userId) } }.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
                item {
                    val liked = Feed.liked[cur.id] == true; val saved = Feed.saved[cur.id] == true
                    LazyRow(Modifier.padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Pill(if (liked) Icons.Filled.Favorite else Icons.Filled.ThumbUp, "${cur.likes}", if (liked) Color(0xFFFF3B3B) else white, chip) { if (!needLogin()) scope.launch { Feed.toggleLike(cur.id) } } }
                        item { Pill(Icons.Filled.ThumbDown, "", if (disliked) MaterialTheme.colorScheme.primary else white, chip) { disliked = !disliked } }
                        item { Pill(Icons.Filled.Share, "مشاركة", white, chip) { ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "${cur.title} - موج البرق\n${cur.url}"), "مشاركة")) } }
                        item { Pill(Icons.Filled.Bookmark, if (saved) "تم الحفظ" else "حفظ", if (saved) Color(0xFFFFC107) else white, chip) { scope.launch { Feed.toggleSave(cur.id) } } }
                        item { Pill(Icons.Filled.Speed, if (speed == 1f) "السرعة" else "${speed}x", white, chip) { speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f }; player.setPlaybackSpeed(speed) } }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1E25)).padding(12.dp)) {
                        Text("التعليقات  ${comments?.size ?: cur.comments}", color = white, fontSize = 15.sp, fontFamily = Cairo)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(cText, { cText = it }, Modifier.weight(1f), placeholder = { Text("كتابة تعليق...") }, maxLines = 2, shape = RoundedCornerShape(50))
                            IconButton(enabled = cText.isNotBlank() && !sending, onClick = {
                                if (needLogin()) return@IconButton
                                val t = cText.trim(); sending = true
                                scope.launch { if (Feed.addComment(cur.id, t)) { cText = ""; comments = Feed.comments(cur.id) ?: comments }; sending = false }
                            }) { Icon(Icons.Filled.Send, "إرسال", tint = MaterialTheme.colorScheme.primary) }
                        }
                        val l = comments
                        if (l == null) CircularProgressIndicator(Modifier.padding(8.dp).size(22.dp))
                        else l.take(20).forEach { c ->
                            Column(Modifier.padding(top = 10.dp)) {
                                Text(c.author, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontFamily = Cairo)
                                Text(c.text, color = white, fontSize = 14.sp)
                            }
                        }
                    }
                }
                items(Feed.videos.filter { it.id != cur.id && it.w >= it.h }.ifEmpty { Feed.videos.filter { it.id != cur.id } }) { v ->
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp).clickable { cur = v }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(chip)) {
                            if (v.thumb != null) AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Row(Modifier.padding(12.dp, 10.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(chip)) { if (v.avatar != null) AsyncImage(v.avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(v.title, color = white, fontSize = 15.sp, fontFamily = Cairo, maxLines = 2)
                                Text("${v.author}  •  ${v.likes} إعجاب", color = grey, fontSize = 12.sp, fontFamily = Cairo, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitBtn(m: Modifier, onClick: () -> Unit) {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Box(Modifier.fillMaxWidth().then(m.let { Modifier }), contentAlignment = if (rtl) Alignment.TopStart else Alignment.TopEnd) {
        Box(Modifier.padding(10.dp).size(38.dp).clip(CircleShape).background(Color(0x99000000)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, "خروج", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Pill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, bg: Color, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        if (label.isNotEmpty()) { Spacer(Modifier.width(6.dp)); Text(label, color = Color.White, fontSize = 13.sp, fontFamily = Cairo) }
    }
}

@Composable
fun VideoCard(v: Video, onClick: () -> Unit) {
    Column(Modifier.width(160.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF141A24)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(100.dp).background(Color(0xFF1E222B))) {
            if (v.thumb != null) AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(v.title, color = Color.White, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(8.dp, 6.dp, 8.dp, 0.dp))
        Text(v.author, color = Color(0xFF8A8F9C), fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(8.dp, 0.dp, 8.dp, 8.dp))
    }
}
