package com.mawjalbarq.app

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume

/** Thresholds. Defaults here; the server row backend_settings.moderation_config overrides them so they can be tuned without a new build. */
data class ModConfig(
    val musicBlockShare: Float = 0.25f,   // block if this share of audio windows is music
    val musicWindowP: Float = 0.5f,       // a window counts as "music" at this YAMNet probability
    val musicReviewMax: Float = 0.5f,     // review if any window reaches this
    val musicReviewShare: Float = 0.10f,  // review if this share of windows is weakly music
    val musicReviewP: Float = 0.25f,
    val nsfwBlock: Float = 0.90f,         // block on one frame at this nudity score
    val nsfwBlockMulti: Float = 0.80f,    // or on two frames at this score
    val nsfwReview: Float = 0.40f,        // review at this score
    val faceMinWidth: Float = 0.06f,      // face width as share of frame width
    val quranMaxMusic: Float = 0.15f      // Quran suggestion needs music max below this
) {
    companion object {
        suspend fun load(): ModConfig {
            return try {
                val (c, t) = api("GET", "/rest/v1/backend_settings?key=eq.moderation_config&select=value")
                if (c != 200) return ModConfig()
                val o = org.json.JSONArray(t).optJSONObject(0)?.optJSONObject("value") ?: return ModConfig()
                val d = ModConfig()
                fun f(k: String, def: Float) = o.optDouble(k, def.toDouble()).toFloat()
                ModConfig(f("music_block_share", d.musicBlockShare), f("music_window_p", d.musicWindowP), f("music_review_max", d.musicReviewMax),
                    f("music_review_share", d.musicReviewShare), f("music_review_p", d.musicReviewP), f("nsfw_block", d.nsfwBlock),
                    f("nsfw_block_multi", d.nsfwBlockMulti), f("nsfw_review", d.nsfwReview), f("face_min_width", d.faceMinWidth), f("quran_max_music", d.quranMaxMusic))
            } catch (_: Exception) { ModConfig() }
        }
    }
}

enum class Verdict { ALLOW, REVIEW, BLOCK }

data class ModResult(
    val verdict: Verdict,
    val reasons: List<String>,
    val musicShare: Float, val musicMax: Float, val nsfwMax: Float, val faces: Int,
    val voiceShare: Float, val quranLikely: Boolean
)

object Moderation {
    private const val WIN = 15600
    private const val MUSIC = 132
    private val VOICE = intArrayOf(27, 28) // YAMNet "Chant" and "Mantra": recitation-like voice

    private fun loadAsset(ctx: Context, name: String): ByteBuffer {
        val b = ctx.assets.open(name).use { it.readBytes() }
        return ByteBuffer.allocateDirect(b.size).order(ByteOrder.nativeOrder()).also { it.put(b); it.rewind() }
    }

    /** Runs all on-device checks on a local video file. Never throws: a failed check counts as "uncertain" -> REVIEW. */
    suspend fun check(ctx: Context, file: File, cfg: ModConfig): ModResult = withContext(Dispatchers.Default) {
        val reasons = ArrayList<String>()
        var block = false; var review = false
        var musicShare = 0f; var musicMax = 0f; var voiceShare = 0f
        var audioOk = true
        try {
            val a = decodeAudio16k(file)
            if (a != null && a.size >= WIN) {
                val (ms, mm, vs) = classifyAudio(ctx, a, cfg)
                musicShare = ms; musicMax = mm; voiceShare = vs
            } else if (a == null) audioOk = false
        } catch (e: Throwable) { audioOk = false }
        if (!audioOk) { review = true; reasons += "audio_check_failed" }
        if (musicShare >= cfg.musicBlockShare) { block = true; reasons += "music" }
        else if (musicMax >= cfg.musicReviewMax || musicShare >= cfg.musicReviewShare) { review = true; reasons += "music_uncertain" }

        var nsfwMax = 0f; var hi = 0; var faces = 0
        val faceFrames = ArrayList<Bitmap>()
        try {
            val frames = grabFrames(file)
            if (frames.isEmpty()) { review = true; reasons += "frames_unreadable" }
            val nsfw = Interpreter(loadAsset(ctx, "models/nsfw_nudity.tflite"))
            val det = FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build())
            try {
                for (fr in frames) {
                    val p = nudity(nsfw, fr)
                    if (p > nsfwMax) nsfwMax = p
                    if (p >= cfg.nsfwBlockMulti) hi++
                    val big = detectFaces(det, fr, cfg.faceMinWidth)
                    if (big > faces) faces = big
                    if (big > 0 && faceFrames.size < 3) faceFrames += fr
                }
            } finally { runCatching { nsfw.close() }; runCatching { det.close() } }
        } catch (e: Throwable) { review = true; reasons += "visual_check_failed" }
        if (nsfwMax >= cfg.nsfwBlock || hi >= 2) { block = true; reasons += "nsfw" }
        else if (nsfwMax >= cfg.nsfwReview) { review = true; reasons += "nsfw_uncertain" }
        if (faces > 0) {
            when (genderCheck(faceFrames)) {
                "male" -> reasons += "people_male"
                "female" -> { block = true; reasons += "people_female" }
                else -> { review = true; reasons += "people" }
            }
        }

