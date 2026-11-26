package com.mawjalbarq.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.videoMetadataDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "video_metadata_cache"
)

data class CachedVideoFeed(
    val videos: List<Video>,
    val updatedAtMs: Long
)

object VideoMetadataCache {
    private val KEY_VIDEOS_JSON = stringPreferencesKey("cached_videos_json")
    private val KEY_UPDATED_AT = longPreferencesKey("cached_videos_updated_at")

    suspend fun save(ctx: Context, videos: List<Video>) = withContext(Dispatchers.IO) {
        val json = encode(videos)
        ctx.applicationContext.videoMetadataDataStore.edit { prefs ->
            prefs[KEY_VIDEOS_JSON] = json
            prefs[KEY_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun load(ctx: Context): CachedVideoFeed? = withContext(Dispatchers.IO) {
        val prefs = ctx.applicationContext.videoMetadataDataStore.data
            .catch { ex ->
                if (ex is IOException) emit(emptyPreferences()) else throw ex
            }
            .first()
        val raw = prefs[KEY_VIDEOS_JSON] ?: return@withContext null
        val list = decode(raw)
        if (list.isEmpty()) return@withContext null
        CachedVideoFeed(
            videos = list,
            updatedAtMs = prefs[KEY_UPDATED_AT] ?: 0L
        )
    }

    suspend fun clear(ctx: Context) = withContext(Dispatchers.IO) {
        ctx.applicationContext.videoMetadataDataStore.edit { prefs ->
            prefs.remove(KEY_VIDEOS_JSON)
            prefs.remove(KEY_UPDATED_AT)
        }
    }

    private fun encode(videos: List<Video>): String {
        val arr = JSONArray()
        for (v in videos) {
            arr.put(
                JSONObject().apply {
                    put("id", v.id)
                    put("title", v.title)
                    put("desc", v.desc)
                    put("url", v.url)
                    if (v.thumb != null) put("thumb", v.thumb) else put("thumb", JSONObject.NULL)
                    put("author", v.author)
                    put("likes", v.likes)
                    put("comments", v.comments)
                    put("userId", v.userId)
                    put("w", v.w)
                    put("h", v.h)
                    if (v.avatar != null) put("avatar", v.avatar) else put("avatar", JSONObject.NULL)
                    put("duration", v.duration)
                    put("views", v.views)
                    put("createdAt", v.createdAt)
                    put("verified", v.verified)
                    put("followers", v.followers)
                }
            )
        }
        return arr.toString()
    }

    private fun decode(raw: String): List<Video> {
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<Video>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                val url = o.optString("url")
                if (id.isBlank() || url.isBlank()) continue
                list.add(
                    Video(
                        id = id,
                        title = o.optString("title"),
                        desc = o.optString("desc"),
                        url = url,
                        thumb = if (o.isNull("thumb")) null else o.optString("thumb").ifBlank { null },
                        author = o.optString("author").ifBlank { "موج البرق" },
                        likes = o.optInt("likes", 0),
                        comments = o.optInt("comments", 0),
                        userId = o.optString("userId"),
                        w = o.optInt("w", 0),
                        h = o.optInt("h", 0),
                        avatar = if (o.isNull("avatar")) null else o.optString("avatar").ifBlank { null },
                        duration = o.optInt("duration", 0).coerceAtLeast(0),
                        views = o.optLong("views", 0L).coerceAtLeast(0L),
                        createdAt = o.optString("createdAt"),
                        verified = o.optBoolean("verified", false),
                        followers = o.optLong("followers", 0L).coerceAtLeast(0L)
                    )
                )
            }
            list.distinctBy { it.id }.distinctBy { it.url }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
