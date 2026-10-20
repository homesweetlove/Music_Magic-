package com.musicmagic.app

import java.io.File
import java.io.RandomAccessFile
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Standard RIFF/WAVE ID3v2.3 chunk. PCM "data" and every unrelated RIFF
 * chunk are copied byte-for-byte. Artwork is in an APIC front-cover frame.
 * WAV cover support varies by third-party player; the Music Magic reader
 * understands this format directly.
 */
object WavRiffTags {
    data class Tags(val fields: Map<String, String>, val picture: ByteArray?)
    private val mapped = linkedMapOf(
        "TIT2" to "title", "TPE1" to "artist", "TALB" to "album",
        "TPE2" to "albumArtist", "TCON" to "genre", "TYER" to "year",
        "TRCK" to "track", "USLT" to "lyrics",
    )
    private const val MAX_TAG = 24 * 1024 * 1024
    private const val MAX_ART = 12 * 1024 * 1024

    fun isWave(file: File): Boolean = RandomAccessFile(file, "r").use { raf ->
        raf.length() >= 12 && raf.readAscii(4) == "RIFF" &&
            raf.readUInt32Le() <= raf.length() - 8 &&
            raf.readAscii(4) == "WAVE"
    }

    fun read(file: File): Tags {
        RandomAccessFile(file, "r").use { raf ->
            require(raf.length() >= 12 && raf.readAscii(4) == "RIFF") { "표준 RIFF WAV 파일이 아닙니다." }
            val riffEnd = minOf(raf.length(), raf.readUInt32Le() + 8)
            require(raf.readAscii(4) == "WAVE") { "WAVE 식별자를 찾을 수 없습니다." }
            var result = Tags(emptyMap(), null)
            while (raf.filePointer + 8 <= riffEnd) {
                val name = raf.readAscii(4)
                val size = raf.readUInt32Le()
                val begin = raf.filePointer
                val next = begin + size + (size and 1L)
                require(next <= riffEnd && next >= begin) { "손상된 WAV 청크입니다." }
                if (name.equals("id3 ", true) && size in 10..MAX_TAG.toLong()) {
                    val bytes = ByteArray(size.toInt())
                    raf.readFully(bytes)
                    result = parseId3(bytes)
                }
                raf.seek(next)
            }
            return result
        }
    }

    fun write(file: File, edits: AudioEdits) {
        require(isWave(file)) { "이 WAV는 표준 RIFF 컨테이너가 아닙니다. 원본을 변경하지 않았어요." }
        val current = read(file)
        val values = linkedMapOf(
            "title" to edits.title, "artist" to edits.artist,
            "album" to edits.album, "albumArtist" to edits.albumArtist,
            "genre" to edits.genre, "year" to edits.year,
            "track" to edits.track, "lyrics" to edits.lyrics,
        )
        val merged = current.fields.toMutableMap()
        values.forEach { (key, value) ->
            if (value.isEmpty()) merged.remove(key) else merged[key] = value
        }
        val artwork = edits.artwork ?: current.picture
        if (artwork != null) require(artwork.size <= MAX_ART) { "표지는 12MB 이하만 저장할 수 있습니다." }
        val id3 = encodeId3(merged, artwork)
        val dest = File.createTempFile("musicmagic-riff-", ".wav", file.parentFile)
        try {
            RandomAccessFile(file, "r").use { source ->
                RandomAccessFile(dest, "rw").use { target ->
                    source.seek(0)
                    require(source.readAscii(4) == "RIFF") { "RIFF WAV가 아닙니다." }
                    val riffEnd = source.readUInt32Le() + 8L
                    require(source.readAscii(4) == "WAVE") { "WAVE WAV가 아닙니다." }
                    target.write("RIFF".toByteArray(Charsets.US_ASCII))
                    target.writeIntLe(0)
                    target.write("WAVE".toByteArray(Charsets.US_ASCII))
                    while (source.filePointer + 8 <= riffEnd) {
                        val name = source.readAscii(4)
                        val size = source.readUInt32Le()
                        val start = source.filePointer
                        val padded = size + (size and 1L)
                        require(start + padded <= riffEnd) { "손상된 RIFF 청크입니다." }
                        if (!name.equals("id3 ", true)) {
                            target.write(name.toByteArray(Charsets.US_ASCII))
                            target.writeIntLe(size)
                            copyExact(source, target, padded)
                        } else source.seek(start + padded)
                    }
                    if (id3.isNotEmpty()) {
                        target.write("id3 ".toByteArray(Charsets.US_ASCII))
                        target.writeIntLe(id3.size.toLong())
                        target.write(id3)
                        if (id3.size and 1 == 1) target.write(0)
                    }
                    require(target.length() <= 0xFFFFFFFFL + 8L) { "RIFF 파일 용량 한도를 초과합니다." }
                    target.seek(4)
                    target.writeIntLe(target.length() - 8L)
                }
            }
            val verified = read(dest)
            values.forEach { (key, value) ->
                check(verified.fields[key].orEmpty() == value) { "WAV $key 태그 기록 검증 실패" }
            }
            if (artwork != null) check(verified.picture?.contentEquals(artwork) == true) {
                "WAV 앨범 커버 기록 검증 실패"
            }
            // Replace the temporary WORK file only after readback passes. The
            // user's SAF original is still guarded by AudioMetadata's rollback.
            dest.copyTo(file, overwrite = true)
        } finally {
            dest.delete()
        }
    }

