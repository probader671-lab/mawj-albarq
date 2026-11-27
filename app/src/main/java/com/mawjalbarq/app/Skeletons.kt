package com.mawjalbarq.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerShift"
    )
    val colors = if (Pal.isDark) {
        listOf(Color(0xFF1A2230), Color(0xFF2B374C), Color(0xFF1A2230))
    } else {
        listOf(Color(0xFFE3E8F0), Color(0xFFF1F5F9), Color(0xFFE3E8F0))
    }
    return Brush.linearGradient(
        colors = colors,
        start = Offset(shift - 350f, shift - 350f),
        end = Offset(shift, shift)
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    brush: Brush = rememberShimmerBrush(),
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier.clip(shape).background(brush),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun PostSkeletonItem(separatorColor: Color = Color(0xFF070A0F)) {
    val brush = rememberShimmerBrush()
    val surf = MaterialTheme.colorScheme.surface
    val faint = Color.White.copy(alpha = 0.16f)

    Column(Modifier.fillMaxWidth().background(surf)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShimmerBox(Modifier.size(42.dp), CircleShape, brush) {
                Icon(Icons.Filled.Person, null, tint = faint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShimmerBox(Modifier.width(136.dp).height(13.dp), RoundedCornerShape(999.dp), brush)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBox(Modifier.width(64.dp).height(10.dp), RoundedCornerShape(999.dp), brush)
                    Icon(Icons.Filled.Public, null, tint = faint, modifier = Modifier.size(12.dp))
                }
            }
            Icon(Icons.Filled.MoreHoriz, null, tint = faint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Icon(Icons.Filled.Close, null, tint = faint, modifier = Modifier.size(18.dp))
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            ShimmerBox(Modifier.fillMaxWidth(0.92f).height(12.dp), RoundedCornerShape(999.dp), brush)
            ShimmerBox(Modifier.fillMaxWidth(0.58f).height(12.dp), RoundedCornerShape(999.dp), brush)
        }

        Spacer(Modifier.height(8.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(215.dp), RoundedCornerShape(0.dp), brush) {
            Icon(Icons.Filled.Image, null, tint = faint, modifier = Modifier.size(46.dp))
        }

        HorizontalDivider(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Icons.Outlined.ThumbUp,
                Icons.Outlined.ModeComment,
                Icons.Outlined.BookmarkBorder,
                Icons.Outlined.Share
            ).forEach { ic ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(ic, null, tint = faint, modifier = Modifier.size(18.dp))
                    ShimmerBox(Modifier.width(34.dp).height(10.dp), RoundedCornerShape(999.dp), brush)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(7.dp).background(separatorColor))
    }
}

@Composable
fun HomeVideoSkeletonCard(ratio: Float = 16f / 9f) {
    val brush = rememberShimmerBrush()
    val faint = Color.White.copy(alpha = 0.2f)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF101C26))
            .border(1.dp, Color(0x16FFFFFF), RoundedCornerShape(20.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(brush)
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0x440B1720))
                    .border(1.dp, faint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = faint, modifier = Modifier.size(28.dp))
            }
            ShimmerBox(
                Modifier.align(Alignment.TopEnd).padding(10.dp).size(54.dp, 22.dp),
                RoundedCornerShape(8.dp),
                brush
            )
            ShimmerBox(
                Modifier.align(Alignment.BottomEnd).padding(10.dp).size(44.dp, 20.dp),
                RoundedCornerShape(6.dp),
                brush
            )
        }

        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ShimmerBox(Modifier.fillMaxWidth(0.84f).height(16.dp), RoundedCornerShape(999.dp), brush)
            ShimmerBox(Modifier.fillMaxWidth(0.45f).height(11.dp), RoundedCornerShape(999.dp), brush)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Icons.Filled.FavoriteBorder, Icons.Outlined.BookmarkBorder, Icons.Outlined.Share, Icons.Filled.Flag).forEach { ic ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(25.dp))
                            .background(Color(0x12FFFFFF))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(ic, null, tint = faint, modifier = Modifier.size(16.dp))
                        ShimmerBox(Modifier.width(28.dp).height(9.dp), RoundedCornerShape(999.dp), brush)
                    }
                }
            }

            HorizontalDivider(thickness = 0.75.dp, color = Color(0x12FFFFFF))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ShimmerBox(Modifier.size(42.dp), CircleShape, brush) {
                    Icon(Icons.Filled.Person, null, tint = faint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    ShimmerBox(Modifier.width(110.dp).height(13.dp), RoundedCornerShape(999.dp), brush)
                    ShimmerBox(Modifier.width(72.dp).height(10.dp), RoundedCornerShape(999.dp), brush)
                }
                ShimmerBox(Modifier.size(82.dp, 34.dp), RoundedCornerShape(25.dp), brush)
            }
        }
    }
}

