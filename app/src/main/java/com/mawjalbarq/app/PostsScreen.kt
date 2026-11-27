package com.mawjalbarq.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import java.time.Duration
import java.time.Instant

data class Post(
    val id: String,
    val userId: String,
    val text: String,
    val mediaType: String,
    val mediaUrl: String?,
    val thumbUrl: String?,
    val videoId: String?,
    val author: String,
    val avatar: String?,
    val verified: Boolean,
    val likes: Int,
    val liked: Boolean,
    val comments: Int,
    val pinned: Boolean,
    val at: String = "",
    val ts: String = ""
)

private const val POSTS_PAGE = 30

private val FeedSeparatorColor = Color(0xFF070A0F)
private val GalleryGreen = Color(0xFF45BD62)

private fun formatRelativeTime(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        val diff = Duration.between(Instant.parse(iso), Instant.now())
        val mins = diff.toMinutes().coerceAtLeast(0)
        val hours = diff.toHours()
        val days = diff.toDays()
        when {
            mins < 1 -> "الآن"
            mins < 60 -> "$mins د"
            hours < 24 -> "$hours س"
            days < 7 -> "$days ي"
            else -> iso.take(10)
        }
    } catch (_: Exception) {
        iso.take(10)
    }
}

private suspend fun loadPosts(after: Post? = null): List<Post>? {
    val cur = if (after == null) "" else {
        val ts = java.net.URLEncoder.encode(after.ts, "UTF-8")
        "&pinned=eq.false&or=(created_at.lt.$ts,and(created_at.eq.$ts,id.lt.${after.id}))"
    }
    val (c, t) = api(
        "GET",
        "/rest/v1/social_posts?select=id,user_id,created_at,content,media_url,thumbnail_url,media_type,video_id,pinned,profiles!social_posts_user_id_fkey(full_name,avatar_url,verified),post_likes(count),post_comments(count)&is_deleted=eq.false$cur&order=pinned.desc,created_at.desc,id.desc&limit=$POSTS_PAGE"
    )
    if (c != 200) return null
    return try {
        val a = JSONArray(t)
        val me = App.userId
        // which of these posts did I like: one extra query for the page, not an embedded row per like
        val likedByMe = HashSet<String>()
        if (me != null && a.length() > 0) {
            val ids = (0 until a.length()).joinToString(",") { a.getJSONObject(it).getString("id") }
            val (lc, lt) = api("GET", "/rest/v1/post_likes?user_id=eq.$me&post_id=in.($ids)&select=post_id")
            if (lc == 200) try { val b = JSONArray(lt); for (i in 0 until b.length()) likedByMe.add(b.getJSONObject(i).getString("post_id")) } catch (_: Exception) {}
        }
        fun cnt(o: JSONObject, k: String) = o.optJSONArray(k)?.optJSONObject(0)?.optInt("count", 0) ?: 0
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val pr = o.optJSONObject("profiles")
            val mediaType = o.optString("media_type", "none")
            val mediaUrl = if (o.isNull("media_url")) null else o.optString("media_url").ifBlank { null }
            val thumbUrl = if (o.isNull("thumbnail_url")) null else o.optString("thumbnail_url").ifBlank { null }
            val videoId = if (o.isNull("video_id")) null else o.optString("video_id").ifBlank { null }
            Post(
                id = o.getString("id"),
                userId = o.optString("user_id"),
                text = if (o.isNull("content")) "" else o.optString("content"),
                mediaType = mediaType,
                mediaUrl = mediaUrl,
                thumbUrl = thumbUrl,
                videoId = videoId,
                author = pr?.optString("full_name").orEmpty().ifBlank { "مستخدم" },
                avatar = if (pr == null || pr.isNull("avatar_url")) null else pr.optString("avatar_url").ifBlank { null },
                verified = pr?.optBoolean("verified", false) ?: false,
                likes = cnt(o, "post_likes"),
                liked = likedByMe.contains(o.getString("id")),
                comments = cnt(o, "post_comments"),
                pinned = o.optBoolean("pinned", false),
                at = formatRelativeTime(o.optString("created_at")),
                ts = o.optString("created_at")
            )
        }
    } catch (_: Exception) {
        null
    }
}

