package com.musicmagic.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.musicmagic.app.library.LibrarySorting
import com.musicmagic.app.library.LibraryTrack
import com.musicmagic.app.library.MusicSort
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FeaturePackTest {
    @Test fun artworkCanBeCroppedRotatedAndSavedAsRealPngOrJpeg() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(150, 90, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(130, 50, 220))
        val file = File.createTempFile("artwork-feature-", ".png", context.cacheDir)
        try {
            file.outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            val png = ArtworkEditing.render(context, Uri.fromFile(file), 1.4f, 1, true)
            val jpeg = ArtworkEditing.render(context, Uri.fromFile(file), 1f, 0, false)
            assertTrue(ArtworkEditing.isPng(png))
            assertFalse(ArtworkEditing.isPng(jpeg))
            assertEquals("image/jpeg", ArtworkEditing.mime(jpeg))
            for (encoded in listOf(png, jpeg)) {
                val image = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
                assertEquals(image.width, image.height)
                image.recycle()
            }
        } finally { bitmap.recycle(); file.delete() }
    }

    @Test fun playlistSortsAndGroupsByArtistFolderAndFavorite() {
        fun song(id: String, name: String, artist: String, folder: String, time: Long) =
            LibraryTrack(Uri.parse("content://test/$id"), "$id.wav",
                name, artist, "Album", "", "WAV", modifiedAt = time,
                folderName = folder)
        val a = song("a", "Zulu", "Singer B", "Rock", 100)
        val b = song("b", "Alpha", "Singer A", "Jazz", 200)
        val c = song("c", "Beta", "Singer B", "Rock", 300)
        assertEquals(listOf(b,a,c), LibrarySorting.sort(listOf(a,b,c), MusicSort.ARTIST))
        assertEquals(listOf(b,c,a), LibrarySorting.sort(listOf(a,b,c), MusicSort.TITLE))
        assertEquals(listOf(c,b,a), LibrarySorting.sort(listOf(a,b,c), MusicSort.RECENT))
        assertEquals(b, LibrarySorting.sort(listOf(a,b,c), MusicSort.FAVORITES,
            setOf(b.uri.toString())).first())
        assertEquals(2, LibrarySorting.byArtist(listOf(a,b,c))["Singer B"]?.size)
        assertEquals(2, LibrarySorting.byFolder(listOf(a,b,c))["Rock"]?.size)
    }

    @Test fun twentyFourBitWavCoverUpdateKeepsEveryPcmByteAndCanRemoveIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pcm = ByteArray(9000) { (it * 17).toByte() }
        val file = File.createTempFile("wave24-", ".wav", context.cacheDir)
        val coverBitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        coverBitmap.eraseColor(android.graphics.Color.rgb(20, 190, 115))
        val image = ByteArrayOutputStream().use {
            check(coverBitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            it.toByteArray()
        }
        coverBitmap.recycle()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + pcm.size)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
            putShort(1.toShort()); putShort(1.toShort())
            putInt(96000); putInt(288000); putShort(3.toShort())
            putShort(24.toShort()); put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
        }.array()
        try {
            file.outputStream().use { it.write(header); it.write(pcm) }
            WavRiffTags.write(file, AudioEdits(file.name, "", "", "", artwork=image))
            assertArrayEquals(image, WavRiffTags.read(file).picture)
            assertArrayEquals(pcm, file.readBytes().copyOfRange(44,44+pcm.size))
            WavRiffTags.write(file, AudioEdits(file.name, "", "", "",removeArtwork=true))
            assertEquals(null, WavRiffTags.read(file).picture)
            assertArrayEquals(pcm, file.readBytes().copyOfRange(44,44+pcm.size))
        } finally { file.delete() }
    }

    @Test fun boundedHistoryCreatesSmallBackupAndCanClearIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("music_magic_edit_history_v1",
            android.content.Context.MODE_PRIVATE)
        val previous=prefs.getString("history","[]")
        val file=File.createTempFile("history-test-", ".wav", context.cacheDir)
        val pcm=ByteArray(4000)
        val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII));putInt(36+pcm.size)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII));putInt(16)
            putShort(1.toShort());putShort(1.toShort());putInt(44100)
            putInt(88200);putShort(2.toShort());putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII));putInt(pcm.size)
        }.array()
        try {
            prefs.edit().putString("history","[]").commit()
            file.outputStream().use { it.write(header);it.write(pcm) }
            EditHistory.record(context,Uri.parse("content://test/new"),file.name,
                file, AudioInfo(fileName=file.name,title="New",supported=true))
            val list=EditHistory.list(context)
            assertEquals(1,list.size)
            assertTrue(list[0].restorable)
            assertTrue(list[0].after.contains("New"))
            EditHistory.clear(context)
            assertTrue(EditHistory.list(context).isEmpty())
        }finally { file.delete(); prefs.edit().putString("history",previous).commit() }
    }
}
