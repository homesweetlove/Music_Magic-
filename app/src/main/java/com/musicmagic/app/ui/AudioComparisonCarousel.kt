package com.musicmagic.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.musicmagic.app.library.CoverArt
import com.musicmagic.app.library.LibraryMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AudioComparison(val before: Uri, val after: Uri, val title: String, val beforeDetail: String, val afterDetail: String)

@Composable
fun AudioComparisonCarousel(comparison: AudioComparison, busy: Boolean, revision: Int = 0, onPlay: (Uri) -> Unit) {
    val context = LocalContext.current
    val cover by produceState<CoverArt?>(null, comparison.before, revision) {
        value = withContext(Dispatchers.IO) { LibraryMedia.cover(context, comparison.before, revision) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Before / After · 비교 듣기", style = MaterialTheme.typography.titleLarge)
        Text("원본과 내보낸 파일을 각각 재생해 비교하세요. 변환은 손실된 음질을 복원하지 않습니다.", style = MaterialTheme.typography.bodyMedium)
        LiquidCardCarousel(listOf(
            LiquidCardItem("before", "Before · 원본", comparison.title, comparison.beforeDetail,
                artwork = cover?.bitmap, accent = cover?.accent ?: MaterialTheme.colorScheme.primary,
                actionLabel = "원본 재생", enabled = !busy),
            LiquidCardItem("after", "After · 변환 결과", comparison.title, comparison.afterDetail,
                artwork = cover?.bitmap, accent = MaterialTheme.colorScheme.tertiary,
                actionLabel = "결과 재생", enabled = !busy),
        ), onAction = { onPlay(if (it.id == "before") comparison.before else comparison.after) })
    }
}
