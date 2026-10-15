package com.musicmagic.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musicmagic.app.library.CoverArt
import com.musicmagic.app.library.LibraryMedia
import com.musicmagic.app.library.LibraryTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MusicLibraryCarousel(tracks: List<LibraryTrack>, busy: Boolean, revision: Int, onSelect: (Uri) -> Unit) {
    var albumKey by remember(tracks) { mutableStateOf<Pair<String, String>?>(null) }
    var showAll by remember(tracks) { mutableStateOf(false) }
    val albums = remember(tracks) { tracks.groupBy { it.album to it.albumArtist.ifBlank { it.artist } }.toList() }
    val songs = if (showAll) tracks else tracks.filter { (it.album to it.albumArtist.ifBlank { it.artist }) == albumKey }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("음악 라이브러리 · ${tracks.size}곡", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row {
            TextButton(onClick = { albumKey = null; showAll = false }) { Text("앨범 ${albums.size}") }
            TextButton(onClick = { albumKey = null; showAll = true }) { Text("모든 곡") }
        }
        if (albumKey == null && !showAll) {
            key(tracks, "albums") {
                ArtworkCarousel(
                    tracks = albums.map { it.second.first() }, revision = revision,
                    cards = albums.map { (group, entries) ->
                        LiquidCardItem(entries.first().uri.toString(), group.first, group.second,
                            "${entries.size}곡", actionLabel = "곡 보기")
                    },
                    onAction = { card -> albumKey = albums.first { it.second.first().uri.toString() == card.id }.first },
                )
            }
        } else {
            if (!showAll) Text(albumKey?.first.orEmpty(), style = MaterialTheme.typography.titleMedium)
            key(tracks, albumKey, showAll) {
                ArtworkCarousel(songs, songs.map {
                    LiquidCardItem(it.uri.toString(), it.title, it.artist, it.quality, actionLabel = "정보 편집", enabled = !busy)
                }, revision, onAction = { onSelect(Uri.parse(it.id)) })
            }
        }
    }
}

/** Only the settled page and its neighbors request art, outside the UI thread. */
@Composable
private fun ArtworkCarousel(tracks: List<LibraryTrack>, cards: List<LiquidCardItem>, revision: Int, onAction: (LiquidCardItem) -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableIntStateOf(0) }
    val art by produceState<Map<String, CoverArt>>(emptyMap(), tracks, current, revision) {
        value = withContext(Dispatchers.IO) {
            ((current - 1).coerceAtLeast(0)..(current + 1).coerceAtMost(tracks.lastIndex)).mapNotNull { index ->
                tracks.getOrNull(index)?.let { it.uri.toString() to LibraryMedia.cover(context, it.uri, revision) }
            }.toMap()
        }
    }
    LiquidCardCarousel(cards.map { item ->
        art[item.id]?.let { item.copy(artwork = it.bitmap, accent = it.accent) } ?: item
    }, modifier = Modifier.fillMaxWidth(), onAction = onAction, onSettled = { card ->
        current = cards.indexOfFirst { it.id == card.id }.coerceAtLeast(0)
    })
}
