package com.example.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.example.data.model.VideoSegment
import java.io.File
import java.nio.ByteBuffer

/**
 * AudioProcessor handles genuine Android-native audio operations:
 * 1. Inspecting real audio streams from imported video sources.
 * 2. Extracting the full audio stream for speech-to-text transcription.
 * 3. Extracting and trimming the original audio matching exact timeline segment boundaries.
 * 4. Muxing H.264 video and AAC audio into final playable MP4 containers.
 * 5. Strict post-export validation of video/audio tracks and durations.
 */
object AudioProcessor {

    fun hasAudioTrack(context: Context, videoUriOrPath: String): Boolean {
        if (videoUriOrPath.isBlank()) return false
        val extractor = MediaExtractor()
        return try {
            setExtractorDataSource(context, extractor, videoUriOrPath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) return true
            }
            false
        } catch (_: Exception) {
            false
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    fun extractFullAudio(context: Context, videoUriOrPath: String, outputAudioFile: File): Boolean {
        if (videoUriOrPath.isBlank()) return false
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            setExtractorDataSource(context, extractor, videoUriOrPath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return false
            }

            extractor.selectTrack(audioTrackIndex)
            muxer = MediaMuxer(outputAudioFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024)
            } else {
                64 * 1024
            }
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var firstPtsUs = -1L

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (firstPtsUs == -1L) {
                    firstPtsUs = sampleTimeUs
                }
                bufferInfo.presentationTimeUs = (sampleTimeUs - firstPtsUs).coerceAtLeast(0L)
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                extractor.advance()
            }

            outputAudioFile.exists() && outputAudioFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { extractor.release() } catch (_: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
    }

    fun extractAndTrimAudio(
        context: Context,
        videoUriOrPath: String,
        segments: List<VideoSegment>,
        outputAudioFile: File
    ): Boolean {
        if (videoUriOrPath.isBlank() || segments.isEmpty()) return false
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            setExtractorDataSource(context, extractor, videoUriOrPath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return false
            }

            extractor.selectTrack(audioTrackIndex)
            muxer = MediaMuxer(outputAudioFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024)
            } else {
                64 * 1024
            }
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            var accumulatedDurationUs = 0L

