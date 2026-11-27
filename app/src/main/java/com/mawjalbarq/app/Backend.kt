package com.mawjalbarq.app
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import okhttp3.MultipartBody
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Cfg {
    const val URL = "https://oratzvhdqlicmusmkqsv.supabase.co"
    const val ANON = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9yYXR6dmhkcWxpY211c21rcXN2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTU2NDg2OTMsImV4cCI6MjExMTIyNDY5M30.0WvbsSka86I7wWwxVsjTfvd64cAV5U9XwAoOCSQ3NiI"
    const val CLOUD = "wuuhfgal"
    const val PRESET = "mawj_unsigned"
}

val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS).writeTimeout(300, TimeUnit.SECONDS).build()

object App {
    private lateinit var sp: SharedPreferences
    var token by mutableStateOf<String?>(null)
    var refresh by mutableStateOf<String?>(null)
    var userId by mutableStateOf<String?>(null)
    var email by mutableStateOf("")
    var name by mutableStateOf("")
    var avatar by mutableStateOf<String?>(null)
    var publicId by mutableStateOf("")
    val loggedIn get() = token != null && userId != null
    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("session", Context.MODE_PRIVATE)
        token = sp.getString("t", null); refresh = sp.getString("r", null); userId = sp.getString("u", null)
        email = sp.getString("e", "") ?: ""; name = sp.getString("n", "") ?: ""; avatar = sp.getString("av", null); publicId = sp.getString("pid", "") ?: ""
    }
    fun save() { sp.edit().putString("t", token).putString("r", refresh).putString("u", userId).putString("e", email).putString("n", name).putString("av", avatar).putString("pid", publicId).apply() }
    fun logout() { token = null; refresh = null; userId = null; email = ""; name = ""; avatar = null; publicId = ""; save(); Feed.liked.clear(); Feed.saved.clear() }
    fun fromAuth(j: JSONObject) {
        token = j.optString("access_token"); refresh = j.optString("refresh_token")
        val u = j.optJSONObject("user")
        if (u != null) {
            userId = u.optString("id"); email = u.optString("email")
            val md = u.optJSONObject("user_metadata")
            name = md?.optString("full_name").orEmpty().ifBlank { email.substringBefore("@") }
        }
        save()
    }
}

suspend fun api(method: String, path: String, body: String? = null, prefer: String? = null, retry: Boolean = true): Pair<Int, String> =
    withContext(Dispatchers.IO) {
        val b = Request.Builder().url(Cfg.URL + path).header("apikey", Cfg.ANON)
            .header("Authorization", "Bearer " + (App.token ?: Cfg.ANON))
        if (prefer != null) b.header("Prefer", prefer)
        val rb = (body ?: "").toRequestBody("application/json".toMediaType())
        when (method) {
            "GET" -> b.get(); "DELETE" -> b.delete(); "POST" -> b.post(rb); else -> b.patch(rb)
        }
        val (code, text) = try {
            http.newCall(b.build()).execute().use { it.code to (it.body?.string() ?: "") }
        } catch (e: Exception) { -1 to "" }
        if (code == 401 && retry && App.refresh != null && refreshSession()) api(method, path, body, prefer, false) else code to text
    }

suspend fun refreshSession(): Boolean = withContext(Dispatchers.IO) {
    try {
        val rb = JSONObject().put("refresh_token", App.refresh).toString().toRequestBody("application/json".toMediaType())
        val r = Request.Builder().url(Cfg.URL + "/auth/v1/token?grant_type=refresh_token").header("apikey", Cfg.ANON).post(rb).build()
        http.newCall(r).execute().use {
            if (it.code == 200) { App.fromAuth(JSONObject(it.body!!.string())); true } else { App.logout(); false }
        }
    } catch (e: Exception) { false }
}

fun authError(code: Int, text: String): String {
    val t = text.lowercase()
    return when {
        code == -1 -> "تعذر الاتصال بالإنترنت"
        "invalid login" in t -> "البريد أو كلمة المرور غير صحيحة"
        "already" in t -> "هذا البريد مسجل مسبقاً"
        "password" in t -> "كلمة المرور ضعيفة (8 أحرف على الأقل)"
        "valid email" in t || "email" in t -> "البريد الإلكتروني غير صالح"
        "rate" in t -> "محاولات كثيرة، انتظر قليلاً"
        else -> "حدث خطأ، حاول مرة أخرى"
    }
}

