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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
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
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.clickable
import java.io.File
import kotlin.coroutines.resume

class Meta(val w: Int, val h: Int, val durMs: Long)

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

suspend fun compress(ctx: Context, uri: Uri, out: File, meta: Meta): Boolean = suspendCancellableCoroutine { cont ->
    Handler(Looper.getMainLooper()).post {
        try {
            val effects = ArrayList<androidx.media3.common.Effect>()
            val portrait = meta.h > meta.w
            // Smaller output for weak phones / slow networks: data-saver switch or a long video (> 10 min) = 480p, else 720p
            val small = Media.dataSaver || meta.durMs > 10 * 60_000L
            val long = if (small) 854 else 1280      // portrait height cap
            val short = if (small) 480 else 720      // landscape height cap
            val bitrate = if (small) 1_100_000 else 2_500_000
            if (portrait && meta.h > long) effects.add(Presentation.createForHeight(long))
            else if (!portrait && meta.h > short) effects.add(Presentation.createForHeight(short))
            val item = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(Effects(emptyList(), effects)).build()
            val enc = DefaultEncoderFactory.Builder(ctx)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build()).build()
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

private const val IA_MAX_CLOUDINARY = 95L * 1024 * 1024   // Cloudinary free plan caps a video file at about 100 MB
private const val IA_MIN_MS = 20 * 60_000L
private const val IA_MIN_BYTES = 500L * 1024 * 1024

private fun srcSize(ctx: Context, uri: Uri): Long = try {
    ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
} catch (_: Exception) { 0L }

/** Public upload to archive.org (S3-like API). Keys come from the ia-keys Edge Function (signed-in users only). Returns the item id, or null. */
suspend fun uploadArchive(f: File, title: String, onProgress: (Float) -> Unit): String? = withContext(Dispatchers.IO) {
    try {
        val (kc, kt) = api("POST", "/functions/v1/ia-keys", "{}")
        if (kc != 200) return@withContext null
        val kj = JSONObject(kt); val ak = kj.getString("access"); val sk = kj.getString("secret")
        val id = "mawj-" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        val total = f.length()
        val body = object : okhttp3.RequestBody() {
            override fun contentType() = "video/mp4".toMediaType()
            override fun contentLength() = total
            override fun writeTo(sink: okio.BufferedSink) {
                f.inputStream().use { ins ->
                    val buf = ByteArray(64 * 1024); var sent = 0L; var lastP = -1
                    while (true) { val n = ins.read(buf); if (n < 0) break; sink.write(buf, 0, n); sent += n
                        val p = (sent * 100 / total).toInt(); if (p != lastP) { lastP = p; onProgress(sent.toFloat() / total) } }
                }
            }
        }
        val safeTitle = title.filter { it.code in 32..126 }.ifBlank { "Mawj Al-Barq video" }.take(120)
        val client = http.newBuilder().writeTimeout(10, java.util.concurrent.TimeUnit.MINUTES).readTimeout(10, java.util.concurrent.TimeUnit.MINUTES).build()
        repeat(3) {
            val req = Request.Builder().url("https://s3.us.archive.org/$id/video.mp4").put(body)
                .header("authorization", "LOW $ak:$sk")
                .header("x-amz-auto-make-bucket", "1")
                .header("x-archive-meta-mediatype", "movies")
                .header("x-archive-meta-collection", "opensource_movies")
                .header("x-archive-meta-title", safeTitle)
                .header("x-archive-meta-creator", "Mawj Al-Barq")
                .header("x-archive-interactive-priority", "1").build()
            try { client.newCall(req).execute().use { if (it.code in 200..299) return@withContext id } } catch (_: java.io.IOException) {}
            kotlinx.coroutines.delay(3000)
        }
        null
    } catch (e: Exception) { null }
}

/** Small JPEG frame to Cloudinary (image) so archive.org videos have a thumbnail right away. */
private suspend fun uploadThumb(f: File): String? = withContext(Dispatchers.IO) {
    try {
        val r = MediaMetadataRetriever(); r.setDataSource(f.absolutePath)
        val bmp = r.getFrameAtTime(1_000_000) ?: r.getFrameAtTime(0); r.release()
        if (bmp == null) return@withContext null
        val bo = java.io.ByteArrayOutputStream(); bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, bo)
        val sig = cloudSign("image") ?: return@withContext null
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addSig(sig)
            .addFormDataPart("file", "t.jpg", bo.toByteArray().toRequestBody("image/jpeg".toMediaType())).build()
        http.newCall(Request.Builder().url("https://api.cloudinary.com/v1_1/${Cfg.CLOUD}/image/upload").post(body).build()).execute().use {
            if (it.code == 200) JSONObject(it.body?.string().orEmpty()).optString("secure_url").ifBlank { null } else null
        }
    } catch (_: Exception) { null }
}



/** First ~45 s of speech as a small AAC file, or null. */
private suspend fun extractAudio(ctx: Context, video: File, out: File, startMs: Long = 0, endMs: Long = 45_000): Boolean = suspendCancellableCoroutine { cont ->
    Handler(Looper.getMainLooper()).post {
        try {
            val mi = MediaItem.Builder().setUri(Uri.fromFile(video))
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build()).build()
            val item = EditedMediaItem.Builder(mi).setRemoveVideo(true).build()
            val t = Transformer.Builder(ctx).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (cont.isActive) cont.resume(true) }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { if (cont.isActive) cont.resume(false) }
                }).build()
            out.delete(); t.start(item, out.absolutePath)
            cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { t.cancel() } }
        } catch (e: Exception) { if (cont.isActive) cont.resume(false) }
    }
}

