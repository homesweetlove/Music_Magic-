package com.musicmagic.app.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicmagic.app.library.LibraryTrack
import com.musicmagic.app.library.MusicCollections
import com.musicmagic.app.library.MusicSort
import com.musicmagic.app.library.LibrarySorting

/** Favorites and named playlists are on-device only. Nothing is uploaded or shared. */
@Composable
fun CollectionsPanel(
    tracks: List<LibraryTrack>,
    selected: LibraryTrack?,
    revision: Int,
    onPlayQueue: (List<LibraryTrack>) -> Unit,
) {
    val context = LocalContext.current
    var favorites by remember(context, revision) { mutableStateOf(MusicCollections.favorites(context)) }
    var playlists by remember(context, revision) { mutableStateOf(MusicCollections.playlists(context)) }
    var newName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var removeName by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var ordering by remember(context) { mutableStateOf(MusicCollections.sortOrder(context)) }
    var showSmart by remember { mutableStateOf(false) }
    fun sorted(items: List<LibraryTrack>) = LibrarySorting.sort(items, ordering, favorites)
    val favoriteSongs = sorted(tracks.filter { it.uri.toString() in favorites })
    val selectedId = selected?.uri?.toString()

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("YOUR COLLECTIONS", color = Color.White.copy(alpha = 0.68f),
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.09f),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        enabled = selectedId != null,
                        onClick = {
                            if (selectedId != null) favorites = MusicCollections.toggleFavorite(context, selectedId)
                        },
                    ) {
                        Icon(
                            if (selectedId != null && selectedId in favorites) Icons.Rounded.Favorite
                            else Icons.Rounded.FavoriteBorder,
                            "선택 곡 즐겨찾기",
                            tint = if (selectedId != null && selectedId in favorites) Color(0xFFFF6A93) else Color.White,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("즐겨찾기", fontWeight = FontWeight.SemiBold)
                        Text("${favoriteSongs.size}곡 저장됨", color = Color.White.copy(alpha = 0.63f))
                    }
                    TextButton(onClick = { onPlayQueue(favoriteSongs) }, enabled = favoriteSongs.isNotEmpty()) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
                        Text("재생", color = Color.White)
                    }
                }
                Text("하트 버튼으로 현재 선택한 곡을 저장하세요.",
                    color = Color.White.copy(alpha = 0.64f),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("재생목록", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { newName = ""; creating = true }) { Text("+ 새 목록", color = Color.White) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("자동 정렬", color = Color.White.copy(alpha = .75f), modifier = Modifier.weight(1f))
            TextButton(onClick = {
                ordering = MusicSort.entries[(ordering.ordinal + 1) % MusicSort.entries.size]
                MusicCollections.setSortOrder(context, ordering)
            }) { Text(ordering.label + " ↻", color = Color.White) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("스마트 컬렉션", color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = { showSmart = !showSmart }) {
                Text(if (showSmart) "접기" else "아티스트·폴더별 보기", color = Color.White)
            }
        }
        if (showSmart) {
            val smartGroups = listOf(
                "아티스트" to LibrarySorting.byArtist(tracks),
                "폴더" to LibrarySorting.byFolder(tracks),
            )
            smartGroups.forEach { (kind, groups) ->
                groups.toSortedMap().entries.take(14).forEach { (name, songs) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("$kind · $name", color = Color.White, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text("${songs.size}곡", color = Color.White.copy(alpha = .55f),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onPlayQueue(sorted(songs)) }) {
                            Text("재생", color = Color.White)
                        }
                    }
                }
            }
        }
        if (playlists.isEmpty()) Text("직접 만드는 재생목록은 기기에만 저장돼요.",
            color = Color.White.copy(alpha = 0.63f))
        playlists.forEach { (name, ids) ->
            val songs = sorted(ids.mapNotNull { id -> tracks.firstOrNull { it.uri.toString() == id } })
            Surface(
                shape = RoundedCornerShape(19.dp),
                color = Color.White.copy(alpha = 0.085f),
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text("${songs.size}곡 · ${ids.size - songs.size}곡은 파일 확인 필요",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.62f))
                        }
                        TextButton(onClick = { onPlayQueue(songs) }, enabled = songs.isNotEmpty()) {
                            Text("재생", color = Color.White)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            enabled = selectedId != null,
                            onClick = {
                                if (selectedId != null) playlists = MusicCollections.togglePlaylistSong(context, name, selectedId)
                            },
                        ) {
                            Text(if (selectedId != null && selectedId in ids) "선택 곡 빼기" else "선택 곡 추가",
                                color = Color.White.copy(alpha = 0.88f))
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { removeName = name }) {
                            Text("삭제", color = Color(0xFFFFA9AE))
                        }
                    }
                }
            }
        }
        if (message.isNotBlank()) Text(message, color = Color(0xFFFFC0C0))
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("재생목록 만들기") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(48) },
                    label = { Text("재생목록 이름") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { MusicCollections.createPlaylist(context, newName) }.fold(
                        onSuccess = { playlists = it; creating = false; message = "" },
                        onFailure = { message = it.message.orEmpty(); creating = false },
                    )
                }) { Text("만들기") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("취소") } },
        )
    }
    removeName?.let { name ->
        AlertDialog(
            onDismissRequest = { removeName = null },
            title = { Text("재생목록 삭제") },
            text = { Text("'${name}' 재생목록만 삭제합니다. 음악 파일은 삭제하지 않습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    playlists = MusicCollections.deletePlaylist(context, name)
                    removeName = null
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { removeName = null }) { Text("취소") } },
        )
    }
}