@Composable
fun HomeSuggestionSkeletonRow() {
    val brush = rememberShimmerBrush()
    val faint = Color.White.copy(alpha = 0.2f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF101C26))
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(14.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(152.dp, 88.dp).clip(RoundedCornerShape(10.dp)).background(brush)) {
            Icon(Icons.Filled.PlayArrow, null, tint = faint, modifier = Modifier.align(Alignment.Center).size(26.dp))
            ShimmerBox(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(36.dp, 16.dp), RoundedCornerShape(4.dp), brush)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ShimmerBox(Modifier.fillMaxWidth(0.92f).height(13.dp), RoundedCornerShape(999.dp), brush)
            ShimmerBox(Modifier.fillMaxWidth(0.65f).height(13.dp), RoundedCornerShape(999.dp), brush)
            ShimmerBox(Modifier.width(88.dp).height(10.dp), RoundedCornerShape(999.dp), brush)
            ShimmerBox(Modifier.width(110.dp).height(9.dp), RoundedCornerShape(999.dp), brush)
        }
    }
}

@Composable
fun StoriesSkeletonItems(tallCards: Boolean) {
    val brush = rememberShimmerBrush()
    val faint = Color.White.copy(alpha = 0.2f)
    if (tallCards) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(4) { idx ->
                Box(
                    Modifier
                        .size(104.dp, 184.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(brush)
                        .border(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                ) {
                    if (idx == 0) {
                        Icon(Icons.Filled.AddCircleOutline, null, tint = faint, modifier = Modifier.align(Alignment.Center).size(30.dp))
                    } else {
                        ShimmerBox(Modifier.align(Alignment.TopStart).padding(8.dp).size(34.dp), CircleShape, brush)
                        ShimmerBox(Modifier.align(Alignment.BottomStart).padding(8.dp).width(68.dp).height(11.dp), RoundedCornerShape(999.dp), brush)
                    }
                }
            }
        }
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            items(5) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBox(Modifier.size(64.dp), CircleShape, brush) {
                        Icon(Icons.Filled.Person, null, tint = faint, modifier = Modifier.size(24.dp))
                    }
                    ShimmerBox(Modifier.width(48.dp).height(9.dp), RoundedCornerShape(999.dp), brush)
                }
            }
        }
    }
}

@Composable
fun ClipStageSkeleton() {
    val brush = rememberShimmerBrush()
    val faint = Color.White.copy(alpha = 0.22f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 4.dp, bottom = 58.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(brush)
        ) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color(0x550E131B))
                        .border(1.dp, faint, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = faint, modifier = Modifier.size(36.dp))
                }
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )
            }

            // Bottom-start author + title skeleton
            Column(
                Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 88.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShimmerBox(Modifier.size(40.dp), CircleShape, brush) {
                        Icon(Icons.Filled.Person, null, tint = faint, modifier = Modifier.size(18.dp))
                    }
                    ShimmerBox(Modifier.width(120.dp).height(14.dp), RoundedCornerShape(999.dp), brush)
                }
                ShimmerBox(Modifier.fillMaxWidth(0.78f).height(14.dp), RoundedCornerShape(999.dp), brush)
                ShimmerBox(Modifier.fillMaxWidth(0.45f).height(11.dp), RoundedCornerShape(999.dp), brush)
            }

            // Bottom-end side actions skeleton
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                listOf<ImageVector>(
                    Icons.Filled.Favorite,
                    Icons.Filled.Comment,
                    Icons.Filled.Bookmark,
                    Icons.Filled.Share
                ).forEach { ic ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0x550E131B))
                                .border(0.75.dp, faint, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(ic, null, tint = faint, modifier = Modifier.size(21.dp))
                        }
                        ShimmerBox(Modifier.width(24.dp).height(8.dp), RoundedCornerShape(999.dp), brush)
                    }
                }
            }
        }
    }
}

/** Shimmering avatar + two text lines, repeated. Replaces spinners on list screens (comments, notifications, suggestions). */
@Composable
fun ListSkeleton(rows: Int = 6, modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ShimmerBox(Modifier.size(40.dp), CircleShape, brush)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShimmerBox(Modifier.fillMaxWidth(0.4f).height(12.dp), RoundedCornerShape(6.dp), brush)
                    ShimmerBox(Modifier.fillMaxWidth(if (it % 2 == 0) 0.85f else 0.65f).height(12.dp), RoundedCornerShape(6.dp), brush)
                }
            }
        }
    }
}

/** Profile placeholder: banner, avatar, name, stat blocks, tabs and video placeholders. */
@Composable
fun ProfileSkeleton() {
    val brush = rememberShimmerBrush()
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShimmerBox(Modifier.fillMaxWidth().height(132.dp), RoundedCornerShape(16.dp), brush)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
            ShimmerBox(Modifier.size(88.dp), CircleShape, brush)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(Modifier.width(150.dp).height(18.dp), RoundedCornerShape(6.dp), brush)
                ShimmerBox(Modifier.width(100.dp).height(12.dp), RoundedCornerShape(6.dp), brush)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { ShimmerBox(Modifier.weight(1f).height(56.dp), RoundedCornerShape(16.dp), brush) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { ShimmerBox(Modifier.weight(1f).height(34.dp), RoundedCornerShape(10.dp), brush) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { ShimmerBox(Modifier.weight(1f).aspectRatio(9f / 15f), RoundedCornerShape(12.dp), brush) }
        }
        ShimmerBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(14.dp), brush)
    }
}
