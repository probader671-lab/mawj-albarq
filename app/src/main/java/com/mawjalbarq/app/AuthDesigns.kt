package com.mawjalbarq.app

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class AuthDesign(val num: Int, val title: String) {
    ORBS_FLIP(1, "النظام المتحرك"),
    PURPLE_SLIDE(2, "لوحة جانبية"),
    MATRIX(3, "ماتريكس"),
    RETRO_CYBER(4, "ريترو المستقبل")
}

private object AuthDesignPrefs {
    fun load(ctx: Context): AuthDesign {
        val n = ctx.getSharedPreferences("auth_ui_prefs", Context.MODE_PRIVATE).getInt("design", 1)
        return AuthDesign.entries.firstOrNull { it.num == n } ?: AuthDesign.ORBS_FLIP
    }

    fun save(ctx: Context, design: AuthDesign) {
        ctx.getSharedPreferences("auth_ui_prefs", Context.MODE_PRIVATE).edit().putInt("design", design.num).apply()
    }
}

private class AuthFormHolder {
    var isSignup by mutableStateOf(false)
    var name by mutableStateOf("")
    var email by mutableStateOf("")
    var pass by mutableStateOf("")
    var confirmPass by mutableStateOf("")
    var showPass by mutableStateOf(false)
    var avatarUri by mutableStateOf<android.net.Uri?>(null)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

@Composable
fun AuthPortalScreen(prefs: ThemePrefs, onSuggestions: () -> Unit) {
    DarkLock()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var design by remember { mutableStateOf(AuthDesignPrefs.load(ctx)) }
    val form = remember { AuthFormHolder() }

    fun submit() {
        val em = form.email.trim()
        if (em.isBlank() || form.pass.length < 8) {
            form.error = "أدخل البريد الإلكتروني وكلمة مرور من 8 أحرف على الأقل"
            return
        }
        if (form.isSignup && form.confirmPass != form.pass) {
            form.error = "كلمتا المرور غير متطابقتين"
            return
        }
        val pic = form.avatarUri
        if (form.isSignup && pic == null) {
            form.error = "صورة الحساب إجبارية، اختر صورة لإنشاء الحساب"
            return
        }
        form.busy = true
        form.error = null
        scope.launch {
            if (form.isSignup) {
                val url = uploadImageToCloud(ctx, pic!!)
                form.error = if (url == null) "تعذر رفع الصورة، تحقق من الاتصال وحاول مرة أخرى"
                else signUp(form.name.trim(), em, form.pass, url)
            } else form.error = signIn(em, form.pass)
            form.busy = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (design) {
            AuthDesign.ORBS_FLIP -> OrbsBackdrop(Modifier.fillMaxSize())
            AuthDesign.PURPLE_SLIDE -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E))))
            )
            AuthDesign.MATRIX -> MatrixRainBackdrop(Modifier.fillMaxSize())
            AuthDesign.RETRO_CYBER -> RetroCyberBackdrop(Modifier.fillMaxSize())
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DesignSwitcherBar(
                active = design,
                onSelect = { d ->
                    design = d
                    form.error = null
                    AuthDesignPrefs.save(ctx, d)
                }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                design.title,
                color = Color(0xCCFFFFFF),
                fontSize = 12.sp,
                fontFamily = Cairo
            )
            Spacer(Modifier.height(14.dp))

            Box(Modifier.fillMaxWidth().widthIn(max = 430.dp)) {
                when (design) {
                    AuthDesign.ORBS_FLIP -> OrbsFlipCard(form, ::submit)
                    AuthDesign.PURPLE_SLIDE -> PurpleSlideCard(form, ::submit)
                    AuthDesign.MATRIX -> MatrixAuthCard(form, ::submit)
                    AuthDesign.RETRO_CYBER -> RetroCyberCard(form, ::submit)
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 430.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x8810141F))
                    .border(0.75.dp, Color(0x28FFFFFF), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onSuggestions)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Lightbulb, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Text("الاقتراحات", color = Color.White, fontSize = 12.sp, fontFamily = Cairo)
                }
                var settings by remember { mutableStateOf(false) }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).clickable { settings = true }.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Settings, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("الإعدادات", color = Color.White, fontSize = 12.sp, fontFamily = Cairo)
                }
                if (settings) SettingsSheet(prefs) { settings = false }
            }
        }
    }
}

