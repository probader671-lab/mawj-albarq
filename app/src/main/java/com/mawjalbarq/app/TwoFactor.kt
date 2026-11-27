package com.mawjalbarq.app

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Two-step sign-in: TOTP authenticator code (Supabase MFA, free) with an email code as the fallback. */
object Mfa {
    /** Password was accepted but the code has not been entered yet; the session is held here, not in App. */
    var pending by mutableStateOf<JSONObject?>(null)
    var factorId: String = ""
}

private suspend fun authCall(method: String, path: String, body: JSONObject?, token: String?): Pair<Int, String> = withContext(Dispatchers.IO) {
    try {
        val b = Request.Builder().url(Cfg.URL + path).header("apikey", Cfg.ANON).header("Authorization", "Bearer " + (token ?: Cfg.ANON))
        val rb = (body?.toString() ?: "").toRequestBody("application/json".toMediaType())
        when (method) { "GET" -> b.get(); "DELETE" -> b.delete(); else -> b.post(rb) }
        http.newCall(b.build()).execute().use { it.code to (it.body?.string() ?: "") }
    } catch (e: Exception) { -1 to "" }
}

/** Returns the verified TOTP factor id from a token or user response, or null when 2-step is off. */
fun verifiedTotpFactor(userJson: JSONObject?): String? {
    val fs = userJson?.optJSONArray("factors") ?: return null
    for (i in 0 until fs.length()) {
        val f = fs.getJSONObject(i)
        if (f.optString("factor_type") == "totp" && f.optString("status") == "verified") return f.optString("id")
    }
    return null
}

private suspend fun finishLogin(j: JSONObject): String? {
    App.fromAuth(j)
    if (registerDevice()) { val m = banMessage(); App.logout(); Mfa.pending = null; return m }
    Mfa.pending = null
    afterLogin(); return null
}

private fun codeError(c: Int, t: String): String = when {
    c == -1 -> "تعذر الاتصال بالإنترنت"
    c == 429 || "rate" in t.lowercase() -> "محاولات كثيرة، انتظر قليلاً"
    "not authorized" in t.lowercase() -> "خدمة البريد غير مفعّلة لهذا العنوان بعد"
    else -> "الكود غير صحيح أو انتهت صلاحيته"
}

suspend fun mfaVerifyTotp(code: String): String? {
    val p = Mfa.pending ?: return "انتهت الجلسة، سجّل الدخول من جديد"
    val tok = p.optString("access_token")
    val (c1, t1) = authCall("POST", "/auth/v1/factors/${Mfa.factorId}/challenge", JSONObject(), tok)
    if (c1 !in 200..299) return codeError(c1, t1)
    val cid = JSONObject(t1).optString("id")
    val (c2, t2) = authCall("POST", "/auth/v1/factors/${Mfa.factorId}/verify", JSONObject().put("challenge_id", cid).put("code", code.trim()), tok)
    if (c2 !in 200..299) return codeError(c2, t2)
    val j = JSONObject(t2)
    if (!j.has("user")) j.put("user", p.optJSONObject("user"))
    return finishLogin(j)
}

suspend fun mfaSendEmailCode(): String? {
    val em = Mfa.pending?.optJSONObject("user")?.optString("email").orEmpty()
    if (em.isBlank()) return "تعذر تحديد البريد"
    val (c, t) = authCall("POST", "/auth/v1/otp", JSONObject().put("email", em).put("create_user", false), null)
    return if (c in 200..299) null else codeError(c, t)
}

suspend fun mfaVerifyEmailCode(code: String): String? {
    val p = Mfa.pending ?: return "انتهت الجلسة، سجّل الدخول من جديد"
    val em = p.optJSONObject("user")?.optString("email").orEmpty()
    val (c, t) = authCall("POST", "/auth/v1/verify", JSONObject().put("type", "email").put("email", em).put("token", code.trim()), null)
    if (c !in 200..299) return codeError(c, t)
    return finishLogin(JSONObject(t))
}

private fun qr(text: String, size: Int = 512): Bitmap {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    return bmp
}

/** Shown after the password is accepted when the account has 2-step sign-in on. */
@Composable
fun TwoFactorLoginDialog() {
    val p = Mfa.pending ?: return
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var emailMode by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { Mfa.pending = null },
        title = { Text("التحقق بخطوتين", fontFamily = Cairo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (!emailMode) "أدخل الكود المكوّن من 6 أرقام من تطبيق التوثيق" else if (sent) "أرسلنا كودًا إلى بريدك، أدخله هنا" else "سنرسل كودًا إلى بريدك الإلكتروني", fontFamily = Cairo, fontSize = 14.sp)
                if (!emailMode || sent) OutlinedTextField(code, { code = it.filter { c -> c.isDigit() }.take(8) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), label = { Text("الكود") })
                err?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp, fontFamily = Cairo) }
                TextButton(enabled = !busy, onClick = {
                    emailMode = !emailMode; code = ""; err = null; sent = false
                }) { Text(if (emailMode) "استخدام تطبيق التوثيق" else "مشكلة في الموبايل؟ أرسل الكود للبريد", fontFamily = Cairo, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(enabled = !busy && (if (emailMode && !sent) true else code.length >= 6), onClick = {
                busy = true; err = null
                scope.launch {
                    err = if (emailMode && !sent) mfaSendEmailCode().also { if (it == null) sent = true }
                    else if (emailMode) mfaVerifyEmailCode(code) else mfaVerifyTotp(code)
                    busy = false
                }
            }) { Text(if (emailMode && !sent) "إرسال الكود" else "تأكيد", fontFamily = Cairo) }
        },
        dismissButton = { TextButton(onClick = { Mfa.pending = null }) { Text("إلغاء", fontFamily = Cairo) } }
    )
}

