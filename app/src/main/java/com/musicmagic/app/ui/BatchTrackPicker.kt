package com.musicmagic.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicmagic.app.library.LibraryTrack

@Composable
fun BatchTrackPicker(
    tracks: List<LibraryTrack>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    enabled: Boolean,
) {
    if (tracks.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("편집할 곡 · ${selected.size}/${tracks.size}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall)
            TextButton(
                onClick = { onChange(tracks.mapTo(mutableSetOf()) { it.uri.toString() }) },
                enabled = enabled,
            ) { Text("전체") }
            TextButton(onClick = { onChange(emptySet()) }, enabled = enabled) { Text("해제") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(208.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items(tracks, key = { it.uri.toString() }) { song ->
                val id = song.uri.toString()
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = id in selected,
                        onCheckedChange = { yes ->
                            onChange(if (yes) selected + id else selected - id)
                        },
                        enabled = enabled,
                    )
                    Column(Modifier.weight(1f).padding(vertical = 5.dp)) {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium)
                        Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .045f))
            }
        }
    }
}
