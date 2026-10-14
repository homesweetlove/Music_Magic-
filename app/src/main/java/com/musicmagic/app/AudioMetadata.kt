package com.musicmagic.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

data class AudioInfo(val fileName: String, val title: String, val artist: String, val album: String, val supported: Boolean)

object AudioMetadata {
    fun isEditable(context: Context, uri: Uri): Boolean = read(context, uri).supported

    fun read(context: Context, uri: Uri): AudioInfo {
        val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "audio"
        if (!name.endsWith(".mp3", true)) return AudioInfo(name, "", "", "", false)
        return try {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val tags = parse(bytes)
            AudioInfo(name, tags["TIT2"] ?: "", tags["TPE1"] ?: "", tags["TALB"] ?: "", bytes.size < 10 || String(bytes, 0, 3, StandardCharsets.ISO_8859_1) != "ID3" || (bytes[3].toInt() == 3 && bytes[5].toInt() == 0))
        } catch (_: Exception) { AudioInfo(name, "", "", "", false) }
    }

    fun save(context: Context, uri: Uri, title: String, artist: String, album: String, requestedName: String): Result<Unit> = runCatching {
        val doc = DocumentFile.fromSingleUri(context, uri) ?: error("파일에 접근할 수 없습니다.")
        val originalName = doc.name ?: error("파일명을 확인할 수 없습니다.")
        require(originalName.endsWith(".mp3", true)) { "현재는 MP3 파일만 저장할 수 있습니다." }
        require(isEditable(context, uri)) { "지원하지 않는 ID3 구조이므로 원본을 수정하지 않았습니다." }
        require(requestedName.endsWith(".mp3", true)) { "확장자 변환은 아직 지원하지 않습니다. MP3 확장자를 유지하세요." }
        require('/' !in requestedName && '\\' !in requestedName && requestedName.isNotBlank()) { "올바른 파일명을 입력하세요." }
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val offset = id3Size(bytes)
        val newTag = buildTagPreservingFrames(bytes, title, artist, album)
        require(bytes.size <= 200 * 1024 * 1024) { "200MB 초과 파일은 아직 지원하지 않습니다." }
        val updated = ByteArrayOutputStream().apply {
            write(newTag)
            write(bytes, offset, bytes.size - offset)
        }.toByteArray()
        // SAF providers do not guarantee atomic replacement. Keep a cache backup for recovery.
        val backup = java.io.File.createTempFile("musicmagic-backup-", ".mp3", context.cacheDir)
        try {
            backup.outputStream().use { it.write(bytes) }
            try {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(updated) }
                val verified = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                check(verified.contentEquals(updated)) { "저장 검증에 실패했습니다." }
                if (requestedName != originalName) {\n                    // Renaming is a separate provider operation and may fail independently.\n                    check(doc.renameTo(requestedName)) { "태그 저장은 성공했지만 파일명 변경에 실패했습니다." }\n                }
            } catch (error: Exception) {
                val restored = runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { output -> backup.inputStream().use { it.copyTo(output) } } }.isSuccess
                if (!restored) error("저장 실패 및 자동 복구 실패. 원본 파일을 확인하세요.", error)
                throw error
            }
        } finally { backup.delete() }
    }

    private fun id3Size(data: ByteArray): Int {
        if (data.size < 10 || String(data, 0, 3, StandardCharsets.ISO_8859_1) != "ID3") return 0
        val version = data[3].toInt() and 255
        require(version in 2..4) { "지원하지 않는 ID3 버전입니다." }
        val size = synchsafeDecode(data, 6)
        val footer = if (version == 4 && (data[5].toInt() and 0x10) != 0) 10 else 0
        val total = 10 + size + footer
        require(total <= data.size) { "손상된 ID3 헤더입니다." }
        return total
    }

    private fun parse(data: ByteArray): Map<String, String> {
        if (id3Size(data) == 0 || data[3].toInt() == 2) return emptyMap()
        val version = data[3].toInt()
        val end = id3Size(data).coerceAtMost(data.size)
        val result = mutableMapOf<String, String>()
        var p = 10
        while (p + 10 <= end) {
            val id = String(data, p, 4, StandardCharsets.ISO_8859_1)
            if (!id.all { it.isLetterOrDigit() }) break
            val size = if (version == 4) synchsafeDecode(data, p + 4) else ByteBuffer.wrap(data, p + 4, 4).int
            if (size <= 0 || p + 10 + size > end) break
            if (id in setOf("TIT2", "TPE1", "TALB") && size > 1) {
                val enc = data[p + 10].toInt() and 255
                val charset = when (enc) { 0 -> StandardCharsets.ISO_8859_1; 1 -> StandardCharsets.UTF_16; 2 -> StandardCharsets.UTF_16BE; else -> StandardCharsets.UTF_8 }
                result[id] = String(data, p + 11, size - 1, charset).trimEnd('\u0000')
            }
            p += 10 + size
        }
        return result
    }

    // Keep all unrelated ID3v2.3 frames, including embedded album artwork (APIC).
    // Reject layouts that cannot be safely round-tripped instead of discarding metadata.
    private fun buildTagPreservingFrames(data: ByteArray, title: String, artist: String, album: String): ByteArray {
        val preserved = ByteArrayOutputStream()
        val existing = id3Size(data)
        if (existing > 0) {
            require(data[3].toInt() == 3) { "안전한 보존 편집은 ID3v2.3만 지원합니다." }
            require((data[5].toInt() and 0xFF) == 0) { "특수 ID3 플래그가 있어 안전하게 수정할 수 없습니다." }
            var p = 10
            while (p + 10 <= existing) {
                if ((p until p + 4).all { data[it].toInt() == 0 }) break
                val id = String(data, p, 4, StandardCharsets.ISO_8859_1)
                require(id.matches(Regex("[A-Z0-9]{4}"))) { "지원하지 않는 ID3 프레임입니다." }
                val length = ByteBuffer.wrap(data, p + 4, 4).int
                require(length > 0 && p + 10L + length <= existing) { "손상된 ID3 프레임입니다." }
                val flags = ((data[p + 8].toInt() and 255) shl 8) or (data[p + 9].toInt() and 255)
                require((flags and 0x00E0) == 0) { "압축 또는 암호화된 ID3 프레임은 편집할 수 없습니다." }
                if (id !in setOf("TIT2", "TPE1", "TALB")) preserved.write(data, p, 10 + length)
                p += 10 + length
            }
        }
        for ((id, value) in listOf("TIT2" to title, "TPE1" to artist, "TALB" to album)) {
            val payload = byteArrayOf(1) + value.toByteArray(StandardCharsets.UTF_16)
            preserved.write(id.toByteArray(StandardCharsets.US_ASCII))
            preserved.write(ByteBuffer.allocate(4).putInt(payload.size).array())
            preserved.write(byteArrayOf(0, 0))
            preserved.write(payload)
        }
        val body = preserved.toByteArray()
        return byteArrayOf(73, 68, 51, 3, 0, 0) + synchsafeEncode(body.size) + body
    }

    private fun synchsafeDecode(bytes: ByteArray, offset: Int): Int =
        (0..3).fold(0) { acc, i -> (acc shl 7) or (bytes[offset + i].toInt() and 0x7F) }

    private fun synchsafeEncode(size: Int): ByteArray = byteArrayOf(
        ((size shr 21) and 127).toByte(), ((size shr 14) and 127).toByte(),
        ((size shr 7) and 127).toByte(), (size and 127).toByte()
    )
}
