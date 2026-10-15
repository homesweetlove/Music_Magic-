package com.musicmagic.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real audio decoding to 16-bit PCM WAV. Never renames a compressed stream as WAV. */
object WavConverter {
    fun convert(context: Context, source: Uri, target: Uri): Result<Long> = runCatching {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        val temp = File.createTempFile("musicmagic-pcm-", ".wav", context.cacheDir)
        try {
            context.contentResolver.openAssetFileDescriptor(source, "r")!!.use { afd ->
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("오디오 트랙을 찾지 못했습니다.")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("알 수 없는 코덱")
            val codec = MediaCodec.createDecoderByType(mime)
            decoder = codec
            codec.configure(format, null, null, 0)
            codec.start()
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var totalPcm = 0L
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()
            RandomAccessFile(temp, "rw").use { out ->
                out.write(ByteArray(44))
                while (!outputDone) {
                    if (!inputDone) {
                        val index = codec.dequeueInputBuffer(10000)
                        if (index >= 0) {
                            val buf = codec.getInputBuffer(index)!!
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val index = codec.dequeueOutputBuffer(info, 10000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outputFormat = codec.outputFormat
                            sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else android.media.AudioFormat.ENCODING_PCM_16BIT
                            require(pcmEncoding == android.media.AudioFormat.ENCODING_PCM_16BIT) {
                                "16-bit PCM으로 디코딩할 수 없는 형식입니다."
                            }
                        }
                        in 0..Int.MAX_VALUE -> {
                            if (info.size > 0) {
                                val buf = codec.getOutputBuffer(index)!!
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size)
                                buf.get(bytes)
                                out.write(bytes)
                                totalPcm += bytes.size
                                require(totalPcm <= 2_000_000_000L) { "WAV 파일 크기 제한을 초과했습니다." }
                            }
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(index, false)
                        }
                    }
                }
                val byteRate = sampleRate * channels * 2
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray(Charsets.US_ASCII))
                header.putInt((36 + totalPcm).toInt())
                header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
                header.putInt(16)
                header.putShort(1.toShort())
                header.putShort(channels.toShort())
                header.putInt(sampleRate)
                header.putInt(byteRate)
                header.putShort((channels * 2).toShort())
                header.putShort(16.toShort())
                header.put("data".toByteArray(Charsets.US_ASCII))
                header.putInt(totalPcm.toInt())
                out.seek(0)
                out.write(header.array())
            }
            context.contentResolver.openOutputStream(target, "wt")?.use { dest ->
                temp.inputStream().use { it.copyTo(dest) }
            } ?: error("WAV 저장 위치에 쓸 수 없습니다.")
            totalPcm + 44
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            extractor.release()
            temp.delete()
        }
    }
}
