package com.musicmagic.app.library

/**
 * Order is evaluated against freshly scanned metadata: a playlist stores URI
 * membership and insertion order, never a frozen copy of titles or artists.
 */
enum class MusicSort(val label: String) {
    MANUAL("추가한 순서"),
    TITLE("제목순"),
    ARTIST("아티스트순"),
    ALBUM("앨범순"),
    RECENT("최근 수정순"),
    FAVORITES("즐겨찾기 우선"),
}

object LibrarySorting {
    fun sort(tracks: List<LibraryTrack>, mode: MusicSort, favorites: Set<String> = emptySet()): List<LibraryTrack> =
        when (mode) {
            MusicSort.MANUAL -> tracks
            MusicSort.TITLE -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            MusicSort.ARTIST -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
            MusicSort.ALBUM -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.album })
            MusicSort.RECENT -> tracks.sortedByDescending { it.modifiedAt }
            MusicSort.FAVORITES -> tracks.sortedWith(compareByDescending<LibraryTrack> { it.uri.toString() in favorites }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }

    fun byArtist(tracks: List<LibraryTrack>) = tracks.groupBy { it.artist.ifBlank { "아티스트 미상" } }
    fun byFolder(tracks: List<LibraryTrack>) = tracks.groupBy { it.folderName.ifBlank { "선택한 폴더" } }
}
