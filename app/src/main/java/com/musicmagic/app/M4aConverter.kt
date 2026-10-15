package com.musicmagic.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File

/** Converts supported audio into AAC-in-M4A using Android's platform decoder and encoder. */
object M4aConverter {
    fun convert(context: Context, source: Uri, target: Uri): Result<Long> = runCatching {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        val temp = File.createTempFile("musicmagic-aac-", ".m4a", context.cacheDir)
        try {
            context.contentResolver.openAssetFileDescriptor(source, "r")!!.use {
                extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("오디오 트랙이 없습니다.")
            extractor.selectTrack(track)
            val input = extractor.getTrackFormat(track)
            val rate = input.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = input.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            require(channels in 1..2) { "현재 모노·스테레오 오디오만 변환할 수 있습니다." }
            val mime = input.getString(MediaFormat.KEY_MIME)!!
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(input, null, null, 0)
            decoder.start()
            val output = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels)
            output.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            output.setInteger(MediaFormat.KEY_BIT_RATE, if (channels == 1) 96000 else 192000)
            output.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 262144)
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(output, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            muxer = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val decInfo = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            var decoderInputEnded = false
            var decoderEnded = false
            var encoderEnded = false
            var muxerTrack = -1
            var muxerStarted = false
            var pcmSamples = 0L
            while (!encoderEnded) {
                if (!decoderInputEnded) {
                    val index = decoder.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            decoderInputEnded = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                if (!decoderEnded) {
                    when (val index = decoder.dequeueOutputBuffer(decInfo, 10000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val fmt = decoder.outputFormat
                            val encoding = if (fmt.containsKey(MediaFormat.KEY_PCM_ENCODING))
                                fmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            else android.media.AudioFormat.ENCODING_PCM_16BIT
                            require(encoding == android.media.AudioFormat.ENCODING_PCM_16BIT) {
                                "디코더의 PCM 형식이 16비트가 아닙니다."
                            }
                            require(fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) == rate &&
                                fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == channels) {
                                "변환 중 샘플레이트 또는 채널 수가 변경되었습니다."
                            }
                        }
                        in 0..Int.MAX_VALUE -> {
                            val bytes = ByteArray(decInfo.size)
                            if (decInfo.size > 0) {
                                val buffer = decoder.getOutputBuffer(index)!!
                                buffer.position(decInfo.offset)
                                buffer.limit(decInfo.offset + decInfo.size)
                                buffer.get(bytes)
                            }
                            var offset = 0
                            while (offset < bytes.size) {
                                val inputIndex = encoder.dequeueInputBuffer(10000)
                                if (inputIndex < 0) continue
                                val buffer = encoder.getInputBuffer(inputIndex)!!
                                buffer.clear()
                                val size = minOf(buffer.remaining(), bytes.size - offset)
                                // PCM frames must not be split across channel/sample boundaries.
                                val aligned = size - size % (channels * 2)
                                require(aligned > 0) { "인코더 입력 버퍼가 너무 작습니다." }
                                buffer.put(bytes, offset, aligned)
                                encoder.queueInputBuffer(inputIndex, 0, aligned,
                                    pcmSamples * 1000000L / rate, 0)
                                pcmSamples += aligned / (channels * 2)
                                offset += aligned
                            }
                            decoderEnded = decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            decoder.releaseOutputBuffer(index, false)
                            if (decoderEnded) {
                                while (true) {
                                    val inputIndex = encoder.dequeueInputBuffer(10000)
                                    if (inputIndex >= 0) {
                                        encoder.queueInputBuffer(inputIndex, 0, 0,
                                            pcmSamples * 1000000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                        break
                                    }
                                    drainEncoder(encoder, muxer, encInfo, muxerTrack, muxerStarted)
                                }
                            }
                        }
                    }
                }
                when (val index = encoder.dequeueOutputBuffer(encInfo, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "중복 출력 포맷" }
                        muxerTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    in 0..Int.MAX_VALUE -> {
                        if (encInfo.size > 0) {
                            check(muxerStarted) { "M4A 출력 트랙이 준비되지 않았습니다." }
                            val buffer = encoder.getOutputBuffer(index)!!
                            buffer.position(encInfo.offset)
                            buffer.limit(encInfo.offset + encInfo.size)
                            muxer.writeSampleData(muxerTrack, buffer, encInfo)
                        }
                        encoderEnded = encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(index, false)
                    }
                }
            }
            check(muxerStarted && temp.length() >= 0) { "M4A 파일 생성 실패" }
            muxer.stop()
            muxer.release()
            muxer = null
            context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                temp.inputStream().use { it.copyTo(out) }
            } ?: error("M4A 파일 저장 실패")
            temp.length()
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { muxer?.release() }
            extractor.release()
            temp.delete()
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        info: MediaCodec.BufferInfo,
        track: Int,
        started: Boolean
    ) {
        val index = encoder.dequeueOutputBuffer(info, 10000)
        if (index >= 0) {
            if (info.size > 0 && started) {
                val buffer = encoder.getOutputBuffer(index)!!
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                muxer.writeSampleData(track, buffer, info)
            }
            encoder.releaseOutputBuffer(index, false)
        }
    }
}
