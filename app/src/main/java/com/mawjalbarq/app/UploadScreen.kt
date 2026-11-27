package com.mawjalbarq.app
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

private class Meta(val w: Int, val h: Int, val durMs: Long)

private fun readMeta(ctx: Context, uri: Uri): Meta {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(ctx, uri)
        var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
        Meta(w, h, r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
    } finally { r.release() }
}

private suspend fun compress(ctx: Context, uri: Uri, out: File, meta: Meta): Boolean = suspendCancellableCoroutine { cont ->
    Handler(Looper.getMainLooper()).post {
        try {
            val effects = ArrayList<androidx.media3.common.Effect>()
            val portrait = meta.h > meta.w
            if (portrait && meta.h > 1280) effects.add(Presentation.createForHeight(1280))
            else if (!portrait && meta.h > 720) effects.add(Presentation.createForHeight(720))
            val item = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(Effects(emptyList(), effects)).build()
            val enc = DefaultEncoderFactory.Builder(ctx)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(2_500_000).build()).build()
            val t = Transformer.Builder(ctx).setVideoMimeType(MimeTypes.VIDEO_H264).setEncoderFactory(enc)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (cont.isActive) cont.resume(true) }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { if (cont.isActive) cont.resume(false) }
                }).build()
            out.delete()
            t.start(item, out.absolutePath)
            cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { t.cancel() } }
        } catch (e: Exception) { if (cont.isActive) cont.resume(false) }
    }
}

private const val CHUNK = 6 * 1024 * 1024  // Cloudinary needs >= 5 MB per chunk except the last

/** Chunked, resumable upload straight to Cloudinary (unsigned preset). Each chunk is retried with backoff,
 *  so a dropped connection costs one chunk, not the whole file. [onProgress] gets 0f..1f. */
private suspend fun uploadCloudinary(f: File, onProgress: (Float) -> Unit = {}): JSONObject? = withContext(Dispatchers.IO) {
    val total = f.length()
    if (total <= 0L) return@withContext null
    val uploadId = java.util.UUID.randomUUID().toString()
    val url = "https://api.cloudinary.com/v1_1/${Cfg.CLOUD}/video/upload"
    var start = 0L
    var last: JSONObject? = null
    try {
        java.io.RandomAccessFile(f, "r").use { raf ->
            while (start < total) {
                val len = minOf(CHUNK.toLong(), total - start).toInt()
                val buf = ByteArray(len)
                raf.seek(start); raf.readFully(buf)
                val end = start + len - 1
                var ok = false
                var attempt = 0
                while (!ok && attempt < 6) {
                    try {
                        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                            .addFormDataPart("upload_preset", Cfg.PRESET)
                            .addFormDataPart("file", "clip.mp4", buf.toRequestBody("application/octet-stream".toMediaType())).build()
                        val req = Request.Builder().url(url).post(body)
                            .header("X-Unique-Upload-Id", uploadId)
                            .header("Content-Range", "bytes $start-$end/$total").build()
                        http.newCall(req).execute().use {
                            val txt = it.body?.string().orEmpty()
                            if (it.code == 200) { last = JSONObject(txt); ok = true }
                            else if (it.code in 400..499 && it.code != 408 && it.code != 429) return@withContext null  // permanent error
                        }
                    } catch (e: java.io.IOException) { /* dropped connection: retry this chunk */ }
                    if (!ok) { attempt++; kotlinx.coroutines.delay(minOf(1000L shl attempt, 15000L)) }
                }
                if (!ok) return@withContext null
                start = end + 1
                onProgress(start.toFloat() / total)
            }
        }
        last?.takeIf { it.has("secure_url") }
    } catch (e: Exception) { null }
}

