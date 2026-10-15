package com.musicmagic.app

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class OnlineTags(val title: String, val artist: String, val album: String)

object MusicBrainzLookup {
    fun search(title: String, artist: String): Result<OnlineTags> = runCatching {
        require(title.isNotBlank()) { "검색할 곡 제목을 입력하세요." }
        val query = "recording:\"$title\"" + if (artist.isNotBlank()) " AND artist:\"$artist\"" else ""
        val endpoint = "https://musicbrainz.org/ws/2/recording/?query=" +
            Uri.encode(query) + "&fmt=json&limit=5"
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "MusicMagic/1.1 (Android metadata editor)")
        try {
            check(conn.responseCode == 200) { "MusicBrainz 응답 오류: ${conn.responseCode}" }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val recordings = json.optJSONArray("recordings")
            require(recordings != null && recordings.length() > 0) { "검색 결과가 없습니다." }
            val item = recordings.getJSONObject(0)
            val credits = item.optJSONArray("artist-credit")
            val foundArtist = credits?.optJSONObject(0)?.optJSONObject("artist")?.optString("name").orEmpty()
            val releases = item.optJSONArray("releases")
            val foundAlbum = releases?.optJSONObject(0)?.optString("title").orEmpty()
            OnlineTags(item.optString("title"), foundArtist, foundAlbum)
        } finally {
            conn.disconnect()
        }
    }
}
