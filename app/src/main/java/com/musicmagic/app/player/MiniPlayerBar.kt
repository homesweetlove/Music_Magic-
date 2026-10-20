package com.musicmagic.app.player

import android.content.ComponentName
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.min
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.musicmagic.app.library.LibraryMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Compact playback controls remain available while the metadata editor is visible. */
@Composable
fun MiniPlayerBar(onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var mediaId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }
    var pulledUp by remember { mutableFloatStateOf(0f) }
    val animatedPull by animateFloatAsState(pulledUp, spring(), label = "mini-player expansion")

    DisposableEffect(context) {
        val pending = MediaController.Builder(
            context, SessionToken(context, ComponentName(context, PlaybackService::class.java))
        ).buildAsync()
        var alive = true
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                val item = player.currentMediaItem
                mediaId = item?.mediaId
                title = item?.mediaMetadata?.title?.toString().orEmpty()
                artist = item?.mediaMetadata?.artist?.toString().orEmpty()
                playing = player.isPlaying
            }
        }
        pending.addListener({
            runCatching { pending.get() }.onSuccess { player ->
                if (alive) {
                    controller = player
                    player.addListener(listener)
                    mediaId = player.currentMediaItem?.mediaId
                    title = player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
                    artist = player.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty()
                    playing = player.isPlaying
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            alive = false
            controller?.removeListener(listener)
            controller = null
            MediaController.releaseFuture(pending)
        }
    }
    val artwork by produceState<android.graphics.Bitmap?>(null, mediaId) {
        val uri = mediaId?.let { runCatching { Uri.parse(it) }.getOrNull() }
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            LibraryMedia.cover(context, uri).bitmap
        }
    }
    if (mediaId == null) return

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
            .graphicsLayer {
                translationY = animatedPull
                val lift = (-animatedPull / (100f * density)).coerceIn(0f, 1f)
                scaleX = 1f + lift * .035f
                scaleY = 1f + lift * .04f
            }
            .pointerInput(onExpand) {
                var distance = 0f
                detectVerticalDragGestures(
                    onDragStart = { distance = 0f },
                    onVerticalDrag = { change, drag ->
                        change.consume()
                        distance += drag
                        pulledUp = min(0f, distance).coerceAtLeast(-120.dp.toPx())
                    },
                    onDragEnd = {
                        val expand = distance < -45.dp.toPx()
                        pulledUp = 0f
                        if (expand) onExpand()
                    },
                    onDragCancel = { pulledUp = 0f },
                )
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(49.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onExpand),
                contentAlignment = Alignment.Center,
            ) {
                if (artwork != null) Image(
                    bitmap = artwork!!.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.size(49.dp), contentScale = ContentScale.Crop,
                )
                else Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).clickable(onClick = onExpand)) {
                Text(title.ifBlank { "음악 재생 중" }, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist.ifBlank { "재생 화면 열기" }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { if (playing) controller?.pause() else controller?.play() }) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (playing) "일시정지" else "재생")
            }
            IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                Icon(Icons.Rounded.SkipNext, "다음 곡")
            }
        }
    }
}
