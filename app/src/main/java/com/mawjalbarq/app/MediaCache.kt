package com.mawjalbarq.app
import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Disk cache for watched clips + warm-up of the next ones (idea ported from the old clips app). */
object MediaCache {
    private var cache: SimpleCache? = null

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

    suspend fun prefetch(ctx: Context, urls: List<String>) = withContext(Dispatchers.IO) {
        val f = factory(ctx)
        for (u in urls.filter { it.startsWith("http") }.take(3)) {
            var src: DataSource? = null
            try {
                src = f.createDataSource()
                src.open(DataSpec.Builder().setUri(u).build())
                val buf = ByteArray(128 * 1024); var total = 0L
                while (total < 6L * 1024 * 1024) { val r = src.read(buf, 0, buf.size); if (r < 0) break; total += r }
            } catch (_: Exception) {} finally { runCatching { src?.close() } }
        }
    }
}
