package com.mawjalbarq.app
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(prefs: ThemePrefs) {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 100.dp)) {
        Text("حسابي", color = Color.White, fontSize = 24.sp, fontFamily = Cairo)
        Spacer(Modifier.height(16.dp))
        if (App.loggedIn) {
            Text(App.name, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontFamily = Cairo)
            Text(App.email, color = Color(0xFF8A8F9C), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { App.logout() }) { Text("تسجيل الخروج") }
        } else AuthForm()
        Spacer(Modifier.height(28.dp))
        Text("لون التطبيق", color = Color.White, fontSize = 16.sp, fontFamily = Cairo)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Accents.forEachIndexed { i, a ->
                Box(Modifier.size(44.dp).clip(CircleShape).background(a.main)
                    .border(if (i == prefs.index) 3.dp else 0.dp, Color.White, CircleShape).clickable { prefs.set(i) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(Accents[prefs.index].name, color = Color(0xFF8A8F9C), fontSize = 13.sp)
    }
}


@Composable
fun AuthForm() {
    val scope = rememberCoroutineScope()
    var signup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (signup) "إنشاء حساب" else "تسجيل الدخول", color = Color.White, fontSize = 18.sp, fontFamily = Cairo)
        if (signup) OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("الاسم") }, singleLine = true)
        OutlinedTextField(email, { email = it.trim() }, Modifier.fillMaxWidth(), label = { Text("البريد الإلكتروني") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text("كلمة المرور") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        err?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp) }
        Button(enabled = !busy && email.isNotBlank() && pass.length >= 8, modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true; err = null
            scope.launch {
                err = if (signup) signUp(name.trim(), email, pass) else signIn(email, pass)
                busy = false
            }
        }) { Text(if (busy) "..." else if (signup) "إنشاء الحساب" else "دخول") }
        TextButton(onClick = { signup = !signup; err = null }) {
            Text(if (signup) "لديك حساب؟ سجّل الدخول" else "ليس لديك حساب؟ أنشئ حساباً")
        }
        GoogleButton()
    }
}

@Composable
fun GoogleButton() {
    OutlinedButton(onClick = { GoogleAuth.start?.invoke() }, modifier = Modifier.fillMaxWidth(), enabled = GoogleAuth.enabled) {
        Text(if (GoogleAuth.enabled) "المتابعة باستخدام Google" else "Google (قريباً)")
    }
}

object GoogleAuth {
    var enabled = false
    var start: (() -> Unit)? = null
}
