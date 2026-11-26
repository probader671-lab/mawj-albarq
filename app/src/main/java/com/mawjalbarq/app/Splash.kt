package com.mawjalbarq.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val enter = remember { Animatable(0f) }
    val tr = rememberInfiniteTransition(label = "splashGlow")
    val pulse by tr.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val sweep by tr.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )

    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
        delay(220)
        onDone()
    }

    val p = enter.value
    val orange = Color(0xFFFF7A1A)
    val green = Color(0xFF10B981)

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF060A10), Color(0xFF0B1420), Color(0xFF070C12))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(orange.copy(alpha = 0.22f * p), Color.Transparent),
                    center = Offset(size.width * 0.22f, size.height * 0.24f),
                    radius = size.width * 0.65f
                ),
                radius = size.width * 0.65f,
                center = Offset(size.width * 0.22f, size.height * 0.24f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(green.copy(alpha = 0.22f * p), Color.Transparent),
                    center = Offset(size.width * 0.78f, size.height * 0.74f),
                    radius = size.width * 0.68f
                ),
                radius = size.width * 0.68f,
                center = Offset(size.width * 0.78f, size.height * 0.74f)
            )
            drawCircle(
                brush = Brush.sweepGradient(listOf(orange.copy(alpha = 0.55f), green.copy(alpha = 0.55f), orange.copy(alpha = 0.55f))),
                radius = 96.dp.toPx() * pulse,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
                alpha = 0.45f * p
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                Modifier
                    .size(152.dp)
                    .scale((0.72f + 0.28f * p) * pulse)
                    .alpha(p)
                    .clip(CircleShape)
                    .background(Color(0x880E1622))
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(listOf(orange, green)),
                        shape = CircleShape
                    )
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.logo_mark),
                    contentDescription = "شعار موج البرق",
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            }

            Spacer(Modifier.height(22.dp))

            Text(
                "موج البرق",
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(Color.White, Color(0xFFE2E8F0))),
                    fontSize = 33.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Cairo
                ),
                modifier = Modifier.alpha(p)
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "مقاطع قصيرة وفيديوهات عالية الجودة",
                color = Color(0xFF94A3B8),
                fontSize = 13.5.sp,
                fontFamily = Tajawal,
                modifier = Modifier.alpha(p * 0.92f)
            )

            Spacer(Modifier.height(28.dp))

            Box(
                Modifier
                    .width(148.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x22FFFFFF))
                    .alpha(p)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.35f + 0.65f * sweep)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Brush.horizontalGradient(listOf(orange, green)))
                )
            }
        }
    }
}
