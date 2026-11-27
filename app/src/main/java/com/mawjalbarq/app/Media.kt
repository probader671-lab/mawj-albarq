package com.mawjalbarq.app

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Delivery URLs for Cloudinary media. Non-Cloudinary URLs pass through unchanged. */
object Media {
    private var appCtx: Context? = null
    private var sp: android.content.SharedPreferences? = null
    /** Data saver switch (settings): always the small 480p version. */
    var dataSaver by mutableStateOf(false)
        private set
    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        sp = ctx.applicationContext.getSharedPreferences("media", Context.MODE_PRIVATE)
        dataSaver = sp?.getBoolean("saver", false) ?: false
    }
    fun setSaver(b: Boolean) { dataSaver = b; sp?.edit()?.putBoolean("saver", b)?.apply() }
    private fun width(): Int = 480   // only used when the data saver is on

    private const val VID = "/video/upload/"
    private fun isCld(u: String) = u.contains("res.cloudinary.com/") && u.contains(VID)

    /** Adaptive HLS (Cloudinary sp_auto: several bitrates, the player follows the connection). */
    fun playUrl(u: String, durationSec: Int = 0): String =
        // Videos over 15s play as progressive MP4: on-demand HLS can report a partial duration (e.g. 8s) and stall.
        if (dataSaver) smallMp4(u)
        else if (durationSec > 15) mp4Url(u)
        else if (isCld(u) && u.endsWith(".mp4") && !u.contains("/sp_auto/"))
            u.replace(VID, VID + "sp_auto/").removeSuffix(".mp4") + ".m3u8"
        else u

    /** Cloudinary-made H.264 MP4 at 480p/720p (cached after the first request); non-Cloudinary URLs unchanged. */
    private fun smallMp4(u: String): String {
        val base = mp4Url(u)
        if (!isCld(base) || !base.endsWith(".mp4")) return base
        val w = width()
        val q = if (w <= 480) "q_auto:low" else "q_auto"
        return base.replace(VID, VID + "c_limit,w_$w,h_$w,vc_h264,$q/")
    }

    /** Back to the plain MP4 the file was uploaded as (used if HLS fails to start). */
    fun mp4Url(u: String): String =
        if (u.contains("/sp_auto/") && u.endsWith(".m3u8")) u.replace("/sp_auto/", "/").removeSuffix(".m3u8") + ".mp4"
        else u.replace(Regex("/video/upload/c_limit,w_\\d+,h_\\d+,vc_h264,q_auto(:low)?/"), "/video/upload/")

    /** Small, auto-quality version of a Cloudinary image so lists do not download full-size pictures. */
    fun thumb(u: String, w: Int = 640): String {
        val k = listOf("/image/upload/", VID).firstOrNull { u.contains("res.cloudinary.com/") && u.contains(it) } ?: return u
        if (u.contains("/w_$w,")) return u
        return u.replace(k, k + "w_$w,c_limit,q_auto/")
    }

    /** If an HLS stream errors out, retry the same video as plain MP4 once. */
    fun withMp4Fallback(p: Player): Player {
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val uri = p.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
                val mp4 = mp4Url(uri)
                if (mp4 != uri) {
                    p.setMediaItem(MediaItem.fromUri(mp4)); p.prepare(); p.playWhenReady = true
                }
            }
        })
        return p
    }

    /** TrackSelector configured to always prefer the highest supported video resolution and bitrate. */
    fun highQualityTrackSelector(ctx: Context): androidx.media3.exoplayer.trackselection.DefaultTrackSelector =
        androidx.media3.exoplayer.trackselection.DefaultTrackSelector(ctx).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true)
                    .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
            )
        }
}
