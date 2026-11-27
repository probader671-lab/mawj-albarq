package com.mawjalbarq.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Instant push without Google: a foreground service keeps one Supabase Realtime WebSocket open and shows a phone notification the moment a notification row is inserted for the user. */
class PushService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (job?.isActive != true) job = scope.launch { loop() }
        return START_STICKY
    }

    override fun onDestroy() { job?.cancel(); scope.cancel(); super.onDestroy() }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("mawj_live", "الإشعارات الفورية", NotificationManager.IMPORTANCE_MIN))
        val n: Notification = NotificationCompat.Builder(this, "mawj_live").setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("موج البرق").setContentText("الإشعارات الفورية مفعّلة").setOngoing(true).setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(android.app.PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE)).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(4401, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(4401, n)
    }

    private suspend fun loop() {
        var delayMs = 3000L
        while (currentCoroutineContext().isActive) {
            App.init(applicationContext)
            val uid = App.userId
            if (uid == null || App.token == null) { stopSelf(); return }
            val closed = CompletableDeferred<Unit>()
            var ws: WebSocket? = null
            var gotJoin = false
            try {
                if (App.refresh != null) refreshSession()
                val client = http.newBuilder().readTimeout(0, TimeUnit.SECONDS).pingInterval(0, TimeUnit.SECONDS).build()
                val req = Request.Builder().url("wss://oratzvhdqlicmusmkqsv.supabase.co/realtime/v1/websocket?apikey=${Cfg.ANON}&vsn=1.0.0").build()
                ws = client.newWebSocket(req, object : WebSocketListener() {
                    override fun onOpen(w: WebSocket, r: Response) {
                        val join = JSONObject().put("topic", "realtime:mawj_notifs").put("event", "phx_join").put("ref", "1").put("join_ref", "1").put("payload",
                            JSONObject().put("access_token", App.token).put("config", JSONObject().put("postgres_changes", org.json.JSONArray().put(
                                JSONObject().put("event", "INSERT").put("schema", "public").put("table", "notifications").put("filter", "user_id=eq.$uid")))))
                        w.send(join.toString())
                    }
                    override fun onMessage(w: WebSocket, text: String) {
                        try {
                            val j = JSONObject(text)
                            when (j.optString("event")) {
                                "phx_reply" -> if (j.optString("topic") == "realtime:mawj_notifs" && j.optJSONObject("payload")?.optString("status") == "ok") gotJoin = true
                                "postgres_changes" -> {
                                    val rec = j.getJSONObject("payload").getJSONObject("data").getJSONObject("record")
                                    val type = rec.optString("type")
                                    if (!Fg.on && type in setOf("new_video", "like", "comment", "follow")) {
                                        val id = rec.optString("id"); val actor = rec.optString("actor_id")
                                        scope.launch {
                                            showPhoneNotification(applicationContext, id, type, actor)
                                            getSharedPreferences("push", Context.MODE_PRIVATE).edit().putString("last_$uid", rec.optString("created_at")).apply()
                                        }
                                    }
                                }
                                "phx_close", "phx_error" -> w.close(1000, null)
                            }
                        } catch (_: Exception) {}
                    }
                    override fun onClosed(w: WebSocket, code: Int, reason: String) { closed.complete(Unit) }
                    override fun onFailure(w: WebSocket, t: Throwable, r: Response?) { closed.complete(Unit) }
                })
                var ref = 10; var sinceRefresh = 0
                while (!closed.isCompleted) {
                    delay(25_000); sinceRefresh += 25
                    if (closed.isCompleted) break
                    ws.send(JSONObject().put("topic", "phoenix").put("event", "heartbeat").put("payload", JSONObject()).put("ref", (ref++).toString()).toString())
                    if (sinceRefresh >= 2700) {   // new access token every 45 min so the channel keeps its authorization
                        sinceRefresh = 0
                        if (refreshSession()) ws.send(JSONObject().put("topic", "realtime:mawj_notifs").put("event", "access_token").put("ref", (ref++).toString())
                            .put("payload", JSONObject().put("access_token", App.token)).toString())
                    }
                }
                if (gotJoin) delayMs = 3000L
            } catch (_: Exception) {}
            runCatching { ws?.cancel() }
            delay(delayMs); delayMs = minOf(delayMs * 2, 60_000L)
        }
    }

    companion object {
        fun enabled(ctx: Context) = ctx.getSharedPreferences("push", Context.MODE_PRIVATE).getBoolean("instant", true)
        fun setEnabled(ctx: Context, on: Boolean) { ctx.getSharedPreferences("push", Context.MODE_PRIVATE).edit().putBoolean("instant", on).apply(); if (on) start(ctx) else stop(ctx) }
        fun start(ctx: Context) { if (!enabled(ctx) || !App.loggedIn) return; try { ctx.startForegroundService(Intent(ctx, PushService::class.java)) } catch (_: Exception) {} }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, PushService::class.java)) }
    }
}
