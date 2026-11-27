package com.mawjalbarq.app

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

enum class ClipUiStyle(val label: String) {
    CLASSIC("النمط الافتراضي"),
    REELS("نمط ريلز"),
    TIKTOK("نمط تيك توك"),
    X_PILL("نمط الشريط");

    fun next(): ClipUiStyle {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

object ClipStylePrefs {
    private const val PREF = "clip_ui_prefs"
    private const val KEY = "clip_ui_style"

    fun load(ctx: Context): ClipUiStyle {
        val name = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
        return ClipUiStyle.entries.firstOrNull { it.name == name } ?: ClipUiStyle.CLASSIC
    }

    fun save(ctx: Context, style: ClipUiStyle) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, style.name).apply()
    }
}

@Composable
fun BoxScope.ReelsClipOverlay(
    v: Video,
    liked: Boolean,
    saved: Boolean,
    following: Boolean,
    tightBottom: Boolean = false,
    extraAction: (@Composable () -> Unit)? = null,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    onFollow: () -> Unit,
    onCreator: () -> Unit
) {
    var descOpen by remember(v.id) { mutableStateOf(false) }
    val bPad = if (tightBottom) 8.dp else 16.dp

    // Left (End in RTL) vertical column with crisp outline icons & counts
    Column(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 10.dp, bottom = bPad),
        verticalArrangement = Arrangement.spacedBy(11.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CleanStackIcon(
            icon = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label = formatCount(v.likes.toLong()),
            tint = if (liked) Color(0xFFFF2D55) else Color.White,
            iconSize = 29,
            modifier = Modifier.testTag("clip_like_button"),
            onClick = onLike
        )
        CleanStackIcon(
            icon = Icons.Outlined.ChatBubbleOutline,
            label = formatCount(v.comments.toLong()),
            tint = Color.White,
            iconSize = 27,
            modifier = Modifier.testTag("clip_comment_button"),
            onClick = onComment
        )
        CleanStackIcon(
            icon = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            label = "حفظ",
            tint = if (saved) Color(0xFFFFD21F) else Color.White,
            iconSize = 28,
            modifier = Modifier.testTag("clip_save_button"),
            onClick = onSave
        )
        CleanStackIcon(
            icon = Icons.AutoMirrored.Outlined.Send,
            label = "مشاركة",
            tint = Color.White,
            iconSize = 26,
            modifier = Modifier.testTag("clip_share_button"),
            onClick = onShare
        )
        IconButton(
            onClick = onReport,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0x660B0F17))
                .border(0.75.dp, Color(0x28FFFFFF), CircleShape)
                .testTag("clip_reels_more_button")
        ) {
            Icon(Icons.Filled.MoreHoriz, "المزيد", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        extraAction?.invoke()
    }

    // Bottom-right (Start in RTL): Avatar + handle + outlined follow pill + title
    Column(
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = 14.dp, bottom = bPad, end = 78.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable(onClick = onCreator)) { Avatar(v.avatar, v.author, 36) }
            Spacer(Modifier.width(8.dp))
            Text(
                v.author,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Cairo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color(0xAA000000), blurRadius = 6f)),
                modifier = Modifier.clickable(onClick = onCreator).weight(1f, fill = false)
            )
            if (v.verified) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.Verified, "موثق", tint = Color(0xFF3897F0), modifier = Modifier.size(15.dp))
            }
            if (v.userId.isNotBlank() && v.userId != App.userId) {
                Spacer(Modifier.width(8.dp))
                Text(
                    if (following) "تتابعه" else "متابعة",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = Cairo,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (following) Color(0x44FFFFFF) else Color(0x2E000000))
                        .border(1.dp, Color(0xCCFFFFFF), RoundedCornerShape(8.dp))
                        .clickable(onClick = onFollow)
                        .padding(horizontal = 11.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        ExpandableClipTitle(v.title, v.desc, descOpen) { descOpen = !descOpen }
    }
}

