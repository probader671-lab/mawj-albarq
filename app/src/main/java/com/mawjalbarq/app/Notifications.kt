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

data class Notif(val id: String, val type: String, val actor: String, val actorAvatar: String?, val videoId: String?, val at: String, val read: Boolean, val ts: String = "", val reason: String = "")

object Notifs {
    var unread by mutableIntStateOf(0)
    var isAdmin by mutableStateOf(false)
    suspend fun checkAdmin() { if (App.userId == null) { isAdmin = false; return }; val (c, t) = api("POST", "/rest/v1/rpc/is_admin", "{}"); isAdmin = c == 200 && t.trim() == "true" }
    suspend fun refreshCount() {
        val u = App.userId ?: run { unread = 0; return }
        val (c, t) = api("GET", "/rest/v1/notifications?user_id=eq.$u&is_read=eq.false&select=id&limit=100")
        if (c == 200) try { unread = JSONArray(t).length() } catch (_: Exception) {}
    }
    const val PAGE = 30
    suspend fun load(after: Notif? = null): List<Notif>? {
        val u = App.userId ?: return emptyList()
        val cur = if (after != null && after.ts.isNotBlank()) { val t = java.net.URLEncoder.encode(after.ts, "UTF-8"); "&or=(created_at.lt.$t,and(created_at.eq.$t,id.lt.${after.id}))" } else ""
        val (c, t) = api("GET", "/rest/v1/notifications?user_id=eq.$u&select=id,type,actor_id,video_id,created_at,is_read,reason$cur&order=created_at.desc,id.desc&limit=$PAGE")
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
                    if (o.isNull("video_id")) null else o.optString("video_id"), o.optString("created_at").take(10), o.optBoolean("is_read"), o.optString("created_at"), if (o.isNull("reason")) "" else o.optString("reason"))
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
    var moreN by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Notifs.checkAdmin() }
    if (showQueue) { ModerationQueueScreen { showQueue = false }; return }
    LaunchedEffect(Unit) { val r = Notifs.load(); if (r == null) failed = true else { list = r; moreN = r.size >= Notifs.PAGE }; Notifs.markAllRead() }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("الإشعارات", color = Ink, fontSize = 24.sp, fontFamily = Cairo, modifier = Modifier.weight(1f))
            if (Notifs.isAdmin) TextButton(onClick = { showQueue = true }) { Text("المراجعة") }
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        val l = list
        when {
            !App.loggedIn -> Text("سجّل الدخول لرؤية الإشعارات", color = Color.Gray)
            failed -> Text("تعذر تحميل الإشعارات", color = Color.Gray)
            l == null -> ListSkeleton()
            l.isEmpty() -> Text("لا توجد إشعارات بعد", color = Color.Gray)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                items(l, key = { it.id }) { n ->
                    val (icon, tint, msg) = when (n.type) {
                        "like" -> Triple(Icons.Filled.Favorite, Color(0xFFFF2D55), "أعجبه مقطعك")
                        "comment" -> Triple(Icons.Filled.Comment, MaterialTheme.colorScheme.secondary, "علّق على مقطعك")
                        "new_video" -> Triple(Icons.Filled.VideoLibrary, MaterialTheme.colorScheme.primary, "نزّل فيديو جديد")
                        "moderation" -> Triple(Icons.Filled.Shield, Color(0xFFFFB300), "مقطع بانتظار مراجعتك")
                        "restricted" -> Triple(Icons.Filled.Shield, Color(0xFFFF2D55), "تم تقييد منشورك" + if (n.reason.isNotBlank()) ": " + n.reason else "")
                        "moderation_blocked" -> Triple(Icons.Filled.Shield, Color(0xFFFF2D55), "تم منع مقطع تلقائياً")
                        else -> Triple(Icons.Filled.PersonAdd, MaterialTheme.colorScheme.primary, "بدأ بمتابعتك")
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (n.read) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { if (n.type.startsWith("moderation")) showQueue = true else n.videoId?.let(onOpenVideo) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(n.actorAvatar, n.actor, 44)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (n.type == "restricted") "إدارة موج البرق" else n.actor, color = Ink, fontFamily = Cairo, fontSize = 15.sp, maxLines = 1)
                            Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(n.at, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        Icon(icon, null, tint = tint)
                    }
                }
                if (moreN) item(key = "more_notifs") {
                    LaunchedEffect(l.size) {
                        val r = Notifs.load(l.lastOrNull())
                        if (r == null) { moreN = false } else {
                            val seen = l.mapTo(HashSet()) { it.id }
                            list = l + r.filter { seen.add(it.id) }
                            moreN = r.size >= Notifs.PAGE
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                }
            }
        }
    }
}