@Composable
private fun DesignSwitcherBar(active: AuthDesign, onSelect: (AuthDesign) -> Unit) {
    val barBg = if (active == AuthDesign.MATRIX) Color(0xCC001400) else Color(0x22FFFFFF)
    val barBorder = if (active == AuthDesign.MATRIX) Color(0xFF00FF00) else Color(0x3DFFFFFF)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(barBg)
            .border(1.dp, barBorder, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AuthDesign.entries.forEach { d ->
            val selected = d == active
            val activeBg = when (active) {
                AuthDesign.ORBS_FLIP -> Color(0xFFFF6B6B)
                AuthDesign.PURPLE_SLIDE -> Color.White
                AuthDesign.MATRIX -> Color(0xFF00FF00)
                AuthDesign.RETRO_CYBER -> Color(0xFF00FFFF)
            }
            val activeText = when (active) {
                AuthDesign.PURPLE_SLIDE -> Color(0xFF573B8A)
                AuthDesign.MATRIX, AuthDesign.RETRO_CYBER -> Color.Black
                AuthDesign.ORBS_FLIP -> Color.White
            }
            val idleBg = if (active == AuthDesign.MATRIX) Color(0x80000000) else Color(0x22FFFFFF)
            val idleText = if (active == AuthDesign.MATRIX) Color(0xFF00FF00) else Color.White

            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .size(36.dp)
                    .scale(if (selected) 1.1f else 1f)
                    .clip(CircleShape)
                    .background(if (selected) activeBg else idleBg)
                    .border(
                        width = if (active == AuthDesign.MATRIX || selected) 1.2.dp else 0.75.dp,
                        color = if (active == AuthDesign.MATRIX) Color(0xFF00FF00) else Color(0x44FFFFFF),
                        shape = CircleShape
                    )
                    .clickable { onSelect(d) }
                    .testTag("auth_design_${d.num}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    d.num.toString(),
                    color = if (selected) activeText else idleText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = Cairo
                )
            }
        }
    }
}

/* ==================== DESIGN 1: ORBS + 3D FLIP CARD (login.html) ==================== */

@Composable
private fun OrbsBackdrop(modifier: Modifier) {
    val tr = rememberInfiniteTransition(label = "orbs")
    val t by tr.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "orbT")
    val pt by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "pt")

    Canvas(modifier.background(Color(0xFF0F0F0F))) {
        val w = size.width
        val h = size.height
        val o1 = Offset(w * 0.16f + cos(t) * 40f, h * 0.16f + sin(t) * 45f)
        val o2 = Offset(w * 0.84f + sin(t * 0.8f) * 45f, h * 0.82f + cos(t * 0.8f) * 40f)
        val o3 = Offset(w * 0.50f + cos(t * 1.2f) * 35f, h * 0.48f + sin(t * 1.2f) * 35f)

        drawCircle(Brush.radialGradient(listOf(Color(0x99FF6B6B), Color.Transparent), center = o1, radius = w * 0.62f), radius = w * 0.62f, center = o1)
        drawCircle(Brush.radialGradient(listOf(Color(0x994FACFE), Color.Transparent), center = o2, radius = w * 0.68f), radius = w * 0.68f, center = o2)
        drawCircle(Brush.radialGradient(listOf(Color(0x66764BA2), Color.Transparent), center = o3, radius = w * 0.48f), radius = w * 0.48f, center = o3)

        for (i in 0 until 20) {
            val fx = ((i * 73 % 100) / 100f) * w
            val fy = (((1f - pt) + (i * 0.13f)) % 1f) * h
            val alpha = sin((((fy / h).coerceIn(0f, 1f)) * PI).toFloat()) * 0.45f
            drawCircle(Color.White.copy(alpha = alpha), radius = (2f + (i % 3) * 1.3f).dp.toPx(), center = Offset(fx, fy))
        }
    }
}