suspend fun signIn(email: String, pass: String): String? {
    val (c, t) = api("POST", "/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", pass).toString())
    if (c != 200) return authError(c, t)
    App.fromAuth(JSONObject(t)); afterLogin(); return null
}

suspend fun signUp(name: String, email: String, pass: String, avatarUrl: String): String? {
    val data = JSONObject().put("full_name", name).put("avatar_url", avatarUrl)
    val (c, t) = api("POST", "/auth/v1/signup", JSONObject().put("email", email).put("password", pass).put("data", data).toString())
    if (c !in 200..299) return authError(c, t)
    val j = JSONObject(t)
    if (!j.has("access_token")) return "تحقق من بريدك لتأكيد الحساب"
    App.fromAuth(j); App.name = name.ifBlank { App.name }; App.avatar = avatarUrl; App.save()
    api("PATCH", "/rest/v1/profiles?id=eq.${App.userId}", JSONObject().put("full_name", App.name).put("avatar_url", avatarUrl).toString())
    afterLogin(); return null
}

suspend fun afterLogin() {
    Feed.loadUserState()
    val (c, t) = api("GET", "/rest/v1/profiles?id=eq.${App.userId}&select=full_name,avatar_url,public_id")
    if (c == 200) try {
        val o = JSONArray(t).optJSONObject(0) ?: return
        if (!o.isNull("full_name")) { App.name = o.getString("full_name") }
        if (!o.isNull("avatar_url")) { App.avatar = o.getString("avatar_url") }
        App.save()
        if (!o.isNull("public_id")) { App.publicId = o.getString("public_id") }
        App.save()
    } catch (_: Exception) {}
}

suspend fun uploadImageToCloud(ctx: Context, uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
    try {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("upload_preset", Cfg.PRESET)
            .addFormDataPart("file", "a.jpg", bytes.toRequestBody("image/jpeg".toMediaType())).build()
        http.newCall(Request.Builder().url("https://api.cloudinary.com/v1_1/${Cfg.CLOUD}/image/upload").post(body).build()).execute().use {
            if (it.code == 200) JSONObject(it.body!!.string()).getString("secure_url") else null
        }
    } catch (e: Exception) { null }
}

object Theme { var prefs: ThemePrefs? = null }

data class Video(
    val id: String,
    val title: String,
    val desc: String,
    val url: String,
    val thumb: String?,
    val author: String,
    val likes: Int,
    val comments: Int,
    val userId: String = "",
    val w: Int = 0,
    val h: Int = 0,
    val avatar: String? = null,
    val duration: Int = 0,
    val views: Long = 0L,
    val createdAt: String = "",
    val verified: Boolean = false,
    val followers: Long = 0L,
    val isQuran: Boolean = false
) {
    val portrait get() = h == 0 || h >= w
}
data class Comment(
    val id: String,
    val text: String,
    val author: String,
    val avatar: String? = null,
    val at: String = "",
    val likes: Int = 0,
    val verified: Boolean = false,
    val userId: String = "",
    val ts: String = ""
)

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.US, h, m, s)
    else "%02d:%02d".format(java.util.Locale.US, m, s)
}

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.US, h, m, s)
    else "%02d:%02d".format(java.util.Locale.US, m, s)
}

fun formatCount(n: Long): String = when {
    n >= 1_000_000L -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000L -> String.format(java.util.Locale.US, "%.1fK", n / 1_000.0)
    else -> n.toString()
}

fun formatVideoDate(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        val diff = java.time.Duration.between(java.time.Instant.parse(iso), java.time.Instant.now())
        val mins = diff.toMinutes().coerceAtLeast(0)
        val hours = diff.toHours()
        val days = diff.toDays()
        when {
            mins < 1 -> "الآن"
            mins < 60 -> "منذ $mins د"
            hours < 24 -> "منذ $hours س"
            days < 7 -> "منذ $days ي"
            else -> iso.take(10)
        }
    } catch (_: Throwable) {
        iso.take(10)
    }
}

