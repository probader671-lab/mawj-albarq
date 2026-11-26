package com.mawjalbarq.app
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
        delay(400); onDone()
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF06070A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.logo_mark), null,
                Modifier.size(180.dp).scale(0.6f + 0.4f * t.value).rotate(-90f * (1f - t.value)).alpha(t.value))
            Spacer(Modifier.height(20.dp))
            Text("موج البرق", color = Color.White, fontSize = 32.sp, fontFamily = Cairo, modifier = Modifier.alpha(t.value))
        }
    }
}