@Composable
private fun OrbsFlipCard(form: AuthFormHolder, onSubmit: () -> Unit) {
    val flipDeg by animateFloatAsState(
        targetValue = if (form.isSignup) 180f else 0f,
        animationSpec = tween(650),
        label = "cardFlip"
    )
    val tr = rememberInfiniteTransition(label = "stripe")
    val shift by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "shift")
    val showBack = flipDeg > 90f

    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = flipDeg
                cameraDistance = 14f * density
            }
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x991E1E1E))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(24.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFF6B6B),
                            if (shift > 0.5f) Color(0xFF4FACFE) else Color(0xFFEE5A52),
                            Color(0xFF4FACFE),
                            Color(0xFFFF6B6B)
                        )
                    )
                )
        )
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { rotationY = if (showBack) 180f else 0f }
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (showBack) "إنشاء حساب" else "تسجيل الدخول",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Cairo
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (showBack) "انضم إلى موج البرق اليوم" else "مرحباً بك مجدداً في موج البرق",
                color = Color(0xFFAAAAAA),
                fontSize = 14.sp,
                fontFamily = Cairo
            )
            Spacer(Modifier.height(18.dp))

            ErrorBanner(form.error, bg = Color(0x33FF6B6B), fg = Color(0xFFFF8E8E))

            if (showBack) {
                AvatarPickerField(form)
                StyledDarkField(form.name, { form.name = it }, "الاسم (اختياري)", Color(0xFF4FACFE))
                Spacer(Modifier.height(10.dp))
            }
            StyledDarkField(form.email, { form.email = it.trim() }, "البريد الإلكتروني", Color(0xFFFF6B6B), keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(10.dp))
            StyledDarkField(
                form.pass, { form.pass = it }, "كلمة المرور (8+ أحرف)", Color(0xFFFF6B6B), password = !form.showPass,
                onTogglePass = { form.showPass = !form.showPass }
            )
            if (showBack) {
                Spacer(Modifier.height(10.dp))
                StyledDarkField(form.confirmPass, { form.confirmPass = it }, "تأكيد كلمة المرور", Color(0xFF4FACFE), password = !form.showPass)
            }

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFFF6B6B), Color(0xFFEE5A52))))
                    .clickable(enabled = !form.busy, onClick = onSubmit)
                    .testTag("auth_submit_button"),
                contentAlignment = Alignment.Center
            ) {
                if (form.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (showBack) "إنشاء حساب" else "تسجيل الدخول", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Cairo)
            }

            OrDivider(Color(0x24FFFFFF), Color(0x99FFFFFF))
            GoogleWhiteButton()

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (showBack) "لديك حساب بالفعل؟" else "ليس لديك حساب؟", color = Color(0x88FFFFFF), fontSize = 13.sp, fontFamily = Cairo)
                Text(
                    if (showBack) "تسجيل الدخول" else "إنشاء حساب جديد",
                    color = Color(0xFFEE5A52),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Cairo,
                    modifier = Modifier.clickable { form.isSignup = !form.isSignup; form.error = null }
                )
            }
        }
    }
}

/* ==================== DESIGN 2: PURPLE SLIDE CARD (login2.html) ==================== */

@Composable
private fun PurpleSlideCard(form: AuthFormHolder, onSubmit: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF573B8A))))
                .padding(horizontal = 22.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(targetState = form.isSignup, label = "purpleOverlay") { signup ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (signup) "هل لديك حساب بالفعل؟" else "ليس لديك حساب؟",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Cairo,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (signup) "سجل الدخول الآن لتبقى على اتصال" else "انضم إلينا الآن وابدأ رحلتك معنا",
                        color = Color(0xDDFFFFFF),
                        fontSize = 13.sp,
                        fontFamily = Cairo,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { form.isSignup = !form.isSignup; form.error = null },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.2.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text(if (signup) "تسجيل الدخول" else "إنشاء حساب جديد", fontFamily = Cairo, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
            Text(
                if (form.isSignup) "إنشاء حساب" else "تسجيل الدخول",
                color = Color(0xFF333333),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Cairo
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (form.isSignup) "استخدم بريدك الإلكتروني للتسجيل" else "استخدم حسابك الخاص للدخول",
                color = Color(0xFF666666),
                fontSize = 12.sp,
                fontFamily = Cairo
            )
            Spacer(Modifier.height(12.dp))

            ErrorBanner(form.error, bg = Color(0x22FF4D4D), fg = Color(0xFFD32F2F))

            if (form.isSignup) {
                AvatarPickerField(form)
                LightFilledInput(form.name, { form.name = it }, "الاسم (اختياري)")
                Spacer(Modifier.height(8.dp))
            }
            LightFilledInput(form.email, { form.email = it.trim() }, "البريد الإلكتروني", KeyboardType.Email)
            Spacer(Modifier.height(8.dp))
            LightFilledInput(
                form.pass, { form.pass = it }, "كلمة المرور", KeyboardType.Password, password = !form.showPass,
                onTogglePass = { form.showPass = !form.showPass }
            )
            if (form.isSignup) {
                Spacer(Modifier.height(8.dp))
                LightFilledInput(form.confirmPass, { form.confirmPass = it }, "تأكيد كلمة المرور", KeyboardType.Password, password = !form.showPass)
            }

            Spacer(Modifier.height(14.dp))
            Button(
                enabled = !form.busy,
                onClick = onSubmit,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF573B8A)),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auth_submit_button")
            ) {
                if (form.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (form.isSignup) "إنشاء حساب" else "تسجيل الدخول", fontFamily = Cairo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            OrDivider(Color(0xFFE0E0E0), Color(0xFF888888))
            GoogleWhiteButton(border = Color(0xFFDDDDDD))
        }
    }
}

