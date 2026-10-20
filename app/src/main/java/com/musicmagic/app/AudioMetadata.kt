package com.musicmagic.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import java.io.File
import java.io.FileOutputStream

/** Metadata that is stored in the audio file itself. */
data class AudioInfo(
    val fileName: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val track: String = "",
    val lyrics: String = "",
    val durationSeconds: Long = 0L,
    val bitRate: String = "",
    val sampleRate: String = "",
    val format: String = "",
    val sizeBytes: Long = 0L,
    val supported: Boolean = false,
    val error: String? = null,
)

data class AudioEdits(
    val fileName: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val track: String = "",
    val lyrics: String = "",
    val artwork: ByteArray? = null,
)

data class SaveResult(
    val info: AudioInfo, val uri: Uri,
    val convertedFromWav: Boolean = false,
)

object AudioMetadata {
    private const val UNDO_FILE = "musicmagic-last-backup"
    private const val MAX_FILE_BYTES = 1_024L * 1_024L * 1_024L // 1 GiB

    private val editableExtensions = setOf(
        "mp3", "flac", "m4a", "m4b", "mp4",
        "ogg", "oga", "opus", "wav", "aif", "aiff", "wma", "dsf"
    )

    init {
        TagOptionSingleton.getInstance().setAndroid(true)
    }

    fun read(context: Context, uri: Uri): AudioInfo {
        val doc = DocumentFile.fromSingleUri(context, uri)
        val name = doc?.name ?: "audio"
        val ext = extensionOf(name)
        val size = doc?.length() ?: 0L

        if (ext !in editableExtensions) {
            return AudioInfo(
                fileName = name,
                format = ext.uppercase(),
                sizeBytes = size,
                supported = false,
                error = "지원하지 않는 오디오 형식입니다."
            )
        }
        if (size > MAX_FILE_BYTES) {
            return AudioInfo(
                fileName = name,
                format = ext.uppercase(),
                sizeBytes = size,
                supported = false,
                error = "1GB를 초과하는 파일은 안전을 위해 편집하지 않습니다."
            )
        }

        var temp: File? = null
        return try {
            temp = copyUriToTemp(context, uri, ext)
            val audioFile = if (ext == "wav") runCatching { AudioFileIO.read(temp) }.getOrNull()
                else AudioFileIO.read(temp)
            val tag = audioFile?.tag
            val wav = if (ext == "wav") WavRiffTags.read(temp) else null
            fun field(key: FieldKey, fallback: String): String =
                wav?.fields?.get(fallback) ?: tag?.getFirst(key).orEmpty()
            AudioInfo(
                fileName = name,
                title = field(FieldKey.TITLE, "title"),
                artist = field(FieldKey.ARTIST, "artist"),
                album = field(FieldKey.ALBUM, "album"),
                albumArtist = field(FieldKey.ALBUM_ARTIST, "albumArtist"),
                genre = field(FieldKey.GENRE, "genre"),
                year = field(FieldKey.YEAR, "year"),
                track = field(FieldKey.TRACK, "track"),
                lyrics = field(FieldKey.LYRICS, "lyrics"),
                durationSeconds = audioFile?.audioHeader?.trackLength?.toLong() ?: 0L,
                bitRate = audioFile?.audioHeader?.bitRate.orEmpty(),
                sampleRate = audioFile?.audioHeader?.sampleRate.orEmpty(),
                format = ext.uppercase(),
                sizeBytes = size,
                supported = true,
            )
        } catch (e: Exception) {
            AudioInfo(
                fileName = name,
                format = ext.uppercase(),
                sizeBytes = size,
                supported = false,
                error = e.message ?: "메타데이터를 읽을 수 없습니다."
            )
        } finally {
            temp?.delete()
        }
    }

