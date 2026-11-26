package com.mawjalbarq.app
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
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
    val loggedIn get() = token != null && userId != null
    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("session", Context.MODE_PRIVATE)
        token = sp.getString("t", null); refresh = sp.getString("r", null); userId = sp.getString("u", null)
        email = sp.getString("e", "") ?: ""; name = sp.getString("n", "") ?: ""
    }
    fun save() { sp.edit().putString("t", token).putString("r", refresh).putString("u", userId).putString("e", email).putString("n", name).apply() }
    fun logout() { token = null; refresh = null; userId = null; email = ""; name = ""; save(); Feed.liked.clear(); Feed.saved.clear() }
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

suspend fun signUp(name: String, email: String, pass: String): String? {
    val data = JSONObject().put("full_name", name)
    val (c, t) = api("POST", "/auth/v1/signup", JSONObject().put("email", email).put("password", pass).put("data", data).toString())
    if (c !in 200..299) return authError(c, t)
    val j = JSONObject(t)
    if (!j.has("access_token")) return "تحقق من بريدك لتأكيد الحساب"
    App.fromAuth(j); App.name = name.ifBlank { App.name }; App.save()
    api("PATCH", "/rest/v1/profiles?id=eq.${App.userId}", JSONObject().put("full_name", App.name).toString())
    afterLogin(); return null
}

suspend fun afterLogin() {
    Feed.loadUserState()
    val (c, t) = api("GET", "/rest/v1/profiles?id=eq.${App.userId}&select=full_name,theme_accent_argb")
    if (c == 200) try {
        val o = JSONArray(t).optJSONObject(0) ?: return
        if (!o.isNull("full_name")) { App.name = o.getString("full_name"); App.save() }
        if (!o.isNull("theme_accent_argb")) {
            val v = o.getLong("theme_accent_argb")
            val i = Accents.indexOfFirst { (it.main.toArgb().toLong() and 0xFFFFFFFFL) == v }
            if (i >= 0) Theme.prefs?.set(i, sync = false)
        }
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

data class Video(val id: String, val title: String, val desc: String, val url: String, val thumb: String?,
                 val author: String, val likes: Int, val comments: Int, val userId: String = "")
data class Comment(val id: String, val text: String, val author: String)

object Feed {
    val videos = mutableStateListOf<Video>()
    var loading by mutableStateOf(false)
    var error by mutableStateOf(false)
    val liked = mutableStateMapOf<String, Boolean>()
    val saved = mutableStateMapOf<String, Boolean>()
    var jumpTo by mutableStateOf<Int?>(null)

    suspend fun load() {
        loading = true; error = false
        val (c, t) = api("GET", "/rest/v1/videos?select=id,user_id,title,description,video_url,thumbnail_url,likes,comments_count,profiles!videos_user_id_fkey(full_name)&status=eq.ready&deleted=eq.false&order=created_at.desc&limit=40")
        if (c == 200) try {
            val a = JSONArray(t); val list = ArrayList<Video>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                list.add(Video(o.getString("id"), o.optString("title"), if (o.isNull("description")) "" else o.optString("description"),
                    o.getString("video_url"), if (o.isNull("thumbnail_url")) null else o.optString("thumbnail_url"),
                    o.optJSONObject("profiles")?.optString("full_name").orEmpty().ifBlank { "موج البرق" },
                    o.optInt("likes"), o.optInt("comments_count"), o.optString("user_id")))
            }
            videos.clear(); videos.addAll(list)
        } catch (e: Exception) { error = true } else error = true
        loading = false
        loadUserState()
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
        if (i >= 0) videos[i] = videos[i].copy(likes = maxOf(0, videos[i].likes + d))
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

    private var gp: android.content.SharedPreferences? = null
    fun init(ctx: Context) {
        if (gp != null) return
        gp = ctx.applicationContext.getSharedPreferences("guest_saved", Context.MODE_PRIVATE)
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

    suspend fun comments(id: String): List<Comment>? {
        val (c, t) = api("GET", "/rest/v1/comments?video_id=eq.$id&is_deleted=eq.false&select=id,content,profiles!comments_user_id_fkey(full_name)&order=created_at.desc&limit=100")
        if (c != 200) return null
        return try {
            val a = JSONArray(t)
            (0 until a.length()).map { val o = a.getJSONObject(it)
                Comment(o.getString("id"), o.optString("content"), o.optJSONObject("profiles")?.optString("full_name").orEmpty().ifBlank { "مستخدم" }) }
        } catch (e: Exception) { null }
    }

    suspend fun addComment(id: String, text: String): Boolean {
        val u = App.userId ?: return false
        val (c, _) = api("POST", "/rest/v1/comments", JSONObject().put("user_id", u).put("video_id", id).put("content", text).toString())
        if (c in 200..299) {
            val i = videos.indexOfFirst { it.id == id }
            if (i >= 0) videos[i] = videos[i].copy(comments = videos[i].comments + 1)
            return true
        }
        return false
    }
}
