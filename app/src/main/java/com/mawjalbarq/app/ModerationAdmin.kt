package com.mawjalbarq.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class QueueItem(
    val id: String, val videoId: String?, val kind: String, val reasons: List<String>,
    val music: Double, val nsfw: Double, val faces: Int, val title: String, val url: String?, val thumb: String?, val status: String, val userId: String, val w: Int = 0, val h: Int = 0
)

private fun reasonAr(r: String) = when (r) {
    "music" -> "موسيقى (مؤكد)"
    "music_uncertain" -> "احتمال موسيقى"
    "nsfw" -> "محتوى مخالف (مؤكد)"
    "nsfw_uncertain" -> "محتوى مشكوك فيه"
    "people" -> "ظهور أشخاص"
    "audio_check_failed", "visual_check_failed", "frames_unreadable" -> "تعذّر الفحص"
    else -> r
}

object ModQueue {
    suspend fun load(status: String): List<QueueItem>? {
        val filter = if (status == "pending") "status=eq.pending" else "status=eq.blocked"
        val (c, t) = api("GET", "/rest/v1/moderation_queue?$filter&select=id,video_id,kind,reasons,music_max,nsfw_max,faces,status,user_id,videos(title,video_url,thumbnail_url,width,height)&order=created_at.desc,id.desc&limit=30")
        if (c != 200) return null
        return try {
            val a = JSONArray(t)
            (0 until a.length()).map {
                val o = a.getJSONObject(it); val v = o.optJSONObject("videos")
                val rs = o.optJSONArray("reasons") ?: JSONArray()
                QueueItem(o.getString("id"), if (o.isNull("video_id")) null else o.optString("video_id"), o.optString("kind"),
                    (0 until rs.length()).map { i -> rs.getString(i) }, o.optDouble("music_max", 0.0), o.optDouble("nsfw_max", 0.0), o.optInt("faces"),
                    v?.optString("title").orEmpty(), v?.optString("video_url"), v?.optString("thumbnail_url")?.ifBlank { null }, o.optString("status"), o.optString("user_id"), v?.optInt("width") ?: 0, v?.optInt("height") ?: 0)
            }
        } catch (_: Exception) { null }
    }

    suspend fun decide(item: QueueItem, approve: Boolean): Boolean {
        val vid = item.videoId ?: return false
        val (c1, _) = api("PATCH", "/rest/v1/videos?id=eq.$vid", JSONObject().put("status", if (approve) "ready" else "rejected").toString())
        if (c1 !in 200..299) return false
        val (c2, _) = api("PATCH", "/rest/v1/moderation_queue?id=eq.${item.id}", JSONObject().put("status", if (approve) "approved" else "rejected")
            .put("reviewed_by", App.userId).put("reviewed_at", java.time.Instant.now().toString()).toString())
        if (approve) Feed.load()
        return c2 in 200..299
    }
}

@Composable
fun ModerationQueueScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf<List<QueueItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    suspend fun reload() { items = null; failed = false; val r = ModQueue.load(if (tab == 0) "pending" else "blocked"); if (r == null) failed = true else items = r }
    LaunchedEffect(tab) { reload() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("مراجعة المحتوى", color = Ink, fontSize = 22.sp, fontFamily = Cairo, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("بانتظار القرار") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("الممنوعة تلقائياً") })
        }
        Spacer(Modifier.height(10.dp))
        val l = items
        when {
            failed -> Text("تعذر التحميل (تأكد أن حسابك أدمن)", color = Color.Gray)
            l == null -> ListSkeleton(4)
            l.isEmpty() -> Text("لا يوجد شيء هنا", color = Color.Gray)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                items(l, key = { it.id }) { q ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(width = 96.dp, height = 64.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A3548))) {
                                if (q.thumb != null) AsyncImage(Media.thumb(q.thumb), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(q.title.ifBlank { "مقطع ممنوع (لم يُرفع)" }, color = Ink, fontFamily = Cairo, fontSize = 15.sp, maxLines = 1)
                                Text(q.reasons.joinToString(" • ") { reasonAr(it) }, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                                Text("موسيقى %.0f%%  محتوى %.0f%%  وجوه %d".format(q.music * 100, q.nsfw * 100, q.faces), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                        if (tab == 0 && q.videoId != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { q.url?.let { u -> Feed.activeVideo = Video(id = q.videoId, title = q.title, desc = "", url = u, thumb = q.thumb, author = "", likes = 0, comments = 0, userId = q.userId, w = q.w, h = q.h) } }, modifier = Modifier.weight(1f)) { Text("معاينة") }
                            Button(enabled = busyId == null, onClick = { busyId = q.id; scope.launch { ModQueue.decide(q, true); busyId = null; reload() } }, modifier = Modifier.weight(1f)) { Text("قبول") }
                            Button(enabled = busyId == null, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)), onClick = { busyId = q.id; scope.launch { ModQueue.decide(q, false); busyId = null; reload() } }, modifier = Modifier.weight(1f)) { Text("رفض") }
                        }
                    }
                }
            }
        }
    }
}