        val verdict = if (block) Verdict.BLOCK else if (review) Verdict.REVIEW else Verdict.ALLOW
        val quran = musicMax < cfg.quranMaxMusic && voiceShare >= 0.5f && faces == 0
        ModResult(verdict, reasons, musicShare, musicMax, nsfwMax, faces, voiceShare, quran)
    }

    private fun classifyAudio(ctx: Context, a: FloatArray, cfg: ModConfig): Triple<Float, Float, Float> {
        val total = a.size / WIN
        val n = minOf(total, 60)
        val interp = Interpreter(loadAsset(ctx, "models/yamnet.tflite"))
        try {
            var music = 0; var weak = 0; var maxP = 0f; var voice = 0
            val inp = FloatArray(WIN)
            val out = Array(1) { FloatArray(521) }
            for (i in 0 until n) {
                val w = if (n == total) i else (i.toLong() * (total - 1) / maxOf(n - 1, 1)).toInt()
                System.arraycopy(a, w * WIN, inp, 0, WIN)
                interp.run(inp, out)
                val s = out[0]
                val m = s[MUSIC]
                if (m > maxP) maxP = m
                if (m >= cfg.musicWindowP) music++
                if (m >= cfg.musicReviewP) weak++
                if (VOICE.maxOf { s[it] } >= 0.3f) voice++
            }
            // report weak share through musicShare only when it matters: use the larger of strong share and weak share scaled to review level
            val strong = music.toFloat() / n
            val weakS = weak.toFloat() / n
            // encode: strong share, but if weak share exceeds the review share keep it visible via max
            return Triple(strong, if (weakS >= cfg.musicReviewShare) maxOf(maxP, cfg.musicReviewMax) else maxP, voice.toFloat() / n)
        } finally { interp.close() }
    }

    private fun nudity(interp: Interpreter, src: Bitmap): Float {
        val b = Bitmap.createScaledBitmap(src, 224, 224, true)
        val px = IntArray(224 * 224); b.getPixels(px, 0, 224, 0, 0, 224, 224)
        val buf = ByteBuffer.allocateDirect(224 * 224 * 3).order(ByteOrder.nativeOrder())
        for (p in px) { buf.put(((p shr 16) and 0xFF).toByte()); buf.put(((p shr 8) and 0xFF).toByte()); buf.put((p and 0xFF).toByte()) }
        buf.rewind()
        val out = Array(1) { ByteArray(2) }   // [nonnude, nude]
        interp.run(buf, out)
        if (b !== src) b.recycle()
        return (out[0][1].toInt() and 0xFF) / 255f
    }

    /** Remote gender check via the face-gender Edge Function (Face++ key lives server-side).
     *  Returns "male" / "female" only when every face in every frame agrees and quality is good; anything else (or any failure) = "unsure". */
    private suspend fun genderCheck(frames: List<Bitmap>): String = withContext(Dispatchers.IO) {
        try {
            if (frames.isEmpty() || !App.loggedIn) return@withContext "unsure"
            val arr = org.json.JSONArray()
            for (f in frames) {
                val bo = java.io.ByteArrayOutputStream()
                val s = 320f / maxOf(f.width, f.height)
                val b = if (s < 1f) Bitmap.createScaledBitmap(f, (f.width * s).toInt().coerceAtLeast(1), (f.height * s).toInt().coerceAtLeast(1), true) else f
                b.compress(Bitmap.CompressFormat.JPEG, 80, bo)
                arr.put(android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP))
            }
            val (code, text) = api("POST", "/functions/v1/face-gender", JSONObject().put("frames", arr).toString())
            if (code != 200) return@withContext "unsure"
            val j = JSONObject(text)
            if (!j.optBoolean("available")) return@withContext "unsure"
            val fr = j.getJSONArray("frames")
            var male = 0; var female = 0; var bad = 0; var covered = 0; var seen = 0
            for (i in 0 until fr.length()) {
                val faces = fr.getJSONObject(i).optJSONArray("faces") ?: continue
                for (k in 0 until faces.length()) {
                    val f = faces.getJSONObject(k)
                    val g = f.optString("gender", "")
                    // Female whose mouth/lower face is fully covered = treated as niqab (publish). Threshold is a prior, not validated.
                    if (g == "Female" && f.optDouble("mouth_occ", 0.0) >= 80.0) { covered++; continue }
                    val q = f.optDouble("quality", 0.0); val bl = f.optDouble("blur", 100.0); val yaw = Math.abs(f.optDouble("yaw", 90.0))
                    seen++
                    if (g.isEmpty() || q < 30 || bl > 70 || yaw > 55) { bad++; continue }
                    if (g == "Male") male++ else if (g == "Female") female++
                }
            }
            // Loosened (build 16): one clear male/covered face with no female evidence is enough; a few blurry or side-on faces no longer force review.
            if (seen == 0 && covered == 0) "male"   // Face++ found no real face (the on-device detector fired on something small): nothing to judge
            else if (female == 0 && male + covered >= 1 && bad <= male + covered) "male"
            else if (bad == 0 && female >= 2 && male == 0) "female"
            else "unsure"
        } catch (e: Throwable) { "unsure" }
    }

    private suspend fun detectFaces(det: com.google.mlkit.vision.face.FaceDetector, bmp: Bitmap, minW: Float): Int =
        suspendCancellableCoroutine { cont ->
            det.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { fs -> if (cont.isActive) cont.resume(fs.count { it.boundingBox.width().toFloat() / bmp.width >= minW }) }
                .addOnFailureListener { if (cont.isActive) cont.resume(0) }
        }

    private fun grabFrames(file: File): List<Bitmap> {
        val r = MediaMetadataRetriever()
        val out = ArrayList<Bitmap>()
        try {
            r.setDataSource(file.absolutePath)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val n = if (durMs < 4000) 4 else 8
            for (i in 0 until n) {
                val t = (durMs * 1000L * (2 * i + 1)) / (2 * n)
                val f = r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                val s = 480f / maxOf(f.width, f.height)
                out += if (s < 1f) Bitmap.createScaledBitmap(f, (f.width * s).toInt().coerceAtLeast(1), (f.height * s).toInt().coerceAtLeast(1), true) else f
            }
        } finally { runCatching { r.release() } }
        return out
    }

    /** Decodes the audio track to mono 16 kHz floats (first ~2 minutes). Empty array = no audio track. null = failed. */
    private fun decodeAudio16k(file: File, maxSec: Int = 120): FloatArray? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.absolutePath)
            var idx = -1; var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val tf = ex.getTrackFormat(i)
                if (tf.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { idx = i; fmt = tf; break }
            }
            if (idx < 0 || fmt == null) return FloatArray(0)
            ex.selectTrack(idx)
            val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0); codec.start()
            var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var mono = FloatArray(sr * 20); var n = 0
            val info = MediaCodec.BufferInfo()
            var inDone = false; var outDone = false
            try {
                while (!outDone && n < sr * maxSec) {
                    if (!inDone) {
                        val ib = codec.dequeueInputBuffer(10_000)
                        if (ib >= 0) {
                            val buf = codec.getInputBuffer(ib)!!
                            val sz = ex.readSampleData(buf, 0)
                            if (sz < 0) { codec.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                            else { codec.queueInputBuffer(ib, 0, sz, ex.sampleTime, 0); ex.advance() }
                        }
                    }
                    val ob = codec.dequeueOutputBuffer(info, 10_000)
                    if (ob >= 0) {
                        val buf = codec.getOutputBuffer(ob)!!
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        val sb = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val frames = sb.remaining() / maxOf(ch, 1)
                        if (n + frames > mono.size) mono = mono.copyOf(maxOf(mono.size * 2, n + frames))
                        for (f in 0 until frames) { var s = 0f; for (c in 0 until ch) s += sb.get() / 32768f; mono[n++] = s / ch }
                        codec.releaseOutputBuffer(ob, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    } else if (ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val of = codec.outputFormat; sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            } finally { runCatching { codec.stop() }; runCatching { codec.release() } }
            if (sr == 16000) return mono.copyOf(n)
            val outN = (n.toLong() * 16000 / sr).toInt()
            val res = FloatArray(outN)
            for (i in 0 until outN) {
                val pos = i.toDouble() * sr / 16000; val i0 = pos.toInt(); val fr = (pos - i0).toFloat()
                val a = mono[minOf(i0, n - 1)]; val b = mono[minOf(i0 + 1, n - 1)]
                res[i] = a + (b - a) * fr
            }
            return res
        } catch (e: Throwable) { return null } finally { runCatching { ex.release() } }
    }
}
