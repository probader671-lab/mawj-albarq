package com.mawjalbarq.app
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/* ---------- continue watching ---------- */
object Ui { var barVisible by mutableStateOf(true); var deep by mutableStateOf(false) }

object Watch {
    fun save(ctx: Context, id: String) = ctx.getSharedPreferences("watch", Context.MODE_PRIVATE).edit().putString("last", id).apply()
    fun savePos(ctx: Context, id: String, frac: Float) = ctx.getSharedPreferences("watch", Context.MODE_PRIVATE).edit().putFloat("p_$id", frac).apply()
    fun pos(ctx: Context, id: String): Float = ctx.getSharedPreferences("watch", Context.MODE_PRIVATE).getFloat("p_$id", 0f)
    fun last(ctx: Context): String? = ctx.getSharedPreferences("watch", Context.MODE_PRIVATE).getString("last", null)
}

/* ---------- stories ---------- */
data class Story(val id: String, val userId: String, val author: String, val avatar: String?, val image: String, val caption: String)

suspend fun loadStories(): List<Story> {
    val (c, t) = api("GET", "/rest/v1/stories?select=id,user_id,media_url,caption,media_type,profiles!stories_user_id_fkey(full_name,avatar_url)&is_deleted=eq.false&expires_at=gt.${java.time.Instant.now()}&order=created_at.desc&limit=60")
    if (c != 200) return emptyList()
    return try {
        val a = JSONArray(t)
        (0 until a.length()).mapNotNull {
            val o = a.getJSONObject(it); if (o.optString("media_type", "image") == "video") return@mapNotNull null
            val p = o.optJSONObject("profiles")
            Story(o.getString("id"), o.getString("user_id"), p?.optString("full_name").orEmpty().ifBlank { "مستخدم" },
                if (p == null || p.isNull("avatar_url")) null else p.optString("avatar_url"), o.getString("media_url"), if (o.isNull("caption")) "" else o.optString("caption"))
        }
    } catch (e: Exception) { emptyList() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoriesStrip(onNeedLogin: () -> Unit, tallCards: Boolean = false) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var stories by remember { mutableStateOf<List<Story>>(emptyList()) }
    var storiesLoading by remember { mutableStateOf(true) }
    var viewing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(App.userId) {
        storiesLoading = true
        stories = loadStories()
        storiesLoading = false
    }
    if (storiesLoading && stories.isEmpty()) {
        StoriesSkeletonItems(tallCards)
    } else if (tallCards) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Box(
                    Modifier
                        .size(104.dp, 184.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .clickable { if (App.loggedIn) adding = true else onNeedLogin() }
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(0.68f)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), MaterialTheme.colorScheme.surface)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val av = App.avatar
                            if (av != null) {
                                AsyncImage(av, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Avatar(null, App.name.ifBlank { "م" }, 52)
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(0.32f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(top = 14.dp, start = 6.dp, end = 6.dp, bottom = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "إنشاء قصة",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = Cairo,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .offset(y = 32.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, "إنشاء قصة", tint = Color.Black, modifier = Modifier.size(20.dp))
                    }
                }
            }
            items(stories.distinctBy { it.userId }) { s ->
                Box(
                    Modifier
                        .size(104.dp, 184.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .clickable { viewing = stories.indexOfFirst { it.userId == s.userId } }
                ) {
                    AsyncImage(s.image, s.author, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(74.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(2.dp)
                    ) {
                        Avatar(s.avatar, s.author, 32)
                    }
                    Text(
                        s.author,
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontFamily = Cairo,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                    )
                }
            }
        }
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            item {
                Column(Modifier.clickable { if (App.loggedIn) adding = true else onNeedLogin() }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF1E222B)).border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape), contentAlignment = Alignment.Center) {
                        Text("+", color = MaterialTheme.colorScheme.secondary, fontSize = 28.sp)
                    }
                    Text("قصتك", color = Color.White, fontSize = 11.sp)
                }
            }
            items(stories.distinctBy { it.userId }) { s ->
                Column(Modifier.clickable { viewing = stories.indexOfFirst { it.userId == s.userId } }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp)) { Avatar(s.avatar, s.author, 58) }
                    Text(s.author.take(10), color = Color.White, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
    viewing?.let { start ->
        var i by remember { mutableIntStateOf(start) }
        val prog = remember(i) { Animatable(0f) }
        var paused by remember { mutableStateOf(false) }
        LaunchedEffect(i, paused) {
            if (!paused) { prog.animateTo(1f, tween(((1f - prog.value) * 5000).toInt().coerceAtLeast(1), easing = LinearEasing)); if (i < stories.lastIndex) i++ else viewing = null }
        }
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(i) {
                detectTapGestures(onPress = { paused = true; tryAwaitRelease(); paused = false },
                    onTap = { o -> if (o.x < size.width / 2) { if (i < stories.lastIndex) i++ else viewing = null } else { if (i > 0) i-- } })
            }) {
                val s = stories[i]
                AsyncImage(s.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp).align(Alignment.TopCenter), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    stories.indices.forEach { k ->
                        LinearProgressIndicator(progress = { if (k < i) 1f else if (k == i) prog.value else 0f }, modifier = Modifier.weight(1f).height(3.dp).clip(CircleShape),
                            color = Color.White, trackColor = Color(0x44FFFFFF))
                    }
                }
                Row(Modifier.statusBarsPadding().padding(top = 30.dp, start = 12.dp, end = 12.dp).align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(s.avatar, s.author, 36); Spacer(Modifier.width(8.dp)); Text(s.author, color = Color.White, fontFamily = Cairo)
                }
                if (s.caption.isNotBlank()) Text(s.caption, color = Color.White, fontSize = 16.sp, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp))
            }
        }
    }
    if (adding) ModalBottomSheet(sheetState = fullSheet(), onDismissRequest = { adding = false }, containerColor = Color(0xFF141A24)) {
        var img by remember { mutableStateOf<Uri?>(null) }
        var cap by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) img = it }
        Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("إضافة قصة", color = Color.White, fontSize = 18.sp, fontFamily = Cairo)
            OutlinedButton(onClick = { pick.launch("image/*") }) { Text(if (img == null) "اختر صورة" else "تم اختيار الصورة ✓") }
            OutlinedTextField(cap, { cap = it }, Modifier.fillMaxWidth(), label = { Text("تعليق (اختياري)") })
            Button(enabled = img != null && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true
                scope.launch {
                    val url = uploadImageToCloud(ctx, img!!)
                    if (url != null) {
                        val exp = java.time.Instant.now().plusSeconds(86400).toString()
                        val b = JSONObject().put("user_id", App.userId).put("media_url", url).put("media_type", "image").put("caption", cap.trim()).put("expires_at", exp)
                        api("POST", "/rest/v1/stories", b.toString())
                        stories = loadStories(); adding = false
                    }
                    busy = false
                }
            }) { Text(if (busy) "..." else "نشر القصة") }
        }
    }
}

