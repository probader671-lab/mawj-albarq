package com.mawjalbarq.app
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class Post(val id: String, val text: String, val image: String?, val author: String, val likes: Int, val liked: Boolean, val comments: Int, val at: String = "")

private suspend fun loadPosts(): List<Post>? {
    val (c, t) = api("GET", "/rest/v1/social_posts?select=id,created_at,content,media_url,media_type,profiles!social_posts_user_id_fkey(full_name),post_likes(user_id),post_comments(id)&is_deleted=eq.false&order=created_at.desc&limit=50")
    if (c != 200) return null
    return try {
        val a = JSONArray(t); val me = App.userId
        (0 until a.length()).map {
            val o = a.getJSONObject(it); val likes = o.optJSONArray("post_likes") ?: JSONArray()
            Post(o.getString("id"), if (o.isNull("content")) "" else o.optString("content"),
                if (o.isNull("media_url") || o.optString("media_type") != "image") null else o.optString("media_url"),
                o.optJSONObject("profiles")?.optString("full_name").orEmpty().ifBlank { "مستخدم" }, likes.length(),
                me != null && (0 until likes.length()).any { i -> likes.getJSONObject(i).optString("user_id") == me },
                o.optJSONArray("post_comments")?.length() ?: 0, o.optString("created_at").take(10))
        }
    } catch (e: Exception) { null }
}

