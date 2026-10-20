package com.musicmagic.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.media.MediaPlayer
import androidx.documentfile.provider.DocumentFile
import java.security.MessageDigest
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.musicmagic.app.library.LibraryMedia
import com.musicmagic.app.library.LibraryTrack
import com.musicmagic.app.library.MusicCollections
import com.musicmagic.app.ui.AudioComparison
import com.musicmagic.app.ui.AudioComparisonCarousel
import com.musicmagic.app.ui.MusicLibraryCarousel
import com.musicmagic.app.ui.SelectedMusicCarousel
import com.musicmagic.app.ui.GlassIconButton
import com.musicmagic.app.ui.MagicButton
import com.musicmagic.app.ui.MagicTypography
import com.musicmagic.app.player.MusicPlayerScreen
import com.musicmagic.app.player.MiniPlayerBar
import com.musicmagic.app.player.PlaybackService
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val IosBlue = Color(0xFF007AFF)
private val IosGreen = Color(0xFF30D158)
private val IosOrange = Color(0xFFFF9F0A)
private val LightBackground = Color(0xFFF2F2F7)
private val DarkBackground = Color(0xFF09090C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MusicMagicApp() }
    }
}

@Composable
private fun MusicMagicApp() {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var originalInfo by remember { mutableStateOf<AudioInfo?>(null) }
    var fileName by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var albumArtist by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var track by remember { mutableStateOf("") }
    var lyrics by remember { mutableStateOf("") }
    var folderFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var libraryTracks by remember { mutableStateOf<List<LibraryTrack>>(emptyList()) }
    var libraryRevision by remember { mutableIntStateOf(0) }
    var currentScreen by remember { mutableStateOf("editor") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var comparison by remember { mutableStateOf<AudioComparison?>(null) }
    var exportSource by remember { mutableStateOf<Pair<Uri, AudioInfo>?>(null) }
    var batchStatus by remember { mutableStateOf("") }
    var batchArtist by remember { mutableStateOf("") }
    var batchAlbum by remember { mutableStateOf("") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val lifecycle = (context as ComponentActivity).lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player?.release()
                player = null
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player?.release(); player = null }
    }
    var artworkBytes by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var success by remember { mutableStateOf(false) }

    // Restore a stored SAF grant by rescanning. Never cache raw audio bytes in prefs.
    LaunchedEffect(Unit) {
        MusicCollections.lastTree(context)?.let { tree ->
            val permitted = context.contentResolver.persistedUriPermissions.any {
                it.isReadPermission && it.uri == tree
            }
            if (!permitted) {
                MusicCollections.forgetTree(context)
                batchStatus = "기존 음악 폴더 접근 권한이 만료됐어요. 다시 선택해주세요."
                return@LaunchedEffect
            }
            busy = true
            batchStatus = "이전에 선택한 음악 폴더를 복원하는 중…"
            val result = runCatching { withContext(Dispatchers.IO) {
                LibraryMedia.scan(context, tree) { }
            } }
            result.fold(
                onSuccess = { tracks ->
                    libraryTracks = tracks
                    folderFiles = tracks.map { it.uri }
                    batchStatus = "${tracks.size}곡 복원 완료"
                },
                onFailure = {
                    MusicCollections.forgetTree(context)
                    batchStatus = "폴더 복원 실패: ${it.message ?: "다시 폴더를 선택해주세요."}"
                },
            )
            busy = false
        }
    }

    fun preview(uri: Uri) {
        player?.release()
        val candidate = MediaPlayer()
        player = candidate
        candidate.setOnPreparedListener {
            if (player === it) it.start()
        }
        candidate.setOnCompletionListener { if (player === it) { it.release(); player = null } }
        candidate.setOnErrorListener { media, _, _ ->
            if (player === media) { media.release(); player = null; status = "이 파일을 재생할 수 없습니다." }
            true
        }
        runCatching { candidate.setDataSource(context, uri); candidate.prepareAsync() }.onFailure {
            candidate.release()
            if (player === candidate) player = null
            status = "미리듣기 실패: ${it.message}"
        }
    }

    fun applyInfo(info: AudioInfo) {
        player?.release()
        player = null
        originalInfo = info
        fileName = info.fileName
        title = info.title
        artist = info.artist
        album = info.album
        albumArtist = info.albumArtist
        genre = info.genre
        year = info.year
        track = info.track
        lyrics = info.lyrics
        artworkBytes = null
    }

    fun openEditor(uri: Uri) {
        if (busy) return
        selectedUri = uri
        comparison = null
        busy = true
        currentScreen = "editor"
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { AudioMetadata.read(context, uri) }
            applyInfo(loaded)
            status = loaded.error.orEmpty()
            busy = false
        }
    }

    fun updateLibrary(oldUri: Uri, saved: SaveResult) {
        LibraryMedia.invalidateArtwork()
        MusicCollections.replaceUri(context, oldUri, saved.uri)
        libraryTracks = libraryTracks.map { entry ->
            if (LibraryMedia.sameDocument(entry.uri, oldUri)) LibraryMedia.updated(entry, saved.uri, saved.info) else entry
        }
        folderFiles = folderFiles.map { if (LibraryMedia.sameDocument(it, oldUri)) saved.uri else it }.distinct()
    }

    val m4aPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4")) { target ->
        val pending = exportSource
        exportSource = null
        if (target != null && pending != null) {
            val (source, sourceInfo) = pending
            if (source == target) {
                status = "원본과 다른 새 파일로 내보내세요."
                success = false
                return@rememberLauncherForActivityResult
            }
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { M4aConverter.convert(context, source, target) }
                result.fold(
                    onSuccess = {
                        comparison = AudioComparison(source, target, sourceInfo.title.ifBlank { sourceInfo.fileName },
                            "${sourceInfo.format} · ${sourceInfo.sampleRate} Hz", "AAC M4A · $it bytes")
                        status = "AAC M4A 변환 완료: $it bytes"; success = true
                    },
                    onFailure = { status = "M4A 변환 실패: " + (it.message ?: "알 수 없는 오류"); success = false }
                )
                busy = false
            }
        }
    }

    val wavPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { target ->
        val pending = exportSource
        exportSource = null
        if (target != null && pending != null) {
            val (source, sourceInfo) = pending
            if (source == target) {
                status = "원본과 다른 새 파일로 내보내세요."
                success = false
                return@rememberLauncherForActivityResult
            }
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { WavConverter.convert(context, source, target) }
                result.fold(
                    onSuccess = {
                        comparison = AudioComparison(source, target, sourceInfo.title.ifBlank { sourceInfo.fileName },
                            "${sourceInfo.format} · ${sourceInfo.sampleRate} Hz", "PCM WAV · $it bytes")
                        status = "실제 PCM WAV 변환 완료: $it bytes"; success = true
                    },
                    onFailure = { status = "WAV 변환 실패: " + (it.message ?: "알 수 없는 오류"); success = false }
                )
                busy = false
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            busy = true
            batchStatus = "음악 폴더를 읽는 중…"
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { LibraryMedia.scan(context, uri) { /* bounded background scan */ } }
                }
                result.fold(onSuccess = { tracks ->
                    libraryTracks = tracks
                    folderFiles = tracks.map { it.uri }
                    MusicCollections.rememberTree(context, uri)
                    batchStatus = "${tracks.size}개 음악 파일을 찾았어요. (최대 500곡)"
                }, onFailure = { batchStatus = "폴더를 읽을 수 없습니다: ${it.message}" })
                busy = false
            }
        }
    }

    val artPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { LibraryMedia.prepareArtwork(context, uri) }
                result.fold(onSuccess = {
                    artworkBytes = it
                    status = "앨범아트를 최대 1000px JPEG로 최적화했어요. 저장하면 음악 파일에 기록됩니다."
                }, onFailure = { status = it.message ?: "이미지를 읽을 수 없습니다." })
                busy = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedUri = uri
        comparison = null
        busy = true
        status = ""
        success = false
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        scope.launch {
            val info = withContext(Dispatchers.IO) { AudioMetadata.read(context, uri) }
            applyInfo(info)
            status = info.error.orEmpty()
            busy = false
        }
    }

    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF0A84FF),
            onPrimary = Color.White,
            background = DarkBackground,
            onBackground = Color(0xFFF5F5F7),
            surface = Color(0xFF1C1C1E),
            onSurface = Color(0xFFF5F5F7),
            surfaceVariant = Color(0xFF29292D),
            onSurfaceVariant = Color(0xFFC7C7CC),
            secondaryContainer = Color(0xFF1D3048),
            onSecondaryContainer = Color(0xFF91C6FF),
        )
    } else {
        lightColorScheme(
            primary = IosBlue,
            onPrimary = Color.White,
            background = LightBackground,
            onBackground = Color(0xFF1C1C1E),
            surface = Color.White,
            onSurface = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFFE9E9EE),
            onSurfaceVariant = Color(0xFF636366),
            secondaryContainer = Color(0xFFE5F0FF),
            onSecondaryContainer = Color(0xFF0065CF),
        )
    }

    MaterialTheme(colorScheme = colors, typography = MagicTypography) {
        val info = originalInfo
        val originalExtension = info?.fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val requestedExtension = fileName.substringAfterLast('.', "").lowercase()
        val extensionChanged = info != null && requestedExtension != originalExtension
        val editable = info?.supported == true && selectedUri != null

        val playbackTracks = if (libraryTracks.isNotEmpty()) libraryTracks else selectedUri?.let { uri ->
            info?.let { meta ->
                listOf(LibraryTrack(
                    uri = uri, fileName = meta.fileName,
                    title = meta.title.ifBlank { meta.fileName }, artist = meta.artist.ifBlank { "알 수 없는 아티스트" },
                    album = meta.album.ifBlank { "앨범 정보 없음" }, albumArtist = meta.albumArtist,
                    quality = meta.format,
                ))
            }
        }.orEmpty()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerContentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Spacer(Modifier.height(34.dp))
                    Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(54.dp).background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(18.dp)
                            ),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(29.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Music Magic", style = MaterialTheme.typography.headlineSmall)
                            Text("YOUR SOUND STUDIO", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 10.sp, letterSpacing = 1.sp)
                        }
                    }
                    Spacer(Modifier.height(36.dp))
                    Text("LIBRARY", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f),
                        fontSize = 11.sp, letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(start = 27.dp, bottom = 9.dp))
                    NavigationDrawerItem(
                        label = { Text("음악 정보 편집", fontWeight = FontWeight.Medium) },
                        selected = currentScreen == "editor",
                        icon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { currentScreen = "editor"; scope.launch { drawerState.close() } },
                        shape = RoundedCornerShape(19.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text("뮤직 플레이어", fontWeight = FontWeight.Medium) },
                        selected = currentScreen == "player",
                        icon = { Icon(Icons.Rounded.Headphones, null) },
                        onClick = { currentScreen = "player"; scope.launch { drawerState.close() } },
                        shape = RoundedCornerShape(19.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text("MADE FOR YOUR MUSIC", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        fontSize = 10.sp, letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(start = 27.dp, bottom = 32.dp))
                }
            },
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        if (targetState == "player") {
                            (slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow),
                            ) + scaleIn(initialScale = .91f, animationSpec = tween(340)) +
                                fadeIn(tween(280))) togetherWith
                                (fadeOut(tween(170)) + scaleOut(targetScale = .96f, animationSpec = tween(210)))
                        } else {
                            (fadeIn(tween(260)) + scaleIn(initialScale = .98f, animationSpec = tween(290))) togetherWith
                                (slideOutVertically(targetOffsetY = { it }, animationSpec = tween(320)) +
                                    fadeOut(tween(240)))
                        }
                    },
                    label = "Music Magic screen",
                ) { screen ->
                    if (screen == "player") {
                        MusicPlayerScreen(
                            tracks = playbackTracks,
                            revision = libraryRevision,
                            onMenu = { scope.launch { drawerState.open() } },
                            onPickFolder = { folderPicker.launch(null) },
                            onPickFile = { picker.launch(arrayOf("audio/*", "application/ogg", "application/octet-stream")) },
                            onEdit = ::openEditor,
                        )
                    } else {
                        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 116.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(
                        icon = Icons.Rounded.Menu, contentDescription = "메뉴 열기",
                        onClick = { scope.launch { drawerState.open() } },
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.095f),
                        shape = RoundedCornerShape(30.dp),
                    ) {
                        Text("●  OFFLINE STUDIO", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(1.dp))
                Text("Music Magic", style = MaterialTheme.typography.displayLarge)
                Text(
                    text = "당신의 음악, 더 아름답게.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    fontSize = 16.sp,
                )
                Surface(
                    shape = RoundedCornerShape(25.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.14f else 0.075f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(52.dp).background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(17.dp),
                            ), contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Headphones, null, tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("나만의 뮤직 스튜디오", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("앨범 감상부터 메타데이터 편집까지",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
                        }
                        GlassIconButton(
                            icon = Icons.Rounded.Headphones, contentDescription = "뮤직 플레이어 열기",
                            onClick = { currentScreen = "player" },
                            tint = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        )
                    }
                }

                 if (libraryTracks.isNotEmpty()) {
                    MusicLibraryCarousel(libraryTracks, busy, libraryRevision, onSelect = ::openEditor)
                }

                SectionCard(title = "폴더 · 일괄 편집", icon = Icons.Rounded.FolderOpen) {
                    MagicButton(onClick = { folderPicker.launch(null) }, enabled = !busy) { Text("음악 폴더 선택") }
                    Text("${folderFiles.size}곡 선택됨")
                    EditorField("일괄 아티스트 (빈칸은 유지)", batchArtist, { batchArtist = it })
                    EditorField("일괄 앨범 (빈칸은 유지)", batchAlbum, { batchAlbum = it })
                    MagicButton(onClick = {
                        player?.release(); player = null
                        PlaybackService.stopForEditing(folderFiles)
                        busy = true
                        scope.launch {
                            val (message, savedFiles) = withContext(Dispatchers.IO) {
                                var ok = 0
                                val errors = mutableListOf<String>()
                                val savedFiles = mutableListOf<Pair<Uri, SaveResult>>()
                                for (uri in folderFiles) {
                                    val info = AudioMetadata.read(context, uri)
                                    if (!info.supported) { errors.add(info.fileName); continue }
                                    val edits = AudioEdits(info.fileName, info.title,
                                        batchArtist.ifBlank { info.artist }, batchAlbum.ifBlank { info.album },
                                        info.albumArtist, info.genre, info.year, info.track, info.lyrics)
                                    AudioMetadata.save(context, uri, edits).fold(onSuccess = {
                                        ok++; savedFiles.add(uri to it)
                                    }, onFailure = { errors.add(info.fileName) })
                                }
                                ("$ok/${folderFiles.size}곡 저장 성공" + if (errors.isNotEmpty()) " · 실패: " + errors.take(5).joinToString() else "") to savedFiles
                            }
                            batchStatus = message
                            savedFiles.forEach { (old, saved) ->
                                updateLibrary(old, saved)
                                if (selectedUri?.let { LibraryMedia.sameDocument(it, old) } == true) {
                                    selectedUri = saved.uri
                                    originalInfo = saved.info
                                    if (batchArtist.isNotBlank()) artist = saved.info.artist
                                    if (batchAlbum.isNotBlank()) album = saved.info.album
                                }
                            }
                            libraryRevision++
                            comparison = null
                            busy = false
                        }
                    }, enabled = !busy && folderFiles.isNotEmpty() && (batchArtist.isNotBlank() || batchAlbum.isNotBlank())) { Text("선택 폴더 일괄 저장") }
                    MagicButton(onClick = {
                        busy = true
                        scope.launch {
                            batchStatus = withContext(Dispatchers.IO) {
                                val seen = mutableMapOf<String, String>()
                                val duplicates = mutableListOf<String>()
                                for (uri in folderFiles) {
                                    val name = DocumentFile.fromSingleUri(context, uri)?.name ?: uri.toString()
                                    val md = MessageDigest.getInstance("SHA-256")
                                    context.contentResolver.openInputStream(uri)?.use { stream ->
                                        val buf = ByteArray(65536)
                                        while (true) { val n = stream.read(buf); if (n < 0) break; md.update(buf, 0, n) }
                                    }
                                    val key = md.digest().joinToString("") { "%02x".format(it) }
                                    val first = seen.putIfAbsent(key, name)
                                    if (first != null) duplicates.add("$" + "{first} / " + name)
                                }
                                if (duplicates.isEmpty()) "바이트 단위 중복 파일이 없습니다." else "중복: " + duplicates.take(8).joinToString("; ")
                            }
                            busy = false
                        }
                    }, enabled = !busy && folderFiles.isNotEmpty()) { Text("중복 음악 검사 (SHA-256)") }
                    if (batchStatus.isNotBlank()) Text(batchStatus)
                }

                comparison?.let { result ->
                    androidx.compose.runtime.key(result) {
                        AudioComparisonCarousel(result, busy, libraryRevision, onPlay = ::preview)
                    }
                }

                MagicButton(onClick = {
                    player?.release(); player = null
                    PlaybackService.stopForEditing(emptyList())
                    busy = true
                    scope.launch {
                        val undoPrefs = context.getSharedPreferences("musicmagic", android.content.Context.MODE_PRIVATE)
                        val oldUri = (undoPrefs.getString("undo_original_uri", null)
                            ?: undoPrefs.getString("undo_uri", null))?.let(Uri::parse)
                        val result = withContext(Dispatchers.IO) { AudioMetadata.undoLastWithUri(context) }
                        result.fold(
                            onSuccess = { saved ->
                                LibraryMedia.invalidateArtwork()
                                oldUri?.let { updateLibrary(it, saved) }
                                if (selectedUri?.let { oldUri != null && LibraryMedia.sameDocument(it, oldUri) } == true) {
                                    selectedUri = saved.uri
                                    applyInfo(saved.info)
                                }
                                libraryRevision++
                                comparison = null
                                status = "마지막 수정 이전 상태로 복원했습니다: " + saved.info.fileName; success = true
                            },
                            onFailure = { status = it.message ?: "복원에 실패했습니다."; success = false }
                        )
                        busy = false
                    }
                }, enabled = !busy) { Text("↶ 마지막 저장 실행 취소") }

                FilePickerCard(
                    info = info,
                    busy = busy,
                    onPick = {
                        picker.launch(
                            arrayOf(
                                "audio/*",
                                "application/ogg",
                                "application/octet-stream",
                            )
                        )
                    },
                )

                if (info != null) {
                    selectedUri?.let { uri ->
                        androidx.compose.runtime.key(uri) {
                            SelectedMusicCarousel(uri, info, artworkBytes, libraryRevision, busy,
                                onArt = { artPicker.launch("image/*") }, onPlay = { preview(uri) })
                        }
                    }
                    MagicButton(onClick = {
                        selectedUri?.let(::preview)
                    }, enabled = !busy) { Text("▶ 음악 미리듣기") }
                    MagicButton(onClick = { player?.release(); player = null }, enabled = player != null) { Text("■ 재생 정지") }
                    SectionCard(title = "음악 정보", icon = Icons.Rounded.MusicNote) {
                        EditorField("제목", title, { title = it })
                        DividerInset()
                        EditorField("아티스트 · 만든이", artist, { artist = it })
                        DividerInset()
                        EditorField("앨범", album, { album = it })
                        DividerInset()
                        EditorField("앨범 아티스트", albumArtist, { albumArtist = it })
                    }

                    MagicButton(onClick = {
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { MusicBrainzLookup.search(title, artist) }
                            result.fold(
                                onSuccess = { tags ->
                                    title = tags.title
                                    artist = tags.artist
                                    if (tags.album.isNotBlank()) album = tags.album
                                    status = "MusicBrainz 검색 결과를 입력했습니다. 확인 후 저장하세요."
                                    success = true
                                },
                                onFailure = { status = it.message ?: "온라인 검색 실패"; success = false }
                            )
                            busy = false
                        }
                    }, enabled = !busy && title.isNotBlank()) { Text("MusicBrainz에서 곡 정보 자동 검색") }

                    SectionCard(title = "세부 정보", icon = Icons.Rounded.Edit) {
                        EditorField("장르", genre, { genre = it })
                        DividerInset()
                        EditorField("연도", year, { year = it })
                        DividerInset()
                        EditorField("트랙 번호", track, { track = it })
                    }

                    SectionCard(title = "빠른 태그 정리", icon = Icons.Rounded.Edit) {
                        MagicButton(onClick = {
                            val base = fileName.substringBeforeLast('.', fileName)
                            val parts = base.split(" - ", limit = 2)
                            if (parts.size == 2) {
                                artist = parts[0].trim()
                                title = parts[1].trim()
                                status = "파일명에서 아티스트·제목을 추출했어요. 확인 후 저장하세요."
                            } else status = "파일명이 '아티스트 - 제목' 형식이어야 합니다."
                        }) { Text("파일명에서 제목·아티스트 추출") }
                        MagicButton(onClick = {
                            fun clean(value: String) = value.trim().replace(Regex("\\s+"), " ")
                            title = clean(title); artist = clean(artist); album = clean(album)
                            albumArtist = clean(albumArtist); genre = clean(genre)
                            year = clean(year); track = clean(track)
                            status = "태그의 불필요한 공백을 정리했습니다."
                        }) { Text("태그 공백 일괄 정규화") }
                        Text("오디오 품질: " + info.format + " · " + info.bitRate + " kbps · " + info.sampleRate + " Hz · " + info.durationSeconds + "초")
                        Text(if (info.format in setOf("FLAC", "WAV", "AIFF", "AIF")) "무손실 저장 형식 (원본 품질은 별도 검증 필요)" else "손실 또는 형식별 확인 필요")
                    }

                    SectionCard(title = "가사", icon = Icons.Rounded.Edit) {
                        Text("LRC 타임스탬프([mm:ss.xx])를 포함한 가사도 텍스트로 편집할 수 있어요. 단, 별도 동기화 태그로 저장하지는 않습니다.")
                        androidx.compose.material3.OutlinedTextField(value = lyrics, onValueChange = { lyrics = it }, label = { Text("가사") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                    }

                    SectionCard(title = "앨범아트", icon = Icons.Rounded.MusicNote) {
                        MagicButton(onClick = { artPicker.launch("image/*") }, enabled = !busy) {
                            Text(if (artworkBytes == null) "표지 이미지 선택" else "새 표지 선택됨 · 변경하기")
                        }
                    }

                    SectionCard(title = "오디오 상세", icon = Icons.Rounded.Info) {
                        Text("길이: ${info.durationSeconds}초 · 비트레이트: ${info.bitRate} · 샘플레이트: ${info.sampleRate}")
                    }

                    MagicButton(onClick = {
                        exportSource = selectedUri?.let { it to info }
                        val base = info.fileName.substringBeforeLast('.', info.fileName)
                        wavPicker.launch(base + ".wav")
                    }, enabled = !busy) { Text("실제 PCM WAV로 변환하여 새 파일 저장") }

                    MagicButton(onClick = {
                        exportSource = selectedUri?.let { it to info }
                        m4aPicker.launch(info.fileName.substringBeforeLast('.', info.fileName) + ".m4a")
                    }, enabled = !busy) { Text("AAC M4A로 실제 인코딩하여 새 파일 저장") }

                    SectionCard(title = "파일", icon = Icons.Rounded.AudioFile) {
                        EditorField("파일명 · 확장자", fileName, { fileName = it })
                        MagicButton(onClick = {
                            val ext = info.fileName.substringAfterLast('.', "")
                            val base = listOf(artist, title).map { it.trim().replace("/", "_").replace(":", "_") }.filter { it.isNotBlank() }.joinToString(" - ")
                            if (base.isNotBlank()) fileName = base + if (ext.isBlank()) "" else "." + ext
                        }) { Text("아티스트 - 제목으로 파일명 정리") }
                        if (extensionChanged) {
                            Spacer(Modifier.height(10.dp))
                            Notice(
                                text = "확장자 이름만 바뀝니다. 예: MP3 → FLAC으로 적어도 오디오 코덱 자체가 변환되지는 않아요.",
                                color = IosOrange,
                            )
                        }
                    }

                    SectionCard(title = "저장 전 변경 사항", icon = Icons.Rounded.Info) {
                        val changes = listOf(
                            "제목" to (info.title to title),
                            "아티스트" to (info.artist to artist),
                            "앨범" to (info.album to album),
                            "앨범 아티스트" to (info.albumArtist to albumArtist),
                            "장르" to (info.genre to genre),
                            "연도" to (info.year to year),
                            "트랙" to (info.track to track),
                            "가사" to (info.lyrics to lyrics),
                            "파일명" to (info.fileName to fileName)
                        ).filter { it.second.first != it.second.second }
                        if (changes.isEmpty() && artworkBytes == null) Text("변경된 내용이 없습니다.")
                        changes.forEach { (label, pair) ->
                            Text(label + ": " + pair.first.take(48) + " → " + pair.second.take(48))
                        }
                        if (artworkBytes != null) Text("앨범아트: 새 이미지로 변경")
                    }

                    if (status.isNotBlank()) {
                        Notice(
                            text = status,
                            color = if (success) IosGreen else IosOrange,
                            success = success,
                        )
                    }

                    MagicButton(
                        onClick = {
                            val uri = selectedUri ?: return@MagicButton
                            player?.release(); player = null
                            PlaybackService.stopForEditing(listOf(uri))
                            busy = true
                            status = ""
                            success = false
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    AudioMetadata.save(
                                        context,
                                        uri,
                                        AudioEdits(
                                            fileName = fileName.trim(),
                                            title = title,
                                            artist = artist,
                                            album = album,
                                            albumArtist = albumArtist,
                                            genre = genre,
                                            year = year,
                                            track = track,
                                            lyrics = lyrics,
                                            artwork = artworkBytes,
                                        ),
                                    )
                                }
                                result.fold(
                                    onSuccess = { saved ->
                                        selectedUri = saved.uri
                                        applyInfo(saved.info)
                                        comparison = null
                                        libraryRevision++
                                        updateLibrary(uri, saved)
                                        success = true
                                        status = if (saved.convertedFromWav)
                                             "WAV 앨범아트를 FLAC으로 무손실 변환해 새 파일로 저장했습니다. 기존 WAV 원본은 유지됩니다."
                                         else "원본 파일에 저장했어요. 다른 플레이어에서도 이 메타데이터를 읽을 수 있습니다."
                                    },
                                    onFailure = { error ->
                                        success = false
                                        status = error.message ?: "저장에 실패했습니다."
                                    },
                                )
                                busy = false
                            }
                        },
                        enabled = editable && !busy && fileName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = Color.White,
                            )
                        } else {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("원본 파일에 저장", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                SupportedFormatsCard()
                Spacer(Modifier.height(24.dp))
                        }
                        MiniPlayerBar(
                            onExpand = { currentScreen = "player" },
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .padding(horizontal = 17.dp, vertical = 12.dp),
                        )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilePickerCard(info: AudioInfo?, busy: Boolean, onPick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (info == null) Icons.Rounded.FolderOpen else Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = IosBlue,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info?.fileName ?: "음악 파일 선택",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (info == null) {
                            "MP3, FLAC, M4A, OGG, Opus, WAV 등"
                        } else {
                            "${info.format.ifBlank { "AUDIO" }} · ${humanSize(info.sizeBytes)}"
                        },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                        fontSize = 13.sp,
                    )
                }
            }
            MagicButton(
                onClick = onPick,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(if (info == null) "파일 열기" else "다른 파일 선택")
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(7.dp))
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                letterSpacing = 1.15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)),
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun EditorField(label: String, value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.075f),
        ),
    )
}

@Composable
private fun DividerInset() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 5.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}

@Composable
private fun Notice(text: String, color: Color, success: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (success) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun SupportedFormatsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text("지원 형식", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "MP3 · FLAC · M4A/MP4 · OGG · Opus · WAV · AIFF · WMA · DSF",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "메타데이터 수정은 오디오를 재인코딩하지 않아 음질에 영향을 주지 않습니다.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes <= 0L -> "크기 알 수 없음"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
