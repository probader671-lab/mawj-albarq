package com.mawjalbarq.app
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

private const val REL = "https://api.github.com/repos/probader671-lab/mawj-albarq/releases/tags/demo"
private const val APK = "https://github.com/probader671-lab/mawj-albarq/releases/download/demo/mawj-albarq.apk"

private suspend fun latest(): Pair<Int, String>? = withContext(Dispatchers.IO) {
    try {
        http.newCall(Request.Builder().url(REL).build()).execute().use {
            if (it.code != 200) return@use null
            val j = JSONObject(it.body!!.string())
            val code = Regex("versionCode=(\\d+)").find(j.optString("body"))?.groupValues?.get(1)?.toIntOrNull() ?: return@use null
            code to APK
        }
    } catch (e: Exception) { null }
}

private suspend fun download(ctx: android.content.Context, url: String, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
    try {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val f = File(dir, "mawj-albarq.apk")
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.code != 200) return@use null
            val total = r.body!!.contentLength().toFloat()
            r.body!!.byteStream().use { i -> f.outputStream().use { o ->
                val buf = ByteArray(65536); var done = 0L
                while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n
                    if (total > 0) onProgress(done / total) }
            } }
            f
        }
    } catch (e: Exception) { null }
}

@Suppress("DEPRECATION")
private fun isDebugSigned(ctx: android.content.Context): Boolean = try {
    val pkg = if (Build.VERSION.SDK_INT >= 28) {
        ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners?.firstOrNull()
    } else {
        ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            .signatures?.firstOrNull()
    }
    val cert = pkg?.let {
        java.security.cert.CertificateFactory.getInstance("X.509")
            .generateCertificate(it.toByteArray().inputStream()) as? java.security.cert.X509Certificate
    }
    cert?.subjectX500Principal?.name?.contains("Android Debug", ignoreCase = true) == true
} catch (_: Exception) { BuildConfig.DEBUG }

@Composable
fun UpdatePrompt() {
    val ctx = LocalContext.current
    if (remember { isDebugSigned(ctx) }) return
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var err by remember { mutableStateOf(false) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && !busy) scope.launch { latest()?.let { if (it.first > BuildConfig.VERSION_CODE) info = it } }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    info?.let { (_, url) ->
        AlertDialog(onDismissRequest = { if (!busy) info = null },
            title = { Text("تحديث جديد") },
            text = { Column {
                Text(if (err) "تعذر التنزيل، حاول مرة أخرى." else "توجد نسخة أحدث من موج البرق. اضغط تحديث وسيتم التنزيل والتثبيت مباشرة.")
                if (busy) { Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) }
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)))
                    return@TextButton
                }
                busy = true; err = false
                scope.launch {
                    val fresh = latest()
                    val f = download(ctx, fresh?.second ?: url) { progress = it }
                    busy = false
                    if (f == null) { err = true; return@launch }
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                    ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                    info = null
                }
            }) { Text("تحديث") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { info = null }) { Text("لاحقاً") } })
    }
}
