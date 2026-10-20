package com.musicmagic.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.musicmagic.app.ArtworkEditing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Apple-style inline crop/zoom editor: the image is actually re-encoded. */
@Composable
fun AlbumArtworkDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onSave: (ByteArray) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picture by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { ArtworkEditing.load(context, uri) }.getOrNull()
        }
    }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var turns by remember(uri) { mutableIntStateOf(0) }
    var png by remember(uri) { mutableStateOf(uri.path?.endsWith(".png", ignoreCase = true) == true) }
    var busy by remember(uri) { mutableStateOf(false) }
    var error by remember(uri) { mutableStateOf("") }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("앨범 표지 편집", style = MaterialTheme.typography.headlineSmall)
                Text("확대·회전 후 가운데 정사각형으로 저장돼요.",
                    style = MaterialTheme.typography.bodySmall)
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f)
                        .clip(RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (picture == null) CircularProgressIndicator()
                    else Image(
                        bitmap = picture!!.asImageBitmap(),
                        contentDescription = "편집할 표지 미리보기",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                rotationZ = turns * 90f
                            },
                    )
                }
                Text("확대 배율 · ${"%.1f".format(zoom)}배")
                Slider(value = zoom, onValueChange = { zoom = it },
                    valueRange = 1f..3f, enabled = !busy)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { turns = (turns + 1) % 4 }, enabled = !busy) {
                        Text("↻ 90° 회전")
                    }
                    Spacer(Modifier.weight(1f))
                    Text("PNG 보존")
                    Switch(checked = png, onCheckedChange = { png = it }, enabled = !busy)
                }
                Text(if (png) "PNG · 투명 영역 보존" else "JPEG · 호환성 우선",
                    style = MaterialTheme.typography.bodySmall)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onRemove, enabled = !busy) { Text("표지 삭제") }
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("취소") }
                    Button(onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ArtworkEditing.render(context, uri, zoom, turns, png)
                                }
                            }.fold(onSuccess = onSave, onFailure = { error = it.message.orEmpty() })
                            busy = false
                        }
                    }, enabled = !busy && picture != null) { Text("적용") }
                }
            }
        }
    }
}