@Composable
fun BoxScope.TikTokClipOverlay(
    v: Video,
    liked: Boolean,
    saved: Boolean,
    following: Boolean,
    isPlaying: Boolean,
    tightBottom: Boolean = false,
    extraAction: (@Composable () -> Unit)? = null,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onFollow: () -> Unit,
    onCreator: () -> Unit
) {
    var descOpen by remember(v.id) { mutableStateOf(false) }
    val bPad = if (tightBottom) 8.dp else 14.dp

    val discTransition = rememberInfiniteTransition(label = "disc")
    val discAngle by discTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6500, easing = LinearEasing), RepeatMode.Restart),
        label = "discAngle"
    )

    // Left (End in RTL) stack: Avatar + '+' badge, filled white icons, spinning vinyl disc, optional settings at end
    Column(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 10.dp, bottom = bPad),
        verticalArrangement = Arrangement.spacedBy(11.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(48.dp, 54.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
                    .clickable(onClick = onCreator)
            ) {
                Avatar(v.avatar, v.author, 44)
            }
            if (v.userId.isNotBlank() && v.userId != App.userId && !following) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFE2C55))
                        .border(1.dp, Color.White, CircleShape)
                        .clickable(onClick = onFollow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, "متابعة", tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }

        CleanStackIcon(
            icon = Icons.Filled.Favorite,
            label = formatCount(v.likes.toLong()),
            tint = if (liked) Color(0xFFFE2C55) else Color.White,
            iconSize = 31,
            modifier = Modifier.testTag("clip_like_button"),
            onClick = onLike
        )
        CleanStackIcon(
            icon = Icons.Filled.ChatBubble,
            label = formatCount(v.comments.toLong()),
            tint = Color.White,
            iconSize = 28,
            modifier = Modifier.testTag("clip_comment_button"),
            onClick = onComment
        )
        CleanStackIcon(
            icon = Icons.Filled.Bookmark,
            label = "حفظ",
            tint = if (saved) Color(0xFFFFD21F) else Color.White,
            iconSize = 29,
            modifier = Modifier.testTag("clip_save_button"),
            onClick = onSave
        )
        CleanStackIcon(
            icon = Icons.AutoMirrored.Filled.Reply,
            label = "مشاركة",
            tint = Color.White,
            iconSize = 30,
            modifier = Modifier.testTag("clip_share_button"),
            onClick = onShare
        )

        // Spinning disc
        Box(
            Modifier
                .size(40.dp)
                .rotate(if (isPlaying) discAngle else 0f)
                .clip(CircleShape)
                .background(Color(0xFF1A1F29))
                .border(5.dp, Color(0xFF2B3240), CircleShape)
                .clickable(onClick = onCreator),
            contentAlignment = Alignment.Center
        ) {
            Avatar(v.avatar, v.author, 20)
        }
        extraAction?.invoke()
    }

    // Bottom-right (Start in RTL): Bold name + title
    Column(
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = 14.dp, bottom = bPad, end = 80.dp)
    ) {
        Row(
            Modifier.clickable(onClick = onCreator),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                v.author,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Cairo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color(0xAA000000), blurRadius = 6f)),
                modifier = Modifier.weight(1f, fill = false)
            )
            if (v.verified) {
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Filled.Verified, "موثق", tint = Color(0xFF20D5EC), modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        ExpandableClipTitle(v.title, v.desc, descOpen) { descOpen = !descOpen }
    }
}

