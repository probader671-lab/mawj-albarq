package com.mawjalbarq.app

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** Launch intro bundled in the APK (res/raw/intro_video.mp4): plays once offline, then onDone(). Tap to skip. */
@OptIn(UnstableApi::class)
@Composable
fun IntroVideo(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val done = remember { mutableStateOf(false) }
    val finish = { if (!done.value) { done.value = true; onDone() } }
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(RawResourceDataSource.buildRawResourceUri(R.raw.intro_video)))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) finish() }
            override fun onPlayerError(error: PlaybackException) { finish() }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() }) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = {
            PlayerView(it).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                this.player = player
            }
        })
    }
}