/** Profile screen: turn 2-step on (QR) or off. */
@Composable
fun TwoFactorSettingsDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var factorId by remember { mutableStateOf<String?>(null) }   // verified factor, if any
    var enroll by remember { mutableStateOf<JSONObject?>(null) }  // {id, totp:{uri,secret}}
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    suspend fun reload() {
        val (c, t) = authCall("GET", "/auth/v1/user", null, App.token)
        factorId = if (c == 200) verifiedTotpFactor(JSONObject(t)) else null
        loading = false
    }
    LaunchedEffect(Unit) { reload() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("التحقق بخطوتين", fontFamily = Cairo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val e = enroll
                if (loading) CircularProgressIndicator()
                else if (e != null) {
                    val uri = e.getJSONObject("totp").getString("uri"); val secret = e.getJSONObject("totp").getString("secret")
                    Text("امسح الرمز بتطبيق توثيق (Google Authenticator أو غيره) ثم اكتب الكود", fontFamily = Cairo, fontSize = 13.sp)
                    val bmp = remember(uri) { qr(uri) }
                    Image(bmp.asImageBitmap(), null, Modifier.size(200.dp).background(Color.White))
                    TextButton(onClick = {
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { err = "لا يوجد تطبيق توثيق مثبت" }
                    }) { Text("فتح في تطبيق التوثيق على نفس الجهاز", fontFamily = Cairo, fontSize = 12.sp) }
                    Text("أو أدخل المفتاح يدويًا: $secret", fontSize = 11.sp)
                    OutlinedTextField(code, { code = it.filter { c -> c.isDigit() }.take(6) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), label = { Text("الكود") })
                } else if (factorId != null) {
                    Text("التحقق بخطوتين مفعّل ✅\nعند تسجيل الدخول يُطلب كود من التطبيق، أو كود يصل إلى بريدك إن كان هناك مشكلة في الموبايل.", fontFamily = Cairo, fontSize = 14.sp)
                } else {
                    Text("غير مفعّل. فعّله لحماية حسابك حتى لو سُرقت كلمة المرور. يمكنك أيضًا استلام الكود على بريدك إذا واجهت مشكلة في الموبايل.", fontFamily = Cairo, fontSize = 14.sp)
                }
                err?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp, fontFamily = Cairo) }
            }
        },
        confirmButton = {
            val e = enroll
            if (!loading) {
                if (e != null) Button(enabled = !busy && code.length == 6, onClick = {
                    busy = true; err = null
                    scope.launch {
                        val fid = e.getString("id")
                        val (c1, t1) = authCall("POST", "/auth/v1/factors/$fid/challenge", JSONObject(), App.token)
                        if (c1 !in 200..299) err = codeError(c1, t1) else {
                            val (c2, t2) = authCall("POST", "/auth/v1/factors/$fid/verify", JSONObject().put("challenge_id", JSONObject(t1).optString("id")).put("code", code), App.token)
                            if (c2 !in 200..299) err = codeError(c2, t2) else {
                                val j = JSONObject(t2); App.token = j.optString("access_token"); App.refresh = j.optString("refresh_token"); App.save()
                                enroll = null; code = ""; reload()
                            }
                        }
                        busy = false
                    }
                }) { Text("تأكيد وتفعيل", fontFamily = Cairo) }
                else if (factorId != null) Button(enabled = !busy, onClick = {
                    busy = true; err = null
                    scope.launch {
                        val (c, t) = authCall("DELETE", "/auth/v1/factors/$factorId", null, App.token)
                        if (c !in 200..299) err = "تعذر الإلغاء، سجّل الدخول من جديد وحاول" else reload()
                        busy = false
                    }
                }) { Text("إلغاء التفعيل", fontFamily = Cairo) }
                else Button(enabled = !busy, onClick = {
                    busy = true; err = null
                    scope.launch {
                        // drop any half-finished enrollment first
                        val (cu, tu) = authCall("GET", "/auth/v1/user", null, App.token)
                        if (cu == 200) JSONObject(tu).optJSONArray("factors")?.let { fs -> for (i in 0 until fs.length()) { val f = fs.getJSONObject(i); if (f.optString("status") != "verified") authCall("DELETE", "/auth/v1/factors/${f.optString("id")}", null, App.token) } }
                        val (c, t) = authCall("POST", "/auth/v1/factors", JSONObject().put("factor_type", "totp").put("friendly_name", "mawj-" + System.currentTimeMillis()).put("issuer", "موج البرق"), App.token)
                        if (c in 200..299) enroll = JSONObject(t) else err = "تعذر بدء التفعيل"
                        busy = false
                    }
                }) { Text("تفعيل", fontFamily = Cairo) }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("إغلاق", fontFamily = Cairo) } }
    )
}
