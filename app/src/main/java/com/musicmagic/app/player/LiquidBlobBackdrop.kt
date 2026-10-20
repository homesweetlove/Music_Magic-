package com.musicmagic.app.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import com.musicmagic.app.ui.rememberLiquidMotionEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Three large, soft colour blobs behind the player (the "물방울" layers in Figma).
 * [accent] is read every frame so the colours follow the card being swiped;
 * [stretch] (0..1) squashes the blobs while a card is in flight.
 * Soft edges come from radial gradients, not a full-screen blur, so it stays cheap.
 */
@Composable
fun LiquidBlobBackdrop(
    accent: () -> Color,
    stretch: () -> Float,
    modifier: Modifier = Modifier,
) {
    val motion = rememberLiquidMotionEnabled()
    val loop = rememberInfiniteTransition(label = "liquid blobs")
    val phase by loop.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
        label = "blob drift",
    )
    Canvas(modifier) {
        val base = accent()
        val colors = listOf(base, base.shiftHue(-38f), base.shiftHue(42f, sat = 0.9f))
        val t = if (motion) phase else 0f
        val s = if (motion) stretch().coerceIn(0f, 1f) else 0f
        val w = size.width
        val h = size.height
        val blobs = listOf(
            Triple(Offset(w * 0.2f + sin(t) * w * 0.10f, h * 0.27f + cos(t) * h * 0.035f), w * 0.78f, 0.85f),
            Triple(Offset(w * 0.86f + cos(t * 1.3f) * w * 0.12f, h * 0.43f + sin(t) * h * 0.03f), w * 0.70f, 0.70f),
            Triple(Offset(w * 0.32f + sin(t * 0.8f) * w * 0.07f, h * 0.68f - sin(t) * h * 0.04f), w * 0.66f, 0.55f),
        )
        blobs.forEachIndexed { i, (center, radius, alpha) ->
            val c = colors[i]
            val sx = 1f + s * (if (i == 1) -0.15f else 0.22f)
            val sy = 1f + s * (if (i == 1) 0.18f else -0.10f)
            scale(sx, sy, pivot = center) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(c.copy(alpha = alpha), c.copy(alpha = alpha * 0.4f), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }
        // Darken toward the bottom so text and buttons stay readable.
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF0B0D16).copy(alpha = 0.15f),
                0.5f to Color(0xFF0B0D16).copy(alpha = 0.55f),
                1f to Color(0xFF0B0D16).copy(alpha = 0.97f),
            ),
        )
    }
}

internal fun Color.shiftHue(degrees: Float, sat: Float = 1f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
    hsv[1] = (hsv[1] * sat).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
