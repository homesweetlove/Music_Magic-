package com.musicmagic.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.sin

/**
 * The visibly connecting membrane between the departing and arriving covers.
 * Progress is read inside Canvas rather than during composition so a drag
 * updates the shape every frame without rebuilding all album text/buttons.
 * It is purely visual and does not intercept pager touches or a11y actions.
 */
@Composable
fun LiquidMorphBridge(
    from: () -> Color,
    to: () -> Color,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val signed = progress().coerceIn(-1f, 1f)
        val t = abs(signed)
        if (t < .035f || t > .96f) return@Canvas
        // Maximum connection near halfway; smoothly dissolve at either end.
        val stretch = sin(t * Math.PI).toFloat().coerceIn(0f, 1f)
        val direction = if (signed < 0) 1f else -1f
        val centerX = size.width * .5f
        val cy = size.height * .43f
        val width = size.width * (.19f + stretch * .22f)
        val thickness = size.height * (.036f + stretch * .052f)
        val core = thickness * .42f
        val leftX = centerX - width / 2f
        val rightX = centerX + width / 2f

        // Two caps sharing the same fluid neck: curvature follows the exact
        // finger offset, not a canned slide/crossfade animation.
        val bridge = Path().apply {
            moveTo(leftX, cy - thickness)
            cubicTo(leftX + width * .18f, cy - thickness * .9f,
                centerX - width * .19f, cy - core,
                centerX, cy - core)
            cubicTo(centerX + width * .16f, cy - core,
                rightX - width * .22f, cy - thickness,
                rightX, cy - thickness)
            cubicTo(rightX + thickness * .72f, cy - thickness * .4f,
                rightX + thickness * .72f, cy + thickness * .4f,
                rightX, cy + thickness)
            cubicTo(rightX - width * .2f, cy + thickness,
                centerX + width * .16f, cy + core,
                centerX, cy + core)
            cubicTo(centerX - width * .19f, cy + core,
                leftX + width * .18f, cy + thickness,
                leftX, cy + thickness)
            cubicTo(leftX - thickness * .72f, cy + thickness * .4f,
                leftX - thickness * .72f, cy - thickness * .4f,
                leftX, cy - thickness)
            close()
        }
        val start = if (direction > 0) from() else to()
        val finish = if (direction > 0) to() else from()
        val alpha = (stretch * .76f).coerceIn(0f, .76f)
        drawPath(bridge, Brush.horizontalGradient(
            listOf(start.copy(alpha = alpha), lerp(start, finish, .5f).copy(alpha = alpha),
                finish.copy(alpha = alpha)),
            startX = leftX, endX = rightX,
        ))
        drawPath(bridge, Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = alpha * .35f), Color.Transparent,
                Color.White.copy(alpha = alpha * .17f)), startX = leftX, endX = rightX,
        ), style = Stroke(width = 2f * density))
        // Droplets disconnect from the neck near the finger release.
        if (stretch > .3f) {
            for (i in 0..2) {
                val radius = (3.1f + i * 2.2f) * density * stretch
                val x = centerX + direction * width * (.25f + .23f * i)
                val y = cy + (if (i % 2 == 0) -1f else 1f) * thickness * (1.8f + i)
                drawCircle(lerp(start, finish, i / 3f).copy(alpha = alpha * .6f),
                    radius = radius, center = Offset(x, y))
            }
        }
    }
}
