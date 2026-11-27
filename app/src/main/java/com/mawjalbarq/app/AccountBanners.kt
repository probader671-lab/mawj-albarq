package com.mawjalbarq.app

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

enum class BannerPreset(
    val id: String,
    val assetPath: String,
    val title: String,
    val colors: List<Color>,
    val icon: ImageVector
) {
    FLUID_MAWJ(
        id = "fluid_mawj",
        assetPath = "banners/banner_fluid_mawj.html",
        title = "موج البرق المائي",
        colors = listOf(Color(0xFF020305), Color(0xFF0E2A47), Color(0xFF2EA8FF)),
        icon = Icons.Filled.Waves
    ),
    CYBER_MAWJ(
        id = "cyber_mawj",
        assetPath = "banners/banner_cyber_mawj.html",
        title = "موج البرق الرقمي",
        colors = listOf(Color(0xFF020205), Color(0xFF083344), Color(0xFF00E5FF)),
        icon = Icons.Filled.AutoAwesome
    ),
    SPACE(
        id = "space",
        assetPath = "banners/banner_space.html",
        title = "المجموعة الشمسية",
        colors = listOf(Color(0xFF08090A), Color(0xFF1E1B4B), Color(0xFFFB7209)),
        icon = Icons.Filled.RocketLaunch
    ),
    EARTH(
        id = "earth",
        assetPath = "banners/banner_earth.html",
        title = "كوكب الأرض",
        colors = listOf(Color(0xFF060B19), Color(0xFF1D4ED8), Color(0xFF38BDF8)),
        icon = Icons.Filled.Public
    ),
    RAIN(
        id = "rain",
        assetPath = "banners/banner_rain.html",
        title = "مطر الليل",
        colors = listOf(Color(0xFF080D28), Color(0xFF1E293B), Color(0xFF50AFFF)),
        icon = Icons.Filled.WaterDrop
    ),
    CAR(
        id = "car",
        assetPath = "banners/banner_car.html",
        title = "السيارة السريعة",
        colors = listOf(Color(0xFF019B66), Color(0xFF00C380), Color(0xFF202428)),
        icon = Icons.Filled.DirectionsCar
    )
}

sealed class ResolvedBanner {
    data class Preset(val preset: BannerPreset, val isDefault: Boolean) : ResolvedBanner()
    data class CustomImage(val url: String) : ResolvedBanner()
}

object BannerStore {
    const val PRESET_PREFIX = "preset:"

    fun presetValue(preset: BannerPreset): String = "$PRESET_PREFIX${preset.id}"

    fun parsePreset(value: String?): BannerPreset? {
        if (value.isNullOrBlank()) return null
        if (!value.startsWith(PRESET_PREFIX)) return null
        val id = value.removePrefix(PRESET_PREFIX).trim()
        return BannerPreset.entries.firstOrNull { it.id == id }
    }

    fun defaultForUser(userId: String): BannerPreset {
        if (userId.isBlank()) return BannerPreset.FLUID_MAWJ
        val idx = ((userId.hashCode().toLong() and 0x7FFFFFFFL) % BannerPreset.entries.size).toInt()
        return BannerPreset.entries[idx]
    }

    fun resolve(bannerValue: String?, userId: String): ResolvedBanner {
        val raw = bannerValue?.trim().orEmpty()
        if (raw.isEmpty()) {
            return ResolvedBanner.Preset(defaultForUser(userId), isDefault = true)
        }
        val preset = parsePreset(raw)
        if (preset != null) {
            return ResolvedBanner.Preset(preset, isDefault = false)
        }
        return ResolvedBanner.CustomImage(raw)
    }

    fun labelFor(bannerValue: String?, userId: String): String = when (val res = resolve(bannerValue, userId)) {
        is ResolvedBanner.Preset -> if (res.isDefault) "${res.preset.title} (افتراضي)" else res.preset.title
        is ResolvedBanner.CustomImage -> "صورة مخصصة"
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun AnimatedBannerWebView(
    assetPath: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                isClickable = false
                isFocusable = false
                setOnTouchListener { _, _ -> true }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    setSupportZoom(false)
                    displayZoomControls = false
                    builtInZoomControls = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mediaPlaybackRequiresUserGesture = false
                }
                tag = assetPath
                loadUrl("file:///android_asset/$assetPath")
            }
        },
        update = { webView ->
            if (webView.tag != assetPath) {
                webView.tag = assetPath
                webView.loadUrl("file:///android_asset/$assetPath")
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.onPause()
            webView.destroy()
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountBannerView(
    bannerValue: String?,
    userId: String,
    modifier: Modifier = Modifier,
    showHint: Boolean = false,
    showBottomScrim: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    val resolved = remember(bannerValue, userId) { BannerStore.resolve(bannerValue, userId) }
    val haptic = LocalHapticFeedback.current
    val bg = MaterialTheme.colorScheme.background
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier.background(
            when (resolved) {
                is ResolvedBanner.Preset -> Brush.linearGradient(resolved.preset.colors)
                is ResolvedBanner.CustomImage -> Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            }
        )
    ) {
        when (resolved) {
            is ResolvedBanner.Preset -> {
                AnimatedBannerWebView(
                    assetPath = resolved.preset.assetPath,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ResolvedBanner.CustomImage -> {
                AsyncImage(
                    model = resolved.url,
                    contentDescription = "بانر الحساب",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        if (showBottomScrim) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.72f))))
            )
        }

        if (showHint && onLongPress != null) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0x88000000),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.ZoomOutMap,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        "اضغط مطولاً للتكبير",
                        color = Color.White,
                        fontFamily = Tajawal,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Transparent gesture catcher over WebView so long-press & tap work reliably without WebView stealing touches
        if (onClick != null || onLongPress != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onClick?.invoke() },
                        onLongClick = {
                            if (onLongPress != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongPress()
                            }
                        }
                    )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnlargedBannerDialog(
    bannerValue: String?,
    userId: String,
    ownerName: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler(onBack = onDismiss)
        val label = remember(bannerValue, userId) { BannerStore.labelFor(bannerValue, userId) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xE604070E))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF0E131B))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            ownerName.ifBlank { "غلاف الحساب" },
                            color = Color.White,
                            fontFamily = Cairo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            label,
                            color = Color(0xFF94A3B8),
                            fontFamily = Tajawal,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .testTag("close_enlarged_banner_button")
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "إغلاق معاينة البانر",
                            tint = Color.White
                        )
                    }
                }
                AccountBannerView(
                    bannerValue = bannerValue,
                    userId = userId,
                    showHint = false,
                    showBottomScrim = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .padding(horizontal = 10.dp)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .testTag("enlarged_banner_view")
                )
            }
        }
    }
}