/** Copies up to 45 s of the first audio track into an .m4a without re-encoding. False if there is no AAC track or anything fails. */
private fun muxAudio(video: File, out: File): Boolean {
    val ex = android.media.MediaExtractor()
    return try {
        ex.setDataSource(video.absolutePath)
        var idx = -1
        for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { idx = i; break }
        if (idx < 0) return false
        ex.selectTrack(idx)
        out.delete()
        val mux = android.media.MediaMuxer(out.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val ti = mux.addTrack(ex.getTrackFormat(idx)); mux.start()
        val buf = java.nio.ByteBuffer.allocate(1 shl 18); val info = android.media.MediaCodec.BufferInfo()
        while (true) {
            val n = ex.readSampleData(buf, 0); if (n < 0 || ex.sampleTime > 45_000_000L) break
            info.set(0, n, ex.sampleTime, if (ex.sampleFlags and android.media.MediaExtractor.SAMPLE_FLAG_SYNC != 0) android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            mux.writeSampleData(ti, buf, info); ex.advance()
        }
        mux.stop(); mux.release(); true
    } catch (_: Throwable) { false } finally { runCatching { ex.release() } }
}

/** Title + description from the speech in the video via the ai-title Edge Function (Groq).
 *  Listens to the whole video in 45 s pieces (max 14 pieces = 10 min). Longer videos are sampled: 14 pieces spread evenly.
 *  Empty strings when there is no usable speech or anything fails. */
private suspend fun autoText(ctx: Context, video: File): Pair<String, String> {
    return try {
        val durMs = withContext(Dispatchers.IO) { readMeta(ctx, Uri.fromFile(video)).durMs }.coerceAtLeast(1000L)
        val seg = 45_000L; val maxSeg = 14
        val n = minOf(maxSeg.toLong(), (durMs + seg - 1) / seg).toInt().coerceAtLeast(1)
        val sb = StringBuilder()
        for (i in 0 until n) {
            val start = if (durMs <= seg * maxSeg) i * seg else (i.toLong() * (durMs - seg) / (maxSeg - 1).coerceAtLeast(1))
            val end = minOf(start + seg, durMs)
            if (end - start < 1500L) continue
            val a = File(ctx.cacheDir, "tt_" + System.currentTimeMillis() + "_$i.m4a")
            try {
                var ok = extractAudio(ctx, video, a, start, end) && a.length() >= 2000L
                if (!ok && start == 0L) ok = withContext(Dispatchers.IO) { muxAudio(video, a) } && a.length() >= 2000L
                if (!ok || a.length() > 4_000_000L) continue
                val t = withContext(Dispatchers.IO) {
                    val b64 = android.util.Base64.encodeToString(a.readBytes(), android.util.Base64.NO_WRAP)
                    val (c, tx) = api("POST", "/functions/v1/ai-title", JSONObject().put("audio", b64).put("transcribe_only", true).toString())
                    if (c == 200) JSONObject(tx).optString("text") else ""
                }
                if (t.isNotBlank()) sb.append(t.trim()).append(' ')
            } finally { a.delete() }
        }
        val all = sb.toString().trim()
        if (all.length < 4) return "" to ""
        withContext(Dispatchers.IO) {
            val (c, t) = api("POST", "/functions/v1/ai-title", JSONObject().put("text", all.take(12000)).toString())
            if (c != 200) "" to "" else JSONObject(t).let { it.optString("title") to it.optString("description") }
        }
    } catch (_: Exception) { "" to "" }
}

/** Background upload pipeline: compress, check content, upload, publish. The uploader never waits on it. */
object UploadJobs {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)
    var status by mutableStateOf("")
    private val gate = kotlinx.coroutines.sync.Semaphore(1)   // one video at a time (weak phones)
    private fun toast(c: Context, m: String) { android.widget.Toast.makeText(c, m, android.widget.Toast.LENGTH_LONG).show() }

    @Volatile var pending = 0
    private class Job(val src: Uri, val title: String, val desc: String, val meta: Meta, val big: Boolean, val onPub: suspend () -> Unit)
    private var lastJob: Job? = null
    fun retry(ctx: Context) { val j = lastJob ?: return; start(ctx, j.src, j.title, j.desc, j.meta, j.big, j.onPub) }
    /** Failure is shown as a persistent notification (a toast is invisible once the app is in the background), with a retry action. */
    private fun fail(ctx: Context, m: String, canRetry: Boolean = true) {
        toast(ctx, m)
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("upload_fail") == null)
                nm.createNotificationChannel(android.app.NotificationChannel("upload_fail", "فشل الرفع", android.app.NotificationManager.IMPORTANCE_HIGH))
            val open = android.app.PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), android.app.PendingIntent.FLAG_IMMUTABLE)
            val b = androidx.core.app.NotificationCompat.Builder(ctx, "upload_fail").setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("فشل نشر الفيديو").setContentText(m).setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(m))
                .setAutoCancel(true).setContentIntent(open)
            if (canRetry && lastJob != null) {
                val ri = android.app.PendingIntent.getService(ctx, 2, Intent(ctx, UploadService::class.java).setAction("RETRY"), android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
                b.addAction(0, "إعادة المحاولة", ri)
            }
            nm.notify(4472, b.build())
        } catch (_: Exception) { }
    }

    fun start(ctx: Context, src: Uri, title: String, desc: String, meta: Meta, bigSrc: Boolean, onPublished: suspend () -> Unit) {
        lastJob = Job(src, title, desc, meta, bigSrc, onPublished)
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(4472)
        pending++
        UploadService.start(ctx.applicationContext)
        scope.launch {
            gate.acquire()
            try { run(ctx, src, title, desc, meta, bigSrc, onPublished) } catch (e: Exception) { status = ""; fail(ctx, "تعذر نشر الفيديو، حاول مرة ثانية") } finally { gate.release(); pending-- }
        }
    }

    private suspend fun run(ctx: Context, src: Uri, title0: String, desc0: String, meta: Meta, bigSrc: Boolean, onPublished: suspend () -> Unit) {
        var title = title0; var desc = desc0
        val out = File(ctx.cacheDir, "upload_" + System.currentTimeMillis() + ".mp4")
        status = "جارٍ ضغط الفيديو في الخلفية..."
        if (!compress(ctx, src, out, meta)) { status = ""; fail(ctx, "فشل ضغط الفيديو"); return }
        if (title.isBlank()) {
            status = "جارٍ كتابة العنوان تلقائيًا..."
            val (at, ad) = autoText(ctx, out)
            title = at.ifBlank { "فيديو" }
            if (desc.isBlank()) desc = ad
        }
        status = "جارٍ فحص المحتوى في الخلفية..."
        val cfg = ModConfig.load()
        val mod = Moderation.check(ctx, out, cfg)
        fun qBody(vid: String?) = JSONObject().put("kind", if (mod.verdict == Verdict.BLOCK) "blocked" else "review")
            .put("reasons", org.json.JSONArray(mod.reasons)).put("music_share", mod.musicShare.toDouble()).put("music_max", mod.musicMax.toDouble())
            .put("nsfw_max", mod.nsfwMax.toDouble()).put("faces", mod.faces).apply { if (vid != null) put("video_id", vid) }
        if (mod.verdict == Verdict.BLOCK) {
            api("POST", "/rest/v1/moderation_queue", qBody(null).toString())
            out.delete(); status = ""
            fail(ctx, "هذا المقطع يخالف قواعد المحتوى ولا يمكن نشره", false); return
        }
        val useArchive = bigSrc || out.length() > IA_MAX_CLOUDINARY
        var r: JSONObject? = null
        var iaId: String? = null
        status = "جارٍ الرفع..."
        if (useArchive) {
            val om = withContext(Dispatchers.IO) { readMeta(ctx, Uri.fromFile(out)) }
            iaId = uploadArchive(out, title) { p -> status = "جارٍ الرفع... " + (p * 100).toInt() + "%" }
            if (iaId != null) r = JSONObject().put("width", om.w).put("height", om.h).put("duration", om.durMs / 1000.0).put("bytes", out.length())
        } else {
            r = uploadCloudinary(out) { p -> status = "جارٍ الرفع... " + (p * 100).toInt() + "%" }
        }
        val iaThumb = if (iaId != null) uploadThumb(out) else null
        val keepForDeep = r != null && mod.verdict == Verdict.ALLOW
        if (!keepForDeep) out.delete()
        if (r == null) { status = ""; fail(ctx, "فشل الرفع، تحقق من الاتصال"); return }
        status = "جارٍ النشر..."
        val pid = if (iaId != null) iaId else r.getString("public_id")
        val w = r.optInt("width"); val h = r.optInt("height")
        val body = JSONObject().put("user_id", App.userId).put("title", title.trim().take(120)).put("description", desc.take(300))
            .put("video_url", if (iaId != null) "https://archive.org/download/$iaId/video.mp4" else r.getString("secure_url"))
            .put("thumbnail_url", if (iaId != null) (iaThumb ?: "https://archive.org/services/img/$iaId") else "https://res.cloudinary.com/${Cfg.CLOUD}/video/upload/so_0/$pid.jpg")
            .put("storage_type", if (iaId != null) "archive" else "cloudinary").put("media_public_id", pid).put("mime_type", "video/mp4")
            .put("size_bytes", r.optLong("bytes")).put("duration", Math.round(r.optDouble("duration", 0.0)).toInt())
            .put("width", w).put("height", h).put("is_vertical", h > w).put("is_short", r.optDouble("duration", 0.0) <= 90.0)
            .put("status", if (mod.verdict == Verdict.REVIEW) "pending_review" else "ready")
            .put("music_score", mod.musicMax.toDouble().coerceIn(0.0, 1.0))
            .put("is_quran", mod.quranLikely || (Reco.looksQuranic(title + " " + desc) && mod.musicMax < cfg.quranMaxMusic && mod.faces == 0))
        val (c, rt) = api("POST", "/rest/v1/videos", body.toString(), prefer = "return=representation")
        status = ""
        if (c in 200..299) {
            if (mod.verdict == Verdict.REVIEW) {
                val vid = try { JSONArray(rt).getJSONObject(0).getString("id") } catch (_: Exception) { null }
                api("POST", "/rest/v1/moderation_queue", qBody(vid).toString())
                toast(ctx, "المقطع قيد المراجعة وسيُنشر بعد موافقة الإدارة")
            } else {
                toast(ctx, "تم نشر الفيديو!"); onPublished()
                // Deeper pass after publishing: 24 frames and up to 4 min of audio. A violation restricts the video and notifies the uploader.
                val vid = try { JSONArray(rt).getJSONObject(0).getString("id") } catch (_: Exception) { null }
                if (vid != null) try {
                    val deep = Moderation.check(ctx, out, cfg, deep = true)
                    if (deep.verdict == Verdict.BLOCK) {
                        val why = if (deep.reasons.contains("nsfw")) "محتوى غير لائق ظهر في فحص إضافي" else if (deep.reasons.contains("music")) "موسيقى ظهرت في فحص إضافي" else "مخالفة لقواعد المحتوى"
                        AdminApi.restrictVideo(vid, why)
                    }
                } catch (_: Throwable) { }
            }
        } else { val lim = rt.contains("daily video limit"); fail(ctx, if (lim) "وصلت للحد اليومي: ٥ فيديوهات في اليوم" else "فشل النشر", !lim) }
        out.delete()
    }
}

