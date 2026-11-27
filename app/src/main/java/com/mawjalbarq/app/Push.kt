package com.mawjalbarq.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** True while the app UI is on screen (no phone notification is needed then). */
object Fg { @Volatile var on = false }

/** Push without Firebase: a periodic background check of the user's notification rows (new video from someone followed, likes, comments, follows) shown as normal phone notifications. */
class PushWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        App.init(ctx)
        val u = App.userId ?: return Result.success()
        if (Fg.on) return Result.success()
        val sp = ctx.getSharedPreferences("push", Context.MODE_PRIVATE)
        val last = sp.getString("last_$u", null)
        val nowIso = java.time.Instant.now().toString()
        if (last == null) { sp.edit().putString("last_$u", nowIso).apply(); return Result.success() }   // first run: do not replay old rows
        val enc = java.net.URLEncoder.encode(last, "UTF-8")
        val (c, t) = api("GET", "/rest/v1/notifications?user_id=eq.$u&is_read=eq.false&created_at=gt.$enc&type=in.(new_video,like,comment,follow)&select=id,type,actor_id,video_id,created_at&order=created_at.asc&limit=20")
        if (c != 200) return if (c == -1) Result.retry() else Result.success()
        val a = try { JSONArray(t) } catch (_: Exception) { return Result.success() }
        if (a.length() == 0) return Result.success()
        val ids = (0 until a.length()).mapNotNull { a.getJSONObject(it).optString("actor_id").takeIf { s -> s.isNotBlank() && s != "null" } }.distinct()
        val names = HashMap<String, String>()
        if (ids.isNotEmpty()) {
            val (c2, t2) = api("GET", "/rest/v1/profiles?id=in.(${ids.joinToString(",")})&select=id,full_name")
            if (c2 == 200) try { val b = JSONArray(t2); for (i in 0 until b.length()) { val o = b.getJSONObject(i); names[o.getString("id")] = o.optString("full_name").ifBlank { "مستخدم" } } } catch (_: Exception) {}
        }
        ensureChannel(ctx)
        val canPost = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        var newest = last
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val ts = o.optString("created_at"); if (ts > newest) newest = ts
            if (!canPost) continue
            val who = names[o.optString("actor_id")] ?: "مستخدم"
            val msg = when (o.optString("type")) {
                "new_video" -> "$who نزّل فيديو جديد"
                "like" -> "$who أعجبه مقطعك"
                "comment" -> "$who علّق على مقطعك"
                else -> "$who بدأ بمتابعتك"
            }
            val open = PendingIntent.getActivity(ctx, i, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, CH).setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle("موج البرق").setContentText(msg)
                .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
            try { NotificationManagerCompat.from(ctx).notify(o.optString("id").hashCode(), n) } catch (_: SecurityException) {}
        }
        sp.edit().putString("last_$u", newest).apply()
        return Result.success()
    }

    companion object {
        const val CH = "mawj_updates"
        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 26) (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(NotificationChannel(CH, "تحديثات موج البرق", NotificationManager.IMPORTANCE_DEFAULT))
        }
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<PushWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("mawj_push", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
