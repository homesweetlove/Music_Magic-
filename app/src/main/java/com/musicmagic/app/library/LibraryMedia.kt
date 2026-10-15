package com.musicmagic.app.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.musicmagic.app.AudioInfo
import java.io.ByteArrayOutputStream

/** Lightweight browsing data; opening the editor still uses the verified metadata engine. */
data class LibraryTrack(
    val uri: Uri,
    val fileName: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val quality: String,
)

data class CoverArt(val bitmap: Bitmap?, val accent: Color)

object LibraryMedia {
    /** Bounded input and sampled decode for artwork chosen through SAF. Call on Dispatchers.IO. */
    fun prepareArtwork(context: Context, uri: Uri): Result<ByteArray> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(out.size() + count <= 12 * 1024 * 1024) { "이미지가 12MB를 초과합니다." }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } ?: error("이미지를 읽을 수 없습니다.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "이미지를 디코딩할 수 없습니다." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("이미지를 디코딩할 수 없습니다.")
        var resized = bitmap
        try {
            val ratio = minOf(1f, 1000f / maxOf(bitmap.width, bitmap.height))
            if (ratio < 1f) resized = Bitmap.createScaledBitmap(bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
            ByteArrayOutputStream().use { output ->
                check(resized.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "이미지 최적화에 실패했습니다." }
                output.toByteArray()
            }
        } finally {
            if (resized !== bitmap) resized.recycle()
            bitmap.recycle()
        }
    }

    fun sameDocument(first: Uri, second: Uri): Boolean = first == second ||
        (first.authority == second.authority && runCatching {
            DocumentsContract.getDocumentId(first) == DocumentsContract.getDocumentId(second)
        }.getOrDefault(false))

    fun updated(track: LibraryTrack, uri: Uri, info: AudioInfo): LibraryTrack = track.copy(
        uri = uri, fileName = info.fileName, title = info.title.ifBlank { info.fileName },
        artist = info.artist.ifBlank { "알 수 없는 아티스트" }, album = info.album.ifBlank { "앨범 정보 없음" },
        albumArtist = info.albumArtist,
    )

    private val extensions = setOf("mp3", "flac", "m4a", "m4b", "mp4", "ogg", "oga", "opus", "wav", "aif", "aiff", "wma", "dsf")
    // Only thumbnails are cached. Evicted bitmaps may still be in a Compose frame: never recycle them.
    private val covers = object : LruCache<String, CoverArt>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: CoverArt) = value.bitmap?.allocationByteCount ?: 64
    }

    suspend fun scan(context: Context, tree: Uri, onProgress: (Int) -> Unit): List<LibraryTrack> {
        val queue = ArrayDeque<DocumentFile>()
        DocumentFile.fromTreeUri(context, tree)?.let(queue::add)
        val visited = mutableSetOf<Uri>()
        val tracks = mutableListOf<LibraryTrack>()
        while (queue.isNotEmpty() && tracks.size < 500) {
            currentCoroutineContext().ensureActive()
            val directory = queue.removeFirst()
            if (!visited.add(directory.uri)) continue
            for (file in directory.listFiles()) {
                currentCoroutineContext().ensureActive()
                if (tracks.size >= 500) break
                if (file.isDirectory) queue.add(file)
                else if (file.isFile && file.name?.substringAfterLast('.', "")?.lowercase() in extensions) {
                    tracks.add(readTrack(context, file.uri, file.name ?: "음악 파일"))
                    onProgress(tracks.size)
                }
            }
        }
        return tracks
    }

    fun readTrack(context: Context, uri: Uri, name: String): LibraryTrack {
        val fallback = LibraryTrack(uri, name, name.substringBeforeLast('.', name), "알 수 없는 아티스트", "앨범 정보 없음", "", name.substringAfterLast('.', "AUDIO").uppercase())
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            fun tag(key: Int) = retriever.extractMetadata(key).orEmpty().trim()
            val rate = if (Build.VERSION.SDK_INT >= 31) tag(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE) else ""
            fallback.copy(
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE).ifBlank { fallback.title },
                artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST).ifBlank { fallback.artist },
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM).ifBlank { fallback.album },
                albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                quality = listOf(fallback.quality, rate.takeIf { it.isNotBlank() }?.let { "$it Hz" }).filterNotNull().joinToString(" · "),
            )
        } catch (_: Exception) {
            fallback // Unsupported codec or unavailable provider: keep the file reachable.
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun cover(context: Context, uri: Uri, revision: Int = 0): CoverArt {
        val key = "$uri#$revision"
        covers.get(key)?.let { return it }
        val retriever = MediaMetadataRetriever()
        val bitmap = try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.takeIf { it.size <= 16 * 1024 * 1024 }?.let(::thumbnail)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
        return CoverArt(bitmap, accent(bitmap)).also { covers.put(key, it) }
    }

    fun thumbnail(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    fun accent(bitmap: Bitmap?): Color {
        if (bitmap == null) return Color(0xFF0A84FF)
        var r = 0L; var g = 0L; var b = 0L; var count = 0
        // A bounded 8x8 sample; no full-size pixel array or per-frame work.
        for (y in 0 until 8) for (x in 0 until 8) {
            val pixel = bitmap.getPixel(x * bitmap.width / 8, y * bitmap.height / 8)
            if (android.graphics.Color.alpha(pixel) < 128) continue
            r += android.graphics.Color.red(pixel); g += android.graphics.Color.green(pixel); b += android.graphics.Color.blue(pixel); count++
        }
        return if (count == 0) Color(0xFF0A84FF) else Color((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }
}
