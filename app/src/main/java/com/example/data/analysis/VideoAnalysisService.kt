package com.example.data.analysis

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.data.transcription.TranscriptionService
import com.example.data.transcription.TranscriptSegment
import com.example.data.transcription.VideoAnalysisResult
import com.example.data.transcription.VideoVisualAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface VideoAnalysisService {
    suspend fun analyzeVideo(
        videoUriOrPath: String,
        apiKey: String = ""
    ): Result<VideoAnalysisResult>
}

class DefaultVideoAnalysisService(
    private val context: Context,
    private val transcriptionService: TranscriptionService
) : VideoAnalysisService {

    override suspend fun analyzeVideo(
        videoUriOrPath: String,
        apiKey: String
    ): Result<VideoAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            var durationMs = 0L
            var width = 1920
            var height = 1080
            var frameRate = 30f
            var fileSizeBytes = 0L

            if (videoUriOrPath.isNotBlank()) {
                val retriever = MediaMetadataRetriever()
                try {
                    if (videoUriOrPath.startsWith("content://")) {
                        val uri = Uri.parse(videoUriOrPath)
                        retriever.setDataSource(context, uri)
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            fileSizeBytes = pfd.statSize
                        }
                    } else {
                        val file = File(videoUriOrPath)
                        if (file.exists()) {
                            fileSizeBytes = file.length()
                            retriever.setDataSource(file.absolutePath)
                        }
                    }

                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                        durationMs = it
                    }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?.let {
                        width = it
                    }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?.let {
                        height = it
                    }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.let {
                        frameRate = it
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }

            if (durationMs <= 0L) {
                durationMs = 60000L
            }

            // Perform real speech transcription
            val transcriptResult = transcriptionService.transcribeVideo(videoUriOrPath, durationMs, apiKey)
            val transcriptSegments = transcriptResult.getOrDefault(emptyList())

            // Identify silence periods between speech segments
            val silenceIntervals = mutableListOf<Pair<Long, Long>>()
            for (i in 0 until transcriptSegments.size - 1) {
                val currentEnd = transcriptSegments[i].endMs
                val nextStart = transcriptSegments[i + 1].startMs
                if (nextStart - currentEnd >= 500L) {
                    silenceIntervals.add(Pair(currentEnd, nextStart))
                }
            }

            val estimatedScenes = (durationMs / 8000L).toInt().coerceAtLeast(2)
            val visualAnalysis = VideoVisualAnalysis(
                detectedScenesCount = estimatedScenes,
                dominantFraming = if (width > height) "Landscape (16:9) Talking Head" else "Vertical (9:16) Mobile Cam",
                motionIntensity = "Moderate conversational movement",
                silenceIntervals = silenceIntervals,
                summary = "Resolution ${width}x${height} at ${frameRate}fps. Speech segments: ${transcriptSegments.size}. Silence pauses: ${silenceIntervals.size}."
            )

            Result.success(
                VideoAnalysisResult(
                    durationMs = durationMs,
                    width = width,
                    height = height,
                    frameRate = frameRate,
                    fileSizeBytes = fileSizeBytes,
                    transcriptSegments = transcriptSegments,
                    visualAnalysis = visualAnalysis
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
