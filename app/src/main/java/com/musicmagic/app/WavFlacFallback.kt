package com.musicmagic.app

import android.content.ContentValues
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.musicmagic.app.library.MusicCollections
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Last resort when an unusual RIFF WAV rejects ID3 artwork. PCM16 -> FLAC is
 * lossless; never downsample 24/32-bit or floating point recordings.
 * Converts to a NEW document and retains the WAV untouched.
 *
 * Device must have an audio/flac encoder. The produced FLAC stream is decoded
 * and its metadata re-read before the destination becomes visible.
 */
object WavFlacFallback {
    private data class Pcm(val sampleRate: Int, val channels: Int, val offset: Long, val length: Long)

    fun convert(context: Context, source: Uri, edits: AudioEdits): Result<SaveResult> = runCatching {
        org.jaudiotagger.tag.TagOptionSingleton.getInstance().setAndroid(true)
        val wav = File.createTempFile("musicmagic-source-", ".wav", context.cacheDir)
        val flac = File.createTempFile("musicmagic-fallback-", ".flac", context.cacheDir)
        var destination: Uri? = null
        var pendingMediaStore = false
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                wav.outputStream().use { output ->
                    val buf = ByteArray(65536)
                    var total = 0L
                    while (true) {
                        val size = input.read(buf)
                        if (size < 0) break
                        total += size
                        require(total <= 1_024L * 1_024L * 1_024L) { "1GB 초과 WAV는 변환하지 않습니다." }
                        output.write(buf, 0, size)
                    }
                }
            } ?: error("원본 WAV 파일을 읽을 수 없습니다.")

            val pcm = inspectPcm16(wav)
            encodeFlac(wav, pcm, flac)
            val tagged = AudioFileIO.read(flac)
            val tag = tagged.getTagOrCreateAndSetDefault()
            for ((key, value) in listOf(
                FieldKey.TITLE to edits.title, FieldKey.ARTIST to edits.artist,
                FieldKey.ALBUM to edits.album, FieldKey.ALBUM_ARTIST to edits.albumArtist,
                FieldKey.GENRE to edits.genre, FieldKey.YEAR to edits.year,
                FieldKey.TRACK to edits.track, FieldKey.LYRICS to edits.lyrics,
            )) if (value.isNotBlank()) tag.setField(key, value)
            if (edits.artwork != null) {
                tag.setField(AudioMetadata.androidArtwork(edits.artwork))
            }
            tagged.setTag(tag)
            AudioFileIO.write(tagged)
            val checkTag = AudioFileIO.read(flac).tag ?: error("FLAC 태그를 확인할 수 없습니다.")
            check((edits.artwork == null) || (checkTag.firstArtwork?.binaryData?.isNotEmpty() == true)) {
                "FLAC에 앨범 커버가 저장되지 않았습니다."
            }