    /**
     * Edits a temporary copy first. Only after a successful re-read does it overwrite the
     * user-selected document. A second copy of the original is retained for best-effort recovery.
     */
    fun save(context: Context, uri: Uri, edits: AudioEdits): Result<SaveResult> = runCatching {
        val doc = DocumentFile.fromSingleUri(context, uri)
            ?: error("파일에 접근할 수 없습니다.")
        val originalName = doc.name ?: error("파일명을 확인할 수 없습니다.")
        val ext = extensionOf(originalName)
        require(ext in editableExtensions) { "이 오디오 형식의 메타데이터 쓰기를 지원하지 않습니다." }
        require(edits.fileName.isNotBlank()) { "파일명은 비워둘 수 없습니다." }
        require('/' !in edits.fileName && '\\' !in edits.fileName) { "파일명에 / 또는 \\ 문자를 사용할 수 없습니다." }
        require(extensionOf(edits.fileName) == ext) { "확장자 변경은 실제 오디오 변환이 아닙니다. 기존 확장자를 유지하세요." }
        require(doc.length() <= MAX_FILE_BYTES) { "1GB를 초과하는 파일은 편집할 수 없습니다." }

        val backup = copyUriToTemp(context, uri, ext, "musicmagic-backup-")
        val work = File.createTempFile("musicmagic-work-", ".$ext", context.cacheDir)
        val verify = File.createTempFile("musicmagic-verify-", ".$ext", context.cacheDir)

        try {
            backup.inputStream().use { input ->
                FileOutputStream(work).use { output -> input.copyTo(output) }
            }

            if (ext == "wav") {
                // Keep every audio chunk untouched. A standard RIFF ID3/APIC chunk
                // can carry cover-only edits without touching title/artist.
                try {
                    WavRiffTags.write(work, edits)
                } catch (wavError: Exception) {
                    if (edits.artwork == null) throw wavError
                    // Fall back only if WAV cannot carry this artwork. Keep the
                    // source WAV untouched; the new FLAC is verified first.
                    return@runCatching WavFlacFallback.convert(context, uri, edits).getOrElse {
                        throw IllegalStateException(
                            "WAV 표지 저장 실패. 자동 FLAC 변환도 불가능합니다: ${it.message}",
                            it,
                        )
                    }
                }
            } else {
                val audioFile = AudioFileIO.read(work)
                val tag = audioFile.getTagOrCreateAndSetDefault()
                // Do not re-create absent metadata when only artwork changed.
                val requested = listOf(
                    FieldKey.TITLE to edits.title, FieldKey.ARTIST to edits.artist,
                    FieldKey.ALBUM to edits.album, FieldKey.ALBUM_ARTIST to edits.albumArtist,
                    FieldKey.GENRE to edits.genre, FieldKey.YEAR to edits.year,
                    FieldKey.TRACK to edits.track, FieldKey.LYRICS to edits.lyrics,
                )
                requested.forEach { (key, value) ->
                    if (tag.getFirst(key) != value) {
                        if (value.isEmpty()) tag.deleteField(key) else tag.setField(key, value)
                    }
                }
                if (edits.artwork != null) {
                    require(edits.artwork.size <= 12 * 1024 * 1024) { "앨범아트는 12MB 이하만 가능합니다." }
                    val artwork = androidArtwork(edits.artwork)
                    tag.deleteArtworkField()
                    tag.setField(artwork)
                }
                audioFile.setTag(tag)
                AudioFileIO.write(audioFile)
            }

            if (ext == "wav") {
                verifyWav(work, edits)
            } else {
                val written = AudioFileIO.read(work).tag
                    ?: error("저장 검증 중 태그를 다시 읽을 수 없습니다.")
                verifyFields(written, edits)
            }

            var outputUri = uri
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    work.inputStream().use { input -> input.copyTo(output) }
                } ?: error("원본 파일을 쓸 수 없습니다.")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(verify).use { output -> input.copyTo(output) }
                } ?: error("저장한 파일을 검증할 수 없습니다.")
                if (ext == "wav") {
                    verifyWav(verify, edits)
                } else {
                    val realTag = AudioFileIO.read(verify).tag
                        ?: error("저장한 파일의 메타데이터를 읽을 수 없습니다.")
                    verifyFields(realTag, edits)
                }

