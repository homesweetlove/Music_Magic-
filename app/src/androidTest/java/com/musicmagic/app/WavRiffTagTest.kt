package com.musicmagic.app

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavRiffTagTest {
    private fun createWave(): Pair<File, ByteArray> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wav = File.createTempFile("musicmagic-wav-apic-", ".wav", context.cacheDir)
        val pcm = ByteArray(8192) { ((it * 13) % 256).toByte() }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcm.size)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(8000)
            putInt(16000)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
        }.array()
        wav.outputStream().use { it.write(header); it.write(pcm) }
        return wav to pcm
    }

    private fun cover(): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(220, 55, 140))
        return try {
            ByteArrayOutputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it))
                it.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    @Test fun wavArtworkOnlyNoArtistOrTitlePreservesPcmAcrossEdits() {
        val (wav, pcm) = createWave()
        try {
            val artwork = cover()
            val edits = AudioEdits(
                fileName = wav.name, title = "", artist = "", album = "",
                artwork = artwork,
            )
            WavRiffTags.write(wav, edits)
            val readback = WavRiffTags.read(wav)
            assertArrayEquals(artwork, readback.picture)
            assertTrue(readback.fields.isEmpty())
            assertArrayEquals(pcm, wav.readBytes().copyOfRange(44, 44 + pcm.size))
            // Re-saving only the cover must not append duplicate ID3 chunks.
            val previousLength = wav.length()
            WavRiffTags.write(wav, edits)
            assertEquals(previousLength, wav.length())
            assertArrayEquals(pcm, wav.readBytes().copyOfRange(44, 44 + pcm.size))
            assertArrayEquals(artwork, WavRiffTags.read(wav).picture)
        } finally { wav.delete() }
    }

    @Test fun wavTitleOnlyCanBeSavedWithoutArtworkOrArtist() {
        val (wav, pcm) = createWave()
        try {
            WavRiffTags.write(wav, AudioEdits(
                fileName = wav.name, title = "한글 제목", artist = "", album = "",
            ))
            val tag = WavRiffTags.read(wav)
            assertEquals("한글 제목", tag.fields["title"])
            assertNull(tag.picture)
            assertArrayEquals(pcm, wav.readBytes().copyOfRange(44, 44 + pcm.size))
        } finally { wav.delete() }
    }
}
