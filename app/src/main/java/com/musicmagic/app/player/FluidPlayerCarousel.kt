package com.musicmagic.app.player

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.musicmagic.app.ui.rememberLiquidMotionEnabled
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/** One album card in the player carousel. */
data class FluidCard(
    val id: String,
    val album: String,
    val artist: String,
    val artwork: Bitmap?,
    val accent: Color,
)

/**
 * Player-only album carousel, following the Figma "액체 카드 모션" frame:
 * the focused card is large, neighbours peek at 80 % size, and a card in flight
 * stretches sideways, flattens, tilts and blurs before springing back into shape.
 * The caller owns [state] so the background can follow the same swipe.
 */
@Composable
fun FluidPlayerCarousel(
    cards: List<FluidCard>,
    state: PagerState,
    modifier: Modifier = Modifier,
    onCardClick: (index: Int) -> Unit,
) {
    val motion = rememberLiquidMotionEnabled()
    val context = LocalContext.current
    val lowRam = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice ?: true
    }
    val allowBlur = motion && !lowRam && Build.VERSION.SDK_INT >= 31
    val density = LocalDensity.current
    // Pre-built blur levels: no allocation while a frame is drawn.
    val blurs = remember(allowBlur, density.density) {
        if (!allowBlur) emptyList() else List(6) { level ->
            if (level == 0) null else with(density) {
                BlurEffect((level * 2.6f).dp.toPx(), (level * 0.9f).dp.toPx(), TileMode.Decal)
            }
        }
    }
    val fling = PagerDefaults.flingBehavior(
        state = state,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapAnimationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
    )

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // 260 × 320 card on a 390-wide phone; shrink on short screens.
        val cardWidth: Dp = min(maxWidth - 130.dp, (maxHeight - 40.dp) * 0.8125f).coerceAtLeast(120.dp)
        val stride = cardWidth * 0.923f // neighbour centre sits 240 dp away from a 260 dp card
        val corner = cardWidth * 0.154f
        HorizontalPager(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            pageSize = PageSize.Fixed(stride),
            contentPadding = PaddingValues(horizontal = (maxWidth - stride) / 2, vertical = 20.dp),
            beyondViewportPageCount = 1,
            key = { cards[it].id },
            flingBehavior = fling,
        ) { page ->
            val card = cards[page]
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FluidAlbumCard(
                    card = card,
                    width = cardWidth,
                    corner = corner,
                    motion = motion,
                    blurs = blurs,
                    offset = { (state.currentPage - page) + state.currentPageOffsetFraction },
                    moving = { state.isScrollInProgress },
                    focused = page == state.currentPage,
                    onClick = { onCardClick(page) },
                )
            }
        }
    }
}

@Composable
private fun FluidAlbumCard(
    card: FluidCard,
    width: Dp,
    corner: Dp,
    motion: Boolean,
    blurs: List<BlurEffect?>,
    offset: () -> Float,
    moving: () -> Boolean,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val cover = remember(card.artwork) { card.artwork?.takeUnless { it.isRecycled }?.asImageBitmap() }
    val shape = RoundedCornerShape(corner)
    Box(
        Modifier
            .requiredWidth(width)
            .aspectRatio(0.8125f)
            .graphicsLayer {
                val p = offset()
                val d = abs(p).coerceIn(0f, 1f)
                // Peaks half-way between two slots: the "liquid" moment.
                val flight = if (motion) sin(d * PI).toFloat() else 0f
                val base = 1f - 0.2f * d
                scaleX = base * (1f + 0.13f * flight)
                scaleY = base * (1f - 0.10f * flight)
                rotationZ = if (motion) 3f * flight * sign(p) else 0f
                alpha = when {
                    abs(p) <= 1f -> 1f - 0.4f * d
                    else -> (0.6f * (2f - abs(p))).coerceIn(0f, 0.6f)
                }
                this.shape = shape
                clip = true
                shadowElevation = (22f * (1f - d)).dp.toPx()
                ambientShadowColor = card.accent
                spotShadowColor = card.accent
                renderEffect = if (blurs.isNotEmpty() && moving()) {
                    blurs[(flight * 5f).roundToInt().coerceIn(0, 5)]
                } else null
            }
            .background(
                Brush.linearGradient(
                    listOf(lerp(card.accent, Color.White, 0.12f), lerp(card.accent, Color(0xFF0B0D16), 0.68f)),
                ),
            )
            .clickable(onClickLabel = if (focused) "이 곡 재생" else "이 카드로 이동", onClick = onClick)
            .semantics { contentDescription = "${card.album}, ${card.artist}" },
    ) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    // Slight parallax keeps the cover alive inside the moving glass.
                    val p = if (motion) offset().coerceIn(-1f, 1f) else 0f
                    scaleX = 1.08f
                    scaleY = 1.08f
                    translationX = -p * size.width * 0.06f
                },
            )
        } else {
            // No embedded cover: soft light / shadow / ring from the Figma card.
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White.copy(alpha = 0.16f), size.width * 0.44f, Offset(size.width * 0.9f, size.height * 0.14f))
                drawCircle(Color.Black.copy(alpha = 0.18f), size.width * 0.33f, Offset(size.width * 0.1f, size.height * 0.8f))
                drawCircle(
                    Color.White.copy(alpha = 0.35f), size.width * 0.185f,
                    Offset(size.width * 0.72f, size.height * 0.62f), style = Stroke(1.5.dp.toPx()),
                )
            }
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .padding(start = 22.dp, end = 22.dp, top = 36.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(card.album, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(card.artist, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