/* ==================== DESIGN 3: MATRIX MODE (login3.html) ==================== */

@Composable
private fun MatrixRainBackdrop(modifier: Modifier) {
    val tr = rememberInfiniteTransition(label = "matrix")
    val tick by tr.animateFloat(0f, 100f, infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart), label = "tick")
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textSize = 36f
        }
    }
    val glyphs = remember { listOf("0", "1", "م", "ب", "7", "X", "9", "A", "Z", "3") }

    Canvas(modifier.background(Color.Black)) {
        val colW = 44f
        val cols = (size.width / colW).toInt().coerceAtLeast(8)
        for (c in 0 until cols) {
            val speed = 1f + (c % 4) * 0.45f
            val headY = ((tick * speed * 14f + c * 97f) % (size.height + 360f)) - 120f
            val x = c * colW + 10f
            for (k in 0 until 9) {
                val y = headY - k * 34f
                if (y < -40f || y > size.height + 40f) continue
                val alpha = ((1f - k / 9f) * 0.42f).coerceIn(0.04f, 0.45f)
                paint.color = Color(0xFF00FF00).copy(alpha = alpha).toArgb()
                val ch = glyphs[(c + k + tick.toInt()) % glyphs.size]
                drawContext.canvas.nativeCanvas.drawText(ch, x, y, paint)
            }
        }
    }
}

@Composable
private fun MatrixAuthCard(form: AuthFormHolder, onSubmit: () -> Unit) {
    val tr = rememberInfiniteTransition(label = "mPulse")
    val pulse by tr.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "p")
    val neonGreen = Color(0xFF00FF00)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xB3001400))
            .border(1.dp, Color(0x4D00FF00), RoundedCornerShape(16.dp))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Terminal, null, tint = neonGreen, modifier = Modifier.size(38.dp).scale(pulse))
        Spacer(Modifier.height(6.dp))
        Text("نظام الدخول الآمن", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Cairo)
        Spacer(Modifier.height(14.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x88000000))
                .border(1.dp, Color(0x3300FF00), RoundedCornerShape(10.dp))
                .padding(4.dp)
        ) {
            listOf(false to "تسجيل الدخول", true to "حساب جديد").forEach { (isUp, lbl) ->
                val on = form.isSignup == isUp
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) neonGreen else Color.Transparent)
                        .clickable { form.isSignup = isUp; form.error = null }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(lbl, color = if (on) Color.Black else Color(0x99FFFFFF), fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Cairo)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        ErrorBanner(form.error, bg = Color(0x33FF0000), fg = Color(0xFFFF6666))

        if (form.isSignup) {
            AvatarPickerField(form)
            MatrixField(form.name, { form.name = it }, "الاسم (اختياري)", Icons.Filled.Person)
            Spacer(Modifier.height(10.dp))
        }
        MatrixField(form.email, { form.email = it.trim() }, "البريد الإلكتروني", Icons.Filled.Email, keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(10.dp))
        MatrixField(
            form.pass, { form.pass = it }, "كلمة المرور", Icons.Filled.Lock, password = !form.showPass,
            onTogglePass = { form.showPass = !form.showPass }
        )
        if (form.isSignup) {
            Spacer(Modifier.height(10.dp))
            MatrixField(form.confirmPass, { form.confirmPass = it }, "تأكيد كلمة المرور", Icons.Filled.Lock, password = !form.showPass)
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            enabled = !form.busy,
            onClick = onSubmit,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, neonGreen),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = neonGreen),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auth_submit_button")
        ) {
            if (form.busy) CircularProgressIndicator(Modifier.size(20.dp), color = neonGreen, strokeWidth = 2.dp)
            else Text(if (form.isSignup) "إنشاء الحساب" else "دخول للنظام", fontFamily = Cairo, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        OrDivider(Color(0x3300FF00), Color(0x8800FF00))
        GoogleWhiteButton()
    }
}