                if (edits.fileName != originalName) {
                    outputUri = DocumentsContract.renameDocument(
                        context.contentResolver,
                        uri,
                        edits.fileName,
                    ) ?: error("메타데이터 저장은 성공했지만 파일명 변경에 실패했습니다.")
                }
            } catch (writeError: Exception) {
                val restored = runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        backup.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("복구를 위해 원본을 쓸 수 없습니다.")
                }.isSuccess
                if (!restored) {
                    throw IllegalStateException(
                        "저장 중 문제가 발생했고 자동 복구도 실패했습니다. 원본 파일을 확인하세요.",
                        writeError,
                    )
                }
                if (ext == "wav" && edits.artwork != null) {
                    return@runCatching WavFlacFallback.convert(context, uri, edits).getOrElse {
                        throw IllegalStateException(
                            "WAV 원본을 복구했지만 FLAC 변환이 불가능합니다: ${it.message}", it,
                        )
                    }
                }
                throw writeError
            }

            val undoFile = File(context.filesDir, UNDO_FILE)
            backup.copyTo(undoFile, overwrite = true)
            context.getSharedPreferences("musicmagic", Context.MODE_PRIVATE).edit()
                .putString("undo_uri", outputUri.toString())
                .putString("undo_original_uri", outputUri.toString())
                .putString("undo_name", originalName)
                .apply()
            context.contentResolver.notifyChange(outputUri, null)
            SaveResult(
                info = AudioInfo(
                    fileName = edits.fileName,
                    title = edits.title,
                    artist = edits.artist,
                    album = edits.album,
                    albumArtist = edits.albumArtist,
                    genre = edits.genre,
                    year = edits.year,
                    track = edits.track,
                    lyrics = edits.lyrics,
                    format = ext.uppercase(),
                    sizeBytes = work.length(),
                    supported = true,
                ),
                uri = outputUri,
            )
        } finally {
            backup.delete()
            work.delete()
            verify.delete()
        }
    }

    fun undoLast(context: Context): Result<AudioInfo> = undoLastWithUri(context).map { it.info }

    /** Providers may return a different document ID when restoring the previous filename. */
    fun undoLastWithUri(context: Context): Result<SaveResult> = runCatching {
        val prefs = context.getSharedPreferences("musicmagic", Context.MODE_PRIVATE)
        val uri = Uri.parse(prefs.getString("undo_uri", null) ?: error("복원할 이전 버전이 없습니다."))
        // Keep the pre-restore identity if rename succeeds but a provider read fails.
        // A retry then returns to the UI's original entry, even after another rename.
        if (!prefs.contains("undo_original_uri")) prefs.edit().putString("undo_original_uri", uri.toString()).apply()
        val backup = File(context.filesDir, UNDO_FILE)
        require(backup.isFile && backup.length() > 0L) { "복원용 백업 파일이 없습니다." }
        val resolver = context.contentResolver
        resolver.openOutputStream(uri, "wt")?.use { out ->
            backup.inputStream().use { it.copyTo(out) }
        } ?: error("이 파일에 복원 내용을 쓸 수 없습니다.")
        var restoredUri = uri
        val oldName = prefs.getString("undo_name", null)
        if (!oldName.isNullOrBlank()) {
            val currentName = DocumentFile.fromSingleUri(context, uri)?.name
            if (currentName != oldName) {
                runCatching { DocumentsContract.renameDocument(resolver, uri, oldName) }.getOrNull()?.let {
                    restoredUri = it
                    prefs.edit().putString("undo_uri", it.toString()).apply()
                }
            }
        }
        val restored = read(context, restoredUri)
        check(restored.supported) { "복원 후 파일 검증에 실패했습니다." }
        prefs.edit().remove("undo_uri").remove("undo_original_uri").remove("undo_name").apply()
        backup.delete()
        SaveResult(restored, restoredUri)
    }

    private fun verifyWav(file: File, edits: AudioEdits) {
        val actual = WavRiffTags.read(file)
        val requested = mapOf(
            "title" to edits.title, "artist" to edits.artist, "album" to edits.album,
            "albumArtist" to edits.albumArtist, "genre" to edits.genre,
            "year" to edits.year, "track" to edits.track, "lyrics" to edits.lyrics,
        )
        requested.forEach { (key, value) ->
            check(actual.fields[key].orEmpty() == value) { "WAV $key 저장 검증 실패" }
        }
        if (edits.artwork != null) check(actual.picture?.contentEquals(edits.artwork) == true) {
            "WAV 앨범커버 저장 검증 실패"
        }
    }

    /** Avoid StandardArtwork's desktop-only javax.imageio.ImageIO on Android. */
    internal fun androidArtwork(bytes: ByteArray): org.jaudiotagger.tag.images.AndroidArtwork {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        require(opts.outWidth > 0 && opts.outHeight > 0) { "이미지 데이터가 올바르지 않습니다." }
        return org.jaudiotagger.tag.images.AndroidArtwork().apply {
            binaryData = bytes
            mimeType = "image/jpeg"
            description = ""
            pictureType = 3
            width = opts.outWidth
            height = opts.outHeight
        }
    }

    private fun writeArtworkTemp(context: Context, bytes: ByteArray): File {
        val file = File.createTempFile("musicmagic-cover-", ".jpg", context.cacheDir)
        file.writeBytes(bytes)
        file.deleteOnExit()
        return file
    }

    private fun verifyFields(tag: org.jaudiotagger.tag.Tag, edits: AudioEdits) {
        for ((field, expected) in listOf(
            FieldKey.TITLE to edits.title,
            FieldKey.ARTIST to edits.artist,
            FieldKey.ALBUM to edits.album,
            FieldKey.ALBUM_ARTIST to edits.albumArtist,
            FieldKey.GENRE to edits.genre,
            FieldKey.YEAR to edits.year,
            FieldKey.TRACK to edits.track,
            FieldKey.LYRICS to edits.lyrics,
        )) {
            check(tag.getFirst(field) == expected) { "${field.name} 메타데이터 저장 검증에 실패했습니다." }
        }
    }

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase()

    private fun copyUriToTemp(
        context: Context,
        uri: Uri,
        extension: String,
        prefix: String = "musicmagic-read-",
    ): File {
        val suffix = if (extension.isBlank()) ".audio" else ".$extension"
        val temp = File.createTempFile(prefix, suffix, context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(temp).use { output -> input.copyTo(output) }
        } ?: error("파일을 읽을 수 없습니다.")
        return temp
    }
}
