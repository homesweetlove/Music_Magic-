package com.musicmagic.app

import android.graphics.Bitmap
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FlacFallbackTest {
    @Test fun pcm16WaveCanConvertToNewLosslessFlacWithoutTouchingOriginal() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_FLAC, 44100, 1)
        assumeTrue("Device has no FLAC encoder",
            MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format) != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pcm = ByteArray(16384) { ((it * 23) % 256).toByte() }
        val wav = File.createTempFile("fallback-pcm-", ".wav", context.cacheDir)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcm.size)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(44100)
            putInt(88200)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
        }.array()
        wav.outputStream().use { it.write(header); it.write(pcm) }
        val pristine = wav.readBytes()
        val cover = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).let { bitmap ->
            bitmap.eraseColor(android.graphics.Color.rgb(40, 190, 130))
            try {
                ByteArrayOutputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it))
                    it.toByteArray()
                }
            } finally { bitmap.recycle() }
        }
        var created: Uri? = null
        try {
            val result = WavFlacFallback.convert(
                context, Uri.fromFile(wav),
                AudioEdits("fallback.flac", "", "", "", artwork = cover),
            ).getOrThrow()
            created = result.uri
            assertTrue(result.convertedFromWav)
            assertTrue(result.info.supported)
            assertEquals("FLAC", result.info.format)
            assertTrue(context.contentResolver.openInputStream(result.uri)?.use {
                val signature = ByteArray(4)
                it.read(signature)
                String(signature, Charsets.US_ASCII) == "fLaC"
            } == true)
            assertTrue(pristine.contentEquals(wav.readBytes()))
        } finally {
            created?.let { context.contentResolver.delete(it, null, null) }
            wav.delete()
        }
    }
}