/* ==================== DESIGN 4: RETRO CYBER (login4.html) ==================== */

@Composable
private fun RetroCyberBackdrop(modifier: Modifier) {
    val tr = rememberInfiniteTransition(label = "cyberGrid")
    val shift by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart), label = "s")
    Canvas(
        modifier.background(
            Brush.linearGradient(
                listOf(Color(0xFF0A0A2E), Color(0xFF16213E), Color(0xFF0F3460), Color(0xFF533483), Color(0xFFE94560))
            )
        )
    ) {
        val step = 38.dp.toPx()
        val off = shift * step
        var x = off - step
        while (x < size.width + step) {
            var y = off - step
            while (y < size.height + step) {
                drawCircle(Color(0x4DFFFF00), radius = 1.4.dp.toPx(), center = Offset(x, y))
                y += step
            }
            x += step
        }
    }
}

@Composable
private fun RetroCyberCard(form: AuthFormHolder, onSubmit: () -> Unit) {
    val cyan = Color(0xFF00FFFF)
    val magenta = Color(0xFFFF00FF)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Color(0x4D00FFFF), RoundedCornerShape(24.dp))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color(0x1AFFFFFF))
                .border(2.dp, cyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Bolt, null, tint = magenta, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "موج البرق",
            style = TextStyle(brush = Brush.linearGradient(listOf(magenta, cyan)), fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = Cairo)
        )
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x4D000000))
                .border(1.dp, Color(0x3300FFFF), RoundedCornerShape(12.dp))
                .padding(5.dp)
        ) {
            listOf(false to "تسجيل الدخول", true to "حساب جديد").forEach { (isUp, lbl) ->
                val on = form.isSignup == isUp
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) cyan else Color.Transparent)
                        .clickable { form.isSignup = isUp; form.error = null }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(lbl, color = if (on) Color.Black else Color(0xB3FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Cairo)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        ErrorBanner(form.error, bg = Color(0x44FF0055), fg = Color(0xFFFFB3C6))

        if (form.isSignup) {
            AvatarPickerField(form)
            CyberLabeledField("الاسم // NAME", form.name, { form.name = it }, "مستخدم جديد")
            Spacer(Modifier.height(10.dp))
        }
        CyberLabeledField("البريد الإلكتروني // EMAIL", form.email, { form.email = it.trim() }, "name@example.com", KeyboardType.Email)
        Spacer(Modifier.height(10.dp))
        CyberLabeledField(
            "كلمة المرور // PASSWORD", form.pass, { form.pass = it }, "••••••••", KeyboardType.Password,
            password = !form.showPass, onTogglePass = { form.showPass = !form.showPass }
        )
        if (form.isSignup) {
            Spacer(Modifier.height(10.dp))
            CyberLabeledField(
                "تأكيد كلمة المرور // CONFIRM", form.confirmPass, { form.confirmPass = it }, "••••••••", KeyboardType.Password,
                password = !form.showPass
            )
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            enabled = !form.busy,
            onClick = onSubmit,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(2.dp, magenta),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = magenta),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auth_submit_button")
        ) {
            if (form.busy) CircularProgressIndicator(Modifier.size(20.dp), color = magenta, strokeWidth = 2.dp)
            else Text(if (form.isSignup) "إنشاء الحساب" else "دخول الآن", fontFamily = Cairo, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        OrDivider(Color(0x33FFFFFF), Color(0x88FFFFFF))
        GoogleWhiteButton()
    }
}

/* ==================== SHARED INPUT & HELPER COMPONENTS ==================== */

@Composable
private fun ErrorBanner(error: String?, bg: Color, fg: Color) {
    if (error == null) return
    Text(
        error,
        color = fg,
        fontSize = 12.sp,
        fontFamily = Cairo,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun OrDivider(lineColor: Color, textColor: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = lineColor)
        Text("أو", color = textColor, fontSize = 12.sp, fontFamily = Cairo, modifier = Modifier.padding(horizontal = 10.dp))
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = lineColor)
    }
}

