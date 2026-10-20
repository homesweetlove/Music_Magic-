package com.musicmagic.app.player

import android.content.ComponentName
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.musicmagic.app.library.CoverArt
import com.musicmagic.app.library.LibraryMedia
import com.musicmagic.app.library.LibraryTrack
import com.musicmagic.app.ui.LiquidCardCarousel
import com.musicmagic.app.ui.LiquidCardItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Offline music playback for the same SAF files used by the metadata editor.
 * Artwork decoding is deliberately restricted to the focused card and its neighbors.
 */
@Composable
fun MusicPlayerScreen(
    tracks: List<LibraryTrack>,
    revision: Int,
    onMenu: () -> Unit,
    onPickFolder: () -> Unit,
    onPickFile: () -> Unit,
    onEdit: (android.net.Uri) -> Unit,
) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playing by remember { mutableStateOf(false) }
    var currentId by remember { mutableStateOf<String?>(null) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf("") }
    var focusIndex by remember(tracks) { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var draggedProgress by remember { mutableStateOf(0f) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val pendingController = MediaController.Builder(context, token).buildAsync()
        var attached = true
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.isPlaying
                currentId = player.currentMediaItem?.mediaId
                duration = player.duration.takeIf { it > 0L } ?: 0L
                position = player.currentPosition.coerceAtLeast(0L)
                shuffle = player.shuffleModeEnabled
                repeatMode = player.repeatMode
            }
            override fun onPlayerError(exception: PlaybackException) {
                error = "재생할 수 없는 파일입니다: ${exception.errorCodeName}"
            }
        }
        pendingController.addListener({
            runCatching { pendingController.get() }.onSuccess { connected ->
                if (attached) {
                    controller = connected
                    connected.addListener(listener)
                    playing = connected.isPlaying
                    currentId = connected.currentMediaItem?.mediaId
                    duration = connected.duration.takeIf { it > 0L } ?: 0L
                    position = connected.currentPosition.coerceAtLeast(0L)
                    shuffle = connected.shuffleModeEnabled
                    repeatMode = connected.repeatMode
                }
            }.onFailure {
                if (attached) error = "오디오 서비스를 연결할 수 없습니다: ${it.message.orEmpty()}"
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            attached = false
            controller?.removeListener(listener)
            controller = null
            MediaController.releaseFuture(pendingController)
        }
    }

    LaunchedEffect(controller) {
        while (true) {
            controller?.let {
                position = it.currentPosition.coerceAtLeast(0L)
                duration = it.duration.takeIf { value -> value > 0L } ?: 0L
            }
            delay(400)
        }
    }

    val focus = tracks.getOrNull(focusIndex)
    val activeIndex = tracks.indexOfFirst { it.uri.toString() == currentId }
    val activeTrack = tracks.getOrNull(activeIndex)
    val displayTrack = activeTrack ?: focus
    val artworks by produceState<Map<String, CoverArt>>(emptyMap(), tracks, focusIndex, activeIndex, revision) {
        value = withContext(Dispatchers.IO) {
            val indices = ((maxOf(0, focusIndex - 1)..minOf(tracks.lastIndex, focusIndex + 1)).toList() +
                listOf(activeIndex)).distinct().filter { it in tracks.indices }
            indices.map { index ->
                tracks[index].let { it.uri.toString() to LibraryMedia.cover(context, it.uri, revision) }
            }.toMap()
        }
    }
    // Background follows the currently PLAYING cover even if the user browses other cards.
    val target = displayTrack?.let { artworks[it.uri.toString()]?.accent } ?: Color(0xFF6389C8)
    val accent by animateColorAsState(target, animationSpec = tween(550), label = "album cover tint")
    val backdrop by animateColorAsState(lerp(Color(0xFF0B1020), accent, 0.43f), tween(600), label = "fluid backdrop")

    fun play(index: Int) {
        val connected = controller ?: run { error = "플레이어를 연결하는 중입니다."; return }
        if (index !in tracks.indices) return
        error = ""
        val id = tracks[index].uri.toString()
        if (connected.currentMediaItem?.mediaId == id && connected.mediaItemCount == tracks.size) {
            connected.play()
            return
        }
        val queue = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.uri.toString())
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .build(),
                )
                .build()
        }
        connected.setMediaItems(queue, index, 0L)
        connected.prepare()
        connected.play()
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(backdrop, Color(0xFF10111B), Color(0xFF090A10))))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMenu) { Icon(Icons.Rounded.Menu, "메뉴 열기", tint = Color.White) }
            Text("MUSIC MAGIC", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onPickFolder) { Icon(Icons.Rounded.FolderOpen, "음악 폴더 선택", tint = Color.White) }
        }

        Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your sound, in color.", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("앨범 커버의 색으로 채워지는 나만의 플레이어", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        }

        if (tracks.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(52.dp))
                Text("재생할 음악이 없어요", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("오프라인 음악 폴더나 파일을 선택하면 앨범별 카드가 나타납니다.", color = Color.White.copy(alpha = 0.7f))
                Button(onClick = onPickFolder) { Text("음악 폴더 열기") }
                TextButton(onClick = onPickFile) { Text("음악 파일 하나 선택", color = Color.White) }
            }
        } else {
            // The existing carousel implements spring snapping, neighboring previews and
            // graceful reduced-motion/low-RAM fallbacks inspired by Inspora.
            LiquidCardCarousel(
                items = tracks.map { song ->
                    val art = artworks[song.uri.toString()]
                    LiquidCardItem(
                        id = song.uri.toString(),
                        title = song.album,
                        subtitle = song.title,
                        detail = song.artist,
                        artwork = art?.bitmap,
                        accent = art?.accent ?: accent,
                        actionLabel = "이 곡 재생",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                onSettled = { card ->
                    focusIndex = tracks.indexOfFirst { it.uri.toString() == card.id }.coerceAtLeast(0)
                },
                onAction = { card -> play(tracks.indexOfFirst { it.uri.toString() == card.id }) },
            )

            Column(Modifier.fillMaxWidth().padding(horizontal = 25.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (activeTrack != null) "지금 재생 중" else "선택한 곡", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        Text(displayTrack?.title.orEmpty(), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(displayTrack?.artist.orEmpty(), color = Color.White.copy(alpha = 0.69f), fontSize = 14.sp, maxLines = 1)
                    }
                    IconButton(onClick = { focus?.let { onEdit(it.uri) } }) {
                        Icon(Icons.Rounded.Edit, "선택한 곡 정보 편집", tint = Color.White)
                    }
                }

                Slider(
                    value = if (dragging) draggedProgress else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { dragging = true; draggedProgress = it },
                    onValueChangeFinished = {
                        if (duration > 0) controller?.seekTo((duration * draggedProgress).toLong())
                        dragging = false
                    },
                    enabled = duration > 0L,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                    Text(formatTime(duration), color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { controller?.shuffleModeEnabled = !shuffle }, enabled = controller != null) {
                        Icon(Icons.Rounded.Shuffle, "셔플", tint = if (shuffle) Color.White else Color.White.copy(alpha = 0.48f))
                    }
                    IconButton(onClick = { controller?.seekToPreviousMediaItem() }, enabled = controller != null) {
                        Icon(Icons.Rounded.SkipPrevious, "이전 곡", modifier = Modifier.size(34.dp), tint = Color.White)
                    }
                    Button(
                        onClick = {
                            if (playing) controller?.pause()
                            else if (controller?.mediaItemCount == 0) play(focusIndex)
                            else controller?.play()
                        },
                        enabled = controller != null,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF111322)),
                        modifier = Modifier.size(76.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playing) "일시정지" else "재생", modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = { controller?.seekToNextMediaItem() }, enabled = controller != null) {
                        Icon(Icons.Rounded.SkipNext, "다음 곡", modifier = Modifier.size(34.dp), tint = Color.White)
                    }
                    IconButton(onClick = {
                        controller?.repeatMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    }, enabled = controller != null) {
                        Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            "반복 재생", tint = if (repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.48f) else Color.White)
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text("${tracks.size}곡 · 로컬 파일 재생", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium)
                if (error.isNotBlank()) Text(error, color = Color(0xFFFFC4B6), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L)
}
