package com.mawjalbarq.app
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

private const val REL = "https://api.github.com/repos/probader671-lab/mawj-albarq/releases/tags/demo"

private suspend fun latest(): Pair<Int, String>? = withContext(Dispatchers.IO) {
    try {
        http.newCall(Request.Builder().url(REL).build()).execute().use {
            if (it.code != 200) return@use null
            val j = JSONObject(it.body!!.string())
            val code = Regex("versionCode=(\\d+)").find(j.optString("body"))?.groupValues?.get(1)?.toIntOrNull() ?: return@use null
            val assets = j.optJSONArray("assets") ?: return@use null
            if (assets.length() == 0) return@use null
            code to assets.getJSONObject(0).getString("browser_download_url")
        }
    } catch (e: Exception) { null }
}

@Composable
fun UpdatePrompt() {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<Pair<Int, String>?>(null) }
    LaunchedEffect(Unit) { latest()?.let { if (it.first > BuildConfig.VERSION_CODE) info = it } }
    info?.let { (_, url) ->
        AlertDialog(onDismissRequest = { info = null },
            title = { Text("تحديث جديد") }, text = { Text("توجد نسخة أحدث من موج البرق. هل تريد تنزيلها؟") },
            confirmButton = { TextButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); info = null }) { Text("تحديث") } },
            dismissButton = { TextButton(onClick = { info = null }) { Text("لاحقاً") } })
    }
}