    private fun parseId3(data: ByteArray): Tags {
        if (data.size < 10 || String(data, 0, 3, Charsets.US_ASCII) != "ID3") return Tags(emptyMap(), null)
        val major = data[3].toInt() and 255
        if (major != 3 && major != 4) return Tags(emptyMap(), null)
        val total = syncsafe(data, 6).coerceAtMost(data.size - 10)
        val fields = mutableMapOf<String, String>()
        var picture: ByteArray? = null
        var at = 10
        val end = 10 + total
        while (at + 10 <= end) {
            val frame = String(data, at, 4, Charsets.US_ASCII)
            if (frame.any { it !in 'A'..'Z' && it !in '0'..'9' }) break
            val length = if (major == 4) syncsafe(data, at + 4) else
                ByteBuffer.wrap(data, at + 4, 4).order(ByteOrder.BIG_ENDIAN).int
            if (length < 0 || at + 10L + length > end) break
            val offset = at + 10
            val encoding = if (length > 0) data[offset].toInt() and 255 else 0
            val payload = if (length > 1) data.copyOfRange(offset + 1, offset + length) else ByteArray(0)
            if (frame == "APIC" && payload.isNotEmpty()) {
                var idx = payload.indexOf(0)
                if (idx >= 0 && idx + 2 < payload.size) {
                    idx += 2 // skip the MIME terminator and picture type
                    val endDesc = if (encoding == 1 || encoding == 2) {
                        // UTF-16 description (two byte NUL)
                        var pos = idx
                        while (pos + 1 < payload.size && !(payload[pos] == 0.toByte() && payload[pos + 1] == 0.toByte())) pos += 2
                        (pos + 2).coerceAtMost(payload.size)
                    } else (payload.indexOf(0, idx).takeIf { it >= 0 }?.plus(1) ?: idx)
                    if (endDesc < payload.size && payload.size - endDesc <= MAX_ART)
                        picture = payload.copyOfRange(endDesc, payload.size)
                }
            } else if (frame in mapped || frame == "USLT") {
                val textPayload = if (frame == "USLT" && payload.size > 4) payload.copyOfRange(4, payload.size) else payload
                val decoded = when (encoding) {
                    1 -> String(textPayload, Charsets.UTF_16)
                    2 -> String(textPayload, Charsets.UTF_16BE)
                    3 -> String(textPayload, Charsets.UTF_8)
                    else -> String(textPayload, Charsets.ISO_8859_1)
                }.trim('\u0000')
                mapped[frame]?.let { fields[it] = decoded }
            }
            at = offset + length
        }
        return Tags(fields, picture)
    }

    private fun encodeId3(fields: Map<String, String>, picture: ByteArray?): ByteArray {
        val frames = ByteArrayOutputStream()
        mapped.forEach { (frameId, key) ->
            val text = fields[key]?.takeIf(String::isNotBlank) ?: return@forEach
            val data = if (frameId == "USLT") {
                byteArrayOf(1) + "kor".toByteArray(Charsets.ISO_8859_1) +
                    byteArrayOf(0, 0) + text.toByteArray(Charsets.UTF_16)
            } else byteArrayOf(1) + text.toByteArray(Charsets.UTF_16)
            writeFrame(frames, frameId, data)
        }
        if (picture != null) {
            val bytes = byteArrayOf(0) + "image/jpeg".toByteArray(Charsets.ISO_8859_1) +
                byteArrayOf(0, 3, 0) + picture
            writeFrame(frames, "APIC", bytes)
        }
        if (frames.size() == 0) return ByteArray(0)
        check(frames.size() <= MAX_TAG - 10) { "WAV 메타데이터가 너무 큽니다." }
        val total = frames.size()
        val header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            3, 0, 0, ((total ushr 21) and 0x7F).toByte(),
            ((total ushr 14) and 0x7F).toByte(),
            ((total ushr 7) and 0x7F).toByte(), (total and 0x7F).toByte())
        return header + frames.toByteArray()
    }
    private fun writeFrame(output: ByteArrayOutputStream, id: String, data: ByteArray) {
        output.write(id.toByteArray(Charsets.US_ASCII))
        output.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array())
        output.write(byteArrayOf(0, 0))
        output.write(data)
    }
    private fun syncsafe(data: ByteArray, from: Int): Int =
        ((data[from].toInt() and 127) shl 21) or ((data[from+1].toInt() and 127) shl 14) or
            ((data[from+2].toInt() and 127) shl 7) or (data[from+3].toInt() and 127)

    private fun RandomAccessFile.readAscii(n: Int): String = ByteArray(n).also { readFully(it) }
        .toString(Charsets.US_ASCII)
    private fun RandomAccessFile.readUInt32Le(): Long {
        val a = readUnsignedByte(); val b = readUnsignedByte()
        val c = readUnsignedByte(); val d = readUnsignedByte()
        return a.toLong() or (b.toLong() shl 8) or (c.toLong() shl 16) or (d.toLong() shl 24)
    }
    private fun RandomAccessFile.writeIntLe(value: Long) {
        require(value in 0..0xFFFFFFFFL) { "RIFF 4GB 상한을 초과했습니다." }
        write((value and 255).toInt()); write(((value ushr 8) and 255).toInt())
        write(((value ushr 16) and 255).toInt()); write(((value ushr 24) and 255).toInt())
    }
    private fun copyExact(from: RandomAccessFile, to: RandomAccessFile, bytes: Long) {
        var remaining = bytes
        val buffer = ByteArray(65536)
        while (remaining > 0) {
            val count = minOf(remaining, buffer.size.toLong()).toInt()
            from.readFully(buffer, 0, count)
            to.write(buffer, 0, count)
            remaining -= count
        }
    }
}