@Composable
private fun StyledDarkField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    accent: Color,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    onTogglePass: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(hint, color = Color(0x88FFFFFF), fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = onTogglePass?.let { toggle ->
            {
                IconButton(onClick = toggle) {
                    Icon(if (password) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "إظهار", tint = Color(0xAAFFFFFF))
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = accent,
            unfocusedBorderColor = Color(0x28FFFFFF),
            focusedContainerColor = Color(0x12FFFFFF),
            unfocusedContainerColor = Color(0x0DFFFFFF),
            cursorColor = accent
        )
    )
}

@Composable
private fun LightFilledInput(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    onTogglePass: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(hint, color = Color(0xFF777777), fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = onTogglePass?.let { toggle ->
            {
                IconButton(onClick = toggle) {
                    Icon(if (password) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "إظهار", tint = Color(0xFF573B8A))
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color(0xFF222222),
            unfocusedTextColor = Color(0xFF222222),
            focusedBorderColor = Color(0xFF573B8A),
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = Color(0xFFEEEEEE),
            unfocusedContainerColor = Color(0xFFEEEEEE),
            cursorColor = Color(0xFF573B8A)
        )
    )
}

@Composable
private fun MatrixField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    onTogglePass: (() -> Unit)? = null
) {
    val green = Color(0xFF00FF00)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(hint, color = Color(0x6600FF00), fontSize = 13.sp) },
        leadingIcon = { Icon(icon, null, tint = green, modifier = Modifier.size(18.dp)) },
        trailingIcon = onTogglePass?.let { toggle ->
            {
                IconButton(onClick = toggle) {
                    Icon(if (password) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "إظهار", tint = green)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = green,
            unfocusedTextColor = green,
            focusedBorderColor = green,
            unfocusedBorderColor = Color(0x4D00FF00),
            focusedContainerColor = Color(0xCC000000),
            unfocusedContainerColor = Color(0x99000000),
            cursorColor = green
        )
    )
}

@Composable
private fun CyberLabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    onTogglePass: (() -> Unit)? = null
) {
    val cyan = Color(0xFF00FFFF)
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(5.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint, color = Color(0x66FFFFFF), fontSize = 13.sp) },
            trailingIcon = onTogglePass?.let { toggle ->
                {
                    IconButton(onClick = toggle) {
                        Icon(if (password) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "إظهار", tint = cyan)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = cyan,
                unfocusedBorderColor = cyan,
                focusedContainerColor = Color(0x1A00FFFF),
                unfocusedContainerColor = Color(0x4D000000),
                cursorColor = cyan
            )
        )
    }
}

@Composable
private fun GoogleWhiteButton(border: Color = Color.Transparent) {
    val ctx = LocalContext.current
    val orange = Color(0xFFFF7A1A)
    val green = Color(0xFF10B981)
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(orange, green)))
            .border(1.dp, if (border != Color.Transparent) border else Color(0x44FFFFFF), RoundedCornerShape(12.dp))
            .clickable {
                if (GoogleAuth.enabled) GoogleAuth.start?.invoke()
                else Toast.makeText(ctx, "تسجيل الدخول عبر Google قريباً", Toast.LENGTH_SHORT).show()
            }
            .testTag("auth_google_button"),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "G",
                    color = orange,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }
            Text(
                "تسجيل الدخول عبر Google",
                color = Color.White,
                fontFamily = Cairo,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            if (!GoogleAuth.enabled) {
                Text(
                    "قريباً",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Cairo,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x3D000000))
                        .border(0.75.dp, Color(0x55FFFFFF), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}


@Composable
private fun AvatarPickerField(form: AuthFormHolder) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) form.avatarUri = uri
    }
    val pic = form.avatarUri
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp).clip(CircleShape)
                .background(Color(0x33FFFFFF))
                .border(2.dp, if (pic == null) MaterialTheme.colorScheme.primary else Color.White, CircleShape)
                .clickable { launcher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (pic != null) coil.compose.AsyncImage(pic, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (pic == null) "صورة الحساب (إجبارية) - اضغط للاختيار" else "اضغط لتغيير الصورة",
            color = if (pic == null) MaterialTheme.colorScheme.primary else Color(0xCCFFFFFF), fontSize = 12.sp, fontFamily = Cairo
        )
    }
}
