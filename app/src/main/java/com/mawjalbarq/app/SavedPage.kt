package com.mawjalbarq.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** Full saved page: saved videos (tap to play) and saved posts (tap to open), each with a remove button. */
@Composable
fun SavedPage(posts: List<Post>?, savedIds: List<String>, onTogglePost: (String) -> Unit, onOpenPost: (Post) -> Unit, onPlay: (Video) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    val vids = Feed.videos.filter { Feed.saved[it.id] == true }
    val sp = posts?.filter { savedIds.contains(it.id) }.orEmpty()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Ink) }
                Text("المحفوظات", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Cairo, modifier = Modifier.weight(1f))
            }
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary) {
                listOf("الفيديوهات (${vids.size})", "المنشورات (${sp.size})").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontFamily = Cairo, fontSize = 14.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) })
                }
            }
            if (tab == 0) {
                if (vids.isEmpty()) SavedEmpty("لا توجد فيديوهات محفوظة", "اضغط على علامة الحفظ تحت أي فيديو لتجده هنا")
                else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(vids, key = { it.id }) { v ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .clickable { onPlay(v) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(112.dp).aspectRatio(0.8f).clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
                                VideoThumb(v, Modifier.fillMaxSize(), ContentScale.Crop)
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(30.dp).clip(CircleShape).background(Color(0x66000000)))
                                if (v.duration > 0) Text(formatDuration(v.duration), color = Color.White, fontSize = 10.sp,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xB3000000)).padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(v.title.ifBlank { "فيديو" }, color = Ink, fontFamily = Cairo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(v.author, color = MaterialTheme.colorScheme.primary, fontFamily = Cairo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${formatCount(v.views)} مشاهدة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Tajawal, fontSize = 12.sp)
                            }
                            IconButton(onClick = { scope.launch { Feed.toggleSave(v.id) } }) { Icon(Icons.Filled.Bookmark, "إزالة من المحفوظات", tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            } else {
                if (sp.isEmpty()) SavedEmpty("لا توجد منشورات محفوظة", "احفظ أي منشور من صفحة المنشورات ليظهر هنا")
                else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sp, key = { it.id }) { p ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).clickable { onOpenPost(p) }.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(p.avatar, p.author, 36)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.author, color = MaterialTheme.colorScheme.primary, fontFamily = Cairo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    if (p.at.isNotBlank()) Text(p.at, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                                IconButton(onClick = { onTogglePost(p.id) }) { Icon(Icons.Filled.Bookmark, "إزالة", tint = MaterialTheme.colorScheme.primary) }
                            }
                            if (p.text.isNotBlank()) Text(p.text, color = Ink, fontSize = 14.sp, lineHeight = 21.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            val img = if (p.mediaType == "image") p.mediaUrl else p.thumbUrl
                            if (img != null) AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(max = 260.dp).aspectRatio(1.4f).clip(RoundedCornerShape(10.dp)).background(Color.Black))
                            Text("${p.likes} إعجاب • ${p.comments} تعليق", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Tajawal, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    if (posts == null) item { Text("جارٍ التحميل...", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun SavedEmpty(title: String, sub: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Bookmark, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = Ink, fontFamily = Cairo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Tajawal, fontSize = 13.sp)
    }
}