object Feed {
    val videos = mutableStateListOf<Video>()
    val clips: List<Video> get() = videos.filter { it.portrait }
    val wideVideos: List<Video> get() = videos.filter { !it.portrait }
    var loading by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var hasMore by mutableStateOf(true)
    var error by mutableStateOf(false)
    var isOffline by mutableStateOf(false)
    var cacheUpdatedAt by mutableStateOf(0L)
    val liked = mutableStateMapOf<String, Boolean>()
    val saved = mutableStateMapOf<String, Boolean>()
    var jumpTo by mutableStateOf<Int?>(null)
    var activeVideo by mutableStateOf<Video?>(null)
    private const val PAGE_SIZE = 30
    private var nextCursor: Video? = null  // keyset cursor: last video of the last page

    fun open(v: Video, onGoClips: () -> Unit) {
        if (!v.portrait) {
            activeVideo = v
        } else {
            val idx = videos.indexOfFirst { it.id == v.id }
            if (idx >= 0) {
                jumpTo = idx
                onGoClips()
            } else {
                activeVideo = v
            }
        }
    }

    private fun parse(t: String): Pair<Int, List<Video>> {
        val a = JSONArray(t); val list = ArrayList<Video>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val pr = o.optJSONObject("profiles")
            list.add(
                Video(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    desc = if (o.isNull("description")) "" else o.optString("description"),
                    url = o.getString("video_url"),
                    thumb = if (o.isNull("thumbnail_url")) null else o.optString("thumbnail_url"),
                    author = pr?.optString("full_name").orEmpty().ifBlank { "موج البرق" },
                    likes = o.optInt("likes"),
                    comments = o.optInt("comments_count"),
                    userId = o.optString("user_id"),
                    w = o.optInt("width"),
                    h = o.optInt("height"),
                    avatar = pr?.let { p -> if (p.isNull("avatar_url")) null else p.optString("avatar_url") },
                    duration = o.optDouble("duration", 0.0).toInt().coerceAtLeast(0),
                    views = o.optLong("views", 0L).coerceAtLeast(0L),
                    createdAt = o.optString("created_at"),
                    verified = pr?.optBoolean("verified", false) == true,
                    followers = pr?.optLong("followers_count", 0L)?.coerceAtLeast(0L) ?: 0L,
                    isQuran = o.optBoolean("is_quran", false)
                )
            )
        }
        return a.length() to list.distinctBy { it.id }.distinctBy { it.url }
    }

    private var appCtx: Context? = null

    suspend fun restoreFromCache() {
        val ctx = appCtx ?: return
        val cached = VideoMetadataCache.load(ctx) ?: return
        withContext(Dispatchers.Main) {
            if (videos.isEmpty()) {
                videos.addAll(cached.videos)
            }
            cacheUpdatedAt = cached.updatedAtMs
        }
        val urls = cached.videos.filter { it.portrait }.ifEmpty { cached.videos }.take(2).map { it.url }
        MediaCache.prefetch(ctx, urls)
    }

    private fun persistCacheAsync() {
        val ctx = appCtx ?: return
        val snapshot = videos.toList()
        if (snapshot.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            VideoMetadataCache.save(ctx, snapshot)
        }
    }

    private fun cursorFilter(ts: String, id: String): String {
        val t = java.net.URLEncoder.encode(ts, "UTF-8")
        return "&or=(created_at.lt.$t,and(created_at.eq.$t,id.lt.$id))"
    }

    private fun videosQuery(limit: Int, after: Video?): String =
        "/rest/v1/videos?select=id,user_id,width,height,duration,views,created_at,title,description,video_url,thumbnail_url,likes,comments_count,is_quran,profiles!videos_user_id_fkey(full_name,avatar_url,verified,followers_count)&status=eq.ready&deleted=eq.false" + (if (after != null && after.createdAt.isNotBlank()) cursorFilter(after.createdAt, after.id) else "") + "&order=created_at.desc,id.desc&limit=$limit"

    suspend fun load() {
        if (loading) return
        loading = true; error = false
        if (videos.isEmpty()) {
            restoreFromCache()
        }
        val (c, t) = api("GET", videosQuery(PAGE_SIZE, null))
        if (c == 200) {
            try {
                val (raw0, parsed0) = parse(t)
                val merged = ArrayList<Video>(parsed0)
                var cursor: Video? = parsed0.lastOrNull()
                var moreAvailable = raw0 >= PAGE_SIZE
                var extraPages = 0
                // Ensure at least 10 horizontal videos are ready in Home right after initial load with minimal extra requests
                while (moreAvailable && merged.count { !it.portrait } < 10 && extraPages < 3) {
                    val (cExtra, tExtra) = api("GET", videosQuery(PAGE_SIZE, cursor))
                    if (cExtra != 200) break
                    val (rawExtra, parsedExtra) = parse(tExtra)
                    if (rawExtra == 0) {
                        moreAvailable = false
                        break
                    }
                    cursor = parsedExtra.lastOrNull() ?: cursor
                    moreAvailable = rawExtra >= PAGE_SIZE
                    extraPages++
                    val seenIds = merged.mapTo(HashSet()) { it.id }
                    val seenUrls = merged.mapTo(HashSet()) { it.url }
                    parsedExtra.forEach { v ->
                        if (seenIds.add(v.id) && seenUrls.add(v.url)) merged.add(v)
                    }
                }
                withContext(Dispatchers.Main) {
                    videos.clear()
                    videos.addAll(merged)
                    nextCursor = cursor
                    hasMore = moreAvailable
                    isOffline = false
                    error = false
                    cacheUpdatedAt = System.currentTimeMillis()
                }
                appCtx?.let { ctx ->
                    VideoMetadataCache.save(ctx, merged)
                    val urls = merged.filter { it.portrait }.ifEmpty { merged }.take(3).map { it.url }
                    CoroutineScope(Dispatchers.IO).launch { MediaCache.prefetch(ctx, urls) }
                }
            } catch (_: Exception) {
                if (videos.isEmpty()) error = true else isOffline = true
            }
        } else {
            if (videos.isEmpty()) error = true else isOffline = true
        }
        loading = false
        if (!isOffline) {
            loadUserState(); loadFollows(); Notifs.refreshCount()
        }
    }

    suspend fun loadMore(ensureWide: Boolean = false) {
        if (loading || loadingMore || !hasMore || isOffline) return
        loadingMore = true
        try {
            var pages = 0
            val maxPages = if (ensureWide) 2 else 1
            while (hasMore && pages < maxPages) {
                val (c, t) = api("GET", videosQuery(PAGE_SIZE, nextCursor))
                if (c != 200) break
                val (raw, parsed) = parse(t)
                if (raw == 0) {
                    hasMore = false
                    break
                }
                nextCursor = parsed.lastOrNull() ?: nextCursor
                hasMore = raw >= PAGE_SIZE
                pages++
                var addedWide = 0
                withContext(Dispatchers.Main) {
                    val seenIds = videos.mapTo(HashSet()) { it.id }
                    val seenUrls = videos.mapTo(HashSet()) { it.url }
                    parsed.forEach { v ->
                        if (seenIds.add(v.id) && seenUrls.add(v.url)) {
                            videos.add(v)
                            if (!v.portrait) addedWide++
                        }
                    }
                }
                if (ensureWide && addedWide >= 5) break
            }
            persistCacheAsync()
        } catch (_: Exception) {
        } finally {
            loadingMore = false
        }
    }

    suspend fun loadUserState() {
        val u = App.userId ?: return
        try {
            val (c1, t1) = api("GET", "/rest/v1/likes?user_id=eq.$u&select=video_id")
            if (c1 == 200) { liked.clear(); val a = JSONArray(t1); for (i in 0 until a.length()) liked[a.getJSONObject(i).getString("video_id")] = true }
            val (c2, t2) = api("GET", "/rest/v1/saved_videos?user_id=eq.$u&select=video_id")
            if (c2 == 200) { saved.clear(); val a = JSONArray(t2); for (i in 0 until a.length()) saved[a.getJSONObject(i).getString("video_id")] = true }
        } catch (_: Exception) {}
    }

    private fun bump(id: String, d: Int) {
        val i = videos.indexOfFirst { it.id == id }
        if (i >= 0) {
            videos[i] = videos[i].copy(likes = maxOf(0, videos[i].likes + d))
            persistCacheAsync()
        }
    }

    suspend fun toggleLike(id: String) {
        val u = App.userId ?: return
        if (liked[id] == true) {
            liked.remove(id); bump(id, -1)
            api("DELETE", "/rest/v1/likes?user_id=eq.$u&video_id=eq.$id")
        } else {
            liked[id] = true; bump(id, 1)
            api("POST", "/rest/v1/likes", JSONObject().put("user_id", u).put("video_id", id).toString())
        }
    }

    val following = mutableStateMapOf<String, Boolean>()
    suspend fun loadFollows() {
        val u = App.userId ?: return
        val (c, t) = api("GET", "/rest/v1/follows?follower_id=eq.$u&select=following_id")
        if (c == 200) try { following.clear(); val a = JSONArray(t); for (i in 0 until a.length()) following[a.getJSONObject(i).getString("following_id")] = true } catch (_: Exception) {}
    }
    suspend fun toggleFollow(id: String) {
        val u = App.userId ?: return
        if (id.isBlank() || id == u) return
        val wasFollowing = following[id] == true
        val delta = if (wasFollowing) -1L else 1L
        for (i in videos.indices) {
            if (videos[i].userId == id) {
                videos[i] = videos[i].copy(followers = maxOf(0L, videos[i].followers + delta))
            }
        }
        persistCacheAsync()
        if (wasFollowing) {
            following.remove(id)
            api("DELETE", "/rest/v1/follows?follower_id=eq.$u&following_id=eq.$id")
        } else {
            following[id] = true
            api("POST", "/rest/v1/follows", JSONObject().put("follower_id", u).put("following_id", id).toString())
        }
    }

    private var gp: android.content.SharedPreferences? = null
    fun init(ctx: Context) {
        if (gp != null) return
        appCtx = ctx.applicationContext
        gp = appCtx!!.getSharedPreferences("guest_saved", Context.MODE_PRIVATE)
        CoroutineScope(Dispatchers.IO).launch { restoreFromCache() }
        if (App.userId == null) gp!!.getStringSet("ids", emptySet())!!.forEach { saved[it] = true }
    }

    suspend fun toggleSave(id: String) {
        val u = App.userId
        if (u == null) {
            if (saved[id] == true) saved.remove(id) else saved[id] = true
            gp?.edit()?.putStringSet("ids", saved.keys.toSet())?.apply()
            return
        }
        if (saved[id] == true) {
            saved.remove(id); api("DELETE", "/rest/v1/saved_videos?user_id=eq.$u&video_id=eq.$id")
        } else {
            saved[id] = true
            api("POST", "/rest/v1/saved_videos", JSONObject().put("user_id", u).put("video_id", id).toString())
        }
    }

    const val COMMENTS_PAGE = 25

    /** One page of comments, newest first. Pass the last comment of the previous page as [after] for the next page. */
    suspend fun comments(id: String, after: Comment? = null): List<Comment>? {
        val cur = if (after != null && after.ts.isNotBlank()) {
            val t = java.net.URLEncoder.encode(after.ts, "UTF-8")
            "&or=(created_at.lt.$t,and(created_at.eq.$t,id.lt.${after.id}))"
        } else ""
        val (c, t) = api("GET", "/rest/v1/comments?video_id=eq.$id&is_deleted=eq.false&select=id,user_id,content,created_at,likes,profiles!comments_user_id_fkey(full_name,avatar_url,verified)$cur&order=created_at.desc,id.desc&limit=$COMMENTS_PAGE")
        if (c != 200) return null
        return try {
            val a = JSONArray(t)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                val pr = o.optJSONObject("profiles")
                Comment(
                    id = o.getString("id"),
                    text = o.optString("content"),
                    author = pr?.optString("full_name").orEmpty().ifBlank { "مستخدم" },
                    avatar = if (pr == null || pr.isNull("avatar_url")) null else pr.optString("avatar_url"),
                    at = o.optString("created_at").take(10),
                    likes = o.optInt("likes", 0),
                    verified = pr?.optBoolean("verified", false) == true,
                    userId = o.optString("user_id"),
                    ts = o.optString("created_at")
                )
            }
        } catch (e: Exception) { null }
    }

    suspend fun addComment(id: String, text: String): Boolean {
        val u = App.userId ?: return false
        val (c, _) = api("POST", "/rest/v1/comments", JSONObject().put("user_id", u).put("video_id", id).put("content", text).toString())
        if (c in 200..299) {
            val i = videos.indexOfFirst { it.id == id }
            if (i >= 0) {
                videos[i] = videos[i].copy(comments = videos[i].comments + 1)
                persistCacheAsync()
            }
            return true
        }
        return false
    }
}