private const val CHUNK = 6 * 1024 * 1024  // Cloudinary needs >= 5 MB per chunk except the last

/** Chunked, resumable upload straight to Cloudinary (unsigned preset). Each chunk is retried with backoff,
 *  so a dropped connection costs one chunk, not the whole file. [onProgress] gets 0f..1f. */
private suspend fun uploadCloudinary(f: File, onProgress: (Float) -> Unit = {}): JSONObject? = withContext(Dispatchers.IO) {
    val total = f.length()
    if (total <= 0L) return@withContext null
    val uploadId = java.util.UUID.randomUUID().toString()
    val sig = cloudSign("video") ?: return@withContext null   // one signature covers all chunks of this upload
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
                            .addSig(sig)
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
            Text("سجّل الدخول لرفع مقطع", color = Ink, fontSize = 18.sp, fontFamily = Cairo)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onNeedLogin) { Text("تسجيل الدخول") }
        }
        return
    }
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1f) }
    var askArchive by remember { mutableStateOf(false) }
    var archiveOk by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) {
            uri = it
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        if (ok) {
            uri = camUri
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("استوديو الرفع", color = Ink, fontSize = 24.sp, fontFamily = Cairo)
        var frame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        var previewMeta by remember { mutableStateOf<Meta?>(null) }
        LaunchedEffect(uri) {
            val u = uri
            if (u == null) {
                frame = null
                previewMeta = null
            } else {
                val res = withContext(Dispatchers.IO) {
                    val m = runCatching { readMeta(ctx, u) }.getOrNull()
                    val f = try {
                        val r = MediaMetadataRetriever()
                        r.setDataSource(ctx, u)
                        val bmp = runCatching { r.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.getOrNull()
                        r.release()
                        bmp
                    } catch (_: Exception) { null }
                    m to f
                }
                previewMeta = res.first
                frame = res.second
            }
        }
        androidx.compose.animation.AnimatedVisibility(visible = uri != null) {
            val m = previewMeta
            val f = frame
            val isPortrait = remember(m, f) {
                when {
                    m != null && m.w > 0 && m.h > 0 -> m.h >= m.w
                    f != null && f.width > 0 && f.height > 0 -> f.height >= f.width
                    else -> true
                }
            }
            val boxMod = if (isPortrait) {
                Modifier.width(176.dp).aspectRatio(9f / 16f)
            } else {
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    boxMod
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (f != null) {
                        androidx.compose.foundation.Image(
                            f.asImageBitmap(),
                            contentDescription = "معاينة الفيديو",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                    // Orientation & resolution/duration pill
                    val infoParts = buildList {
                        add(if (isPortrait) "عمودي" else "أفقي")
                        if (m != null && m.durMs > 0L) add(formatDurationMs(m.durMs))
                        if (m != null && m.w > 0 && m.h > 0) add("${m.w}×${m.h}")
                    }
                    Text(
                        infoParts.joinToString(" • "),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = Cairo,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .background(Color(0xB3000000))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                    if (!busy) {
                        IconButton(
                            onClick = { uri = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("upload_clear_video")
                        ) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(0xB3000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(androidx.compose.material.icons.Icons.Filled.Close, "إزالة الفيديو", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { pick.launch("video/*") }, enabled = !busy, modifier = Modifier.weight(1f).height(56.dp).testTag("upload_pick_gallery"),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), colors = ButtonDefaults.filledTonalButtonColors()) {
                Icon(androidx.compose.material.icons.Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("من المعرض")
            }
            Button(onClick = {
                val f = File(ctx.cacheDir, "cam").apply { mkdirs() }.let { File(it, "rec_" + System.currentTimeMillis() + ".mp4") }
                val u = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                camUri = u; cam.launch(u)
            }, enabled = !busy, modifier = Modifier.weight(1f).height(56.dp).testTag("upload_pick_camera"), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                Icon(androidx.compose.material.icons.Icons.Filled.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("الكاميرا")
            }
        }
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("العنوان (اختياري، يُكتب تلقائيًا لو تركته فارغًا)") }, singleLine = true, enabled = !busy)
        OutlinedTextField(desc, { desc = it }, Modifier.fillMaxWidth(), label = { Text("الوصف") }, minLines = 3, enabled = !busy)
        if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
        if (UploadJobs.status.isNotEmpty()) Text("في الخلفية: " + UploadJobs.status, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        if (busy) androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Column(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(status.ifBlank { "جارٍ المعالجة..." }, color = Ink, fontSize = 15.sp)
                if (progress >= 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.width(200.dp)) else LinearProgressIndicator(Modifier.width(200.dp))
            }
        }
        if (askArchive) AlertDialog(onDismissRequest = { askArchive = false },
            title = { Text("فيديو كبير") },
            text = { Text("الفيديوهات الأطول من 20 دقيقة أو الأكبر من 500 ميجا بتتنشر علنًا على موقع archive.org، ومش سهل تخليها خاصة أو تمسحها بعد كده. متأكد إنك عايز تكمل؟") },
            confirmButton = { TextButton(onClick = { askArchive = false; archiveOk = true }) { Text("متابعة") } },
            dismissButton = { TextButton(onClick = { askArchive = false }) { Text("إلغاء") } })
        var startTick by remember { mutableStateOf(0) }
        LaunchedEffect(archiveOk) { if (archiveOk) { startTick++ } }
        val go: () -> Unit = {
            val src = uri!!; val tTitle = title.trim(); val tDesc = desc.trim(); busy = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                try {
                    status = "جارٍ التحقق من الفيديو..."
                    val meta = withContext(Dispatchers.IO) { readMeta(ctx, src) }
                    val bigSrc = meta.durMs > IA_MIN_MS || withContext(Dispatchers.IO) { srcSize(ctx, src) } > IA_MIN_BYTES
                    if (bigSrc && !archiveOk) { busy = false; status = ""; askArchive = true; return@launch }
                    archiveOk = false
                    // Everything else (compression, content checks, upload, publishing) runs in the background.
                    UploadJobs.start(ctx.applicationContext, src, tTitle, tDesc, meta, bigSrc) { Feed.load(); onDone() }
                    title = ""; desc = ""; uri = null
                    status = "تم! الفيديو بيتجهز وينشر في الخلفية، تقدر تكمل استخدام التطبيق"
                } catch (e: Exception) { status = "حدث خطأ غير متوقع" }
                busy = false
            }
        }
        LaunchedEffect(startTick) { if (startTick > 0 && uri != null && !busy) go() }
        Button(enabled = uri != null && !busy, modifier = Modifier.fillMaxWidth().height(50.dp).testTag("upload_publish_button"), onClick = { archiveOk = false; go() }) { Text("نشر") }
    }
}
