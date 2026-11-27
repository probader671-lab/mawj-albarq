package com.mawjalbarq.app

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/** Delivery URLs for Cloudinary media. Non-Cloudinary URLs pass through unchanged. */
object Media {
    private const val VID = "/video/upload/"
    private fun isCld(u: String) = u.contains("res.cloudinary.com/") && u.contains(VID)

    /** Adaptive HLS (Cloudinary sp_auto: several bitrates, the player follows the connection). */
    fun playUrl(u: String, durationSec: Int = 0): String =
        // Videos over 15s play as progressive MP4: on-demand HLS can report a partial duration (e.g. 8s) and stall.
        if (durationSec > 15) mp4Url(u)
        else if (isCld(u) && u.endsWith(".mp4") && !u.contains("/sp_auto/"))
            u.replace(VID, VID + "sp_auto/").removeSuffix(".mp4") + ".m3u8"
        else u

    /** Back to the plain MP4 the file was uploaded as (used if HLS fails to start). */
    fun mp4Url(u: String): String =
        if (u.contains("/sp_auto/") && u.endsWith(".m3u8")) u.replace("/sp_auto/", "/").removeSuffix(".m3u8") + ".mp4" else u

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
}
