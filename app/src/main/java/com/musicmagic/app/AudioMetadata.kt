package com.musicmagic.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

data class AudioInfo(val fileName: String, val title: String, val artist: String, val album: String, val supported: Boolean)

object AudioMetadata {
    fun read(context: Context, uri: Uri): AudioInfo {
        val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "audio"
        if (!name.endsWith(".mp3", true)) return AudioInfo(name, "", "", "", false)
        return try {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val tags = parse(bytes)
            AudioInfo(name, tags["TIT2"] ?: "", tags["TPE1"] ?: "", tags["TALB"] ?: "", true)
        } catch (_: Exception) { AudioInfo(name, "", "", "", false) }
    }

    fun save(context: Context, uri: Uri, title: String, artist: String, album: String, requestedName: String): Result<Unit> = runCatching {
        val doc = DocumentFile.fromSingleUri(context, uri) ?: error("파일에 접근할 수 없습니다.")
        val originalName = doc.name ?: error("파일명을 확인할 수 없습니다.")
        require(originalName.endsWith(".mp3", true)) { "현재는 MP3 파일만 저장할 수 있습니다." }
        require(requestedName.endsWith(".mp3", true)) { "확장자 변환은 아직 지원하지 않습니다. MP3 확장자를 유지하세요." }
        require('/' !in requestedName && '\\' !in requestedName && requestedName.isNotBlank()) { "올바른 파일명을 입력하세요." }
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val offset = id3Size(bytes)
        val newTag = buildTag(title, artist, album)
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
                if (requestedName != originalName) check(doc.renameTo(requestedName)) { "메타데이터는 저장됐지만 파일명 변경에 실패했습니다." }
            } catch (error: Exception) {
                runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { output -> backup.inputStream().use { it.copyTo(output) } } }
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

    private fun buildTag(title: String, artist: String, album: String): ByteArray {
        val frames = ByteArrayOutputStream()
        for ((id, value) in listOf("TIT2" to title, "TPE1" to artist, "TALB" to album)) {
            val payload = byteArrayOf(1) + value.toByteArray(StandardCharsets.UTF_16)
            frames.write(id.toByteArray(StandardCharsets.US_ASCII))
            frames.write(ByteBuffer.allocate(4).putInt(payload.size).array())
            frames.write(byteArrayOf(0, 0))
            frames.write(payload)
        }
        val body = frames.toByteArray()
        return byteArrayOf(73, 68, 51, 3, 0, 0) + synchsafeEncode(body.size) + body
    }

    private fun synchsafeDecode(bytes: ByteArray, offset: Int): Int =
        (0..3).fold(0) { acc, i -> (acc shl 7) or (bytes[offset + i].toInt() and 0x7F) }

    private fun synchsafeEncode(size: Int): ByteArray = byteArrayOf(
        ((size shr 21) and 127).toByte(), ((size shr 14) and 127).toByte(),
        ((size shr 7) and 127).toByte(), (size and 127).toByte()
    )
}