            for (seg in segments) {
                val segStartUs = seg.sourceStartMs * 1000L
                val segEndUs = seg.sourceEndMs * 1000L
                if (segEndUs <= segStartUs) continue

                extractor.seekTo(segStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                var segFirstPtsUs = -1L
                var lastWrittenPtsUs = -1L

                while (true) {
                    val sampleTimeUs = extractor.sampleTime
                    if (sampleTimeUs < 0 || sampleTimeUs > segEndUs) {
                        break
                    }

                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break

                    if (sampleTimeUs >= segStartUs || segFirstPtsUs == -1L) {
                        if (segFirstPtsUs == -1L) {
                            segFirstPtsUs = sampleTimeUs
                        }
                        val relativeUs = (sampleTimeUs - segFirstPtsUs).coerceAtLeast(0L)
                        val ptsUs = accumulatedDurationUs + relativeUs

                        // Ensure strictly non-decreasing monotonic timestamps for MediaMuxer
                        val safePtsUs = if (ptsUs > lastWrittenPtsUs) ptsUs else (lastWrittenPtsUs + 1L)
                        bufferInfo.presentationTimeUs = safePtsUs
                        bufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                        lastWrittenPtsUs = safePtsUs
                    }

                    if (!extractor.advance()) break
                }

                accumulatedDurationUs += (seg.durationMs * 1000L)
            }

            outputAudioFile.exists() && outputAudioFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { extractor.release() } catch (_: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
    }

    fun muxVideoAndAudio(videoInputFile: File, audioInputFile: File, outputFile: File): Boolean {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        return try {
            videoExtractor.setDataSource(videoInputFile.absolutePath)
            audioExtractor.setDataSource(audioInputFile.absolutePath)

            var videoTrack = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val f = videoExtractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrack = i
                    videoFormat = f
                    break
                }
            }

            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val f = audioExtractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    audioFormat = f
                    break
                }
            }

            if (videoTrack == -1 || videoFormat == null || audioTrack == -1 || audioFormat == null) {
                return false
            }

            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val videoBuffer = ByteBuffer.allocate(1024 * 1024)
            val audioBuffer = ByteBuffer.allocate(256 * 1024)
            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()

            var videoEos = false
            var audioEos = false

            while (!videoEos || !audioEos) {
                val vTime = if (!videoEos) videoExtractor.sampleTime else Long.MAX_VALUE
                val aTime = if (!audioEos) audioExtractor.sampleTime else Long.MAX_VALUE

                if (!videoEos && (vTime <= aTime || audioEos)) {
                    videoBufferInfo.offset = 0
                    videoBufferInfo.size = videoExtractor.readSampleData(videoBuffer, 0)
                    if (videoBufferInfo.size >= 0) {
                        videoBufferInfo.presentationTimeUs = videoExtractor.sampleTime
                        videoBufferInfo.flags = videoExtractor.sampleFlags
                        muxer.writeSampleData(muxerVideoTrack, videoBuffer, videoBufferInfo)
                        videoExtractor.advance()
                    } else {
                        videoEos = true
                    }
                } else if (!audioEos) {
                    audioBufferInfo.offset = 0
                    audioBufferInfo.size = audioExtractor.readSampleData(audioBuffer, 0)
                    if (audioBufferInfo.size >= 0) {
                        audioBufferInfo.presentationTimeUs = audioExtractor.sampleTime
                        audioBufferInfo.flags = audioExtractor.sampleFlags
                        muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioBufferInfo)
                        audioExtractor.advance()
                    } else {
                        audioEos = true
                    }
                }
            }

            outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { videoExtractor.release() } catch (_: Exception) {}
            try { audioExtractor.release() } catch (_: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
    }

    fun setExtractorDataSource(context: Context, extractor: MediaExtractor, uriOrPath: String) {
        if (uriOrPath.startsWith("content://")) {
            val uri = Uri.parse(uriOrPath)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: throw IllegalArgumentException("Cannot open content URI: $uriOrPath")
        } else {
            val file = File(uriOrPath)
            if (!file.exists()) {
                throw IllegalArgumentException("Source video file does not exist: $uriOrPath")
            }
            extractor.setDataSource(file.absolutePath)
        }
    }

    fun setRetrieverDataSource(context: Context, retriever: MediaMetadataRetriever, uriOrPath: String) {
        if (uriOrPath.startsWith("content://")) {
            val uri = Uri.parse(uriOrPath)
            retriever.setDataSource(context, uri)
        } else {
            val file = File(uriOrPath)
            if (!file.exists()) {
                throw IllegalArgumentException("Source video file does not exist: $uriOrPath")
            }
            retriever.setDataSource(file.absolutePath)
        }
    }

    fun validateExportedMp4(
        outputFile: File,
        expectedDurationMs: Long,
        sourceHadAudio: Boolean
    ) {
        if (!outputFile.exists()) {
            throw IllegalStateException("Validation failed: Export output file does not exist.")
        }
        if (outputFile.length() <= 0L) {
            throw IllegalStateException("Validation failed: Export output file is 0 bytes.")
        }

        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        try {
            retriever.setDataSource(outputFile.absolutePath)
            extractor.setDataSource(outputFile.absolutePath)

            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            if (hasVideo != "yes") {
                throw IllegalStateException("Validation failed: Exported MP4 is missing video track.")
            }

            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            if (sourceHadAudio && hasAudio != "yes") {
                throw IllegalStateException("Validation failed: Exported MP4 is missing audio track despite source containing audio.")
            }

            var videoTrackFound = false
            var audioTrackFound = false

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackFound = true
                } else if (mime.startsWith("audio/")) {
                    audioTrackFound = true
                }
            }

            if (!videoTrackFound) {
                throw IllegalStateException("Validation failed: Extractor found no video track in output file.")
            }
            if (sourceHadAudio && !audioTrackFound) {
                throw IllegalStateException("Validation failed: Extractor found no audio track in output file.")
            }

            val durationMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val overallDurationMs = durationMsStr?.toLongOrNull() ?: 0L
            if (overallDurationMs <= 0L) {
                throw IllegalStateException("Validation failed: Output MP4 has invalid duration ($overallDurationMs ms).")
            }

            val diffMs = kotlin.math.abs(overallDurationMs - expectedDurationMs)
            if (diffMs > 5000L && expectedDurationMs > 5000L) {
                throw IllegalStateException("Validation failed: Output MP4 duration ($overallDurationMs ms) diverges from expected ($expectedDurationMs ms).")
            }
        } finally {
            try { retriever.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}
