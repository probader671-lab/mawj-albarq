package com.mawjalbarq.app
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

data class Notif(val id: String, val type: String, val actor: String, val actorAvatar: String?, val videoId: String?, val at: String, val read: Boolean)

object Notifs {
    var unread by mutableIntStateOf(0)
    suspend fun refreshCount() {
        val u = App.userId ?: run { unread = 0; return }
        val (c, t) = api("GET", "/rest/v1/notifications?user_id=eq.$u&is_read=eq.false&select=id&limit=100")
        if (c == 200) try { unread = JSONArray(t).length() } catch (_: Exception) {}
    }
    suspend fun load(): List<Notif>? {
        val u = App.userId ?: return emptyList()
        val (c, t) = api("GET", "/rest/v1/notifications?user_id=eq.$u&select=id,type,actor_id,video_id,created_at,is_read&order=created_at.desc&limit=60")
        if (c != 200) return null
        return try {
            val a = JSONArray(t)
            val ids = (0 until a.length()).mapNotNull { a.getJSONObject(it).optString("actor_id").takeIf { s -> s.isNotBlank() && s != "null" } }.distinct()
            val names = HashMap<String, Pair<String, String?>>()
            if (ids.isNotEmpty()) {
                val (c2, t2) = api("GET", "/rest/v1/profiles?id=in.(${ids.joinToString(",")})&select=id,full_name,avatar_url")
                if (c2 == 200) { val b = JSONArray(t2); for (i in 0 until b.length()) { val o = b.getJSONObject(i)
                    names[o.getString("id")] = o.optString("full_name").ifBlank { "مستخدم" } to (if (o.isNull("avatar_url")) null else o.optString("avatar_url")) } }
            }
            (0 until a.length()).map {
                val o = a.getJSONObject(it); val aid = o.optString("actor_id")
                Notif(o.getString("id"), o.getString("type"), names[aid]?.first ?: "مستخدم", names[aid]?.second,
                    if (o.isNull("video_id")) null else o.optString("video_id"), o.optString("created_at").take(10), o.optBoolean("is_read"))
            }
        } catch (e: Exception) { null }
    }
    suspend fun markAllRead() {
        val u = App.userId ?: return
        api("PATCH", "/rest/v1/notifications?user_id=eq.$u&is_read=eq.false", JSONObject().put("is_read", true).toString())
        unread = 0
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpenVideo: (String) -> Unit) {
    var list by remember { mutableStateOf<List<Notif>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { val r = Notifs.load(); if (r == null) failed = true else list = r; Notifs.markAllRead() }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("الإشعارات", color = Ink, fontSize = 24.sp, fontFamily = Cairo, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        val l = list
        when {
            !App.loggedIn -> Text("سجّل الدخول لرؤية الإشعارات", color = Color.Gray)
            failed -> Text("تعذر تحميل الإشعارات", color = Color.Gray)
            l == null -> CircularProgressIndicator()
            l.isEmpty() -> Text("لا توجد إشعارات بعد", color = Color.Gray)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                items(l, key = { it.id }) { n ->
                    val (icon, tint, msg) = when (n.type) {
                        "like" -> Triple(Icons.Filled.Favorite, Color(0xFFFF2D55), "أعجبه مقطعك")
                        "comment" -> Triple(Icons.Filled.Comment, MaterialTheme.colorScheme.secondary, "علّق على مقطعك")
                        else -> Triple(Icons.Filled.PersonAdd, MaterialTheme.colorScheme.primary, "بدأ بمتابعتك")
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (n.read) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { n.videoId?.let(onOpenVideo) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(n.actorAvatar, n.actor, 44)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.actor, color = Ink, fontFamily = Cairo, fontSize = 15.sp, maxLines = 1)
                            Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(n.at, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        Icon(icon, null, tint = tint)
                    }
                }
            }
        }
    }
}
