package com.mawjalbarq.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/** Comment send icon: when [sending] turns true the icon swaps for a carrier pigeon that flaps its wings and flies off, then the icon fades back. */
@Composable
fun PigeonSend(sending: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val p = remember { Animatable(0f) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(sending) { if (sending) tick++ }
    LaunchedEffect(tick) {
        if (tick > 0) { p.snapTo(0f); p.animateTo(1f, tween(1100, easing = LinearEasing)); p.snapTo(0f) }
    }
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val t = p.value
    val flying = t > 0f
    val iconAlpha = when { !flying -> 1f; t < 0.12f -> 1f - t / 0.12f; t > 0.85f -> (t - 0.85f) / 0.15f; else -> 0f }
    Box(modifier.size(24.dp)) {
        Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = tint, modifier = Modifier.size(24.dp).alpha(iconAlpha))
        if (flying && t < 0.85f) {
            Canvas(Modifier.size(24.dp)) {
                val s = size.width
                val move = (t - 0.08f).coerceAtLeast(0f)
                val cx = s / 2f + dir * move * s * 4.2f
                val cy = s / 2f - move * s * 3.0f + sin(t * 18f) * s * 0.05f
                val a = if (t > 0.65f) ((0.85f - t) / 0.2f).coerceIn(0f, 1f) else 1f
                val flap = sin(t * 60f)   // wing beat
                val body = Color(0xFFB9C2D0).copy(alpha = a); val dark = Color(0xFF8A94A6).copy(alpha = a)
                withTransform({ scale(dir, 1f, Offset(cx, cy)); rotate(-18f, Offset(cx, cy)) }) {
                    val u = s / 24f
                    // tail
                    drawPath(Path().apply { moveTo(cx - 6 * u, cy); lineTo(cx - 11 * u, cy - 2 * u); lineTo(cx - 11 * u, cy + 3 * u); close() }, dark)
                    // body
                    drawOval(body, Offset(cx - 7 * u, cy - 3.5f * u), androidx.compose.ui.geometry.Size(14 * u, 8 * u))
                    // head
                    drawCircle(body, 3.2f * u, Offset(cx + 7 * u, cy - 3 * u))
                    drawPath(Path().apply { moveTo(cx + 9.8f * u, cy - 3.6f * u); lineTo(cx + 13 * u, cy - 2.6f * u); lineTo(cx + 9.8f * u, cy - 1.8f * u); close() }, Color(0xFFFF9F1C).copy(alpha = a))
                    drawCircle(Color.Black.copy(alpha = a), 0.7f * u, Offset(cx + 7.8f * u, cy - 3.6f * u))
                    // wing (flaps up and down)
                    val h = (9f * flap) * u
                    drawPath(Path().apply { moveTo(cx - 3 * u, cy - 1 * u); quadraticBezierTo(cx - 1 * u, cy - 1 * u - h - 3 * u, cx + 5 * u, cy - 1 * u); quadraticBezierTo(cx + 1 * u, cy + 1.5f * u, cx - 3 * u, cy - 1 * u); close() }, dark)
                }
            }
        }
    }
}