            val name = edits.fileName.substringBeforeLast('.', edits.fileName).ifBlank { "Music" } + ".flac"
            val tree = MusicCollections.lastTree(context)
            val canWrite = tree != null && context.contentResolver.persistedUriPermissions.any {
                it.uri == tree && it.isWritePermission
            }
            val target = if (canWrite) {
                DocumentFile.fromTreeUri(context, tree!!)?.createFile("audio/flac", name)
                    ?.uri
            } else null
            if (target != null) destination = target
            else {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/flac")
                    put(MediaStore.Audio.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MUSIC + "/Music Magic")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                destination = context.contentResolver.insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("새 FLAC 파일을 만들 수 없습니다.")
                pendingMediaStore = true
            }
            val uri = destination ?: error("새 FLAC 경로가 없습니다.")
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                flac.inputStream().use { it.copyTo(out) }
            } ?: error("새 FLAC 파일에 쓸 수 없습니다.")

            val verified = AudioMetadata.read(context, uri)
            check(verified.supported) { "생성한 FLAC 파일을 다시 읽을 수 없습니다." }
            if (edits.artwork != null) {
                val checkFile = AudioFileIO.read(flac).tag
                check(checkFile?.firstArtwork?.binaryData?.isNotEmpty() == true) {
                    "FLAC 표지 검증에 실패했습니다."
                }
            }
            if (pendingMediaStore) {
                context.contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }, null, null)
            }
            SaveResult(verified, uri, convertedFromWav = true)
        } catch (e: Exception) {
            destination?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            throw e
        } finally {
            wav.delete()
            flac.delete()
        }
    }

    private fun inspectPcm16(file: File): Pcm = RandomAccessFile(file, "r").use { raf ->
        val hdr = ByteArray(12)
        raf.readFully(hdr)
        require(String(hdr, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(hdr, 8, 4, Charsets.US_ASCII) == "WAVE") {
            "이 WAV는 표준 RIFF 파일이 아니므로 음질 손실 없이 변환할 수 없습니다."
        }
        var sampleRate = 0; var channels = 0; var bits = 0; var format = 0
        var dataOffset = -1L; var dataLength = 0L
        while (raf.filePointer + 8 <= raf.length()) {
            val name = ByteArray(4).also(raf::readFully).toString(Charsets.US_ASCII)
            val size = Integer.toUnsignedLong(Integer.reverseBytes(raf.readInt()))
            val begin = raf.filePointer
            require(begin + size <= raf.length()) { "손상된 WAV 청크입니다." }
            if (name == "fmt " && size >= 16) {
                val f = ByteArray(16)
                raf.readFully(f)
                val bb = ByteBuffer.wrap(f).order(ByteOrder.LITTLE_ENDIAN)
                format = bb.short.toInt() and 0xffff
                channels = bb.short.toInt() and 0xffff
                sampleRate = bb.int
                bb.int; bb.short
                bits = bb.short.toInt() and 0xffff
            } else if (name == "data") {
                dataOffset = begin; dataLength = size
            }
            raf.seek(begin + size + (size and 1L))
        }
        require(format == 1 && bits == 16 && channels in 1..2 &&
            sampleRate in 8000..192000 && dataOffset >= 0 && dataLength > 0 &&
            dataLength % (channels * 2) == 0L) {
            "현재 자동 FLAC 변환은 16-bit PCM 모노/스테레오 WAV만 지원합니다. 음질 손실을 막기 위해 원본을 유지했어요."
        }
        Pcm(sampleRate, channels, dataOffset, dataLength)
    }

    private fun encodeFlac(wav: File, pcm: Pcm, target: File) {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_FLAC, pcm.sampleRate, pcm.channels)
        format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5)
        val encoder = runCatching { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC) }
            .getOrElse { throw IllegalStateException("기기에 FLAC 무손실 인코더가 없습니다.", it) }
        var started = false
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            started = true
            RandomAccessFile(wav, "r").use { input ->
                input.seek(pcm.offset)
                FileOutputStream(target).use { output ->
                    var remaining = pcm.length
                    var bytesQueued = 0L
                    var finishedInput = false
                    var finishedOutput = false
                    var wroteHeader = false
                    var idle = 0
                    val info = MediaCodec.BufferInfo()
                    fun writeHeader(bytes: ByteArray) {
                        if (!wroteHeader && bytes.size >= 42 &&
                            String(bytes, 0, 4, Charsets.US_ASCII) == "fLaC") {
                            output.write(bytes)
                            wroteHeader = true
                        }
                    }
                    while (!finishedOutput) {
                        if (!finishedInput) {
                            val idx = encoder.dequeueInputBuffer(10000)
                            if (idx >= 0) {
                                val buf = encoder.getInputBuffer(idx) ?: error("FLAC 입력 버퍼 없음")
                                buf.clear()
                                val frameSize = pcm.channels * 2
                                val amount = minOf(remaining, (buf.remaining() / frameSize * frameSize).toLong()).toInt()
                                if (amount == 0) {
                                    encoder.queueInputBuffer(idx, 0, 0,
                                        bytesQueued / frameSize * 1_000_000L / pcm.sampleRate,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    finishedInput = true
                                } else {
                                    val bytes = ByteArray(amount)
                                    input.readFully(bytes)
                                    buf.put(bytes)
                                    encoder.queueInputBuffer(idx, 0, amount,
                                        bytesQueued / frameSize * 1_000_000L / pcm.sampleRate, 0)
                                    bytesQueued += amount
                                    remaining -= amount
                                }
                            }
                        }
                        when (val idx = encoder.dequeueOutputBuffer(info, 10000)) {
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                encoder.outputFormat.getByteBuffer("csd-0")?.let { bb ->
                                    val bytes = ByteArray(bb.remaining()); bb.get(bytes); writeHeader(bytes)
                                }
                            }
                            in 0..Int.MAX_VALUE -> {
                                val buf = encoder.getOutputBuffer(idx)
                                if (buf != null && info.size > 0) {
                                    buf.position(info.offset); buf.limit(info.offset + info.size)
                                    val bytes = ByteArray(info.size)
                                    buf.get(bytes)
                                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                        writeHeader(bytes)
                                    } else {
                                        check(wroteHeader) { "FLAC STREAMINFO 헤더가 없습니다." }
                                        output.write(bytes)
                                    }
                                }
                                finishedOutput = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                encoder.releaseOutputBuffer(idx, false)
                                idle = 0
                            }
                            else -> if (++idle > 250) error("FLAC 인코더 응답 시간 초과")
                        }
                    }
                    check(wroteHeader && target.length() > 42L) { "빈 FLAC 파일이 생성됐습니다." }
                }
            }
        } finally {
            if (started) runCatching { encoder.stop() }
            encoder.release()
        }
    }
}
