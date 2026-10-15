package com.musicmagic.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.musicmagic.app.AudioInfo
import com.musicmagic.app.library.CoverArt
import com.musicmagic.app.library.LibraryMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Album and song faces for single-file users as well as the folder browser. */
@Composable
fun SelectedMusicCarousel(uri: Uri, info: AudioInfo, pendingArt: ByteArray?, revision: Int, busy: Boolean, onArt: () -> Unit, onPlay: () -> Unit) {
    val context = LocalContext.current
    val art by produceState<CoverArt?>(null, uri, pendingArt, revision) {
        value = withContext(Dispatchers.IO) {
            if (pendingArt == null) LibraryMedia.cover(context, uri, revision)
            else LibraryMedia.thumbnail(pendingArt).let { CoverArt(it, LibraryMedia.accent(it)) }
        }
    }
    val accent = art?.accent ?: androidx.compose.material3.MaterialTheme.colorScheme.primary
    LiquidCardCarousel(listOf(
        LiquidCardItem("album", info.album.ifBlank { "앨범 정보 없음" }, info.albumArtist.ifBlank { info.artist },
            if (pendingArt == null) "앨범" else "새 표지 · 저장 전", art?.bitmap, accent,
            actionLabel = "표지 변경", enabled = !busy && info.supported),
        LiquidCardItem("song", info.title.ifBlank { info.fileName }, info.artist,
            "${info.format} · ${info.sampleRate} Hz · ${info.durationSeconds}초", art?.bitmap, accent,
            actionLabel = "미리듣기", enabled = !busy),
    ), onAction = { if (it.id == "album") onArt() else onPlay() })
}