@Composable
fun UploadScreen(onNeedLogin: () -> Unit, onDone: () -> Unit) {
    if (!App.loggedIn) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("سجّل الدخول لرفع مقطع", color = Color.White, fontSize = 18.sp, fontFamily = Cairo)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onNeedLogin) { Text("تسجيل الدخول") }
        }
        return
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1f) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) uri = it }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok -> if (ok) uri = camUri }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("استوديو الرفع", color = Color.White, fontSize = 24.sp, fontFamily = Cairo)
        var frame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(uri) {
            frame = uri?.let { u -> withContext(Dispatchers.IO) { try { val r = MediaMetadataRetriever(); r.setDataSource(ctx, u); val f = r.getFrameAtTime(500_000); r.release(); f } catch (e: Exception) { null } } }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(176.dp).aspectRatio(9f / 16f).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                val f = frame
                if (f != null) androidx.compose.foundation.Image(f.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                else Icon(androidx.compose.material.icons.Icons.Filled.Videocam, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { pick.launch("video/*") }, enabled = !busy, modifier = Modifier.weight(1f).height(56.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), colors = ButtonDefaults.filledTonalButtonColors()) {
                Icon(androidx.compose.material.icons.Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("من المعرض")
            }
            Button(onClick = {
                val f = File(ctx.cacheDir, "cam").apply { mkdirs() }.let { File(it, "rec_" + System.currentTimeMillis() + ".mp4") }
                val u = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                camUri = u; cam.launch(u)
            }, enabled = !busy, modifier = Modifier.weight(1f).height(56.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                Icon(androidx.compose.material.icons.Icons.Filled.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("الكاميرا")
            }
        }
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("العنوان") }, singleLine = true, enabled = !busy)
        OutlinedTextField(desc, { desc = it }, Modifier.fillMaxWidth(), label = { Text("الوصف") }, minLines = 3, enabled = !busy)
        if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
        if (busy) androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Column(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(status.ifBlank { "جارٍ المعالجة..." }, color = Color.White, fontSize = 15.sp)
                if (progress >= 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.width(200.dp)) else LinearProgressIndicator(Modifier.width(200.dp))
            }
        }
        Button(enabled = uri != null && title.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            val src = uri!!; busy = true
            scope.launch {
                try {
                    status = "جارٍ ضغط الفيديو..."
                    val meta = withContext(Dispatchers.IO) { readMeta(ctx, src) }
                    val out = File(ctx.cacheDir, "upload.mp4")
                    if (!compress(ctx, src, out, meta)) { status = "فشل ضغط الفيديو"; busy = false; return@launch }
                    status = "جارٍ الرفع..."
                    progress = 0f
                    val r = uploadCloudinary(out) { p -> progress = p; status = "جارٍ الرفع... " + (p * 100).toInt() + "%" }
                    progress = -1f
                    out.delete()
                    if (r == null) { status = "فشل الرفع، تحقق من الاتصال"; busy = false; return@launch }
                    status = "جارٍ النشر..."
                    val pid = r.getString("public_id")
                    val w = r.optInt("width"); val h = r.optInt("height")
                    val body = JSONObject().put("user_id", App.userId).put("title", title.trim()).put("description", desc.trim())
                        .put("video_url", r.getString("secure_url"))
                        .put("thumbnail_url", "https://res.cloudinary.com/${Cfg.CLOUD}/video/upload/so_0/$pid.jpg")
                        .put("storage_type", "cloudinary").put("media_public_id", pid).put("mime_type", "video/mp4")
                        .put("size_bytes", r.optLong("bytes")).put("duration", Math.round(r.optDouble("duration", 0.0)).toInt())
                        .put("width", w).put("height", h).put("is_vertical", h > w).put("is_short", r.optDouble("duration", 0.0) <= 90.0)
                        .put("status", "ready")
                    val (c, _) = api("POST", "/rest/v1/videos", body.toString())
                    if (c in 200..299) {
                        status = "تم النشر!"; title = ""; desc = ""; uri = null
                        Feed.load(); onDone()
                    } else status = "فشل النشر"
                } catch (e: Exception) { status = "حدث خطأ غير متوقع" }
                busy = false
            }
        }) { Text("نشر") }
    }
}
