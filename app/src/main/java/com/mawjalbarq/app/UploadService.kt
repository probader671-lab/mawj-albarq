package com.mawjalbarq.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/** Keeps the process and CPU alive while videos are being compressed/uploaded, so leaving the app does not stop the upload. */
class UploadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wl: PowerManager.WakeLock? = null
    private var loop: Job? = null

    private fun notif(text: String): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null)
            nm.createNotificationChannel(NotificationChannel(CH, "رفع الفيديو", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CH).setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("موج البرق")
            .setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "RETRY") UploadJobs.retry(applicationContext)
        val n = notif("جارٍ رفع الفيديو...")
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        if (wl == null) {
            wl = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mawj:upload").apply { setReferenceCounted(false); acquire(3 * 60 * 60 * 1000L) }
        }
        if (loop == null) loop = scope.launch {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            while (UploadJobs.pending > 0) {
                nm.notify(ID, notif(UploadJobs.status.ifBlank { "جارٍ رفع الفيديو..." }))
                delay(1500)
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { loop?.cancel(); wl?.let { if (it.isHeld) it.release() }; scope.cancel(); super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null

    companion object {
        const val CH = "upload"; const val ID = 4471
        fun start(ctx: Context) {
            try { androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, UploadService::class.java)) } catch (_: Exception) { }
        }
    }
}