private suspend fun uploadImage(ctx: android.content.Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("upload_preset", Cfg.PRESET)
            .addFormDataPart("file", "p.jpg", bytes.toRequestBody("image/jpeg".toMediaType())).build()
        http.newCall(Request.Builder().url("https://api.cloudinary.com/v1_1/${Cfg.CLOUD}/image/upload").post(body).build()).execute().use {
            if (it.code == 200) JSONObject(it.body!!.string()).getString("secure_url") else null
        }
    } catch (e: Exception) { null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostsScreen(onNeedLogin: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var posts by remember { mutableStateOf<List<Post>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(false) }
    var openComments by remember { mutableStateOf<Post?>(null) }
    var reportPost by remember { mutableStateOf<Post?>(null) }
    fun refresh() { scope.launch { val r = loadPosts(); if (r == null) failed = true else { posts = r; failed = false } } }
    LaunchedEffect(Unit) { refresh() }
    val savedIds = remember { mutableStateListOf<String>().apply { addAll(ctx.getSharedPreferences("posts", android.content.Context.MODE_PRIVATE).getStringSet("saved", emptySet())!!) } }
    var showSaved by remember { mutableStateOf(false) }
    fun toggleSaved(id: String) {
        if (savedIds.contains(id)) savedIds.remove(id) else savedIds.add(id)
        ctx.getSharedPreferences("posts", android.content.Context.MODE_PRIVATE).edit().putStringSet("saved", savedIds.toSet()).apply()
    }
    val conn = remember { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
            if (available.y < -12f) Ui.barVisible = false else if (available.y > 12f) Ui.barVisible = true
            return androidx.compose.ui.geometry.Offset.Zero
        }
    } }
    val ob = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("المنشورات", color = Color.White, fontSize = 24.sp, fontFamily = Cairo, modifier = Modifier.weight(1f))
            BadgedBox(badge = { if (savedIds.isNotEmpty()) Badge { Text(savedIds.size.toString()) } }) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { showSaved = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Bookmark, null, tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        val l = posts
        LazyColumn(Modifier.nestedScroll(conn), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 110.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer).border(1.dp, ob, RoundedCornerShape(20.dp)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(null, App.name.ifBlank { "؟" }, 42)
                        Spacer(Modifier.width(10.dp))
                        Text("بماذا تفكر؟", color = muted, fontSize = 14.sp, modifier = Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { if (App.loggedIn) composing = true else onNeedLogin() }.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Icons.Filled.Image to "صورة", Icons.Filled.Videocam to "فيديو", Icons.Filled.Edit to "منشور").forEach { (ic, t) ->
                            Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { if (App.loggedIn) composing = true else onNeedLogin() }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(ic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            when {
                failed -> item { Text("تعذر تحميل المنشورات", color = Color.Gray) }
                l == null -> item { CircularProgressIndicator() }
                l.isEmpty() -> item { Text("لا توجد منشورات بعد. كن أول من ينشر!", color = Color.Gray) }
                else -> items(l, key = { it.id }) { p ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer).border(1.dp, ob, RoundedCornerShape(20.dp)).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(null, p.author, 40)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.author, color = Color.White, fontFamily = Cairo, fontSize = 15.sp)
                                Text(p.at, color = muted, fontSize = 11.sp)
                            }
                            Icon(Icons.Filled.MoreVert, null, tint = muted, modifier = Modifier.clickable { reportPost = p })
                        }
                        if (p.text.isNotBlank()) Text(p.text, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
                        if (p.image != null) AsyncImage(p.image, null, Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(max = 320.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                        HorizontalDivider(Modifier.padding(top = 10.dp), thickness = 0.5.dp, color = ob)
                        Row(Modifier.padding(top = 6.dp)) {
                            val act = Modifier.weight(1f)
                            Row(act.clip(RoundedCornerShape(12.dp)).clickable {
                                if (!App.loggedIn) onNeedLogin() else scope.launch {
                                    val u = App.userId!!
                                    if (p.liked) api("DELETE", "/rest/v1/post_likes?user_id=eq.$u&post_id=eq.${p.id}")
                                    else api("POST", "/rest/v1/post_likes", JSONObject().put("user_id", u).put("post_id", p.id).toString())
                                    posts = posts?.map { x -> if (x.id == p.id) x.copy(liked = !p.liked, likes = x.likes + if (p.liked) -1 else 1) else x }
                                }
                            }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Favorite, "إعجاب", tint = if (p.liked) Color(0xFFFF2D55) else muted, modifier = Modifier.size(20.dp))
                                Text(" ${p.likes}", color = muted, fontSize = 13.sp)
                            }
                            Row(act.clip(RoundedCornerShape(12.dp)).clickable { openComments = p }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Comment, "تعليقات", tint = muted, modifier = Modifier.size(20.dp))
                                Text(" ${p.comments}", color = muted, fontSize = 13.sp)
                            }
                            Row(act.clip(RoundedCornerShape(12.dp)).clickable {
                                ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, "${p.text}\n- ${p.author} على موج البرق"), "مشاركة"))
                            }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Share, "مشاركة", tint = muted, modifier = Modifier.size(20.dp))
                            }
                            Row(act.clip(RoundedCornerShape(12.dp)).clickable { toggleSaved(p.id) }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Bookmark, "حفظ", tint = if (savedIds.contains(p.id)) MaterialTheme.colorScheme.secondary else muted, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    reportPost?.let { p -> ReportSheet("post", p.id) { reportPost = null } }
    if (showSaved) ModalBottomSheet(sheetState = fullSheet(), onDismissRequest = { showSaved = false }, containerColor = Color(0xFF141A24)) {
        Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("المحفوظات", color = Color.White, fontSize = 18.sp, fontFamily = Cairo)
            val sp = posts?.filter { savedIds.contains(it.id) }.orEmpty()
            if (sp.isEmpty()) Text("لا توجد منشورات محفوظة", color = Color.Gray)
            sp.forEach { p -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
                Text(p.author, color = MaterialTheme.colorScheme.primary, fontFamily = Cairo, fontSize = 14.sp)
                Text(p.text.ifBlank { "صورة" }, color = Color.White, fontSize = 14.sp, maxLines = 3)
            } }
        }
    }
    if (composing) ModalBottomSheet(sheetState = fullSheet(), onDismissRequest = { composing = false }, containerColor = Color(0xFF141A24)) {
        var text by remember { mutableStateOf("") }
        var img by remember { mutableStateOf<Uri?>(null) }
        var busy by remember { mutableStateOf(false) }
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) img = it }
        Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("منشور جديد", color = Color.White, fontSize = 18.sp, fontFamily = Cairo)
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), placeholder = { Text("اكتب ما يدور في ذهنك…") }, minLines = 3)
            OutlinedButton(onClick = { pick.launch("image/*") }, enabled = !busy) { Text(if (img == null) "إضافة صورة" else "تم اختيار الصورة ✓") }
            Button(enabled = !busy && (text.isNotBlank() || img != null), onClick = {
                busy = true
                scope.launch {
                    val url = img?.let { uploadImage(ctx, it) }
                    val b = JSONObject().put("user_id", App.userId).put("content", text.trim())
                    if (url != null) b.put("media_url", url).put("media_type", "image")
                    val (c, _) = api("POST", "/rest/v1/social_posts", b.toString())
                    busy = false
                    if (c in 200..299) { composing = false; refresh() }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "..." else "نشر") }
        }
    }
    openComments?.let { p ->
        ModalBottomSheet(sheetState = fullSheet(), onDismissRequest = { openComments = null; refresh() }, containerColor = Color(0xFF141A24)) {
            var list by remember { mutableStateOf<List<Comment>?>(null) }
            var text by remember { mutableStateOf("") }
            suspend fun load() {
                val (c, t) = api("GET", "/rest/v1/post_comments?post_id=eq.${p.id}&is_deleted=eq.false&select=id,content,profiles!post_comments_user_id_fkey(full_name)&order=created_at.desc")
                if (c == 200) { val a = JSONArray(t); list = (0 until a.length()).map { val o = a.getJSONObject(it)
                    Comment(o.getString("id"), o.optString("content"), o.optJSONObject("profiles")?.optString("full_name").orEmpty().ifBlank { "مستخدم" }) } }
            }
            LaunchedEffect(p.id) { load() }
            Column(Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 540.dp).padding(16.dp).navigationBarsPadding().imePadding()) {
                Text("التعليقات", color = Color.White, fontSize = 18.sp, fontFamily = Cairo)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val l = list
                    if (l == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else if (l.isEmpty()) Text("لا توجد تعليقات", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                    else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(l) { c ->
                        Column { Text(c.author, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp); Text(c.text, color = Color.White) } } }
                }
                if (!App.loggedIn) Button(onClick = { openComments = null; onNeedLogin() }, modifier = Modifier.fillMaxWidth()) { Text("سجّل الدخول للتعليق") }
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("أضف تعليقاً...") })
                    IconButton(enabled = text.isNotBlank(), onClick = {
                        val t = text.trim(); text = ""
                        scope.launch { api("POST", "/rest/v1/post_comments", JSONObject().put("user_id", App.userId).put("post_id", p.id).put("content", t).toString()); load() }
                    }) { Icon(Icons.Filled.Send, "إرسال", tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}
