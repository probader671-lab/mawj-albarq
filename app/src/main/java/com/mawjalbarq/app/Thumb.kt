package com.mawjalbarq.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis

/** Thumbnail that never renders as a black box: uses the saved thumbnail, else a frame
 *  grabbed from the video, and while loading or on failure shows a colored placeholder. */
@Composable
fun VideoThumb(v: Video, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    val loader = remember { ThumbLoader.get(ctx) }
    val hasThumb = !v.thumb.isNullOrBlank()
    val model = remember(v.id, v.thumb) {
        ImageRequest.Builder(ctx)
            .data(if (hasThumb) Media.thumb(v.thumb!!) else v.url)
            .apply { if (!hasThumb) videoFrameMillis(1000) }
            .build()
    }
    SubcomposeAsyncImage(
        model = model, imageLoader = loader, contentDescription = v.title,
        modifier = modifier, contentScale = scale,
        loading = { ThumbPlaceholder() }, error = { ThumbPlaceholder() }
    )
}

/** One shared loader so every thumbnail shares the same memory and disk cache. */
object ThumbLoader {
    @Volatile private var l: ImageLoader? = null
    fun get(ctx: android.content.Context): ImageLoader = l ?: synchronized(this) {
        l ?: ImageLoader.Builder(ctx.applicationContext).components { add(VideoFrameDecoder.Factory()) }.crossfade(true).build().also { l = it }
    }
}

@Composable
fun ThumbPlaceholder() {
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A3548), Color(0xFF4A3A5E)))),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.PlayArrow, null, tint = Color(0x99FFFFFF), modifier = Modifier.then(Modifier)) }
}