@Composable
fun BoxScope.XPillClipOverlay(
    v: Video,
    liked: Boolean,
    saved: Boolean,
    following: Boolean,
    isPlaying: Boolean,
    posMs: Long,
    durMs: Long,
    speed: Float,
    muted: Boolean,
    fillZoom: Boolean,
    extraAction: (@Composable () -> Unit)? = null,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    onFollow: () -> Unit,
    onCreator: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleZoom: () -> Unit
) {
    var descOpen by remember(v.id) { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFrac by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.clickable(onClick = onCreator)) { Avatar(v.avatar, v.author, 38) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable(onClick = onCreator)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            v.author,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Cairo,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (v.verified) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.Verified, "موثق", tint = Color(0xFF1DA1F2), modifier = Modifier.size(15.dp))
                        }
                    }
                    Text(
                        pidOf(v.userId),
                        color = Color(0xFFC2CAD6),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (v.userId.isNotBlank() && v.userId != App.userId) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (following) "تتابعه" else "تابع",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Cairo,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (following) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else Color(0xB3222834))
                            .border(0.75.dp, Color(0x38FFFFFF), RoundedCornerShape(50))
                            .clickable(onClick = onFollow)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            ExpandableClipTitle(v.title, v.desc, descOpen) { descOpen = !descOpen }
            Spacer(Modifier.height(10.dp))

            // Horizontal row of translucent pill chips (like Image 3)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                XPillActionChip(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = formatCount(v.comments.toLong()),
                    tint = Color.White,
                    modifier = Modifier.weight(1f).testTag("clip_comment_button"),
                    onClick = onComment
                )
                XPillActionChip(
                    icon = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    label = formatCount(v.likes.toLong()),
                    tint = if (liked) Color(0xFFFF2D55) else Color.White,
                    modifier = Modifier.weight(1f).testTag("clip_like_button"),
                    onClick = onLike
                )
                XPillActionChip(
                    icon = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    label = "",
                    tint = if (saved) Color(0xFFFFD21F) else Color.White,
                    modifier = Modifier.testTag("clip_save_button"),
                    onClick = onSave
                )
                XPillActionChip(
                    icon = Icons.Outlined.Share,
                    label = "",
                    tint = Color.White,
                    modifier = Modifier.testTag("clip_share_button"),
                    onClick = onShare
                )
                XPillActionChip(
                    icon = Icons.Filled.Flag,
                    label = "",
                    tint = Color(0xFFD0D6E0),
                    modifier = Modifier.testTag("clip_x_report_button"),
                    onClick = onReport
                )
                extraAction?.invoke()
            }
        }

        Spacer(Modifier.height(10.dp))

        // Compact dark control bar at the bottom (matching Image 3)
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xEB0B0F17))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                val frac = if (scrubbing) scrubFrac else if (durMs > 0L) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
                Slider(
                    value = frac,
                    onValueChange = { scrubbing = true; scrubFrac = it },
                    onValueChangeFinished = {
                        if (durMs > 0L) onSeek((scrubFrac * durMs).toLong().coerceIn(0L, durMs))
                        scrubbing = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color(0x33FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth().height(18.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onPlayPause, modifier = Modifier.minimumInteractiveComponentSize().size(40.dp)) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "تشغيل/إيقاف", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                val remMs = if (durMs > 0L) (durMs - (if (scrubbing) (scrubFrac * durMs).toLong() else posMs)).coerceAtLeast(0L) else 0L
                Text(
                    if (durMs > 0L) "-${formatDurationMs(remMs)}" else "",
                    color = Color(0xFFD8DEE9),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${if (speed == 1f) "1" else speed}X",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCycleSpeed)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                IconButton(onClick = onToggleMute, modifier = Modifier.minimumInteractiveComponentSize().size(40.dp)) {
                    Icon(if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, "الصوت", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onToggleZoom, modifier = Modifier.minimumInteractiveComponentSize().size(40.dp)) {
                    Icon(if (fillZoom) Icons.Filled.FitScreen else Icons.Filled.Fullscreen, "الأبعاد", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ExpandableClipTitle(title: String, desc: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp)
            .testTag("clip_title_toggle"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 13.sp,
            fontFamily = Cairo,
            maxLines = if (open) 4 else 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = Shadow(Color(0xAA000000), blurRadius = 5f)),
            modifier = Modifier.weight(1f, fill = false)
        )
        if (desc.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            Icon(
                if (open) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = "عرض الوصف",
                tint = Color(0xFFDDDDDD),
                modifier = Modifier.size(16.dp)
            )
        }
    }
    AnimatedVisibility(visible = open && desc.isNotBlank()) {
        Text(
            desc,
            color = Color(0xFFE0E4EC),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x990E131B))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun CleanStackIcon(
    icon: ImageVector,
    label: String,
    tint: Color,
    iconSize: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val sc = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                scope.launch { sc.animateTo(1.28f, tween(105)); sc.animateTo(1f, spring()) }
                onClick()
            }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0x660B0F17))
                .border(0.75.dp, Color(0x28FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size((iconSize - 6).coerceAtLeast(21).dp).scale(sc.value))
        }
        if (label.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Cairo,
                style = TextStyle(shadow = Shadow(Color(0xCC000000), blurRadius = 6f))
            )
        }
    }
}

@Composable
private fun XPillActionChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val sc = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Row(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0x9E1E242E))
            .border(0.75.dp, Color(0x2EFFFFFF), RoundedCornerShape(50))
            .clickable {
                scope.launch { sc.animateTo(1.22f, tween(100)); sc.animateTo(1f, spring()) }
                onClick()
            }
            .padding(horizontal = if (label.isEmpty()) 11.dp else 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label.ifEmpty { null }, tint = tint, modifier = Modifier.size(18.dp).scale(sc.value))
        if (label.isNotEmpty()) {
            Spacer(Modifier.width(5.dp))
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
