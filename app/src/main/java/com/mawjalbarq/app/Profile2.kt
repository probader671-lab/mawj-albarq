package com.mawjalbarq.app
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ProfileData(val id: String, val name: String, val bio: String, val avatar: String?, val videos: List<Video>, val followers: Int = 0, val following: Int = 0, val publicId: String = "", val banner: String? = null)

/** Disk copy of the last loaded profile, so the account page paints at once and refreshes in the background. */
object ProfileCache {
    private var dir: java.io.File? = null
    fun init(ctx: android.content.Context) { dir = ctx.applicationContext.filesDir }
    private fun f(id: String) = dir?.let { java.io.File(it, "profile_" + id.filter { c -> c.isLetterOrDigit() || c == '-' } + ".json") }
    fun save(id: String, prof: String, vids: String) { try { f(id)?.writeText(JSONObject().put("p", prof).put("v", vids).toString()) } catch (_: Exception) {} }
    fun load(id: String): ProfileData? = try {
        val file = f(id); if (file == null || !file.exists()) null else {
            val j = JSONObject(file.readText()); parseProfile(id, j.getString("p"), j.optString("v"))
        }
    } catch (_: Exception) { null }
    fun clear() { try { dir?.listFiles { x -> x.name.startsWith("profile_") }?.forEach { it.delete() } } catch (_: Exception) {} }
}

suspend fun loadProfile(id: String): ProfileData? {
    val (c, t) = api("GET", "/rest/v1/profiles?id=eq.$id&select=id,full_name,bio,avatar_url,banner_url,verified,followers_count,following_count,public_id")
    if (c != 200) return null
    val (c2, t2) = api("GET", "/rest/v1/videos?user_id=eq.$id&status=eq.ready&deleted=eq.false&select=id,width,height,duration,views,created_at,title,description,video_url,thumbnail_url,likes,comments_count&order=created_at.desc,id.desc&limit=60")
    val r = parseProfile(id, t, if (c2 == 200) t2 else "") ?: return null
    if (c2 == 200) ProfileCache.save(id, t, t2)
    return r
}

fun parseProfile(id: String, t: String, t2: String): ProfileData? {
    val o = try { JSONArray(t).optJSONObject(0) } catch (e: Exception) { null } ?: return null
    val c2 = if (t2.isBlank()) 0 else 200
    val vids = ArrayList<Video>()
    if (c2 == 200) try { val a = JSONArray(t2); for (i in 0 until a.length()) { val v = a.getJSONObject(i)
        vids.add(Video(v.getString("id"), v.optString("title"), if (v.isNull("description")) "" else v.optString("description"), v.getString("video_url"),
            if (v.isNull("thumbnail_url")) null else v.optString("thumbnail_url"), o.optString("full_name"), v.optInt("likes"), v.optInt("comments_count"), id,
            v.optInt("width"), v.optInt("height"), if (o.isNull("avatar_url")) null else o.optString("avatar_url"),
            v.optDouble("duration", 0.0).toInt().coerceAtLeast(0), v.optLong("views", 0L).coerceAtLeast(0L),
            v.optString("created_at"), o.optBoolean("verified", false), o.optLong("followers_count", 0L).coerceAtLeast(0L))) } } catch (_: Exception) {}
    return ProfileData(id, o.optString("full_name").ifBlank { "مستخدم" }, if (o.isNull("bio")) "" else o.optString("bio"),
        if (o.isNull("avatar_url")) null else o.optString("avatar_url"), vids, o.optInt("followers_count", 0).coerceAtLeast(0), o.optInt("following_count", 0).coerceAtLeast(0), o.optString("public_id"),
        if (o.isNull("banner_url") || o.optString("banner_url").isBlank()) null else o.optString("banner_url"))
}

@Composable
fun Avatar(url: String?, name: String, size: Int = 72) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.take(1), color = Color.Black, fontSize = (size / 2).sp, fontFamily = Cairo)
    }
}

