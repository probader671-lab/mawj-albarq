package com.mawjalbarq.app
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
private fun RoundBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Int = 40, tint: Color = Color.White, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.48f).dp))
    }
}

@Composable
private fun WideCard(v: Video, ratio: Float, hero: Boolean, onOpen: () -> Unit, onNeedLogin: () -> Unit, onReport: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = Feed.saved[v.id] == true
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(20.dp)).clickable(onClick = onOpen)) {
            if (v.thumb != null) AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
            Box(Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
                .border(1.25.dp, Color.White.copy(alpha = 0.55f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            if (hero) Text("مميز", color = Color.Black, fontSize = 11.sp, fontFamily = Cairo,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondary).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(v.avatar, v.author, 42)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title, color = Color.White, fontFamily = Cairo, fontSize = 15.sp, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text("${v.author}  ·  ${v.likes} إعجاب  ·  ${v.comments} تعليق", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.width(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RoundBtn(Icons.Filled.Bookmark, 40, if (saved) MaterialTheme.colorScheme.secondary else Color.White) { scope.launch { Feed.toggleSave(v.id) } }
                RoundBtn(Icons.Filled.Share, 40) {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${v.title} - موج البرق\n${v.url}"), "مشاركة"))
                }
                RoundBtn(Icons.Filled.Flag, 40, onClick = onReport)
            }
        }
    }
}

@Composable
fun HomeScreen(onOpen: () -> Unit, onNeedLogin: () -> Unit = {}, onSuggestions: () -> Unit = {}, onNotifs: () -> Unit = {}) {
    val ctx = LocalContext.current
    val list = rememberLazyListState()
    var searching by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var reportFor by remember { mutableStateOf<Video?>(null) }
    val conn = remember { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -12f) Ui.barVisible = false else if (available.y > 12f) Ui.barVisible = true
            return Offset.Zero
        }
    } }
    val all = Feed.videos
    val vids = if (q.isBlank()) all else all.filter { it.title.contains(q, true) || it.author.contains(q, true) }
    fun open(v: Video) { Feed.jumpTo = all.indexOfFirst { it.id == v.id }.coerceAtLeast(0); onOpen() }
    Column(Modifier.fillMaxSize().statusBarsPadding().widthIn(max = 760.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image38()
            Spacer(Modifier.width(12.dp))
            Text("موج البرق", color = Color.White, fontFamily = Cairo, fontSize = 22.sp, modifier = Modifier.weight(1f))
            BadgedBox(badge = { if (Notifs.unread > 0) Badge { Text(Notifs.unread.toString()) } }) { RoundBtn(Icons.Filled.Notifications, 48, Color.White, onNotifs) }
            Spacer(Modifier.width(8.dp))
            RoundBtn(Icons.Filled.Lightbulb, 48, MaterialTheme.colorScheme.secondary, onSuggestions)
            Spacer(Modifier.width(8.dp))
            RoundBtn(if (searching) Icons.Filled.Close else Icons.Filled.Search, 48) { searching = !searching; if (!searching) q = "" }
        }
        androidx.compose.animation.AnimatedVisibility(searching) {
            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)),
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp).fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Text("ابحث عن مقطع أو صانع محتوى", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
                    }
                    if (q.isNotEmpty()) Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(18.dp).clickable { q = "" })
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        LazyColumn(Modifier.fillMaxSize().nestedScroll(conn), state = list, verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 116.dp)) {
            if (q.isBlank()) item { Box(Modifier.padding(horizontal = 16.dp)) { StoriesStrip(onNeedLogin) } }
            val lastId = Watch.last(ctx)
            val lastIdx = all.indexOfFirst { it.id == lastId }
            if (q.isBlank() && lastIdx >= 0) item {
                val v = all[lastIdx]; val frac = Watch.pos(ctx, v.id)
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable { open(v) }) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(58.dp, 72.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                if (v.thumb != null) AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("متابعة المشاهدة", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontFamily = Cairo)
                                Text(v.title, color = Color.White, fontFamily = Cairo, fontSize = 15.sp, maxLines = 2)
                                Text(v.author, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary, trackColor = Color.White.copy(alpha = 0.12f))
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { open(v) }, shape = RoundedCornerShape(50)) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("استكمال")
                        }
                    }
                }
            }
            if (vids.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    if (Feed.loading) CircularProgressIndicator() else Text(if (q.isBlank()) "لا توجد مقاطع بعد" else "لا نتائج", color = Color.Gray)
                }
            }
            itemsIndexed(vids.take(3)) { i, v -> WideCard(v, if (i == 0) 2.05f else 16f / 9f, i == 0, { open(v) }, onNeedLogin) { reportFor = v } }
            if (q.isBlank() && vids.size > 3) item {
                Column {
                    Text("كليبات مميزة", color = Color.White, fontFamily = Cairo, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    val rs = rememberLazyListState()
                    LazyRow(state = rs, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(vids) { i, v ->
                            val info = rs.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }
                            val center = rs.layoutInfo.viewportEndOffset / 2f
                            val p = if (info != null) ((info.offset + info.size / 2f - center) / center).coerceIn(-1f, 1f) else 0f
                            Box(Modifier.size(204.dp, 312.dp).graphicsLayer { rotationY = -p * 18f; cameraDistance = 14f * density }
                                .clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { open(v) }) {
                                if (v.thumb != null) AsyncImage(v.thumb, null, Modifier.fillMaxSize().graphicsLayer { translationX = -p * 24.dp.toPx() }, contentScale = ContentScale.Crop)
                                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))))
                                Row(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(v.avatar, v.author, 32)
                                    Spacer(Modifier.width(8.dp))
                                    Text(v.title, color = Color.White, fontFamily = Cairo, fontSize = 14.sp, maxLines = 2, modifier = Modifier.weight(1f))
                                }
                                Box(Modifier.align(Alignment.TopStart).padding(10.dp).size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.PlayArrow, null, tint = Color.Black)
                                }
                            }
                        }
                    }
                }
            }
            if (vids.size > 3) itemsIndexed(vids.drop(3)) { _, v -> WideCard(v, 16f / 9f, false, { open(v) }, onNeedLogin) { reportFor = v } }
        }
    }
    reportFor?.let { v -> ReportSheet("video", v.id) { reportFor = null } }
}

@Composable
private fun Image38() {
    androidx.compose.foundation.Image(painterResource(R.drawable.logo_mark), null, Modifier.size(38.dp).clip(CircleShape))
}
