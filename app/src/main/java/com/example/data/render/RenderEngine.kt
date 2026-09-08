package com.example.data.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.view.Surface
import com.example.data.audio.AudioProcessor
import com.example.data.model.CaptionBlock
import com.example.data.model.ExportJob
import com.example.data.model.ExportStatus
import com.example.data.model.TimelineState
import com.example.data.model.VideoSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Real MediaCodec & MediaMuxer hardware video and audio export engine.
 * 
 * Pipeline:
 * 1. Inspect source video for real audio tracks.
 * 2. If source video contains audio, extract and trim original audio matching exact timeline segment boundaries.
 * 3. Transcode source video frames using MediaCodec (H.264/AVC) to vertical 9:16 canvas with smart reframing,
 *    zoom punches, hook banners, and burned styled captions.
 * 4. Interleave and mux video + original audio into final MP4 using MediaMuxer.
 * 5. Strict post-export validation: ensures file exists, size > 0, contains video track, contains audio track,
 *    and durations match expected clip bounds.
 * 6. Safely cleans up intermediate temporary files and reports failures explicitly.
 */
class RenderEngine(private val context: Context) {

    suspend fun renderMp4(
        jobId: String = UUID.randomUUID().toString(),
        projectId: String,
        clipTitle: String,
        timeline: TimelineState,
        sourceUriOrPath: String = "",
        resolution: String = "1080x1920",
        fps: Int = 30,
        onProgress: suspend (status: ExportStatus, progress: Float, outputPath: String, fileSize: Long) -> Unit
    ): ExportJob = withContext(Dispatchers.IO) {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(exportDir, "clipper_${jobId}.mp4")
        val tempVideoFile = File(exportDir, "temp_video_${jobId}.mp4")
        val tempAudioFile = File(exportDir, "temp_audio_${jobId}.m4a")

        try {
            onProgress(ExportStatus.ANALYZING, 0.05f, "", 0L)

            // Resolve effective source video
            val effectiveSource = when {
                sourceUriOrPath.isNotBlank() -> sourceUriOrPath
                timeline.sourceVideoUri.isNotBlank() -> timeline.sourceVideoUri
                else -> ""
            }

            if (effectiveSource.isBlank()) {
                throw IllegalStateException("No source video provided for export rendering.")
            }

            // Verify source video readability
            val retriever = MediaMetadataRetriever()
            try {
                if (effectiveSource.startsWith("content://")) {
                    val uri = Uri.parse(effectiveSource)
                    retriever.setDataSource(context, uri)
                } else {
                    val file = File(effectiveSource)
                    if (!file.exists()) {
                        throw IllegalStateException("Source video file does not exist on disk: $effectiveSource")
                    }
                    retriever.setDataSource(file.absolutePath)
                }
            } catch (e: Exception) {
                try { retriever.release() } catch (_: Exception) {}
                throw IllegalStateException("Source video could not be opened: ${e.message}", e)
            }

            val (targetWidth, targetHeight) = when {
                resolution.contains("1080") -> Pair(1080, 1920)
                resolution.contains("720") -> Pair(720, 1280)
                else -> Pair(720, 1280)
            }
            val effectiveFps = fps.coerceIn(24, 60)

            // Ensure timeline has video segments
            val segments = timeline.videoSegments
            if (segments.isEmpty()) {
                try { retriever.release() } catch (_: Exception) {}
                throw IllegalStateException("Timeline contains no video segments to render.")
            }

            val totalDurationMs = timeline.totalDurationMs.coerceAtLeast(500L)

            // 1. Inspect source video for real audio
            val sourceHasAudio = AudioProcessor.hasAudioTrack(context, effectiveSource)

            // 2. If source video contains audio, extract and trim original audio for segments
            if (sourceHasAudio) {
                onProgress(ExportStatus.SMART_CROPPING, 0.15f, "", 0L)
                val audioExtracted = AudioProcessor.extractAndTrimAudio(
                    context = context,
                    videoUriOrPath = effectiveSource,
                    segments = segments,
                    outputAudioFile = tempAudioFile
                )
                if (!audioExtracted || !tempAudioFile.exists() || tempAudioFile.length() == 0L) {
                    if (tempAudioFile.exists()) tempAudioFile.delete()
                    try { retriever.release() } catch (_: Exception) {}
                    throw IllegalStateException("Audio pipeline failed: could not extract or trim original audio from source video.")
                }
            }

            onProgress(ExportStatus.BURNING_CAPTIONS, 0.25f, "", 0L)

            // 3. Transcode real video frames to temp video container
            val renderVideoSuccess = renderNativeVideoFrames(
                context = context,
                retriever = retriever,
                timeline = timeline,
                outputFile = tempVideoFile,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                fps = effectiveFps,
                totalDurationMs = totalDurationMs
            ) { progressStep ->
                val mappedProgress = 0.25f + (progressStep * 0.60f)
                val status = if (progressStep < 0.5f) ExportStatus.BURNING_CAPTIONS else ExportStatus.ENCODING_MP4
                onProgress(status, mappedProgress, "", 0L)
            }

            if (!renderVideoSuccess || !tempVideoFile.exists() || tempVideoFile.length() == 0L) {
                if (tempVideoFile.exists()) tempVideoFile.delete()
                if (tempAudioFile.exists()) tempAudioFile.delete()
                throw IllegalStateException("Video transcode pipeline failed to produce valid video frames.")
            }

            // 4. Mux video and original audio into final MP4
            onProgress(ExportStatus.ENCODING_MP4, 0.88f, "", 0L)
            if (sourceHasAudio) {
                val muxSuccess = AudioProcessor.muxVideoAndAudio(
                    videoInputFile = tempVideoFile,
                    audioInputFile = tempAudioFile,
                    outputFile = outputFile
                )
                if (tempVideoFile.exists()) tempVideoFile.delete()
                if (tempAudioFile.exists()) tempAudioFile.delete()
                if (!muxSuccess || !outputFile.exists() || outputFile.length() == 0L) {
                    if (outputFile.exists()) outputFile.delete()
                    throw IllegalStateException("Muxing pipeline failed: unable to combine video and audio streams into final MP4.")
                }
            } else {
                tempVideoFile.copyTo(outputFile, overwrite = true)
                tempVideoFile.delete()
            }

            // 5. Strict validation of exported MP4 container
            onProgress(ExportStatus.ENCODING_MP4, 0.95f, "", 0L)
            AudioProcessor.validateExportedMp4(
                outputFile = outputFile,
                expectedDurationMs = totalDurationMs,
                sourceHadAudio = sourceHasAudio
            )

            val finalSize = outputFile.length()
            onProgress(ExportStatus.COMPLETED, 1.0f, outputFile.absolutePath, finalSize)

            ExportJob(
                id = jobId,
                projectId = projectId,
                clipTitle = clipTitle,
                status = ExportStatus.COMPLETED,
                progress = 1.0f,
                resolution = resolution,
                fps = effectiveFps,
                fileSizeBytes = finalSize,
                outputFilePath = outputFile.absolutePath,
                createdAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            if (outputFile.exists()) {
                try { outputFile.delete() } catch (_: Exception) {}
            }
            if (tempVideoFile.exists()) {
                try { tempVideoFile.delete() } catch (_: Exception) {}
            }
            if (tempAudioFile.exists()) {
                try { tempAudioFile.delete() } catch (_: Exception) {}
            }
            onProgress(ExportStatus.FAILED, 0f, "", 0L)
            ExportJob(
                id = jobId,
                projectId = projectId,
                clipTitle = clipTitle,
                status = ExportStatus.FAILED,
                progress = 0f,
                resolution = resolution,
                fps = fps,
                fileSizeBytes = 0L,
                outputFilePath = "",
                createdAt = System.currentTimeMillis()
            )
        }
    }

    private suspend fun renderNativeVideoFrames(
        context: Context,
        retriever: MediaMetadataRetriever,
        timeline: TimelineState,
        outputFile: File,
        targetWidth: Int,
        targetHeight: Int,
        fps: Int,
        totalDurationMs: Long,
        onProgressUpdate: suspend (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var muxer: MediaMuxer? = null
        var encoder: MediaCodec? = null
        var inputSurface: Surface? = null

        try {
            val bitRate = when (targetWidth) {
                1080 -> 8_000_000 // 8 Mbps for 1080p
                else -> 4_000_000 // 4 Mbps for 720p
            }
            val totalFrames = ((totalDurationMs * fps) / 1000L).toInt().coerceAtLeast(fps)

            // Setup H.264 Video Encoder
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetWidth, targetHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()

            // Paint styles for burn-in
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = if (targetWidth >= 1080) 48f else 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E6000000")
                style = Paint.Style.FILL
            }
            val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FACC15")
                textSize = if (targetWidth >= 1080) 42f else 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val bannerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(220, 15, 23, 42)
                style = Paint.Style.FILL
            }

            // Encode frames sequentially
            var frameCount = 0
            while (frameCount < totalFrames) {
                val currentTimelinePtsMs = (frameCount * 1000L) / fps

                // Map current timeline PTS to source segment and source timestamp
                var accumulatedMs = 0L
                var activeSegment: VideoSegment? = null
                var sourcePtsMs = 0L

                for (seg in timeline.videoSegments) {
                    val segDur = seg.durationMs
                    if (currentTimelinePtsMs in accumulatedMs..(accumulatedMs + segDur)) {
                        activeSegment = seg
                        val offsetIntoSegment = currentTimelinePtsMs - accumulatedMs
                        sourcePtsMs = (seg.sourceStartMs + offsetIntoSegment).coerceAtMost(seg.sourceEndMs)
                        break
                    }
                    accumulatedMs += segDur
                }

                if (activeSegment == null && timeline.videoSegments.isNotEmpty()) {
                    val lastSeg = timeline.videoSegments.last()
                    activeSegment = lastSeg
                    sourcePtsMs = lastSeg.sourceEndMs
                }

                // Lock hardware canvas on encoder surface
                val canvas: Canvas = inputSurface.lockHardwareCanvas()

                // 1. Extract and render genuine source video frame
                var frameDrawn = false
                try {
                    val frameBitmap = retriever.getFrameAtTime(
                        sourcePtsMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    if (frameBitmap != null) {
                        val bWidth = frameBitmap.width
                        val bHeight = frameBitmap.height

                        // Smart 9:16 crop calculation with cropFocusX
                        val desiredCropWidth = (bHeight * 9f / 16f).toInt().coerceAtMost(bWidth)
                        val focusX = activeSegment?.cropFocusX ?: 0.5f
                        val maxLeft = (bWidth - desiredCropWidth).coerceAtLeast(0)
                        val left = (maxLeft * focusX).toInt().coerceIn(0, maxLeft)
                        val srcRect = Rect(left, 0, left + desiredCropWidth, bHeight)
                        val dstRect = Rect(0, 0, targetWidth, targetHeight)

                        val zoom = activeSegment?.zoomScale ?: 1.0f
                        if (zoom > 1.01f) {
                            canvas.save()
                            canvas.scale(zoom, zoom, targetWidth / 2f, targetHeight / 2f)
                            canvas.drawBitmap(frameBitmap, srcRect, dstRect, null)
                            canvas.restore()
                        } else {
                            canvas.drawBitmap(frameBitmap, srcRect, dstRect, null)
                        }
                        frameBitmap.recycle()
                        frameDrawn = true
                    }
                } catch (_: Throwable) {}

                if (!frameDrawn) {
                    inputSurface.unlockCanvasAndPost(canvas)
                    throw IllegalStateException("Failed to decode source video frame at timestamp ${sourcePtsMs}ms.")
                }

                // 2. Render Headline Hook banner if set
                if (timeline.headlineHook.isNotBlank()) {
                    val bannerRect = RectF(
                        40f,
                        if (targetWidth >= 1080) 80f else 60f,
                        (targetWidth - 40).toFloat(),
                        if (targetWidth >= 1080) 180f else 130f
                    )
                    canvas.drawRoundRect(bannerRect, 16f, 16f, bannerBgPaint)
                    canvas.drawText(
                        "🔥 ${timeline.headlineHook.uppercase()} 🔥",
                        targetWidth / 2f,
                        if (targetWidth >= 1080) 145f else 105f,
                        bannerPaint
                    )
                }

                // 3. Render burned captions matching current timeline PTS
                val activeCaption: CaptionBlock? = timeline.captionBlocks.firstOrNull { block ->
                    currentTimelinePtsMs in block.startMs..block.endMs
                }
                if (activeCaption != null) {
                    val captionY = if (targetWidth >= 1080) (targetHeight - 320).toFloat() else (targetHeight - 220).toFloat()
                    val rectTop = captionY - (if (targetWidth >= 1080) 60f else 45f)
                    val rectBottom = captionY + (if (targetWidth >= 1080) 40f else 30f)
                    val capRect = RectF(50f, rectTop, (targetWidth - 50).toFloat(), rectBottom)

                    // Apply caption preset color styling
                    val style = activeCaption.stylePreset
                    val textColor = try { Color.parseColor(style.textColorHex) } catch (_: Exception) { Color.WHITE }
                    val bgColor = try { Color.parseColor(style.backgroundColorHex) } catch (_: Exception) { Color.parseColor("#E6000000") }

                    bgPaint.color = bgColor
                    textPaint.color = textColor

                    canvas.drawRoundRect(capRect, 18f, 18f, bgPaint)
                    canvas.drawText(activeCaption.text, targetWidth / 2f, captionY, textPaint)
                }

                inputSurface.unlockCanvasAndPost(canvas)

                // Drain encoder output buffers
                var outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                while (outputIndex >= 0) {
                    val encodedData = encoder.getOutputBuffer(outputIndex)
                    if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        if (!muxerStarted) {
                            val newFormat = encoder.outputFormat
                            videoTrackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        if (muxerStarted && bufferInfo.size > 0) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        }
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                }

                frameCount++
                if (frameCount % 15 == 0) {
                    val p = frameCount.toFloat() / totalFrames
                    onProgressUpdate(p)
                }
            }

            // Signal end of stream
            encoder.signalEndOfInputStream()

            // Drain remaining buffers
            var eos = false
            while (!eos) {
                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        eos = true
                    }
                    val encodedData = encoder.getOutputBuffer(outputIndex)
                    if (encodedData != null && muxerStarted && bufferInfo.size > 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { retriever.release() } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { inputSurface?.release() } catch (_: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
    }
}