private suspend fun uploadImage(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        val sig = cloudSign("image") ?: return@withContext null
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addSig(sig)
            .addFormDataPart("file", "p.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            .build()
        http.newCall(Request.Builder().url("https://api.cloudinary.com/v1_1/${Cfg.CLOUD}/image/upload").post(body).build())
            .execute().use { if (it.code == 200) JSONObject(it.body!!.string()).getString("secure_url") else null }
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostsScreen(onNeedLogin: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var posts by remember { mutableStateOf<List<Post>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var morePosts by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(false) }
    var autoPickPhoto by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSaved by remember { mutableStateOf(false) }
    var openComments by remember { mutableStateOf<Post?>(null) }
    var reportPost by remember { mutableStateOf<Post?>(null) }
    var creatorId by remember { mutableStateOf<String?>(null) }
    var previewImage by remember { mutableStateOf<String?>(null) }
    val dismissedIds = remember { mutableStateListOf<String>() }

    fun refresh() {
        scope.launch {
            refreshing = true
            val r = loadPosts()
            if (r == null) failed = true else {
                posts = r
                morePosts = r.size >= POSTS_PAGE
                failed = false
            }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val savedIds = remember {
        mutableStateListOf<String>().apply {
            addAll(ctx.getSharedPreferences("posts", Context.MODE_PRIVATE).getStringSet("saved", emptySet())!!)
        }
    }

    fun toggleSaved(id: String) {
        if (savedIds.contains(id)) savedIds.remove(id) else savedIds.add(id)
        ctx.getSharedPreferences("posts", Context.MODE_PRIVATE).edit().putStringSet("saved", savedIds.toSet()).apply()
    }

    val conn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -12f) Ui.barVisible = false else if (available.y > 12f) Ui.barVisible = true
                return Offset.Zero
            }
        }
    }

    val surf = MaterialTheme.colorScheme.surface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val hairline = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)

    Column(
        Modifier
            .fillMaxSize()
            .background(FeedSeparatorColor)
            .statusBarsPadding()
    ) {
        // Top Brand + Actions Header (matching screenshot layout in Mawj Al-Barq colors)
        Row(
            Modifier
                .fillMaxWidth()
                .background(surf)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "موج البرق",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Cairo,
                modifier = Modifier.weight(1f)
            )
            HeaderIconBtn(Icons.Filled.Add, "منشور جديد", "posts_create_button") {
                if (App.loggedIn) {
                    autoPickPhoto = false
                    composing = true
                } else onNeedLogin()
            }
            Spacer(Modifier.width(6.dp))
            HeaderIconBtn(
                if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                "بحث",
                "posts_search_button"
            ) {
                searchOpen = !searchOpen
                if (!searchOpen) searchQuery = ""
            }
            Spacer(Modifier.width(6.dp))
            BadgedBox(badge = {
                if (savedIds.isNotEmpty()) Badge(containerColor = Color(0xFFFF2D55)) { Text(savedIds.size.toString(), color = Ink, fontSize = 10.sp) }
            }) {
                HeaderIconBtn(Icons.Filled.Bookmark, "المحفوظات", "posts_saved_button") {
                    showSaved = true
                }
            }
        }

        if (searchOpen) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(surf)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().testTag("posts_search_input"),
                    placeholder = { Text("ابحث في المنشورات أو الهاشتاج...", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = muted) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, "مسح", tint = muted)
                            }
                        }
                    }
                )
            }
        }

        if (refreshing && posts != null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = surf
            )
        } else {
            HorizontalDivider(thickness = 0.5.dp, color = hairline)
        }

        val visiblePosts = remember(posts, searchQuery, dismissedIds.size) {
            val q = searchQuery.trim()
            posts?.filter { p ->
                !dismissedIds.contains(p.id) &&
                    (q.isEmpty() || p.text.contains(q, ignoreCase = true) || p.author.contains(q, ignoreCase = true))
            }
        }

        LazyColumn(
            Modifier.weight(1f).nestedScroll(conn),
            contentPadding = PaddingValues(bottom = 86.dp)
        ) {
            // Composer Strip: Avatar + "بم تفكر؟" pill + Green Photo Icon
            item(key = "composer") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(surf)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(App.avatar, App.name.ifBlank { "م" }, 40)
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .clickable {
                                if (App.loggedIn) {
                                    autoPickPhoto = false
                                    composing = true
                                } else onNeedLogin()
                            }
                            .testTag("posts_composer_pill")
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("بم تفكر؟", color = Color(0xFFD6DCE6), fontSize = 15.sp, fontFamily = Tajawal)
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (App.loggedIn) {
                                autoPickPhoto = true
                                composing = true
                            } else onNeedLogin()
                        },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("posts_composer_photo")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, "إضافة صورة", tint = GalleryGreen, modifier = Modifier.size(25.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(7.dp).background(FeedSeparatorColor))
            }

            // Tall Vertical Stories Strip
            item(key = "stories") {
                Box(Modifier.fillMaxWidth().background(surf).padding(vertical = 10.dp)) {
                    StoriesStrip(onNeedLogin = onNeedLogin, tallCards = true)
                }
                Box(Modifier.fillMaxWidth().height(7.dp).background(FeedSeparatorColor))
            }

            when {
                failed && visiblePosts == null -> item(key = "err") {
                    Column(
                        Modifier.fillMaxWidth().background(surf).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("تعذر تحميل المنشورات", color = muted, fontFamily = Cairo)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { refresh() }, shape = RoundedCornerShape(50)) {
                            Text("إعادة المحاولة", fontFamily = Cairo)
                        }
                    }
                }
                visiblePosts == null -> items(3, key = { "post_sk_$it" }) {
                    PostSkeletonItem(FeedSeparatorColor)
                }
                visiblePosts.isEmpty() -> item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().background(surf).padding(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (searchQuery.isNotBlank()) "لا توجد نتائج مطابقة للبحث" else "لا توجد منشورات بعد. كن أول من ينشر!",
                            color = muted,
                            fontFamily = Cairo
                        )
                    }
                }
                else -> items(visiblePosts, key = { it.id }) { p ->
                    PostFeedItem(
                        p = p,
                        saved = savedIds.contains(p.id),
                        onAuthorClick = { if (p.userId.isNotBlank()) creatorId = p.userId },
                        onFollow = {
                            if (!App.loggedIn) onNeedLogin()
                            else scope.launch { Feed.toggleFollow(p.userId) }
                        },
                        onReport = { reportPost = p },
                        onDismiss = { dismissedIds.add(p.id) },
                        onImageClick = { previewImage = it },
                        onVideoClick = {
                            val match = p.videoId?.let { vid -> Feed.videos.firstOrNull { it.id == vid } }
                            val target = match ?: p.mediaUrl?.let { url ->
                                Video(
                                    id = p.videoId ?: p.id,
                                    title = p.text.ifBlank { p.author },
                                    desc = p.text,
                                    url = url,
                                    thumb = p.thumbUrl,
                                    author = p.author,
                                    likes = p.likes,
                                    comments = p.comments,
                                    userId = p.userId,
                                    avatar = p.avatar
                                )
                            }
                            if (target != null) Feed.activeVideo = target
                        },
                        onLike = {
                            if (!App.loggedIn) onNeedLogin()
                            else scope.launch {
                                val u = App.userId!!
                                val nextLiked = !p.liked
                                posts = posts?.map { x ->
                                    if (x.id == p.id) x.copy(liked = nextLiked, likes = (x.likes + if (nextLiked) 1 else -1).coerceAtLeast(0))
                                    else x
                                }
                                if (p.liked) api("DELETE", "/rest/v1/post_likes?user_id=eq.$u&post_id=eq.${p.id}")
                                else api("POST", "/rest/v1/post_likes", JSONObject().put("user_id", u).put("post_id", p.id).toString())
                            }
                        },
                        onComment = { openComments = p },
                        onSave = { toggleSaved(p.id) },
                        onShare = {
                            ctx.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, "${p.text}\n- ${p.author} على موج البرق"),
                                    "مشاركة"
                                )
                            )
                        }
                    )
                    Box(Modifier.fillMaxWidth().height(7.dp).background(FeedSeparatorColor))
                }
            }
                if (morePosts && searchQuery.isBlank() && visiblePosts != null) item(key = "more_posts") {
                    LaunchedEffect(posts?.size) {
                        if (!loadingMore) {
                            loadingMore = true
                            val cur = posts
                            val r = loadPosts(cur?.lastOrNull { !it.pinned } ?: cur?.lastOrNull())
                            if (r == null) morePosts = false else if (cur != null) {
                                val seen = cur.mapTo(HashSet()) { it.id }
                                posts = cur + r.filter { seen.add(it.id) }
                                morePosts = r.size >= POSTS_PAGE
                            }
                            loadingMore = false
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                }
        }
    }

    previewImage?.let { url ->
        Dialog(onDismissRequest = { previewImage = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(url, "معاينة الصورة", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                IconButton(
                    onClick = { previewImage = null },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                ) {
                    Icon(Icons.Filled.Close, "إغلاق", tint = Color.White)
                }
            }
        }
    }

    creatorId?.let { id ->
        CreatorSheet(id, onPlay = { vid ->
            creatorId = null
            Feed.videos.firstOrNull { it.id == vid }?.let { Feed.activeVideo = it }
        }) { creatorId = null }
    }

    reportPost?.let { p -> ReportSheet("post", p.id) { reportPost = null } }

    if (showSaved) {
        ModalBottomSheet(
            sheetState = fullSheet(),
            onDismissRequest = { showSaved = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0x55FFFFFF)) }
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("المحفوظات", color = Ink, fontSize = 18.sp, fontFamily = Cairo)
                val sp = posts?.filter { savedIds.contains(it.id) }.orEmpty()
                if (sp.isEmpty()) {
                    Text("لا توجد منشورات محفوظة", color = muted)
                } else {
                    sp.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { showSaved = false; openComments = p }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(p.avatar, p.author, 36)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.author, color = MaterialTheme.colorScheme.primary, fontFamily = Cairo, fontSize = 14.sp)
                                Text(p.text.ifBlank { "منشور وسائط" }, color = Ink, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { toggleSaved(p.id) }) {
                                Icon(Icons.Filled.Bookmark, "إزالة", tint = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (composing) {
        ModalBottomSheet(
            sheetState = fullSheet(),
            onDismissRequest = { composing = false; autoPickPhoto = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0x55FFFFFF)) }
        ) {
            var text by remember { mutableStateOf("") }
            var img by remember { mutableStateOf<Uri?>(null) }
            var busy by remember { mutableStateOf(false) }
            val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) img = it }
            LaunchedEffect(autoPickPhoto) {
                if (autoPickPhoto) {
                    autoPickPhoto = false
                    pick.launch("image/*")
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp).navigationBarsPadding().imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("إنشاء منشور", color = Ink, fontSize = 18.sp, fontFamily = Cairo, modifier = Modifier.weight(1f))
                    IconButton(onClick = { composing = false }) {
                        Icon(Icons.Filled.Close, "إغلاق", tint = muted)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(App.avatar, App.name.ifBlank { "م" }, 40)
                    Spacer(Modifier.width(10.dp))
                    Text(App.name.ifBlank { "مستخدم" }, color = Ink, fontFamily = Cairo, fontSize = 15.sp)
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().testTag("post_compose_input"),
                    placeholder = { Text("بم تفكر؟") },
                    minLines = 3,
                    shape = RoundedCornerShape(16.dp)
                )
                if (img != null) {
                    Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp))) {
                        AsyncImage(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        IconButton(
                            onClick = { img = null },
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp).clip(CircleShape).background(Color(0xAA000000))
                        ) {
                            Icon(Icons.Filled.Close, "إزالة الصورة", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { pick.launch("image/*") },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, null, tint = GalleryGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (img == null) "صورة" else "تغيير الصورة", fontFamily = Cairo)
                    }
                    Button(
                        enabled = !busy && (text.isNotBlank() || img != null),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("post_compose_submit"),
                        onClick = {
                            busy = true
                            scope.launch {
                                val url = img?.let { uploadImage(ctx, it) }
                                val b = JSONObject().put("user_id", App.userId).put("content", text.trim())
                                if (url != null) b.put("media_url", url).put("media_type", "image")
                                val (c, _) = api("POST", "/rest/v1/social_posts", b.toString())
                                busy = false
                                if (c in 200..299) {
                                    composing = false
                                    refresh()
                                }
                            }
                        }
                    ) {
                        Text(if (busy) "جاري النشر..." else "نشر", fontFamily = Cairo)
                    }
                }
            }
        }
    }

    openComments?.let { p ->
        PostCommentsSheet(
            post = p,
            onNeedLogin = { openComments = null; onNeedLogin() },
            onClose = { openComments = null; refresh() }
        )
    }
}

