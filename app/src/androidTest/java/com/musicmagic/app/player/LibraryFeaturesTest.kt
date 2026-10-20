package com.musicmagic.app.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.musicmagic.app.library.MusicCollections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFeaturesTest {
    @Test fun lrcSupportsMultipleTimestampsAndDecimalPrecision() {
        val lyrics = "[00:01.2][00:02.34]첫 줄\n[00:03.456]둘째 줄\n[ar:artist]"
        val lines = LrcParser.parse(lyrics)
        assertEquals(listOf(1200L, 2340L, 3456L), lines.map { it.timeMs })
        assertEquals(listOf("첫 줄", "첫 줄", "둘째 줄"), lines.map { it.text })
        assertEquals(-1, LrcParser.activeIndex(lines, 50L))
        assertEquals(1, LrcParser.activeIndex(lines, 3000L))
    }

    @Test fun collectionsSurviveReloadAndSafUriRenames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val old = Uri.parse("content://test.files/document/songOld")
        val newer = Uri.parse("content://test.files/document/songNew")
        val tree = Uri.parse("content://test.files/tree/old")
        val prefs = context.getSharedPreferences("music_magic_library_v2", Context.MODE_PRIVATE)
        val snapshot = prefs.all.toMap()
        try {
            prefs.edit().clear().commit()
            MusicCollections.rememberTree(context, tree)
            assertEquals(tree, MusicCollections.lastTree(context))
            assertTrue(old.toString() in MusicCollections.toggleFavorite(context, old.toString()))
            MusicCollections.createPlaylist(context, "좋아하는 음악")
            MusicCollections.togglePlaylistSong(context, "좋아하는 음악", old.toString())
            MusicCollections.replaceUri(context, old, newer)
            assertFalse(old.toString() in MusicCollections.favorites(context))
            assertTrue(newer.toString() in MusicCollections.favorites(context))
            assertEquals(listOf(newer.toString()),
                MusicCollections.playlists(context)["좋아하는 음악"])
            assertEquals(tree, MusicCollections.lastTree(context))
            assertTrue(MusicCollections.deletePlaylist(context, "좋아하는 음악").isEmpty())
        } finally {
            val editor = prefs.edit().clear()
            snapshot.forEach { (key, v) ->
                when (v) {
                    is String -> editor.putString(key, v)
                    is Set<*> -> editor.putStringSet(key, v.filterIsInstance<String>().toSet())
                    is Int -> editor.putInt(key, v)
                    is Long -> editor.putLong(key, v)
                    is Boolean -> editor.putBoolean(key, v)
                    is Float -> editor.putFloat(key, v)
                }
            }
            editor.commit()
        }
    }
}
