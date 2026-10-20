package com.musicmagic.app.player

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musicmagic.app.AudioMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** LRC lyric timestamp in milliseconds. Plain embedded lyrics are also supported. */
data class TimedLyric(val timeMs: Long, val text: String)

object LrcParser {
    private val pattern = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]""")
    fun parse(text: String): List<TimedLyric> {
        val result = mutableListOf<TimedLyric>()
        text.lineSequence().take(2000).forEach { line ->
            val matches = pattern.findAll(line).toList()
            val words = pattern.replace(line, "").trim()
            if (words.isNotEmpty()) for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                if (seconds >= 60) continue
                val decimals = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                val millis = (minutes * 60L + seconds) * 1000L + decimals
                result.add(TimedLyric(millis, words))
            }
        }
        return result.sortedBy { it.timeMs }.distinctBy { it.timeMs to it.text }
    }

    fun activeIndex(lines: List<TimedLyric>, atMs: Long): Int =
        lines.indexOfLast { it.timeMs <= atMs }
}

/** View-only; tapping an LRC timestamp seeks the actual current player. */
@Composable
fun LyricsPanel(uri: Uri?, revision: Int, positionMs: Long, onSeek: (Long) -> Unit) {
    val context = LocalContext.current
    val lyrics by produceState("", uri, revision) {
        value = if (uri == null) "" else withContext(Dispatchers.IO) {
            runCatching { AudioMetadata.read(context, uri).lyrics }.getOrDefault("")
        }
    }
    val timed = LrcParser.parse(lyrics)
    val active = LrcParser.activeIndex(timed, positionMs)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.1f),
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("가사", fontWeight = FontWeight.SemiBold)
            when {
                lyrics.isBlank() -> Text("이 곡에 저장된 가사가 없어요. 편집기에서 가사를 추가할 수 있습니다.",
                    color = Color.White.copy(alpha = 0.7f))
                timed.isEmpty() -> Text(lyrics.take(8000),
                    color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Start)
                else -> {
                    Text("타임스탬프를 눌러 해당 부분부터 재생하세요.",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodySmall)
                    val current = active.coerceAtLeast(0)
                    val first = (current - 3).coerceAtLeast(0)
                    timed.drop(first).take(8).forEachIndexed { offset, item ->
                        val index = first + offset
                        TextButton(onClick = { onSeek(item.timeMs) },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(item.text, modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = if (index == active) Color.White else Color.White.copy(alpha = 0.53f),
                                fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
