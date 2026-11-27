package com.mawjalbarq.app

import android.net.Uri
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** Single-video player for a vertical (portrait) video opened outside the reels feed (profile, notification, admin preview, link).
 *  Portrait videos never play inside the landscape player; landscape ones keep using [FullPlayer]. */
@Composable
fun PortraitPlayer(v: Video, onClose: () -> Unit) {
    DarkLock()
    val ctx = LocalContext.current
    BackHandler { onClose() }
    var firstFrame by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    val player = remember {
        ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(MediaCache.factory(ctx))).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE; playWhenReady = true; Media.withMp4Fallback(this)
            setMediaItem(MediaItem.Builder().setMediaId(v.id).setUri(Uri.parse(Media.playUrl(v.url, v.duration))).build()); prepare()
        }
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onRenderedFirstFrame() { firstFrame = true }
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.clearVideoSurface(); player.release() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures(onTap = { player.playWhenReady = !player.playWhenReady }) }) {
        AndroidView(
            factory = { c ->
                (LayoutInflater.from(c).inflate(R.layout.player_texture_view, null, false) as PlayerView).apply {
                    useController = false; keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.player = null }
        )
        if (!firstFrame) Box(Modifier.fillMaxSize().background(Color.Black))
        if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) {
            Icon(Icons.Filled.Close, "إغلاق", tint = Color.White)
        }
    }
}
