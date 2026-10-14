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
)

data class SaveResult(val info: AudioInfo, val uri: Uri)

object AudioMetadata {
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
            val audioFile = AudioFileIO.read(temp)
            val tag = audioFile.tag
            AudioInfo(
                fileName = name,
                title = tag?.getFirst(FieldKey.TITLE).orEmpty(),
                artist = tag?.getFirst(FieldKey.ARTIST).orEmpty(),
                album = tag?.getFirst(FieldKey.ALBUM).orEmpty(),
                albumArtist = tag?.getFirst(FieldKey.ALBUM_ARTIST).orEmpty(),
                genre = tag?.getFirst(FieldKey.GENRE).orEmpty(),
                year = tag?.getFirst(FieldKey.YEAR).orEmpty(),
                track = tag?.getFirst(FieldKey.TRACK).orEmpty(),
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

            val audioFile = AudioFileIO.read(work)
            val tag = audioFile.getTagOrCreateAndSetDefault()
            tag.setField(FieldKey.TITLE, edits.title)
            tag.setField(FieldKey.ARTIST, edits.artist)
            tag.setField(FieldKey.ALBUM, edits.album)
            tag.setField(FieldKey.ALBUM_ARTIST, edits.albumArtist)
            tag.setField(FieldKey.GENRE, edits.genre)
            tag.setField(FieldKey.YEAR, edits.year)
            tag.setField(FieldKey.TRACK, edits.track)
            audioFile.setTag(tag)
            AudioFileIO.write(audioFile)

            val written = AudioFileIO.read(work).tag
                ?: error("저장 검증 중 태그를 다시 읽을 수 없습니다.")
            verifyFields(written, edits)

            var outputUri = uri
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    work.inputStream().use { input -> input.copyTo(output) }
                } ?: error("원본 파일을 쓸 수 없습니다.")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(verify).use { output -> input.copyTo(output) }
                } ?: error("저장한 파일을 검증할 수 없습니다.")
                val realTag = AudioFileIO.read(verify).tag
                    ?: error("저장한 파일의 메타데이터를 읽을 수 없습니다.")
                verifyFields(realTag, edits)

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
                throw writeError
            }

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

    private fun verifyFields(tag: org.jaudiotagger.tag.Tag, edits: AudioEdits) {
        for ((field, expected) in listOf(
            FieldKey.TITLE to edits.title,
            FieldKey.ARTIST to edits.artist,
            FieldKey.ALBUM to edits.album,
            FieldKey.ALBUM_ARTIST to edits.albumArtist,
            FieldKey.GENRE to edits.genre,
            FieldKey.YEAR to edits.year,
            FieldKey.TRACK to edits.track,
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
