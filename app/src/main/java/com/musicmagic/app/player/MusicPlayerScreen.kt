package com.musicmagic.app.player

import android.content.ComponentName
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
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
import com.musicmagic.app.ui.rememberLiquidMotionEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

private val Ink = Color(0xFF0B0D16)

/** Fallback colours for cards without embedded artwork, so neighbours never look identical. */
private val FallbackAccents = listOf(
    Color(0xFF7B5CFF), Color(0xFFFF8A3D), Color(0xFF1FC79A),
    Color(0xFF3D7BFF), Color(0xFFFF4D7D), Color(0xFFB44DFF),
)

/**
 * Offline music playback for the same SAF files used by the metadata editor.
 * Layout and motion follow the Figma frame "플레이어 — 액체 카드 모션":
 * one focused screen (cards, song, controls, next song); lyrics, the queue and
 * playlists live in a sheet opened from the "다음 곡" bar.
 * Artwork decoding is deliberately restricted to the focused card and its neighbors.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var showMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val motion = rememberLiquidMotionEnabled()

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
    val queueIndex = queuedTracks.indexOfFirst { it.uri.toString() == currentId }
    val upNext: List<Pair<Int, LibraryTrack>> = if (queuedTracks.size > 1) {
        val start = queueIndex.coerceAtLeast(0)
        (1..minOf(5, queuedTracks.size - 1)).map { offset ->
            val index = (start + offset) % queuedTracks.size
            index to queuedTracks[index]
        }
    } else emptyList()
    val nextTrack = upNext.firstOrNull()?.second ?: tracks.getOrNull(focusIndex + 1)

    val artworks by produceState<Map<String, CoverArt>>(emptyMap(), tracks, focusIndex, activeIndex, revision) {
        value = withContext(Dispatchers.IO) {
            val indices = ((maxOf(0, focusIndex - 1)..minOf(tracks.lastIndex, focusIndex + 1)).toList() +
                listOf(activeIndex)).distinct().filter { it in tracks.indices }
            indices.map { index ->
                tracks[index].let { it.uri.toString() to LibraryMedia.cover(context, it.uri, revision) }
            }.toMap()
        }
    }
    val accents = tracks.mapIndexed { index, track ->
        artworks[track.uri.toString()]?.accent ?: FallbackAccents[index % FallbackAccents.size]
    }
    // Play button glow follows the PLAYING (or selected) cover.
    val displayAccent = displayTrack?.let { accents.getOrNull(tracks.indexOf(it)) } ?: FallbackAccents[0]
    val glow by animateColorAsState(displayAccent, tween(700), label = "play glow")

    val pager = rememberPagerState(initialPage = activeIndex.coerceAtLeast(0)) { tracks.size }
    val latestTracks by rememberUpdatedState(tracks)
    LaunchedEffect(pager, tracks) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            if (page in latestTracks.indices) focusIndex = page
        }
    }
    // Next/previous buttons and auto-advance bring the playing card to the centre.
    LaunchedEffect(activeIndex) {
        if (activeIndex in tracks.indices && pager.settledPage != activeIndex && !pager.isScrollInProgress) {
            if (motion) pager.animateScrollToPage(activeIndex, animationSpec = spring(0.72f, 320f))
            else pager.scrollToPage(activeIndex)
        }
    }

    // A short squeeze-and-bounce on the play button whenever the song changes.
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        snapshotFlow { currentId }.distinctUntilChanged().drop(1).collect {
            if (motion && it != null) {
                bounce.animateTo(0.88f, tween(110))
                bounce.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 520f))
            }
        }
    }

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

    Box(Modifier.fillMaxSize().background(Ink)) {
        LiquidBlobBackdrop(
            accent = {
                if (accents.isEmpty()) FallbackAccents[0] else {
                    val page = pager.currentPage.coerceIn(accents.indices)
                    val fraction = pager.currentPageOffsetFraction
                    val other = (page + if (fraction >= 0f) 1 else -1).coerceIn(accents.indices)
                    lerp(accents[page], accents[other], abs(fraction))
                }
            },
            stretch = { (abs(pager.currentPageOffsetFraction) * 2f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            // ---- 윗줄 ----
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, start = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    icon = Icons.Rounded.Menu, contentDescription = "메뉴 열기", onClick = onMenu,
                    tint = Color.White, containerColor = Color.White.copy(alpha = 0.12f),
                    borderColor = Color.White.copy(alpha = 0.16f),
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("지금 재생 중", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("내 음악 · ${tracks.size}곡", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
                GlassIconButton(
                    icon = Icons.Rounded.FolderOpen, contentDescription = "음악 폴더 선택", onClick = onPickFolder,
                    tint = Color.White, containerColor = Color.White.copy(alpha = 0.12f),
                    borderColor = Color.White.copy(alpha = 0.16f),
                )
            }

            if (tracks.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(52.dp))
                    Text("재생할 음악이 없어요", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text("음악 폴더나 파일을 고르면 앨범 카드가 나타나요.", color = Color.White.copy(alpha = 0.7f))
                    Button(onClick = onPickFolder, shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink)) { Text("음악 폴더 열기") }
                    TextButton(onClick = onPickFile) { Text("음악 파일 하나 고르기", color = Color.White) }
                }
            } else {

            // ---- 앨범 카드 ----
            FluidPlayerCarousel(
                cards = tracks.mapIndexed { index, song ->
                    FluidCard(
                        id = song.uri.toString(),
                        album = song.album.ifBlank { song.title },
                        artist = song.albumArtist.ifBlank { song.artist },
                        artwork = artworks[song.uri.toString()]?.bitmap,
                        accent = accents[index],
                    )
                },
                state = pager,
                modifier = Modifier.fillMaxWidth().weight(1f),
                onCardClick = { index ->
                    if (index == pager.currentPage) play(index)
                    else scope.launch { pager.animateScrollToPage(index, animationSpec = spring(0.72f, 320f)) }
                },
            )

            // ---- 곡 정보 ----
            Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = displayTrack,
                    transitionSpec = {
                        (fadeIn(tween(380, delayMillis = 80)) + slideInVertically(tween(420)) { it / 3 }) togetherWith
                            (fadeOut(tween(220)) + slideOutVertically(tween(260)) { -it / 3 })
                    },
                    contentKey = { it?.uri },
                    modifier = Modifier.weight(1f),
                    label = "song info",
                ) { song ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(song?.title.orEmpty(), color = Color.White, fontSize = 26.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(song?.artist?.ifBlank { null }, song?.album?.ifBlank { null }).joinToString(" · "),
                            color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                GlassIconButton(
                    icon = Icons.Rounded.Edit, contentDescription = "이 곡 정보 고치기",
                    onClick = { displayTrack?.let { onEdit(it.uri) } },
                    tint = Color.White, containerColor = Color.White.copy(alpha = 0.12f),
                    borderColor = Color.White.copy(alpha = 0.16f),
                )
            }

            // ---- 진행 막대 ----
            Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 14.dp)) {
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
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                        disabledThumbColor = Color.White.copy(alpha = 0.5f),
                        disabledInactiveTrackColor = Color.White.copy(alpha = 0.18f),
                    ),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(formatTime(duration), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            // ---- 재생 버튼들 ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { controller?.shuffleModeEnabled = !shuffle }, enabled = controller != null) {
                    Icon(Icons.Rounded.Shuffle, "섞어서 듣기", tint = if (shuffle) Color.White else Color.White.copy(alpha = 0.5f))
                }
                IconButton(onClick = { controller?.seekToPreviousMediaItem() }, enabled = controller != null,
                    modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "이전 곡", modifier = Modifier.size(38.dp), tint = Color.White)
                }
                Button(
                    onClick = {
                        if (playing) controller?.pause()
                        else if (controller?.mediaItemCount == 0) play(focusIndex)
                        else controller?.play()
                    },
                    enabled = controller != null,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White, contentColor = Color(0xFF111322),
                        disabledContainerColor = Color.White.copy(alpha = 0.6f),
                    ),
                    modifier = Modifier
                        .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
                        .shadow(26.dp, CircleShape, ambientColor = glow, spotColor = glow)
                        .size(76.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "잠시 멈춤" else "재생", modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { controller?.seekToNextMediaItem() }, enabled = controller != null,
                    modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "다음 곡", modifier = Modifier.size(38.dp), tint = Color.White)
                }
                IconButton(onClick = {
                    controller?.repeatMode = when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }, enabled = controller != null) {
                    Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "반복 재생", tint = if (repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.5f) else Color.White)
                }
            }
            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFFFC4B6), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 32.dp))
            }

            // ---- 다음 곡 막대 (누르면 가사·재생목록) ----
            Surface(
                onClick = { showMore = true },
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.09f),
                contentColor = Color.White,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            ) {
                Row(
                    Modifier.padding(start = 11.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val nextIndex = nextTrack?.let { tracks.indexOf(it) } ?: -1
                    val nextArt = nextTrack?.let { artworks[it.uri.toString()]?.bitmap }
                    val nextAccent = accents.getOrNull(nextIndex) ?: FallbackAccents[1]
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(lerp(nextAccent, Color.White, 0.15f), lerp(nextAccent, Ink, 0.4f)))),
                    ) {
                        if (nextArt != null && !nextArt.isRecycled) {
                            Image(nextArt.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (nextTrack != null) "다음 곡" else "더 보기", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                        Text(
                            nextTrack?.let { listOf(it.title, it.artist).filter(String::isNotBlank).joinToString(" · ") }
                                ?: "가사와 재생목록",
                            fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "가사와 재생목록 열기")
                }
            }
            }
        }
    }

    if (showMore) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { showMore = false },
            sheetState = sheet,
            containerColor = Color(0xFF15171F),
            contentColor = Color.White,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SheetTitle("가사")
                LyricsPanel(
                    uri = displayTrack?.uri, revision = revision,
                    positionMs = position, onSeek = { controller?.seekTo(it) },
                )
                if (upNext.isNotEmpty()) {
                    SheetTitle("다음 곡")
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        color = Color.White.copy(alpha = 0.06f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            upNext.forEach { (index, song) ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { play(index, queuedTracks) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val accent = accents.getOrNull(tracks.indexOf(song)) ?: FallbackAccents[0]
                                    Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(13.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(song.artist, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "이 곡 재생",
                                        tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(21.dp))
                                }
                            }
                        }
                    }
                }
                SheetTitle("재생목록")
                CollectionsPanel(
                    tracks = tracks,
                    selected = focus,
                    revision = revision,
                    onPlayQueue = ::playCollection,
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp))
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L)
}
