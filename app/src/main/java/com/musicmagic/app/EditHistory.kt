package com.musicmagic.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class HistoryEntry(
    val id: String,
    val uri: String,
    val beforeName: String,
    val afterName: String,
    val before: String,
    val after: String,
    val timestamp: Long,
    val backupName: String?,
) {
    val restorable get() = backupName != null
}

/**
 * Private on-device journal. Retains 8 summaries, and up to 120 MB in file
 * backups. Large lossless recordings get a summary only, not a misleading
 * "restorable" badge. No external permissions, network or cloud storage.
 */
object EditHistory {
    private const val PREFS = "music_magic_edit_history_v1"
    private const val KEY = "history"
    private const val MAX_COUNT = 8
    private const val MAX_BACKUP_SIZE = 40L * 1024 * 1024
    private const val MAX_TOTAL = 120L * 1024 * 1024

    private fun folder(context: Context) = File(context.filesDir, "history").also { it.mkdirs() }

    fun list(context: Context): List<HistoryEntry> =
        runCatching {
            val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "[]"))
            (0 until arr.length()).mapNotNull { i ->
                val v = arr.optJSONObject(i) ?: return@mapNotNull null
                HistoryEntry(
                    id = v.getString("id"),
                    uri = v.getString("uri"),
                    beforeName = v.getString("beforeName"),
                    afterName = v.getString("afterName"),
                    before = v.optString("before"),
                    after = v.optString("after"),
                    timestamp = v.optLong("timestamp"),
                    backupName = v.optString("backupName").takeIf { it.isNotBlank() },
                )
            }
        }.getOrDefault(emptyList())

    private fun persist(context: Context, entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id); put("uri", e.uri)
                put("beforeName", e.beforeName); put("afterName", e.afterName)
                put("before", e.before); put("after", e.after)
                put("timestamp", e.timestamp)
                put("backupName", e.backupName ?: "")
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString()).apply()
    }

    /** Called after verifying the physical saved audio; never blocks that save. */
    fun record(context: Context, uri: Uri, originalName: String, original: File, after: AudioInfo) {
        val old = snapshot(original, originalName)
        val file = folder(context)
        val id = UUID.randomUUID().toString()
        var copy: String? = null
        if (original.length() in 1..MAX_BACKUP_SIZE) {
            val name = "$id.bak"
            runCatching { original.copyTo(File(file, name), overwrite = false) }
                .onSuccess { copy = name }
        }
        val entry = HistoryEntry(id, uri.toString(), originalName,
            after.fileName, old, summary(after), System.currentTimeMillis(), copy)
        val entries = (list(context) + entry).toMutableList()
        while (entries.size > MAX_COUNT ||
            entries.sumOf { e -> e.backupName?.let { File(file, it).length() } ?: 0L } > MAX_TOTAL) {
            val removed = entries.removeAt(0)
            removed.backupName?.let { File(file, it).delete() }
        }
        persist(context, entries)
    }

    private fun snapshot(file: File, filename: String): String {
        return runCatching {
            val tag = if (filename.endsWith(".wav", true)) null else AudioFileIO.read(file).tag
            val wav = if (filename.endsWith(".wav", true)) WavRiffTags.read(file).fields else null
            fun value(key: String, id: FieldKey): String =
                wav?.get(key) ?: tag?.getFirst(id).orEmpty()
            listOf(
                value("title", FieldKey.TITLE),
                value("artist", FieldKey.ARTIST),
                value("album", FieldKey.ALBUM),
                value("genre", FieldKey.GENRE),
            ).joinToString(" | ").take(400)
        }.getOrDefault(filename)
    }

    private fun summary(i: AudioInfo) =
        listOf(i.title, i.artist, i.album, i.genre).joinToString(" | ").take(400)

    fun restore(context: Context, entryId: String): Result<SaveResult> = runCatching {
        val entry = list(context).firstOrNull { it.id == entryId }
            ?: error("이 변경 기록을 찾을 수 없습니다.")
        val backupName = entry.backupName ?: error("이 기록은 파일 용량 때문에 원본 백업이 없습니다.")
        require(backupName.matches(Regex("[a-f0-9-]{36}\\.bak"))) { "잘못된 백업 파일입니다." }
        val backup = File(folder(context), backupName)
        require(backup.exists() && backup.length() > 0) { "백업 파일이 사라졌습니다." }
        val uri = Uri.parse(entry.uri)
        val currentName = DocumentFile.fromSingleUri(context, uri)?.name
            ?: error("이전 파일을 찾을 수 없습니다.")
        val guard = File.createTempFile("history-recovery-", ".bak", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(guard).use { input.copyTo(it) }
            } ?: error("복원 전 현재 파일 백업 실패")
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    backup.inputStream().use { it.copyTo(out) }
                } ?: error("원본 파일에 기록할 수 없습니다.")
                val verified = AudioMetadata.read(context, uri)
                check(verified.supported) { "복원 파일의 메타데이터 검증 실패" }
                val result = if (currentName != entry.beforeName) {
                    val renamed = DocumentsContract.renameDocument(
                        context.contentResolver, uri, entry.beforeName)
                        ?: error("이전 파일명 복원 실패")
                    SaveResult(AudioMetadata.read(context, renamed), renamed)
                } else SaveResult(verified, uri)
                check(result.info.supported) { "복원 후 파일 확인 실패" }
                context.contentResolver.notifyChange(result.uri, null)
                result
            } catch (e: Exception) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        guard.inputStream().use { it.copyTo(out) }
                    }
                }
                throw e
            }
        } finally { guard.delete() }
    }

    fun clear(context: Context) {
        list(context).forEach { it.backupName?.let { name -> File(folder(context), name).delete() } }
        persist(context, emptyList())
    }
}