@Composable
fun ProfileHeader(p: ProfileData, onPlay: (String) -> Unit, isMe: Boolean = false, big: Boolean = false, onUpload: (() -> Unit)? = null, extra: @Composable () -> Unit = {}) {
    val bg = MaterialTheme.colorScheme.background
    var showEnlargedBanner by remember { mutableStateOf(false) }
    if (showEnlargedBanner) {
        EnlargedBannerDialog(
            bannerValue = p.banner,
            userId = p.id,
            ownerName = p.name,
            onDismiss = { showEnlargedBanner = false }
        )
    }
    Column(Modifier.fillMaxWidth()) {
        AccountBannerView(
            bannerValue = p.banner,
            userId = p.id,
            showHint = true,
            onLongPress = { showEnlargedBanner = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(if (big) 172.dp else 148.dp)
                .clip(RoundedCornerShape(18.dp))
                .testTag("profile_banner")
        )
        Row(Modifier.fillMaxWidth().offset(y = (-47).dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.Bottom) {
            Box(
                Modifier
                    .size(94.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .border(3.dp, bg, CircleShape)
            ) { Avatar(p.avatar, p.name, 94) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(top = 52.dp)) {
                Text(p.name, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Cairo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (p.publicId.isNotBlank()) Text("ID: " + p.publicId, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, maxLines = 1)
            }
        }
        Column(Modifier.offset(y = (-38).dp).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (p.bio.isNotBlank()) Text(p.bio, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Tajawal, fontSize = 14.sp)
            extra()
        }
        Row(Modifier.offset(y = (-24).dp).fillMaxWidth().padding(horizontal = 2.dp).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer).padding(vertical = 12.dp)) {
            listOf("المتابعون" to p.followers, "المتابَعون" to p.following, "الإعجابات" to p.videos.sumOf { it.likes }, "المقاطع" to p.videos.size).forEach { (l, n) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatCount(n.toLong()), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = Cairo)
                    Text(l, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Tajawal, fontSize = 12.sp)
                }
            }
        }
        val portraitVideos = remember(p.videos) { p.videos.filter { it.portrait } }
        val landscapeVideos = remember(p.videos) { p.videos.filter { !it.portrait } }
        val savedVideos = Feed.videos.filter { Feed.saved[it.id] == true }

        val haptic = LocalHapticFeedback.current
        var tab by remember { mutableIntStateOf(0) }
        val tabs = if (isMe) {
            listOf(
                "الطولية (${portraitVideos.size})",
                "الأفقية (${landscapeVideos.size})",
                "المحفوظات (${savedVideos.size})"
            )
        } else {
            listOf(
                "الطولية (${portraitVideos.size})",
                "الأفقية (${landscapeVideos.size})"
            )
        }
        val safeTab = tab.coerceIn(0, tabs.lastIndex)
        TabRow(
            selectedTabIndex = safeTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.offset(y = (-12).dp)
        ) {
            tabs.forEachIndexed { i, t ->
                Tab(
                    selected = safeTab == i,
                    onClick = {
                        if (tab != i) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tab = i
                    },
                    modifier = Modifier.testTag("profile_tab_$i"),
                    text = {
                        Text(
                            t,
                            fontFamily = Cairo,
                            fontSize = 13.sp,
                            fontWeight = if (safeTab == i) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        AnimatedContent(
            targetState = safeTab,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(130)) },
            label = "profileTabTransition"
        ) { activeTab ->
        when (activeTab) {
            0 -> {
                if (portraitVideos.isEmpty()) {
                    ProfileEmptyVideos(
                        message = "لا توجد مقاطع طولية بعد",
                        icon = Icons.Filled.StayCurrentPortrait,
                        actionLabel = if (isMe && onUpload != null) "ارفع أول مقطع" else null,
                        onAction = onUpload
                    )
                } else {
                    ProfilePortraitGrid(portraitVideos, onPlay)
                }
            }
            1 -> {
                if (landscapeVideos.isEmpty()) {
                    ProfileEmptyVideos(
                        message = "لا توجد فيديوهات أفقية بعد",
                        icon = Icons.Filled.StayCurrentLandscape,
                        actionLabel = if (isMe && onUpload != null) "ارفع فيديو أفقي" else null,
                        onAction = onUpload
                    )
                } else {
                    ProfileLandscapeList(landscapeVideos, onPlay)
                }
            }
            else -> {
                if (savedVideos.isEmpty()) {
                    ProfileEmptyVideos(
                        message = "لا توجد محفوظات بعد",
                        icon = Icons.Filled.BookmarkBorder
                    )
                } else {
                    val savedLandscape = savedVideos.filter { !it.portrait }
                    val savedPortrait = savedVideos.filter { it.portrait }
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (savedLandscape.isNotEmpty()) {
                            Row(
                                Modifier.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.StayCurrentLandscape, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text("فيديوهات أفقية (${savedLandscape.size})", color = Ink, fontFamily = Cairo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            ProfileLandscapeList(savedLandscape, onPlay)
                        }
                        if (savedPortrait.isNotEmpty()) {
                            Row(
                                Modifier.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.StayCurrentPortrait, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text("مقاطع طولية (${savedPortrait.size})", color = Ink, fontFamily = Cairo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            ProfilePortraitGrid(savedPortrait, onPlay)
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ProfileEmptyVideos(
    message: String,
    icon: ImageVector = Icons.Filled.VideoLibrary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Cairo, fontSize = 14.sp)
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(onClick = onAction, shape = RoundedCornerShape(50)) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(actionLabel, fontFamily = Cairo, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ProfileLandscapeCard(v: Video, onPlay: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable { onPlay(v.id) }
            .testTag("profile_landscape_card_${v.id}")
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            VideoThumb(v, Modifier.fillMaxSize(), ContentScale.Crop)
            if (v.duration > 0) {
                Text(
                    formatDuration(v.duration),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                v.title.ifBlank { "فيديو" },
                color = Ink,
                fontFamily = Cairo,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildList {
                add("${formatCount(v.views)} مشاهدة")
                if (v.likes > 0) add("${formatCount(v.likes.toLong())} إعجاب")
                val d = formatVideoDate(v.createdAt)
                if (d.isNotBlank()) add(d)
            }.joinToString(" • ")
            Text(
                meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = Tajawal,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileLandscapeList(videos: List<Video>, onPlay: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        val cols = if (maxWidth >= 600.dp) 2 else 1
        if (cols == 1) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                videos.forEach { v -> ProfileLandscapeCard(v, onPlay, Modifier.fillMaxWidth()) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                videos.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { v -> ProfileLandscapeCard(v, onPlay, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilePortraitGrid(videos: List<Video>, onPlay: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        val cols = if (maxWidth >= 600.dp) 5 else 3
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            videos.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { v ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(9f / 15f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onPlay(v.id) }
                            .testTag("profile_portrait_card_${v.id}")
                    ) {
                        VideoThumb(v, Modifier.fillMaxSize(), ContentScale.Crop)
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        )
                        if (v.duration > 0) {
                            Text(
                                formatDuration(v.duration),
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xB3000000))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            v.title,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = Cairo,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                        )
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
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
    ModalBottomSheet(sheetState = fullSheet(), onDismissRequest = onClose, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp).navigationBarsPadding()) {
            val d = p
            if (d == null) ProfileSkeleton() else ProfileHeader(d, onPlay, big = true) {
                if (id != App.userId) {
                    val sc = rememberCoroutineScope(); val f = Feed.following[id] == true
                    Button(onClick = { if (App.loggedIn) sc.launch { Feed.toggleFollow(id) } }, enabled = App.loggedIn, shape = RoundedCornerShape(50)) { Text(if (f) "تتابعه" else "متابعة") }
                }
            }
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
    var banner by remember { mutableStateOf(cur.banner) }
    var busy by remember { mutableStateOf(false) }
    var previewEnlarged by remember { mutableStateOf(false) }

    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) { busy = true; scope.launch { uploadImageToCloud(ctx, u)?.let { avatar = it }; busy = false } }
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) { busy = true; scope.launch { uploadImageToCloud(ctx, u)?.let { banner = it }; busy = false } }
    }

    if (previewEnlarged) {
        EnlargedBannerDialog(
            bannerValue = banner,
            userId = cur.id,
            ownerName = name.ifBlank { cur.name },
            onDismiss = { previewEnlarged = false }
        )
    }

    ModalBottomSheet(sheetState = fullSheet(), onDismissRequest = { onDone(false) }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("تعديل الملف الشخصي", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Cairo)

            // Live Animated / Custom Banner Preview (long-press to enlarge)
            AccountBannerView(
                bannerValue = banner,
                userId = cur.id,
                showHint = true,
                onLongPress = { previewEnlarged = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .testTag("edit_banner_preview")
            )

            val resolvedBanner = BannerStore.resolve(banner, cur.id)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "البانرات المتحركة لحسابك",
                    color = Ink,
                    fontFamily = Cairo,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    BannerStore.labelFor(banner, cur.id),
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = Tajawal,
                    fontSize = 12.sp
                )
            }

            LazyRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(BannerPreset.entries) { preset ->
                    val selected = resolvedBanner is ResolvedBanner.Preset && resolvedBanner.preset == preset
                    Box(
                        Modifier
                            .width(116.dp)
                            .height(66.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(preset.colors))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable(enabled = !busy) { banner = BannerStore.presetValue(preset) }
                            .padding(8.dp)
                            .testTag("banner_preset_${preset.id}")
                    ) {
                        Icon(
                            preset.icon,
                            contentDescription = preset.title,
                            tint = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.TopStart)
                        )
                        if (selected) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "محدد",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                            )
                        }
                        Text(
                            preset.title,
                            color = Color.White,
                            fontFamily = Cairo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart)
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        pickBanner.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("upload_custom_banner_button")
                ) {
                    Icon(Icons.Filled.Image, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("رفع صورة بانر", fontFamily = Cairo, fontSize = 12.5.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { banner = null },
                    enabled = !busy && banner != null,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("reset_default_banner_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("افتراضي", fontFamily = Cairo, fontSize = 12.5.sp)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            Avatar(avatar, name, 84)
            OutlinedButton(
                onClick = {
                    pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("change_avatar_button")
            ) {
                Text("تغيير الصورة الشخصية", fontFamily = Cairo)
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().testTag("edit_profile_name_input"),
                label = { Text("الاسم", fontFamily = Cairo) },
                singleLine = true
            )
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                modifier = Modifier.fillMaxWidth().testTag("edit_profile_bio_input"),
                label = { Text("الوصف الشخصي", fontFamily = Cairo) },
                minLines = 2
            )
            Button(
                enabled = !busy && name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("save_profile_button"),
                onClick = {
                    busy = true
                    scope.launch {
                        val b = JSONObject()
                            .put("full_name", name.trim())
                            .put("bio", bio.trim())
                            .put("banner_url", banner ?: "")
                        if (avatar != null) b.put("avatar_url", avatar)
                        val (c, _) = api("PATCH", "/rest/v1/profiles?id=eq.${App.userId}", b.toString())
                        if (c in 200..299) {
                            App.name = name.trim()
                            if (avatar != null) App.avatar = avatar
                            App.save()
                        }
                        busy = false
                        onDone(c in 200..299)
                    }
                }
            ) { Text(if (busy) "جاري الحفظ..." else "حفظ التعديلات", fontFamily = Cairo) }
        }
    }
}
