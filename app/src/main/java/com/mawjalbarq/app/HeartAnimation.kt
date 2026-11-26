package com.mawjalbarq.app

import android.graphics.PathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WaterHeartBurst(progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0f || progress >= 1f) return

    val overallAlpha = if (progress < 0.80f) 1f else ((1f - progress) / 0.20f).coerceIn(0f, 1f)
    val heartScale = when {
        progress < 0.14f -> 0.45f + (progress / 0.14f) * 0.65f
        progress < 0.24f -> 1.10f - ((progress - 0.14f) / 0.10f) * 0.10f
        progress > 0.82f -> 1.0f + ((progress - 0.82f) / 0.18f) * 0.12f
        else -> 1f
    }
    val waterFill = ((progress - 0.05f) / 0.65f).coerceIn(0f, 1f)
    val wrap = ((progress - 0.08f) / 0.60f).coerceIn(0f, 1f)
    val meetBurst = ((progress - 0.64f) / 0.30f).coerceIn(0f, 1f)

    Canvas(modifier.size(260.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.26f

        // 1. Glowing circles & orbiting halo dots around the heart
        val ringSpecs = listOf(0.08f to Color(0xFF00E5FF), 0.26f to Color(0xFFFF2D55), 0.48f to Color(0xFFFFD21F))
        ringSpecs.forEach { (startAt, col) ->
            val rp = ((progress - startAt) / 0.65f).coerceIn(0f, 1f)
            if (rp > 0f && rp < 1f) {
                val ringRadius = r * (1.05f + rp * 0.82f)
                val ringAlpha = (1f - rp) * 0.65f * overallAlpha
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, col.copy(alpha = ringAlpha * 0.45f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = ringRadius + 16.dp.toPx()
                    ),
                    radius = ringRadius + 16.dp.toPx(),
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = col.copy(alpha = ringAlpha),
                    radius = ringRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = (3.5f * (1f - rp * 0.5f)).dp.toPx())
                )
            }
        }

        // Orbiting luminous circles around the heart
        val orbColors = listOf(Color(0xFF00E5FF), Color(0xFFFF4D8D), Color(0xFFFFD21F), Color(0xFF60A5FA))
        for (i in 0 until 10) {
            val angle = (i * (2.0 * PI / 10.0)) + progress * 1.15
            val orbRadius = r * (1.28f + 0.26f * sin(progress * PI).toFloat() + (i % 2) * 0.12f)
            val ox = cx + cos(angle).toFloat() * orbRadius
            val oy = cy + sin(angle).toFloat() * orbRadius
            val orbSize = (4.5f + (i % 3) * 2f).dp.toPx() * (0.7f + 0.5f * sin(progress * PI).toFloat())
            val c = orbColors[i % orbColors.size]
            val orbAlpha = (0.35f + 0.65f * sin(progress * PI).toFloat()) * overallAlpha
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = orbAlpha), c.copy(alpha = orbAlpha * 0.7f), Color.Transparent),
                    center = Offset(ox, oy),
                    radius = orbSize * 2.4f
                ),
                radius = orbSize * 2.4f,
                center = Offset(ox, oy)
            )
            drawCircle(color = Color.White.copy(alpha = orbAlpha), radius = orbSize * 0.45f, center = Offset(ox, oy))
        }

        withTransform({ scale(heartScale, heartScale, Offset(cx, cy)) }) {
            // Symmetric right & left heart half paths (start at top cleft, meet at bottom tip)
            val topY = cy - r * 0.34f
            val bottomY = cy + r * 0.88f

            val rightPath = Path().apply {
                moveTo(cx, topY)
                cubicTo(cx + r * 0.25f, cy - r * 0.92f, cx + r * 1.15f, cy - r * 0.72f, cx + r * 1.02f, cy - r * 0.05f)
                cubicTo(cx + r * 0.92f, cy + r * 0.42f, cx + r * 0.42f, cy + r * 0.68f, cx, bottomY)
            }
            val leftPath = Path().apply {
                moveTo(cx, topY)
                cubicTo(cx - r * 0.25f, cy - r * 0.92f, cx - r * 1.15f, cy - r * 0.72f, cx - r * 1.02f, cy - r * 0.05f)
                cubicTo(cx - r * 0.92f, cy + r * 0.42f, cx - r * 0.42f, cy + r * 0.68f, cx, bottomY)
            }
            val fullHeart = Path().apply {
                moveTo(cx, topY)
                cubicTo(cx + r * 0.25f, cy - r * 0.92f, cx + r * 1.15f, cy - r * 0.72f, cx + r * 1.02f, cy - r * 0.05f)
                cubicTo(cx + r * 0.92f, cy + r * 0.42f, cx + r * 0.42f, cy + r * 0.68f, cx, bottomY)
                cubicTo(cx - r * 0.42f, cy + r * 0.68f, cx - r * 0.92f, cy + r * 0.42f, cx - r * 1.02f, cy - r * 0.05f)
                cubicTo(cx - r * 1.15f, cy - r * 0.72f, cx - r * 0.25f, cy - r * 0.92f, cx, topY)
                close()
            }

            // Soft backdrop glow behind the heart
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF2D55).copy(alpha = 0.28f * overallAlpha), Color(0xFF00E5FF).copy(alpha = 0.14f * overallAlpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r * 1.65f
                ),
                radius = r * 1.65f,
                center = Offset(cx, cy)
            )

            // 2. Heart glass interior + rising animated water fill
            clipPath(fullHeart) {
                drawRect(
                    color = Color(0x30141A24).copy(alpha = 0.55f * overallAlpha),
                    topLeft = Offset(cx - r * 1.3f, cy - r * 1.1f),
                    size = androidx.compose.ui.geometry.Size(r * 2.6f, r * 2.2f)
                )

                val liquidTop = (bottomY + 6f) - (bottomY - (cy - r * 0.95f) + 14f) * waterFill
                val leftX = cx - r * 1.25f
                val rightX = cx + r * 1.25f
                val amp = (6.5f * (1f - waterFill * 0.65f)).dp.toPx()
                val phase = progress * 20f

                // Back wave layer
                val backWave = Path().apply {
                    moveTo(leftX, liquidTop)
                    var x = leftX
                    val step = (rightX - leftX) / 28f
                    while (x <= rightX + step) {
                        val y = liquidTop + sin((x / r) * 3.4f - phase + 1.4f) * amp * 0.85f
                        lineTo(x, y)
                        x += step
                    }
                    lineTo(rightX, bottomY + 12f)
                    lineTo(leftX, bottomY + 12f)
                    close()
                }
                drawPath(
                    path = backWave,
                    color = Color(0xFF00E5FF).copy(alpha = 0.42f * overallAlpha)
                )

                // Main front water wave fill
                val frontWave = Path().apply {
                    moveTo(leftX, liquidTop)
                    var x = leftX
                    val step = (rightX - leftX) / 32f
                    while (x <= rightX + step) {
                        val y = liquidTop + sin((x / r) * 3.6f + phase) * amp
                        lineTo(x, y)
                        x += step
                    }
                    lineTo(rightX, bottomY + 12f)
                    lineTo(leftX, bottomY + 12f)
                    close()
                }
                drawPath(
                    path = frontWave,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.92f * overallAlpha),
                            Color(0xFFFF2D55).copy(alpha = 0.95f * overallAlpha),
                            Color(0xFFD90429).copy(alpha = 0.98f * overallAlpha)
                        ),
                        startY = liquidTop - amp,
                        endY = bottomY
                    )
                )

                // Rising water bubbles inside the heart
                if (waterFill > 0.15f) {
                    val bubbleOffsets = listOf(-0.42f to 0.1f, -0.15f to 0.35f, 0.22f to 0.2f, 0.45f to 0.45f, 0.04f to 0.55f)
                    bubbleOffsets.forEachIndexed { idx, (bxFrac, delayFrac) ->
                        val bp = ((waterFill - delayFrac) / 0.7f).coerceIn(0f, 1f)
                        if (bp > 0f) {
                            val bx = cx + bxFrac * r
                            val by = bottomY - bp * (bottomY - liquidTop) * 0.85f
                            if (by > liquidTop + 4f) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.55f * (1f - bp * 0.4f) * overallAlpha),
                                    radius = (2.2f + (idx % 2) * 1.4f).dp.toPx(),
                                    center = Offset(bx, by)
                                )
                            }
                        }
                    }
                }

                // Glossy emoji-like highlight on top-left lobe
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.45f * overallAlpha), Color.Transparent),
                        center = Offset(cx - r * 0.48f, cy - r * 0.36f),
                        radius = r * 0.42f
                    ),
                    radius = r * 0.42f,
                    center = Offset(cx - r * 0.48f, cy - r * 0.36f)
                )
            }

            // Base subtle heart outline
            drawPath(
                path = fullHeart,
                color = Color.White.copy(alpha = 0.28f * overallAlpha),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 3. Dual glowing lights wrapping from right and left sides until meeting at the bottom middle
            val rightMeasure = PathMeasure(rightPath.asAndroidPath(), false)
            val leftMeasure = PathMeasure(leftPath.asAndroidPath(), false)

            val rSeg = android.graphics.Path()
            val lSeg = android.graphics.Path()
            rightMeasure.getSegment(0f, rightMeasure.length * wrap, rSeg, true)
            leftMeasure.getSegment(0f, leftMeasure.length * wrap, lSeg, true)

            val rCompose = rSeg.asComposePath()
            val lCompose = lSeg.asComposePath()

            listOf(rCompose, lCompose).forEach { seg ->
                drawPath(
                    path = seg,
                    color = Color(0xFF00E5FF).copy(alpha = 0.36f * overallAlpha),
                    style = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = seg,
                    color = Color(0xFFFF5CA8).copy(alpha = 0.72f * overallAlpha),
                    style = Stroke(width = 6.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = seg,
                    color = Color.White.copy(alpha = 0.96f * overallAlpha),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Moving bright spark tips on right & left edges
            val rPos = FloatArray(2)
            val lPos = FloatArray(2)
            if (wrap > 0.02f && wrap < 0.98f) {
                rightMeasure.getPosTan(rightMeasure.length * wrap, rPos, null)
                leftMeasure.getPosTan(leftMeasure.length * wrap, lPos, null)
                listOf(Offset(rPos[0], rPos[1]), Offset(lPos[0], lPos[1])).forEach { tip ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = overallAlpha), Color(0xFF00E5FF).copy(alpha = 0.8f * overallAlpha), Color.Transparent),
                            center = tip,
                            radius = 14.dp.toPx()
                        ),
                        radius = 14.dp.toPx(),
                        center = tip
                    )
                }
            }

            // Flash glow at bottom center when right and left beams meet
            if (meetBurst > 0f) {
                val flashAlpha = (1f - meetBurst) * overallAlpha
                val flashRadius = r * (0.3f + meetBurst * 0.65f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = flashAlpha),
                            Color(0xFF00E5FF).copy(alpha = flashAlpha * 0.75f),
                            Color(0xFFFF2D55).copy(alpha = flashAlpha * 0.35f),
                            Color.Transparent
                        ),
                        center = Offset(cx, bottomY),
                        radius = flashRadius
                    ),
                    radius = flashRadius,
                    center = Offset(cx, bottomY)
                )
            }
        }
    }
}
