package com.mawjalbarq.app

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URI
import kotlin.coroutines.coroutineContext

private val Context.smartPrefetchDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "smart_video_prefetch"
)

private data class PrefetchRecord(
    val videoId: String,
    val resolvedUrl: String,
    val cachedBytes: Long,
    val hlsWarmed: Boolean,
    val updatedAtMs: Long
)

/**
 * Smart disk + Jetpack DataStore video pre-fetching engine.
 * Tracks warmed clips in DataStore alongside ExoPlayer's 500 MB SimpleCache
 * and pre-fetches the next 10 videos in two priority tiers for zero-latency vertical switching.
 */
object MediaCache {
    private const val DEFAULT_LOOKAHEAD = 10
    private const val IMMEDIATE_TIER_COUNT = 3
    private const val IMMEDIATE_MP4_BYTES = 3L * 1024 * 1024
    private const val LOOKAHEAD_MP4_BYTES = 1L * 1024 * 1024
    private const val IMMEDIATE_HLS_SEGMENTS = 2
    private const val LOOKAHEAD_HLS_SEGMENTS = 1
    private const val HLS_SEGMENT_MAX_BYTES = 2L * 1024 * 1024
    private const val RECORD_TTL_MS = 24L * 60 * 60 * 1000
    private const val MAX_STORED_RECORDS = 160

    private val KEY_RECORDS_JSON = stringPreferencesKey("prefetch_records_json")
    private val KEY_QUEUE_JSON = stringPreferencesKey("lookahead_queue_json")
    private val KEY_LAST_INDEX = intPreferencesKey("last_active_clip_index")
    private val KEY_LAST_VIDEO_ID = stringPreferencesKey("last_active_video_id")
    private val KEY_UPDATED_AT = longPreferencesKey("prefetch_updated_at_ms")

    private var cache: SimpleCache? = null
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var bgJob: Job? = null

    private val stateMutex = Mutex()
    private var loadedFromStore = false
    private val records = LinkedHashMap<String, PrefetchRecord>()

    @Synchronized
    private fun cache(ctx: Context): SimpleCache = cache ?: SimpleCache(
        File(ctx.applicationContext.cacheDir, "media3_mawj").apply { mkdirs() },
        LeastRecentlyUsedCacheEvictor(500L * 1024 * 1024),
        StandaloneDatabaseProvider(ctx.applicationContext)
    ).also { cache = it }

