package com.musicmagic.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicmagic.app.HistoryEntry
import java.text.DateFormat
import java.util.Date

@Composable
fun EditHistoryPanel(
    entries: List<HistoryEntry>,
    busy: Boolean,
    onRestore: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("최근 변경 ${entries.size}건", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onClear, enabled = !busy && entries.isNotEmpty()) { Text("기록 비우기") }
        }
        if (entries.isEmpty()) Text("아직 기록이 없습니다.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        entries.asReversed().forEach { e ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(e.afterName, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(e.timestamp)), style = MaterialTheme.typography.labelSmall)
                Text("이전  ${e.before}", maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text("변경  ${e.after}", maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                if (e.restorable) {
                    TextButton(enabled = !busy, onClick = { onRestore(e) }) { Text("이 기록 이전으로 복원") }
                } else {
                    Text("대용량 음원: 변경 내용만 기록됨 · 파일 백업 없음",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