/* ---------- suggestions board ---------- */
data class Suggestion(val id: String, val text: String)

@Composable
fun SuggestionsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Suggestion>?>(null) }
    var text by remember { mutableStateOf("") }
    suspend fun load() {
        val (c, t) = api("GET", "/rest/v1/suggestions?select=id,content&order=created_at.desc&limit=50")
        if (c == 200) try { val a = JSONArray(t); list = (0 until a.length()).map { Suggestion(a.getJSONObject(it).getString("id"), a.getJSONObject(it).optString("content")) } } catch (_: Exception) {}
    }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("لوحة الاقتراحات", color = Color.White, fontSize = 22.sp, fontFamily = Cairo, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("اكتب فكرتك أو اقتراحك") }, minLines = 2)
        Button(enabled = text.isNotBlank(), onClick = {
            val t = text.trim(); text = ""
            scope.launch {
                val b = JSONObject().put("content", t); App.userId?.let { b.put("user_id", it) }
                api("POST", "/rest/v1/suggestions", b.toString()); load()
            }
        }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("نشر الاقتراح") }
        val l = list
        if (l == null) CircularProgressIndicator()
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 110.dp)) {
            items(l) { s -> Text(s.text, color = Color.White, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF141A24)).padding(12.dp)) }
        }
    }
}
