package com.musicmagic.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Poster-like, genuinely gesture-bound liquid card used by the player only.
 * Inspired by the saturated themed cards and wet-motion-blur transition in
 * Inspora's reference, not a copy of its footage, original code or art assets.
 */
@Composable
internal fun ImmersiveLiquidCard(
    item: LiquidCardItem,
    modifier: Modifier,
    playing: Boolean,
    motionEnabled: Boolean,
    allowBlur: Boolean,
    offset: () -> Float,
    isMoving: () -> Boolean,
    onAction: () -> Unit,
) {
    val cover = remember(item.artwork) {
        item.artwork?.takeUnless { it.isRecycled }?.asImageBitmap()
    }
    val phaseLoop = rememberInfiniteTransition(label = "liquid album breathing")
    val phase by phaseLoop.animateFloat(
        initialValue = 0f, targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(6500, easing = LinearEasing), RepeatMode.Restart),
        label = "album orbit phase",
    )
    val breathe = if (motionEnabled && playing && !isMoving()) sin(phase) * .032f else 0f
    val artBlur = remember(allowBlur) {
        if (allowBlur) BlurEffect(7f, 2f, TileMode.Clamp) else null
    }
    val shape = RoundedCornerShape(37.dp)
    Box(
        modifier = modifier
            .aspectRatio(.80f)
            .graphicsLayer {
                val progress = if (motionEnabled) offset().coerceIn(-1f, 1f) else 0f
                val distance = abs(progress)
                val dragBend = sin(distance * PI).toFloat()
                scaleX = 1f - distance * .22f + dragBend * .055f
                scaleY = 1f - distance * .18f - dragBend * .045f
                rotationY = progress * 14f
                rotationZ = -progress * 8f
                translationY = distance * 34.dp.toPx()
                alpha = 1f - distance * .18f
                cameraDistance = 760.dp.toPx()
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(item.accent, Color.White, .22f),
                        item.accent,
                        lerp(item.accent, Color(0xFF121122), .72f),
                        Color(0xFF0B0D19),
                    ),
                )
            )
            .drawWithCache {
                val glow = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .25f), item.accent.copy(alpha = .30f), Color.Transparent),
                    center = Offset(size.width * .68f, size.height * .24f),
                    radius = size.width * .83f,
                )
                onDrawBehind { drawRect(glow) }
            },
    ) {
        // Liquid membrane at the leading/trailing edge. Its Bézier bulge is
        // evaluated directly from the live pager offset, not just at snap.
        Canvas(Modifier.fillMaxSize()) {
            val p = if (motionEnabled) offset().coerceIn(-1f, 1f) else 0f
            val strength = abs(p).coerceIn(0f, 1f)
            if (strength > .015f) {
                val left = p > 0
                val edge = if (left) 0f else size.width
                val sign = if (left) 1f else -1f
                val reach = size.width * strength * .32f
                val liquid = Path().apply {
                    moveTo(edge, size.height * .12f)
                    cubicTo(
                        edge + sign * reach * .3f, size.height * .27f,
                        edge + sign * reach, size.height * .30f,
                        edge + sign * reach, size.height * .48f,
                    )
                    cubicTo(
                        edge + sign * reach, size.height * .65f,
                        edge + sign * reach * .35f, size.height * .82f,
                        edge, size.height * .94f,
                    )
                    close()
                }
                drawPath(
                    liquid,
                    brush = Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = strength * .27f),
                            item.accent.copy(alpha = strength * .58f), Color.Transparent)
                    ),
                )
            }
        }
        // Floating inset album with a lens warp and motion-blur pull.
        Box(
            Modifier.fillMaxWidth(.79f).align(Alignment.TopCenter)
                .padding(top = 27.dp)
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = 1.02f + breathe
                    scaleY = 1.02f + breathe
                    rotationZ = breathe * 14f
                }
                .clip(RoundedCornerShape(29.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = "앨범 커버",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer {
                            val p = if (motionEnabled) offset().coerceIn(-1f, 1f) else 0f
                            scaleX = 1.1f + abs(p) * .18f
                            scaleY = 1.1f + abs(p) * .18f
                            translationX = -p * size.width * .11f
                            renderEffect = if (allowBlur && isMoving() && abs(p) > .06f) artBlur else null
                        }
                        .liquidLensWarp(offset, enabled = motionEnabled),
                )
            } else {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White,
                    modifier = Modifier.size(82.dp))
            }
            Spacer(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(
                        Color.White.copy(alpha = .1f), Color.Transparent,
                        Color.Black.copy(alpha = .25f),
                    )))
            )
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(
                    Color.Transparent, Color.Black.copy(alpha = .35f),
                    Color.Black.copy(alpha = .72f),
                )))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("LIQUID MUSIC", fontSize = 10.sp, letterSpacing = 2.8.sp,
                fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .78f))
            Text(item.subtitle.ifBlank { item.title }, color = Color.White, fontSize = 28.sp,
                lineHeight = 32.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold)
            Text(item.detail.ifBlank { item.title }, color = Color.White.copy(alpha = .78f),
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Button(
                onClick = onAction, enabled = item.enabled,
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 46.dp)
                    .semantics { contentDescription = "${item.title}, ${item.actionLabel}" },
                shape = RoundedCornerShape(23.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = .94f),
                    contentColor = Color(0xFF171A28),
                ),
            ) {
                Text(item.actionLabel, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center)
            }
        }
    }
}
