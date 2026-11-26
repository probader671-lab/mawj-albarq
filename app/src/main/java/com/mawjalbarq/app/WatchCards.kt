package com.mawjalbarq.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

val WatchBg: Color get() = if (Pal.isDark) Color(0xFF0B1720) else Color(0xFFF2F4F8)
val WatchCardBg: Color get() = if (Pal.isDark) Color(0xFF101C26) else Color(0xFFFFFFFF)
val WatchCardBorder: Color get() = if (Pal.isDark) Color(0x16FFFFFF) else Color(0x1A000000)
val WatchPillBg: Color get() = if (Pal.isDark) Color(0x14FFFFFF) else Color(0x0F000000)
val WatchTextMain: Color get() = if (Pal.isDark) Color(0xFFE6EEF8) else Color(0xFF111827)
val WatchTextSec: Color get() = if (Pal.isDark) Color(0xFFAEB6BD) else Color(0xFF5B6577)
val VerifiedBlue = Color(0xFF1DA1F2)
val ProgressThumbGreen = Color(0xFF4CAF50)

@Composable
fun VerifiedBadge(sizeDp: Int = 15) {
    Icon(
        Icons.Filled.Verified,
        contentDescription = "موثق",
        tint = VerifiedBlue,
        modifier = Modifier.size(sizeDp.dp)
    )
}

@Composable
fun WatchActionPill(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    activeBg: Color = MaterialTheme.colorScheme.primary,
    tint: Color = WatchTextMain,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (active) activeBg else WatchPillBg
    val fg = if (active) Color.White else tint
    Box(
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(25.dp))
                .background(bg)
                .border(0.75.dp, if (active) activeBg else WatchCardBorder, RoundedCornerShape(25.dp))
                .clickable(onClick = onClick)
                .padding(
                    horizontal = if (compact) 10.dp else 13.dp,
                    vertical = if (compact) 6.dp else 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            Icon(icon, contentDescription = label.ifBlank { null }, tint = fg, modifier = Modifier.size(if (compact) 15.dp else 17.dp))
            if (label.isNotEmpty()) {
                Text(
                    label,
                    color = if (active) Color.White else WatchTextMain,
                    fontSize = if (compact) 11.5.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = Cairo,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun WatchJoinButton(
    following: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(25.dp))
                .background(if (following) Color(0xFF1A2937) else MaterialTheme.colorScheme.primary)
                .border(
                    width = 0.75.dp,
                    color = if (following) Color(0x28FFFFFF) else MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(25.dp)
                )
                .clickable(onClick = onClick)
                .padding(
                    horizontal = if (compact) 12.dp else 16.dp,
                    vertical = if (compact) 5.dp else 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            Icon(
                if (following) Icons.Filled.Check else Icons.Filled.PersonAdd,
                contentDescription = if (following) "تم الإنضمام" else "إنضمام",
                tint = if (following) Color(0xFF4CAF50) else Color.White,
                modifier = Modifier.size(if (compact) 13.dp else 16.dp)
            )
            Text(
                if (following) "تم الإنضمام" else "إنضمام",
                color = Color.White,
                fontSize = if (compact) 11.5.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Cairo,
                maxLines = 1
            )
        }
    }
}

@Composable
fun WatchRailSection(
    title: String = "فيديوهات قد تعجبك",
    videos: List<Video>,
    onSelect: (Video) -> Unit
) {
    if (videos.isEmpty()) return
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(WatchCardBg)
            .border(1.dp, WatchCardBorder, RoundedCornerShape(20.dp))
            .padding(top = 12.dp, bottom = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(
                    Icons.Filled.StayCurrentLandscape,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    title,
                    color = WatchTextMain,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Cairo
                )
            }
            Text(
                "${videos.size} اقتراحات",
                color = WatchTextSec,
                fontSize = 11.5.sp,
                fontFamily = Cairo
            )
        }

        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth()) {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(videos, key = { "rail_${it.id}" }) { v ->
                    Column(
                        Modifier
                            .width(204.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0A141D))
                            .border(0.75.dp, WatchCardBorder, RoundedCornerShape(14.dp))
                            .clickable { onSelect(v) }
                    ) {
                        Box(Modifier.fillMaxWidth().height(114.dp).background(Color(0xFF070F16))) {
                            VideoThumb(v, Modifier.fillMaxSize(), ContentScale.Crop)
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xBB000000))))
                            )
                            if (v.duration > 0) {
                                Text(
                                    formatDuration(v.duration),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xCC000000))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x990B1720))
                                    .border(1.dp, Color(0x66FFFFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(19.dp))
                            }
                        }
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                v.title,
                                color = WatchTextMain,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 17.sp,
                                fontFamily = Cairo,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(5.dp))
                            val meta = buildList {
                                add(v.author)
                                add("${formatCount(v.views)} مشاهدة")
                            }.joinToString(" • ")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (v.verified) VerifiedBadge(12)
                                Text(meta, color = WatchTextSec, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            if (videos.size > 1) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp)
                        .minimumInteractiveComponentSize()
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xE6FFFFFF))
                        .border(1.dp, Color(0x33000000), CircleShape)
                        .clickable {
                            val next = (rowState.firstVisibleItemIndex + 1).coerceAtMost(videos.lastIndex)
                            scope.launch { rowState.animateScrollToItem(next) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "التالي",
                        tint = Color(0xFF0B1720),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WatchSuggestionRowCard(
    v: Video,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(WatchCardBg)
            .border(1.dp, WatchCardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(152.dp, 88.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF070F16))
        ) {
            if (!v.portrait) {
                VideoThumb(v, Modifier.fillMaxSize(), ContentScale.Crop)
            } else {
                VideoThumb(v, Modifier.fillMaxSize().alpha(0.32f), ContentScale.Crop)
                VideoThumb(v, Modifier.fillMaxSize(), ContentScale.Fit)
            }
            if (!v.portrait) {
                Icon(
                    Icons.Filled.StayCurrentLandscape,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x99000000))
                        .padding(3.dp)
                        .size(12.dp)
                )
            }
            if (v.duration > 0) {
                Text(
                    formatDuration(v.duration),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                v.title,
                color = WatchTextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.sp,
                fontFamily = Cairo,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(v.author, color = WatchTextSec, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (v.verified) VerifiedBadge(13)
            }
            val dateText = formatVideoDate(v.createdAt)
            val meta = buildList {
                add("${formatCount(v.views)} مشاهدة")
                if (dateText.isNotBlank()) add(dateText)
            }.joinToString(" • ")
            Text(meta, color = WatchTextSec, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
