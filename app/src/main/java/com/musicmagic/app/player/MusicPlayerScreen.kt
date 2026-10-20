package com.musicmagic.app.player

import android.content.ComponentName
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.musicmagic.app.ui.GlassIconButton
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
    var queueVersion by remember { mutableIntStateOf(0) }
    var showLyrics by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val pendingController = MediaController.Builder(context, token).buildAsync()
        var attached = true
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_TIMELINE_CHANGED)) queueVersion++
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
    val queuedTracks = remember(controller, queueVersion, currentId, tracks) {
        controller?.let { connected ->
            (0 until connected.mediaItemCount).mapNotNull { index ->
                val id = connected.getMediaItemAt(index).mediaId
                tracks.firstOrNull { it.uri.toString() == id }
            }
        }.orEmpty()
    }
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

    fun play(index: Int, source: List<LibraryTrack> = tracks) {
        val connected = controller ?: run { error = "플레이어를 연결하는 중입니다."; return }
        if (index !in source.indices) return
        error = ""
        val id = source[index].uri.toString()
        val sameQueue = connected.mediaItemCount == source.size && source.indices.all {
            connected.getMediaItemAt(it).mediaId == source[it].uri.toString()
        }
        if (connected.currentMediaItem?.mediaId == id && sameQueue) {
            connected.play()
            return
        }
        val queue = source.map { track ->
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
        // PlaybackService enriches the active media item with embedded album art.
        connected.setMediaItems(queue, index, 0L)
        connected.prepare()
        connected.play()
    }

    fun playCollection(source: List<LibraryTrack>) {
        if (source.isNotEmpty()) play(0, source)
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(
                lerp(accent, Color(0xFF10131C), 0.6f),
                backdrop, Color(0xFF12121C), Color(0xFF090A10),
            )))
            .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.13f), Color.Transparent), radius = 960f))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 38.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, start = 22.dp, end = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                icon = Icons.Rounded.Menu, contentDescription = "메뉴 열기", onClick = onMenu,
                tint = Color.White, containerColor = Color.White.copy(alpha = 0.12f),
                borderColor = Color.White.copy(alpha = 0.15f),
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("MUSIC MAGIC", color = Color.White.copy(alpha = 0.63f),
                    fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                Text("지금 듣기", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            GlassIconButton(
                icon = Icons.Rounded.FolderOpen, contentDescription = "음악 폴더 선택", onClick = onPickFolder,
                tint = Color.White, containerColor = Color.White.copy(alpha = 0.12f),
                borderColor = Color.White.copy(alpha = 0.15f),
            )
        }

        Column(Modifier.padding(horizontal = 25.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Feel every note.", color = Color.White, fontSize = 31.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
            Text("앨범마다 달라지는 컬러와 음악의 분위기", color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
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
                Button(onClick = onPickFolder, shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF121420))) { Text("음악 폴더 열기") }
                TextButton(onClick = onPickFile) { Text("음악 파일 하나 선택", color = Color.White) }
            }
        } else {
            // The existing carousel implements spring snapping, neighboring previews and
            // graceful reduced-motion/low-RAM fallbacks inspired by Inspora.
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    surface = Color(0xFF191B25),
                    onSurface = Color.White,
                    onSurfaceVariant = Color.White.copy(alpha = 0.75f),
                    primary = Color.White,
                    onPrimary = Color(0xFF191B25),
                ),
            ) {
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
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color.White.copy(alpha = 0.105f),
                contentColor = Color.White,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 21.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (activeTrack != null) "NOW PLAYING" else "SELECTED TRACK",
                            color = Color.White.copy(alpha = 0.63f), fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 1.7.sp)
                        Text(displayTrack?.title.orEmpty(), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(displayTrack?.artist.orEmpty(), color = Color.White.copy(alpha = 0.69f), fontSize = 14.sp, maxLines = 1)
                    }
                    GlassIconButton(
                        icon = Icons.Rounded.Edit, contentDescription = "선택한 곡 정보 편집",
                        onClick = { displayTrack?.let { onEdit(it.uri) } },
                        tint = Color.White, containerColor = Color.White.copy(alpha = 0.11f),
                        borderColor = Color.White.copy(alpha = 0.13f),
                    )
                }

                Slider(
                    value = if (dragging) draggedProgress else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { dragging = true; draggedProgress = it },
                    onValueChangeFinished = {
                        if (duration > 0) controller?.seekTo((duration * draggedProgress).toLong())
                        dragging = false
                    },
                    enabled = duration > 0L,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                    ),
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
                        shape = RoundedCornerShape(50.dp),
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

                Spacer(Modifier.height(5.dp))
                Text("${tracks.size}곡 · OFFLINE LIBRARY", color = Color.White.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.labelMedium, letterSpacing = 0.9.sp)
                if (error.isNotBlank()) Text(error, color = Color(0xFFFFC4B6), style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 25.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("LYRICS", fontSize = 11.sp, letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.72f))
                TextButton(onClick = { showLyrics = !showLyrics }) {
                    Text(if (showLyrics) "가사 닫기" else "가사 보기", color = Color.White)
                }
            }
            if (showLyrics) LyricsPanel(
                uri = displayTrack?.uri, revision = revision,
                positionMs = position, onSeek = { controller?.seekTo(it) },
            )
            CollectionsPanel(
                tracks = tracks,
                selected = focus,
                revision = revision,
                onPlayQueue = ::playCollection,
            )

            if (queuedTracks.size > 1) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("UP NEXT", color = Color.White.copy(alpha = 0.67f),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.6.sp, modifier = Modifier.padding(start = 4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White.copy(alpha = 0.065f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.085f)),
                    ) {
                        Column(Modifier.padding(vertical = 7.dp)) {
                            val queueIndex = queuedTracks.indexOfFirst { it.uri.toString() == currentId }
                            val nextStart = queueIndex.coerceAtLeast(0)
                            (1..minOf(3, queuedTracks.size - 1)).forEach { offset ->
                                val index = (nextStart + offset) % queuedTracks.size
                                val song = queuedTracks[index]
                                Row(
                                    Modifier.fillMaxWidth().clickable { play(index, queuedTracks) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(42.dp)
                                        .background(accent.copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.MusicNote, null, tint = Color.White,
                                            modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(13.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(song.artist, fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.61f),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "이 곡 재생",
                                        tint = Color.White.copy(alpha = 0.74f),
                                        modifier = Modifier.size(21.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L)
}
