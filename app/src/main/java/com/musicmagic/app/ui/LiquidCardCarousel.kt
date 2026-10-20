package com.musicmagic.app.ui

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * IDs must be unique and stable. Supply immutable item lists and externally prepared artwork
 * (at most 512 px per side). The caller owns bitmaps; keep them alive while displayed.
 */
data class LiquidCardItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val detail: String = "",
    val artwork: Bitmap? = null,
    val accent: Color = Color(0xFF0A84FF),
    val actionLabel: String = "열기",
    val enabled: Boolean = true,
)

/**
 * A finite, lazy carousel. Give it bounded width and room to grow vertically (for example,
 * in the host's vertically scrolling content). Text has no fixed height or line limit.
 *
 * Only the action button calls [onAction]. [onSettled] reports a different item after a
 * completed navigation, never initial composition, restoration, or a list replacement.
 * Changing an item's content without changing its ID is not a new settlement.
 */
@Composable
fun LiquidCardCarousel(
    items: List<LiquidCardItem>,
    modifier: Modifier = Modifier,
    onAction: (LiquidCardItem) -> Unit,
    onSettled: ((LiquidCardItem) -> Unit)? = null,
    immersive: Boolean = false,
) {
    val ids = remember(items) {
        items.map { it.id }.also { keys ->
            require(keys.distinct().size == keys.size) { "LiquidCardItem IDs must be unique" }
        }
    }
    if (items.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("표시할 카드가 없습니다", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val pager = rememberPagerState(pageCount = { items.size })
    val motionEnabled = rememberLiquidMotionEnabled()
    val context = LocalContext.current
    val lowRam = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.isLowRamDevice ?: true
    }
    val scope = rememberCoroutineScope()
    val latestItems by rememberUpdatedState(items)
    val latestSettled by rememberUpdatedState(onSettled)
    var navigating by remember { mutableStateOf(false) }

    // PagerState owns snap completion (including pixel rounding). Observe its settled
    // page rather than imposing a second subpixel alignment threshold that can reject
    // a valid native snap and prevent the next artwork request.
    LaunchedEffect(pager, ids) {
        var lastId = latestItems.getOrNull(pager.settledPage)?.id
        var ignoreCurrentScroll = pager.isScrollInProgress || navigating
        snapshotFlow { pager.settledPage to (pager.isScrollInProgress || navigating) }.collect { (page, scrolling) ->
            if (ignoreCurrentScroll) {
                if (!scrolling) {
                    lastId = latestItems.getOrNull(page)?.id
                    ignoreCurrentScroll = false
                }
            } else if (!scrolling) {
                val item = latestItems.getOrNull(page)
                if (item != null && item.id != lastId) {
                    lastId = item.id
                    latestSettled?.invoke(item)
                }
            }
        }
    }

    val move: (Int) -> Unit = { direction ->
        if (!navigating && !pager.isScrollInProgress) {
            val target = (pager.currentPage + direction).coerceIn(0, latestItems.lastIndex)
            if (target != pager.currentPage) {
                navigating = true
                scope.launch {
                    try {
                        if (motionEnabled) {
                            pager.animateScrollToPage(
                                target,
                                animationSpec = spring(dampingRatio = 0.86f, stiffness = 380f),
                            )
                        } else {
                            pager.scrollToPage(target)
                        }
                    } finally {
                        navigating = false
                    }
                }
            }
        }
    }
    val springFling = PagerDefaults.flingBehavior(
        state = pager,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapAnimationSpec = spring(dampingRatio = 0.86f, stiffness = 380f),
    )
    // Default snapping can still run a decay approach with a zero-duration snap spec.
    // Reduced motion instead settles the nearest page in one logical scroll operation.
    val immediateFling = remember(pager) {
        object : TargetedFlingBehavior {
            override suspend fun ScrollScope.performFling(
                initialVelocity: Float,
                onRemainingDistanceUpdated: (Float) -> Unit,
            ): Float {
                val stride = pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing
                val distance = -pager.currentPageOffsetFraction * stride
                onRemainingDistanceUpdated(distance)
                val consumed = scrollBy(distance)
                onRemainingDistanceUpdated(0f)
                return if (abs(distance - consumed) > 0.5f) initialVelocity else 0f
            }
        }
    }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val settledPage = pager.settledPage.coerceIn(items.indices)
    val pageDescription = "${items.size}개 중 ${settledPage + 1}번째 카드"

    Column(modifier.semantics { isTraversalGroup = true }) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            pageSpacing = 8.dp,
            beyondViewportPageCount = 0,
            key = { page -> items[page].id },
            flingBehavior = if (motionEnabled) springFling else immediateFling,
            verticalAlignment = Alignment.Top,
        ) { page ->
            val item = items[page]
            LiquidCarouselCard(
                item = item,
                dark = dark,
                immersive = immersive,
                motionEnabled = motionEnabled,
                allowBlur = motionEnabled && !lowRam && Build.VERSION.SDK_INT >= 31,
                offset = { (pager.currentPage - page + pager.currentPageOffsetFraction).coerceIn(-1f, 1f) },
                isMoving = { pager.isScrollInProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    // Neighbouring pages remain visible without exposing duplicate TalkBack
                    // actions. HorizontalPager retains its own accessible scroll actions.
                    .then(if (page != pager.currentPage) Modifier.clearAndSetSemantics { } else Modifier),
                onAction = { onAction(item) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = { move(-1) },
                enabled = settledPage > 0 && !navigating && !pager.isScrollInProgress,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), CircleShape)
                    .semantics { contentDescription = "이전 카드" },
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(
                text = "${settledPage + 1} / ${items.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = pageDescription
                    stateDescription = latestItems.getOrNull(settledPage)?.title.orEmpty()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            IconButton(
                onClick = { move(1) },
                enabled = settledPage < items.lastIndex && !navigating && !pager.isScrollInProgress,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), CircleShape)
                    .semantics { contentDescription = "다음 카드" },
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun LiquidCarouselCard(
    item: LiquidCardItem,
    dark: Boolean,
    immersive: Boolean,
    motionEnabled: Boolean,
    allowBlur: Boolean,
    offset: () -> Float,
    isMoving: () -> Boolean,
    modifier: Modifier,
    onAction: () -> Unit,
) {
    val atmosphere by produceState(item.accent, item.artwork, item.accent) {
        // Tiny fixed-cost sampling runs off the UI thread; no decoder, Palette dependency,
        // persistent bitmap cache, or ownership of the supplied bitmap is introduced here.
        value = withContext(Dispatchers.Default) { liquidArtworkAccent(item.artwork, item.accent) }
    }
    val artwork = remember(item.artwork) {
        item.artwork?.takeUnless { it.isRecycled }?.asImageBitmap()
    }
    val density = LocalDensity.current
    val blurEffects = remember(allowBlur, immersive, density.density) {
        if (allowBlur) {
            List(5) { level ->
                if (level == 0) null else BlurEffect(
                    radiusX = with(density) { (level * if (immersive) 2.8f else 0.8f).dp.toPx() },
                    radiusY = with(density) { (level * if (immersive) 1.3f else 0.4f).dp.toPx() },
                    edgeTreatment = TileMode.Clamp,
                )
            }
        } else emptyList()
    }
    val shell = if (dark) Color(0xFF191B25) else Color(0xFFFFFFFF)
    val ink = if (dark) Color(0xFFF5F5F7) else Color(0xFF1C1C1E)
    val secondaryInk = if (dark) Color(0xFFC7C7CC) else Color(0xFF636366)
    val cardShape = RoundedCornerShape(32.dp)

    Column(
        modifier = modifier
            .graphicsLayer {
                val distance = if (motionEnabled) abs(offset()) else 0f
                val stretch = sin(distance * Math.PI).toFloat()
                if (immersive && motionEnabled) {
                    // Spring-powered pull-in: the departing card tilts into a
                    // vanishing point and expands back out as it snaps home.
                    scaleX = 1f - distance * .18f + stretch * .055f
                    scaleY = 1f - distance * .16f - stretch * .032f
                    rotationZ = -offset() * 6f
                    rotationY = offset() * 11f
                    translationY = distance * 22.dp.toPx()
                    cameraDistance = 18.dp.toPx()
                    alpha = 1f - distance * .23f
                } else {
                    scaleX = 1f - distance * .035f + stretch * .012f
                    scaleY = 1f - distance * .025f - stretch * .008f
                }
            }
            .clip(cardShape)
            .background(shell)
            .drawWithCache {
                val glow = Brush.radialGradient(
                    colors = listOf(atmosphere.copy(alpha = if (dark) 0.38f else 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.15f),
                    radius = size.width * 1.05f,
                )
                val lowerGlow = Brush.radialGradient(
                    colors = listOf(atmosphere.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(0f, size.height * 0.85f),
                    radius = size.width * 0.85f,
                )
                onDrawBehind {
                    // Shader gradients stay inside the card; no blurred full-screen layer.
                    clipRect { drawRect(glow); drawRect(lowerGlow) }
                }
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1.12f)
                .graphicsLayer {
                    val position = if (motionEnabled) offset() else 0f
                    shape = RoundedCornerShape(
                        topStart = (24f + 8f * abs(position)).dp,
                        topEnd = 24.dp,
                        bottomEnd = (32f - 8f * abs(position)).dp,
                        bottomStart = 24.dp,
                    )
                    clip = true
                }
                .background(lerp(shell, atmosphere, 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val position = if (motionEnabled) offset() else 0f
                        // Overscan keeps edges covered during the small parallax and blur.
                        scaleX = 1.08f
                        scaleY = 1.08f
                        translationX = -position * size.width * 0.025f
                        renderEffect = if (allowBlur && isMoving()) {
                            blurEffects[(abs(position) * 4f).roundToInt().coerceIn(0, 4)]
                        } else null
                    }.then(
                        if (immersive && motionEnabled) Modifier.liquidLensWarp(offset, enabled = true)
                        else Modifier,
                    ),
                )
            } else {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = ink.copy(alpha = 0.8f),
                    modifier = Modifier.size(64.dp),
                )
            }
            Spacer(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.2f))),
                ),
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                item.title,
                color = ink,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, color = secondaryInk, style = MaterialTheme.typography.bodyLarge)
            }
            if (item.detail.isNotBlank()) {
                Text(item.detail, color = secondaryInk, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(2.dp))
            Button(
                onClick = onAction,
                enabled = item.enabled,
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)
                    .semantics { contentDescription = "${item.title}, ${item.actionLabel}" },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                shape = RoundedCornerShape(50.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (dark) Color.White else Color(0xFF1C1C1E),
                    contentColor = if (dark) Color(0xFF191B25) else Color.White,
                ),
            ) {
                Text(item.actionLabel, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun rememberLiquidMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember(resolver) { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = ValueAnimator.areAnimatorsEnabled()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        enabled = ValueAnimator.areAnimatorsEnabled()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

private fun liquidArtworkAccent(bitmap: Bitmap?, fallback: Color): Color {
    if (bitmap == null || bitmap.isRecycled) return fallback
    var readable: Bitmap? = null
    return try {
        val source = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)?.also { readable = it } ?: return fallback
        } else bitmap
        var red = 0f
        var green = 0f
        var blue = 0f
        var weightSum = 0f
        for (y in 0 until 5) {
            for (x in 0 until 5) {
                val pixel = source.getPixel(
                    ((x + 0.5f) * source.width / 5).toInt().coerceIn(0, source.width - 1),
                    ((y + 0.5f) * source.height / 5).toInt().coerceIn(0, source.height - 1),
                )
                val r = android.graphics.Color.red(pixel) / 255f
                val g = android.graphics.Color.green(pixel) / 255f
                val b = android.graphics.Color.blue(pixel) / 255f
                val chroma = maxOf(r, g, b) - minOf(r, g, b)
                val weight = chroma * android.graphics.Color.alpha(pixel) / 255f
                red += r * weight
                green += g * weight
                blue += b * weight
                weightSum += weight
            }
        }
        if (weightSum < 0.2f) fallback
        else lerp(fallback, Color(red / weightSum, green / weightSum, blue / weightSum), 0.8f)
    } catch (_: IllegalArgumentException) {
        fallback
    } catch (_: IllegalStateException) {
        fallback
    } finally {
        readable?.recycle() // Only a temporary software copy, never the caller's bitmap.
    }
}