    fun factory(ctx: Context): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache(ctx))
        .setUpstreamDataSourceFactory(DefaultDataSource.Factory(ctx.applicationContext))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Fire-and-forget background pre-fetch for callers outside Compose LaunchedEffect (e.g. Feed.load). */
    fun launchSmartPrefetch(
        ctx: Context,
        videos: List<Video>,
        currentIndex: Int,
        lookahead: Int = DEFAULT_LOOKAHEAD
    ) {
        val appCtx = ctx.applicationContext
        val snapshot = videos.toList()
        bgJob?.cancel()
        bgJob = bgScope.launch {
            smartPrefetchNext(appCtx, snapshot, currentIndex, lookahead)
        }
    }

    /**
     * Smart DataStore-backed pre-fetch of the next [lookahead] (default 10) videos after [currentIndex].
     * - Tier 1 (+1..+3): sequentially warmed with deeper buffers (3 MB MP4 or HLS master+variant+2 segments).
     * - Tier 2 (+4..+10): warmed concurrently (2 workers) with fast-start buffers (1 MB MP4 or HLS master+variant+1 segment).
     * - Skips clips already confirmed in both DataStore and SimpleCache to eliminate redundant downloads.
     */
    suspend fun smartPrefetchNext(
        ctx: Context,
        videos: List<Video>,
        currentIndex: Int,
        lookahead: Int = DEFAULT_LOOKAHEAD
    ) = withContext(Dispatchers.IO) {
        if (videos.isEmpty()) return@withContext
        val appCtx = ctx.applicationContext
        ensureLoaded(appCtx)

        val upcoming = videos.drop((currentIndex + 1).coerceAtLeast(0))
            .filter { it.url.startsWith("http") }
            .take(lookahead)
        if (upcoming.isEmpty()) return@withContext

        saveQueueSnapshot(
            ctx = appCtx,
            currentIndex = currentIndex,
            currentVideoId = videos.getOrNull(currentIndex)?.id.orEmpty(),
            upcoming = upcoming
        )

        val dataSourceFactory = factory(appCtx)
        val diskCache = cache(appCtx)

        // Tier 1: Immediate next 3 videos, warmed sequentially so +1 is ready immediately
        val immediate = upcoming.take(IMMEDIATE_TIER_COUNT)
        for (v in immediate) {
            coroutineContext.ensureActive()
            warmSingleVideo(
                ctx = appCtx,
                dataSourceFactory = dataSourceFactory,
                diskCache = diskCache,
                video = v,
                mp4TargetBytes = IMMEDIATE_MP4_BYTES,
                hlsSegmentsToWarm = IMMEDIATE_HLS_SEGMENTS
            )
        }

        // Tier 2: Remaining lookahead videos (+4..+10), bounded 2-worker parallel warmup
        val secondary = upcoming.drop(IMMEDIATE_TIER_COUNT)
        if (secondary.isNotEmpty()) {
            val semaphore = Semaphore(permits = 2)
            coroutineScope {
                secondary.map { v ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            coroutineContext.ensureActive()
                            warmSingleVideo(
                                ctx = appCtx,
                                dataSourceFactory = dataSourceFactory,
                                diskCache = diskCache,
                                video = v,
                                mp4TargetBytes = LOOKAHEAD_MP4_BYTES,
                                hlsSegmentsToWarm = LOOKAHEAD_HLS_SEGMENTS
                            )
                        }
                    }
                }.awaitAll()
            }
        }
    }

    /** Legacy URL-list pre-fetch preserved for compatibility; routes through SimpleCache + DataStore. */
    suspend fun prefetch(ctx: Context, urls: List<String>) = withContext(Dispatchers.IO) {
        val synthetic = urls.filter { it.startsWith("http") }.take(DEFAULT_LOOKAHEAD).map { raw ->
            Video(
                id = raw.hashCode().toString(),
                title = "",
                desc = "",
                url = raw,
                thumb = null,
                author = "",
                likes = 0,
                comments = 0
            )
        }
        if (synthetic.isNotEmpty()) {
            smartPrefetchNext(ctx, synthetic, currentIndex = -1, lookahead = DEFAULT_LOOKAHEAD)
        }
    }

    private suspend fun warmSingleVideo(
        ctx: Context,
        dataSourceFactory: CacheDataSource.Factory,
        diskCache: SimpleCache,
        video: Video,
        mp4TargetBytes: Long,
        hlsSegmentsToWarm: Int
    ) {
        val resolvedUrl = Media.playUrl(video.url, video.duration)
        if (!resolvedUrl.startsWith("http")) return

        val isHls = resolvedUrl.contains(".m3u8", ignoreCase = true)
        if (isAlreadyWarmed(diskCache, video.id, resolvedUrl, isHls, mp4TargetBytes)) {
            return
        }

        var warmedBytes = 0L
        var hlsSuccess = false

        if (isHls) {
            val (hlsBytes, ok) = warmHlsStream(
                dataSourceFactory = dataSourceFactory,
                masterUrl = resolvedUrl,
                segmentsToWarm = hlsSegmentsToWarm
            )
            warmedBytes = hlsBytes
            hlsSuccess = ok
        } else {
            val (bytes, _) = readIntoCache(
                factory = dataSourceFactory,
                url = resolvedUrl,
                maxBytes = mp4TargetBytes,
                captureBytes = false
            )
            warmedBytes = bytes
        }

        if (warmedBytes > 0L) {
            recordWarmup(
                ctx = ctx,
                record = PrefetchRecord(
                    videoId = video.id,
                    resolvedUrl = resolvedUrl,
                    cachedBytes = warmedBytes,
                    hlsWarmed = hlsSuccess,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun warmHlsStream(
        dataSourceFactory: CacheDataSource.Factory,
        masterUrl: String,
        segmentsToWarm: Int
    ): Pair<Long, Boolean> {
        var totalBytes = 0L
        val (masterLen, masterRaw) = readIntoCache(
            factory = dataSourceFactory,
            url = masterUrl,
            maxBytes = 512L * 1024,
            captureBytes = true
        )
        totalBytes += masterLen
        if (masterRaw == null || masterRaw.isEmpty()) return totalBytes to false

        val masterText = masterRaw.toString(Charsets.UTF_8)
        // If master playlist points to variant playlists, select the highest-bitrate variant to match highQualityTrackSelector
        val variantUrl = pickHighestBandwidthVariant(masterUrl, masterText)
        val playlistUrl: String
        val playlistText: String

        if (variantUrl != null) {
            coroutineContext.ensureActive()
            val (varLen, varRaw) = readIntoCache(
                factory = dataSourceFactory,
                url = variantUrl,
                maxBytes = 1024L * 1024,
                captureBytes = true
            )
            totalBytes += varLen
            if (varRaw == null || varRaw.isEmpty()) return totalBytes to false
            playlistUrl = variantUrl
            playlistText = varRaw.toString(Charsets.UTF_8)
        } else {
            playlistUrl = masterUrl
            playlistText = masterText
        }

        val (initMapUrl, segmentUrls) = extractSegments(playlistUrl, playlistText, segmentsToWarm)
        if (initMapUrl != null) {
            coroutineContext.ensureActive()
            val (mapLen, _) = readIntoCache(
                factory = dataSourceFactory,
                url = initMapUrl,
                maxBytes = HLS_SEGMENT_MAX_BYTES,
                captureBytes = false
            )
            totalBytes += mapLen
        }

        var warmedSegs = 0
        for (segUrl in segmentUrls) {
            coroutineContext.ensureActive()
            val (segLen, _) = readIntoCache(
                factory = dataSourceFactory,
                url = segUrl,
                maxBytes = HLS_SEGMENT_MAX_BYTES,
                captureBytes = false
            )
            totalBytes += segLen
            if (segLen > 0L) warmedSegs++
        }

        return totalBytes to (warmedSegs > 0)
    }

    private fun pickHighestBandwidthVariant(baseUrl: String, m3u8: String): String? {
        val lines = m3u8.lineSequence().map { it.trim() }.toList()
        var bestBandwidth = -1L
        var bestUrl: String? = null
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val bwMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                val bw = bwMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                var j = i + 1
                while (j < lines.size && (lines[j].isEmpty() || lines[j].startsWith("#"))) j++
                if (j < lines.size) {
                    val resolved = resolveUrl(baseUrl, lines[j])
                    if (resolved != null && bw >= bestBandwidth) {
                        bestBandwidth = bw
                        bestUrl = resolved
                    }
                }
                i = j
            }
            i++
        }
        return bestUrl
    }

    private fun extractSegments(baseUrl: String, m3u8: String, limit: Int): Pair<String?, List<String>> {
        var initMap: String? = null
        val segments = ArrayList<String>(limit)
        val lines = m3u8.lineSequence().map { it.trim() }.toList()
        var expectSegment = false
        for (line in lines) {
            if (line.isEmpty()) continue
            if (line.startsWith("#EXT-X-MAP:") && initMap == null) {
                val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)
                if (uri != null) initMap = resolveUrl(baseUrl, uri)
            } else if (line.startsWith("#EXTINF")) {
                expectSegment = true
            } else if (!line.startsWith("#") && expectSegment) {
                expectSegment = false
                resolveUrl(baseUrl, line)?.let { segments.add(it) }
                if (segments.size >= limit) break
            }
        }
        return initMap to segments
    }

    private fun resolveUrl(baseUrl: String, ref: String): String? = try {
        URI(baseUrl).resolve(ref).toString()
    } catch (_: Exception) {
        null
    }

    private suspend fun readIntoCache(
        factory: CacheDataSource.Factory,
        url: String,
        maxBytes: Long,
        captureBytes: Boolean
    ): Pair<Long, ByteArray?> {
        var src: DataSource? = null
        return try {
            src = factory.createDataSource()
            src.open(DataSpec.Builder().setUri(Uri.parse(url)).build())
            val buf = ByteArray(64 * 1024)
            val baos = if (captureBytes) ByteArrayOutputStream() else null
            var total = 0L
            while (total < maxBytes) {
                coroutineContext.ensureActive()
                val toRead = minOf(buf.size.toLong(), maxBytes - total).toInt()
                val r = src.read(buf, 0, toRead)
                if (r < 0) break
                total += r
                baos?.write(buf, 0, r)
            }
            total to baos?.toByteArray()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            0L to null
        } finally {
            runCatching { src?.close() }
        }
    }

    private suspend fun isAlreadyWarmed(
        diskCache: SimpleCache,
        videoId: String,
        resolvedUrl: String,
        isHls: Boolean,
        minBytes: Long
    ): Boolean {
        val now = System.currentTimeMillis()
        val rec = stateMutex.withLock { records[videoId] }
        if (rec != null && rec.resolvedUrl == resolvedUrl && (now - rec.updatedAtMs) < RECORD_TTL_MS) {
            val diskBytes = runCatching {
                diskCache.getCachedBytes(resolvedUrl, 0L, if (isHls) Long.MAX_VALUE else minBytes)
            }.getOrDefault(0L)
            val valid = if (isHls) rec.hlsWarmed && diskBytes > 0L else rec.cachedBytes >= minBytes && diskBytes >= minBytes
            if (valid) return true
        }
        return false
    }

    private suspend fun ensureLoaded(ctx: Context) {
        if (loadedFromStore) return
        val raw = ctx.smartPrefetchDataStore.data
            .catch { ex -> if (ex is IOException) emit(emptyPreferences()) else throw ex }
            .first()[KEY_RECORDS_JSON]
        stateMutex.withLock {
            if (loadedFromStore) return
            records.clear()
            if (!raw.isNullOrBlank()) {
                val now = System.currentTimeMillis()
                runCatching {
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val vid = o.optString("id")
                        val url = o.optString("url")
                        val ts = o.optLong("ts", 0L)
                        if (vid.isNotBlank() && url.isNotBlank() && (now - ts) < RECORD_TTL_MS) {
                            records[vid] = PrefetchRecord(
                                videoId = vid,
                                resolvedUrl = url,
                                cachedBytes = o.optLong("bytes", 0L),
                                hlsWarmed = o.optBoolean("hls", false),
                                updatedAtMs = ts
                            )
                        }
                    }
                }
            }
            loadedFromStore = true
        }
    }

    private suspend fun recordWarmup(ctx: Context, record: PrefetchRecord) {
        val serialized = stateMutex.withLock {
            records.remove(record.videoId)
            records[record.videoId] = record
            while (records.size > MAX_STORED_RECORDS) {
                val oldestKey = records.keys.firstOrNull() ?: break
                records.remove(oldestKey)
            }
            val arr = JSONArray()
            for (r in records.values) {
                arr.put(
                    JSONObject().apply {
                        put("id", r.videoId)
                        put("url", r.resolvedUrl)
                        put("bytes", r.cachedBytes)
                        put("hls", r.hlsWarmed)
                        put("ts", r.updatedAtMs)
                    }
                )
            }
            arr.toString()
        }
        runCatching {
            ctx.smartPrefetchDataStore.edit { prefs ->
                prefs[KEY_RECORDS_JSON] = serialized
                prefs[KEY_UPDATED_AT] = System.currentTimeMillis()
            }
        }
    }

    private suspend fun saveQueueSnapshot(
        ctx: Context,
        currentIndex: Int,
        currentVideoId: String,
        upcoming: List<Video>
    ) {
        val queueArr = JSONArray()
        for (v in upcoming) {
            queueArr.put(
                JSONObject().apply {
                    put("id", v.id)
                    put("url", Media.playUrl(v.url, v.duration))
                    put("dur", v.duration)
                }
            )
        }
        val queueJson = queueArr.toString()
        runCatching {
            ctx.smartPrefetchDataStore.edit { prefs ->
                prefs[KEY_LAST_INDEX] = currentIndex
                prefs[KEY_LAST_VIDEO_ID] = currentVideoId
                prefs[KEY_QUEUE_JSON] = queueJson
                prefs[KEY_UPDATED_AT] = System.currentTimeMillis()
            }
        }
    }
}
