package com.mawjalbarq.app
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ProfileData(val id: String, val name: String, val bio: String, val avatar: String?, val videos: List<Video>, val followers: Int = 0, val following: Int = 0)

suspend fun loadProfile(id: String): ProfileData? {
    val (c, t) = api("GET", "/rest/v1/profiles?id=eq.$id&select=id,full_name,bio,avatar_url")
    if (c != 200) return null
    val o = try { JSONArray(t).optJSONObject(0) } catch (e: Exception) { null } ?: return null
    val (c2, t2) = api("GET", "/rest/v1/videos?user_id=eq.$id&status=eq.ready&deleted=eq.false&select=id,title,description,video_url,thumbnail_url,likes,comments_count&order=created_at.desc")
    val vids = ArrayList<Video>()
    if (c2 == 200) try { val a = JSONArray(t2); for (i in 0 until a.length()) { val v = a.getJSONObject(i)
        vids.add(Video(v.getString("id"), v.optString("title"), if (v.isNull("description")) "" else v.optString("description"), v.getString("video_url"),
            if (v.isNull("thumbnail_url")) null else v.optString("thumbnail_url"), o.optString("full_name"), v.optInt("likes"), v.optInt("comments_count"), id)) } } catch (_: Exception) {}
    suspend fun cnt(q: String): Int { return try { val (c3, t3) = api("GET", "/rest/v1/follows?$q"); if (c3 == 200) JSONArray(t3).length() else 0 } catch (_: Exception) { 0 } }
    return ProfileData(id, o.optString("full_name").ifBlank { "مستخدم" }, if (o.isNull("bio")) "" else o.optString("bio"),
        if (o.isNull("avatar_url")) null else o.optString("avatar_url"), vids, cnt("following_id=eq.$id&select=follower_id"), cnt("follower_id=eq.$id&select=following_id"))
}

@Composable
fun Avatar(url: String?, name: String, size: Int = 72) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.take(1), color = Color.Black, fontSize = (size / 2).sp, fontFamily = Cairo)
    }
}

@Composable
fun ProfileHeader(p: ProfileData, onPlay: (String) -> Unit, isMe: Boolean = false, extra: @Composable () -> Unit = {}) {
    val surf = MaterialTheme.colorScheme.surface
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f), surf))))
        Row(Modifier.fillMaxWidth().offset(y = (-47).dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(94.dp).clip(CircleShape).border(3.dp, MaterialTheme.colorScheme.background, CircleShape)) { Avatar(p.avatar, p.name, 94) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(top = 52.dp)) {
                Text(p.name, color = Color.White, fontSize = 22.sp, fontFamily = Cairo, maxLines = 1)
                Text("@" + p.name.replace(" ", "_"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
            }
        }
        Column(Modifier.offset(y = (-38).dp).padding(horizontal = 16.dp)) {
            if (p.bio.isNotBlank()) Text(p.bio, color = Color(0xFFD0D6E0), fontSize = 14.sp)
            extra()
        }
        Row(Modifier.offset(y = (-24).dp).fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer).padding(vertical = 12.dp)) {
            listOf("المتابعون" to p.followers, "المتابَعون" to p.following, "الإعجابات" to p.videos.sumOf { it.likes }, "المقاطع" to p.videos.size).forEach { (l, n) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(n.toString(), color = Color.White, fontSize = 18.sp, fontFamily = Cairo)
                    Text(l, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        var tab by remember { mutableIntStateOf(0) }
        val tabs = if (isMe) listOf("الكليبات", "المحفوظات") else listOf("الكليبات")
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary, modifier = Modifier.offset(y = (-12).dp)) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontFamily = Cairo) }) }
        }
        val shown = if (tab == 0) p.videos else Feed.videos.filter { Feed.saved[it.id] == true }
        if (shown.isEmpty()) Text(if (tab == 0) "لا توجد مقاطع بعد" else "لا توجد محفوظات", color = Color.Gray, modifier = Modifier.padding(16.dp))
        else Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            shown.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { v ->
                        Box(Modifier.weight(1f).aspectRatio(9f / 15f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onPlay(v.id) }) {
                            if (v.thumb != null) AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Text(v.title, color = Color.White, fontSize = 11.sp, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorSheet(id: String, onPlay: (String) -> Unit, onClose: () -> Unit) {
    var p by remember { mutableStateOf<ProfileData?>(null) }
    LaunchedEffect(id) { p = loadProfile(id) }
    ModalBottomSheet(sheetState = fullSheet(), onDismissRequest = onClose, containerColor = Color(0xFF141A24)) {
        Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
            val d = p
            if (d == null) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally)) else ProfileHeader(d, onPlay)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileSheet(cur: ProfileData, onDone: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(cur.name) }
    var bio by remember { mutableStateOf(cur.bio) }
    var avatar by remember { mutableStateOf(cur.avatar) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) { busy = true; scope.launch { uploadImageToCloud(ctx, u)?.let { avatar = it }; busy = false } }
    }
    ModalBottomSheet(sheetState = fullSheet(), onDismissRequest = { onDone(false) }, containerColor = Color(0xFF141A24)) {
        Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("تعديل الملف الشخصي", color = Color.White, fontSize = 18.sp, fontFamily = Cairo)
            Avatar(avatar, name, 84)
            OutlinedButton(onClick = { pick.launch("image/*") }, enabled = !busy) { Text("تغيير الصورة") }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("الاسم") }, singleLine = true)
            OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text("الوصف الشخصي") }, minLines = 2)
            Button(enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true
                scope.launch {
                    val b = JSONObject().put("full_name", name.trim()).put("bio", bio.trim())
                    if (avatar != null) b.put("avatar_url", avatar)
                    val (c, _) = api("PATCH", "/rest/v1/profiles?id=eq.${App.userId}", b.toString())
                    if (c in 200..299) { App.name = name.trim(); App.save() }
                    busy = false; onDone(c in 200..299)
                }
            }) { Text(if (busy) "..." else "حفظ التعديلات") }
        }
    }
}
