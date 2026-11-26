package com.mawjalbarq.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.*
import okhttp3.Request
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Animated, subtle, transparent app-icon watermark that drifts around the frame (drawn per frame). */
private class MovingLogoOverlay(private val w: Int, private val h: Int, private val logo: Bitmap) : BitmapOverlay() {
    private val frames = arrayOf(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888), Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
    private var flip = 0
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val size = (minOf(w, h) * 0.16f).roundToInt().coerceAtLeast(48)
    private val icon = Bitmap.createScaledBitmap(logo, size, size, true)

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        flip = 1 - flip   // alternate two bitmaps so the texture is refreshed every frame
        val bmp = frames[flip]
        val t = presentationTimeUs / 1_000_000.0
        val c = Canvas(bmp)
        c.drawColor(0, PorterDuff.Mode.CLEAR)
        val x = ((w - size) * (0.5 + 0.5 * sin(t * 0.7))).toFloat()
        val y = ((h - size) * (0.5 + 0.5 * sin(t * 0.5 + 1.3))).toFloat()
        paint.alpha = (70 + 20 * sin(t * 1.6)).roundToInt().coerceIn(40, 100)   // about 27-39% opacity, gently pulsing
        c.save()
        c.rotate((8 * sin(t * 1.1)).toFloat(), x + size / 2f, y + size / 2f)
        c.drawBitmap(icon, x, y, paint)
        c.restore()
        return bmp
    }
}

object ShareVideo {
    var status by mutableStateOf<String?>(null)
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var transformer: Transformer? = null

    fun cancel() {
        job?.cancel(); transformer?.cancel(); transformer = null; status = null
    }

    private fun linkShare(ctx: Context, v: Video) {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "${v.title} - موج البرق\n${v.url}"), "مشاركة"))
    }

    fun share(ctx: Context, v: Video) {
        if (status != null) return
        status = "جاري تنزيل الفيديو..."
        job = scope.launch {
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val src = File(dir, "src.mp4"); val out = File(dir, "mawj_${v.id.take(8)}.mp4")
            try {
                val ok = withContext(Dispatchers.IO) { download(v.url, src) }
                if (!ok) throw IllegalStateException("download")
                status = "جاري إضافة العلامة المائية..."
                val done = withContext(Dispatchers.Default) { true } && exportWithLogo(ctx, src, out)
                if (!done) throw IllegalStateException("export")
                status = null
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", out)
                val i = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_TEXT, "${v.title} - موج البرق")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(Intent.createChooser(i, "مشاركة"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = null
                Toast.makeText(ctx, "تعذر تجهيز الفيديو، تتم مشاركة الرابط", Toast.LENGTH_SHORT).show()
                linkShare(ctx, v)
            } finally {
                src.delete(); status = null
            }
        }
    }

    private fun download(url: String, f: File): Boolean = try {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return false
            r.body!!.byteStream().use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            true
        }
    } catch (e: Exception) { false }

    private suspend fun exportWithLogo(ctx: Context, src: File, out: File): Boolean = suspendCancellableCoroutine { cont ->
        Handler(Looper.getMainLooper()).post {
            try {
                val r = MediaMetadataRetriever()
                var w: Int; var h: Int
                try {
                    r.setDataSource(src.absolutePath)
                    w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
                } finally { r.release() }
                if (w <= 0 || h <= 0) { if (cont.isActive) cont.resume(false); return@post }
                // same size cap as uploads, then draw the overlay at the final size
                val limit = if (h > w) 1280 else 720
                if (h > limit) { val s = limit.toFloat() / h; w = (w * s).roundToInt(); h = limit }
                w = w and 1.inv(); h = h and 1.inv()
                val logo = BitmapFactory.decodeResource(ctx.resources, R.drawable.logo_mark)
                val effects = ArrayList<Effect>()
                effects.add(Presentation.createForHeight(h))
                effects.add(OverlayEffect(ImmutableList.of(MovingLogoOverlay(w, h, logo))))
                val item = EditedMediaItem.Builder(MediaItem.fromUri(android.net.Uri.fromFile(src))).setEffects(Effects(emptyList(), effects)).build()
                val enc = DefaultEncoderFactory.Builder(ctx)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(3_000_000).build()).build()
                val t = Transformer.Builder(ctx).setVideoMimeType(MimeTypes.VIDEO_H264).setEncoderFactory(enc)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (cont.isActive) cont.resume(true) }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { if (cont.isActive) cont.resume(false) }
                    }).build()
                transformer = t
                out.delete()
                t.start(item, out.absolutePath)
                cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { t.cancel() } }
            } catch (e: Exception) { if (cont.isActive) cont.resume(false) }
        }
    }
}

@Composable
fun ShareProgressHost() {
    val s = ShareVideo.status ?: return
    Dialog(onDismissRequest = { }) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp).background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(14.dp))
            Text(s, color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp, fontFamily = Cairo)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { ShareVideo.cancel() }) { Text("إلغاء") }
        }
    }
}
