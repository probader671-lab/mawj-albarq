package com.mawjalbarq.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Viewer's switch for Quran background play (default on). Only Quran-flagged videos ever use it. */
object QuranPrefs {
    fun enabled(ctx: Context) = ctx.getSharedPreferences("quran", Context.MODE_PRIVATE).getBoolean("bg", true)
    fun set(ctx: Context, v: Boolean) = ctx.getSharedPreferences("quran", Context.MODE_PRIVATE).edit().putBoolean("bg", v).apply()
}

object QuranBg {
    @Volatile var session: MediaSession? = null

    /** Call after a video is loaded into [player]. Starts the media session + notification service only for Quran videos with the switch on. */
    fun sync(ctx: Context, player: ExoPlayer, quran: Boolean) {
        if (quran && QuranPrefs.enabled(ctx)) start(ctx, player) else release(ctx, player)
    }

    private fun start(ctx: Context, player: ExoPlayer) {
        val app = ctx.applicationContext
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
        player.setHandleAudioBecomingNoisy(true)
        player.setWakeMode(C.WAKE_MODE_NETWORK)
        if (session?.player !== player) {
            session?.release()
            session = MediaSession.Builder(app, player).setId("quran_bg").build()
        }
        if (Build.VERSION.SDK_INT >= 33 && ctx is Activity &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(ctx, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 77)
        }
        app.startService(Intent(app, QuranPlaybackService::class.java))
    }

    /** Stop the background session if it belongs to [player] (so a screen leaving never kills another screen's session). */
    fun release(ctx: Context, player: Player) {
        val s = session ?: return
        if (s.player !== player) return
        session = null
        s.release()
        ctx.applicationContext.stopService(Intent(ctx.applicationContext, QuranPlaybackService::class.java))
    }
}

class QuranPlaybackService : MediaSessionService() {
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = QuranBg.session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        QuranBg.session?.let { if (!sessions.contains(it)) addSession(it) }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = QuranBg.session?.player
        if (p == null || !p.playWhenReady) stopSelf()
    }
}
