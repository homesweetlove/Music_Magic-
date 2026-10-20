package com.musicmagic.app.ui

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Always part of the real player rendering, not a static image.
 * Nested radial glows and revolving, tapered spiral ribbons lead the eye into
 * the album cover. The entire effect is drawn in one Compose Canvas layer.
 * If reduced motion is on, or the device is low-memory, only a still ambient
 * gradient remains. Playback does not claim to be BPM- or FFT-synchronized.
 */
@Composable
fun LiquidVortexBackdrop(
    accent: Color,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lowRam = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.isLowRamDevice ?: true
    }
    val activeMotion = rememberLiquidMotionEnabled() && !lowRam
    val transition = rememberInfiniteTransition(label = "liquid portal orbit")
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart),
        label = "vortex rotation",
    )
    val energy by animateFloatAsState(
        if (activeMotion && playing) 1f else if (activeMotion) 0.32f else 0f,
        animationSpec = tween(900),
        label = "playback liquid intensity",
    )
    // Don't read orbit while the system requests reduced motion; no animation
    // reaches Canvas invalidations in this case.
    val phase = if (activeMotion) orbit else 0f

    Canvas(modifier) {
        val center = Offset(size.width * .50f, size.height * .34f)
        val outer = maxOf(size.width, size.height) * .70f
        val halo = Brush.radialGradient(
            listOf(accent.copy(alpha = .24f * (0.4f + energy * .6f)),
                accent.copy(alpha = .07f), Color.Transparent),
            center = center, radius = outer,
        )
        drawCircle(halo, radius = outer, center = center)
        if (!activeMotion || energy < .01f) return@Canvas

        // Inward depth is suggested by multiple spiralling strands of decreasing
        // width and opacity. Motion uses only GPU-friendly path and gradient draws.
        val radius = size.width * .57f
        val core = size.width * .025f
        val sweep = Brush.sweepGradient(
            listOf(
                accent.copy(alpha = .04f),
                Color.White.copy(alpha = .16f * energy),
                accent.copy(alpha = .45f * energy),
                Color.Transparent,
                accent.copy(alpha = .04f),
            ), center = center,
        )
        for (arm in 0 until 5) {
            val startAngle = phase * (if (arm % 2 == 0) 1f else -.65f) +
                arm * (2f * PI.toFloat() / 5f)
            val path = Path()
            for (point in 0..34) {
                val t = point / 34f
                val spiralAngle = startAngle + t * 4.6f
                val r = core + (radius - core) * t.pow(1.35f)
                val p = Offset(
                    center.x + cos(spiralAngle) * r,
                    center.y + sin(spiralAngle) * r * .75f,
                )
                if (point == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(
                path = path, brush = sweep,
                style = Stroke(width = (2.2f + arm % 3) * density, cap = StrokeCap.Round),
                alpha = (.30f + .40f * energy),
            )
        }
        // A breathing inner core, tiny enough to avoid washing out text.
        val breath = .5f + .5f * sin(phase * 2f)
        val inside = core * (3f + breath)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = .14f * energy),
                    accent.copy(alpha = .2f * energy), Color.Transparent),
                center = center, radius = inside,
            ),
            radius = inside,
            center = center,
        )
    }
}

/** Optional actual liquid-lens distortion of the ARTWORK on Android 13+.
 * On Android 10–12 the same spring depth, artwork parallax and motion blur stay.
 */
@Composable
fun Modifier.liquidLensWarp(
    dragOffset: () -> Float,
    enabled: Boolean,
): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val shader = remember { AlbumLensShader.make() }
    val fx = remember(shader) {
        android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "art").asComposeRenderEffect()
    }
    return this.graphicsLayer {
        if (size.width > 0f && size.height > 0f) {
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("twist", dragOffset().coerceIn(-1f, 1f))
            renderEffect = fx
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object AlbumLensShader {
    private const val SOURCE = """
        uniform shader art;
        uniform float2 resolution;
        uniform float twist;

        half4 main(float2 pos) {
            float2 middle = resolution * 0.5;
            float2 delta = pos - middle;
            float radius = length(delta) / max(1.0, min(resolution.x, resolution.y));
            // Strong in the center, naturally tapering toward artwork edges.
            float envelope = pow(max(0.0, 1.0 - radius * 1.75), 2.0);
            float angle = -twist * envelope * 2.4;
            float cs = cos(angle);
            float sn = sin(angle);
            float2 sampleAt = middle + float2(
                delta.x * cs - delta.y * sn,
                delta.x * sn + delta.y * cs
            );
            return art.eval(clamp(sampleAt, float2(0.0), resolution));
        }
    """
    fun make(): android.graphics.RuntimeShader = android.graphics.RuntimeShader(SOURCE)
}
