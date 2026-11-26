package com.mawjalbarq.app
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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

data class ProfileData(val id: String, val name: String, val bio: String, val avatar: String?, val videos: List<Video>)

suspend fun loadProfile(id: String): ProfileData? {
    val (c, t) = api("GET", "/rest/v1/profiles?id=eq.$id&select=id,full_name,bio,avatar_url")
    if (c != 200) return null
    val o = try { JSONArray(t).optJSONObject(0) } catch (e: Exception) { null } ?: return null
    val (c2, t2) = api("GET", "/rest/v1/videos?user_id=eq.$id&status=eq.ready&deleted=eq.false&select=id,title,description,video_url,thumbnail_url,likes,comments_count&order=created_at.desc")
    val vids = ArrayList<Video>()
    if (c2 == 200) try { val a = JSONArray(t2); for (i in 0 until a.length()) { val v = a.getJSONObject(i)
        vids.add(Video(v.getString("id"), v.optString("title"), if (v.isNull("description")) "" else v.optString("description"), v.getString("video_url"),
            if (v.isNull("thumbnail_url")) null else v.optString("thumbnail_url"), o.optString("full_name"), v.optInt("likes"), v.optInt("comments_count"), id)) } } catch (_: Exception) {}
    return ProfileData(id, o.optString("full_name").ifBlank { "مستخدم" }, if (o.isNull("bio")) "" else o.optString("bio"),
        if (o.isNull("avatar_url")) null else o.optString("avatar_url"), vids)
}

@Composable
fun Avatar(url: String?, name: String, size: Int = 72) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.take(1), color = Color.Black, fontSize = (size / 2).sp, fontFamily = Cairo)
    }
}

@Composable
fun ProfileHeader(p: ProfileData, onPlay: (String) -> Unit, extra: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(p.avatar, p.name, 84)
        Spacer(Modifier.height(8.dp))
        Text(p.name, color = Color.White, fontSize = 20.sp, fontFamily = Cairo)
        if (p.bio.isNotBlank()) Text(p.bio, color = Color(0xFFB0B5C0), fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text("${p.videos.size} مقطع  ·  ${p.videos.sumOf { it.likes }} إعجاب", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
        extra()
        Spacer(Modifier.height(12.dp))
    }
    if (p.videos.isEmpty()) Text("لا توجد مقاطع بعد", color = Color.Gray, modifier = Modifier.padding(8.dp))
    else LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 520.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(p.videos) { v ->
            Box(Modifier.aspectRatio(0.62f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1E222B)).clickable { onPlay(v.id) }) {
                if (v.thumb != null) AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Text(v.title, color = Color.White, fontSize = 11.sp, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorSheet(id: String, onPlay: (String) -> Unit, onClose: () -> Unit) {
    var p by remember { mutableStateOf<ProfileData?>(null) }
    LaunchedEffect(id) { p = loadProfile(id) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color(0xFF12141A)) {
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
    ModalBottomSheet(onDismissRequest = { onDone(false) }, containerColor = Color(0xFF12141A)) {
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