@Composable
private fun HeaderIconBtn(icon: ImageVector, desc: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PostFeedItem(
    p: Post,
    saved: Boolean,
    onAuthorClick: () -> Unit,
    onFollow: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    val surf = MaterialTheme.colorScheme.surface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val isFollowing = Feed.following[p.userId] == true
    var expanded by remember(p.id) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().background(surf)) {
        // Post Header
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.clickable(onClick = onAuthorClick)) {
                Avatar(p.avatar, p.author, 42)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.author,
                        color = Ink,
                        fontFamily = Cairo,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clickable(onClick = onAuthorClick)
                    )
                    if (p.verified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Verified, "موثق", tint = primary, modifier = Modifier.size(15.dp))
                    }
                    if (p.userId.isNotBlank() && p.userId != App.userId) {
                        Text(" · ", color = muted, fontSize = 14.sp)
                        Text(
                            if (isFollowing) "تتابعه" else "متابعة",
                            color = if (isFollowing) muted else primary,
                            fontFamily = Cairo,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable(onClick = onFollow)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p.pinned) {
                        Text("مثبت · ", color = primary, fontSize = 11.sp, fontFamily = Cairo)
                    }
                    if (p.at.isNotBlank()) {
                        Text(p.at, color = muted, fontSize = 12.sp)
                        Text(" · ", color = muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.Public, "عام", tint = muted, modifier = Modifier.size(13.dp))
                }
            }
            IconButton(onClick = onReport, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.Filled.MoreHoriz, "خيارات", tint = muted, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.Filled.Close, "إخفاء", tint = muted, modifier = Modifier.size(21.dp))
            }
        }

        // Post Text with Hashtag Highlight + Expandable "... المزيد"
        if (p.text.isNotBlank()) {
            val annotated = remember(p.text, primary) {
                buildAnnotatedString {
                    append(p.text)
                    Regex("#[\\p{L}\\p{N}_]+").findAll(p.text).forEach { m ->
                        addStyle(SpanStyle(color = primary, fontWeight = FontWeight.Medium), m.range.first, m.range.last + 1)
                    }
                }
            }
            val needsTruncate = !expanded && (p.text.length > 120 || p.text.count { it == '\n' } >= 3)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = annotated,
                    color = Ink,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (needsTruncate) {
                    Text("عرض المزيد...", color = muted, fontSize = 13.sp, fontFamily = Cairo, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        // Full-Width Edge-to-Edge Media
        if (p.mediaType == "image" && p.mediaUrl != null) {
            Spacer(Modifier.height(6.dp))
            AsyncImage(
                model = p.mediaUrl,
                contentDescription = p.text.ifBlank { "صورة المنشور" },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 440.dp)
                    .background(Color(0xFF0A0E14))
                    .clickable { onImageClick(p.mediaUrl) },
                contentScale = ContentScale.Crop
            )
        } else if (p.mediaType == "video" && (p.thumbUrl != null || p.mediaUrl != null)) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(Color.Black)
                    .clickable(onClick = onVideoClick),
                contentAlignment = Alignment.Center
            ) {
                val preview = p.thumbUrl ?: p.mediaUrl
                if (preview != null) {
                    AsyncImage(preview, "فيديو المنشور", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                        .border(1.dp, Color(0x55FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, "تشغيل الفيديو", tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }
        }

        // Engagement Count Summary
        if (p.likes > 0 || p.comments > 0) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (p.likes > 0) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.ThumbUp, null, tint = Color.Black, modifier = Modifier.size(11.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("${p.likes}", color = muted, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                if (p.comments > 0) {
                    Text(
                        "${p.comments} تعليق",
                        color = muted,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable(onClick = onComment)
                    )
                }
            }
        } else {
            Spacer(Modifier.height(6.dp))
        }

        HorizontalDivider(
            Modifier.padding(horizontal = 12.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )

        // 4-Action Bottom Bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PostActionItem(
                icon = if (p.liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                label = "أعجبني",
                tint = if (p.liked) primary else muted,
                modifier = Modifier.weight(1f),
                onClick = onLike
            )
            PostActionItem(
                icon = Icons.Outlined.ModeComment,
                label = "تعليق",
                tint = muted,
                modifier = Modifier.weight(1f),
                onClick = onComment
            )
            PostActionItem(
                icon = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = if (saved) "محفوظ" else "حفظ",
                tint = if (saved) MaterialTheme.colorScheme.secondary else muted,
                modifier = Modifier.weight(1f),
                onClick = onSave
            )
            PostActionItem(
                icon = Icons.Outlined.Share,
                label = "مشاركة",
                tint = muted,
                modifier = Modifier.weight(1f),
                onClick = onShare
            )
        }
    }
}

@Composable
private fun PostActionItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = tint, fontSize = 13.sp, fontFamily = Cairo, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostCommentsSheet(post: Post, onNeedLogin: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Comment>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    var moreC by remember { mutableStateOf(false) }

    suspend fun loadMoreC() {
        val cur = list ?: return
        val last = cur.lastOrNull() ?: return
        val ts = java.net.URLEncoder.encode(last.ts, "UTF-8")
        val (c, t) = api("GET", "/rest/v1/post_comments?post_id=eq.${post.id}&is_deleted=eq.false&select=id,content,created_at,profiles!post_comments_user_id_fkey(full_name,avatar_url)&or=(created_at.lt.$ts,and(created_at.eq.$ts,id.lt.${last.id}))&order=created_at.desc,id.desc&limit=${Feed.COMMENTS_PAGE}")
        if (c != 200) return
        try {
            val a = JSONArray(t)
            val more = (0 until a.length()).map {
                val o = a.getJSONObject(it); val pr = o.optJSONObject("profiles")
                Comment(
                    id = o.getString("id"), text = o.optString("content"),
                    author = pr?.optString("full_name").orEmpty().ifBlank { "مستخدم" },
                    avatar = if (pr == null || pr.isNull("avatar_url")) null else pr.optString("avatar_url").ifBlank { null },
                    at = formatRelativeTime(o.optString("created_at")), ts = o.optString("created_at")
                )
            }
            val seen = cur.mapTo(HashSet()) { it.id }
            list = cur + more.filter { seen.add(it.id) }
            moreC = a.length() >= Feed.COMMENTS_PAGE
        } catch (_: Exception) {}
    }

    suspend fun load() {
        failed = false
        val (c, t) = api(
            "GET",
            "/rest/v1/post_comments?post_id=eq.${post.id}&is_deleted=eq.false&select=id,content,created_at,profiles!post_comments_user_id_fkey(full_name,avatar_url)&order=created_at.desc,id.desc&limit=${Feed.COMMENTS_PAGE}"
        )
        if (c == 200) {
            try {
                val a = JSONArray(t)
                list = (0 until a.length()).map {
                    val o = a.getJSONObject(it)
                    val pr = o.optJSONObject("profiles")
                    Comment(
                        id = o.getString("id"),
                        text = o.optString("content"),
                        author = pr?.optString("full_name").orEmpty().ifBlank { "مستخدم" },
                        avatar = if (pr == null || pr.isNull("avatar_url")) null else pr.optString("avatar_url").ifBlank { null },
                        at = formatRelativeTime(o.optString("created_at")),
                        ts = o.optString("created_at")
                    )
                }
                moreC = list!!.size >= Feed.COMMENTS_PAGE
            } catch (_: Exception) {
                failed = true
            }
        } else {
            failed = true
        }
    }

    LaunchedEffect(post.id) { load() }

    fun send() {
        val t = text.trim()
        if (t.isBlank() || sending) return
        sending = true
        scope.launch {
            val (c, _) = api(
                "POST",
                "/rest/v1/post_comments",
                JSONObject().put("user_id", App.userId).put("post_id", post.id).put("content", t).toString()
            )
            if (c in 200..299) {
                text = ""
                load()
            }
            sending = false
        }
    }

    ModalBottomSheet(
        sheetState = fullSheet(),
        onDismissRequest = onClose,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0x55FFFFFF)) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 340.dp, max = 580.dp)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ModeComment, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("التعليقات", color = Ink, fontSize = 18.sp, fontFamily = Cairo)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${list?.size ?: post.comments}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontFamily = Cairo,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.Close, "إغلاق", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            Box(Modifier.weight(1f, fill = true).fillMaxWidth()) {
                val l = list
                when {
                    failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("تعذر تحميل التعليقات", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Cairo)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { scope.launch { load() } }) { Text("إعادة المحاولة", fontFamily = Cairo) }
                    }
                    l == null -> ListSkeleton(5, Modifier.padding(16.dp))
                    l.isEmpty() -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ModeComment, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(38.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("لا توجد تعليقات بعد", color = Ink, fontSize = 15.sp, fontFamily = Cairo)
                        Text("كن أول من يشارك رأيه!", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                        items(l, key = { it.id }) { c ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF1A2230))
                                    .border(0.75.dp, Color(0x18FFFFFF), RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Avatar(c.avatar, c.author, 36)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(c.author, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontFamily = Cairo, modifier = Modifier.weight(1f, false))
                                        if (c.at.isNotBlank()) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(c.at, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(c.text, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
                                }
                            }
                        }
                        if (moreC) item(key = "more_post_comments") {
                            LaunchedEffect(l.size) { loadMoreC() }
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            if (!App.loggedIn) {
                Button(
                    onClick = onNeedLogin,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                ) { Text("سجّل الدخول للتعليق", fontFamily = Cairo) }
            } else {
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f).testTag("post_comment_input"),
                        placeholder = { Text("اكتب تعليقاً...") },
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() })
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        enabled = text.isNotBlank() && !sending,
                        onClick = { send() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (text.isNotBlank() && !sending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("post_comment_send")
                    ) {
                        if (sending) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                "إرسال",
                                tint = if (text.isNotBlank()) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
