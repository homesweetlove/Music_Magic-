package com.musicmagic.app.library

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Preferences only contain URI identifiers and collection membership; never audio bytes.
 * Stored SAF permissions are verified by the caller before reopening files.
 */
object MusicCollections {
    private const val PREFS = "music_magic_library_v2"
    private const val TREE = "music_tree"
    private const val FAVORITES = "favorite_uris"
    private const val PLAYLISTS = "playlists"

    private const val ORDER = "collection_sort_order"

    fun sortOrder(context: Context): MusicSort = runCatching {
        MusicSort.valueOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ORDER, MusicSort.MANUAL.name).orEmpty())
    }.getOrDefault(MusicSort.MANUAL)

    fun setSortOrder(context: Context, order: MusicSort) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ORDER, order.name).apply()
    }

    fun lastTree(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TREE, null)?.let(Uri::parse)

    fun rememberTree(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(TREE, uri.toString()).apply()
    }

    fun forgetTree(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(TREE).apply()
    }

    fun favorites(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(FAVORITES, emptySet()).orEmpty().toSet()

    fun toggleFavorite(context: Context, uri: String): Set<String> {
        val next = favorites(context).toMutableSet()
        if (!next.add(uri)) next.remove(uri)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(FAVORITES, next.toSet()).apply()
        return next
    }

    fun playlists(context: Context): Map<String, List<String>> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PLAYLISTS, "{}").orEmpty()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { name ->
                    val array = json.optJSONArray(name) ?: JSONArray()
                    put(name, (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.distinct())
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun savePlaylists(context: Context, playlists: Map<String, List<String>>) {
        val json = JSONObject()
        playlists.forEach { (name, songs) ->
            json.put(name, JSONArray(songs.distinct()))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(PLAYLISTS, json.toString()).apply()
    }

    fun createPlaylist(context: Context, rawName: String): Map<String, List<String>> {
        val name = rawName.trim().take(48)
        require(name.isNotBlank()) { "재생목록 이름을 입력하세요." }
        val existing = playlists(context)
        require(name !in existing) { "이미 존재하는 재생목록입니다." }
        return (existing + (name to emptyList())).also { savePlaylists(context, it) }
    }

    fun togglePlaylistSong(context: Context, name: String, uri: String): Map<String, List<String>> {
        val current = playlists(context)
        val entries = current[name].orEmpty().toMutableList()
        if (!entries.remove(uri)) entries.add(uri)
        return current.toMutableMap().apply { put(name, entries) }
            .also { savePlaylists(context, it) }
    }

    fun deletePlaylist(context: Context, name: String): Map<String, List<String>> =
        playlists(context).filterKeys { it != name }.also { savePlaylists(context, it) }

    /** File renames may create new SAF URIs; keep lists pointing to the actual saved file. */
    fun replaceUri(context: Context, oldUri: Uri, newUri: Uri) {
        if (oldUri == newUri) return
        val previous = oldUri.toString()
        val replacement = newUri.toString()
        val favorites = favorites(context)
        if (previous in favorites) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(FAVORITES, favorites - previous + replacement).apply()
        }
        val lists = playlists(context)
        if (lists.values.any { previous in it }) {
            savePlaylists(context, lists.mapValues { (_, items) ->
                items.map { if (it == previous) replacement else it }.distinct()
            })
        }
    }
}
