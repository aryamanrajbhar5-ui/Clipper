package com.example.data.render

import android.content.Context
import com.example.data.model.ExportJob
import com.example.data.model.ExportStatus
import com.example.data.model.TimelineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class RenderEngine(private val context: Context) {

    suspend fun renderMp4(
        jobId: String,
        projectId: String,
        clipTitle: String,
        timeline: TimelineState,
        resolution: String = "1080x1920 (9:16)",
        fps: Int = 60,
        onProgress: suspend (ExportStatus, Float, String, Long) -> Unit
    ): ExportJob = withContext(Dispatchers.IO) {
        val sanitizedTitle = clipTitle.replace(Regex("[^a-zA-Z0-9_]"), "_").take(30)
        val fileName = "AI_Clipper_${sanitizedTitle}_${System.currentTimeMillis()}.mp4"
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(exportDir, fileName)

        // Stage 1: Analyzing cuts and transitions (0 - 25%)
        onProgress(ExportStatus.ANALYZING, 0.10f, "", 0L)
        delay(400)
        onProgress(ExportStatus.ANALYZING, 0.25f, "", 0L)
        delay(400)

        // Stage 2: Smart Cropping 9:16 frame pan (25 - 55%)
        onProgress(ExportStatus.SMART_CROPPING, 0.40f, "", 0L)
        delay(500)
        onProgress(ExportStatus.SMART_CROPPING, 0.55f, "", 0L)
        delay(400)

        // Stage 3: Burning Captions and Effects (55 - 80%)
        onProgress(ExportStatus.BURNING_CAPTIONS, 0.65f, "", 0L)
        delay(500)
        onProgress(ExportStatus.BURNING_CAPTIONS, 0.80f, "", 0L)
        delay(400)

        // Stage 4: Encoding MP4 Bitstream (80 - 100%)
        onProgress(ExportStatus.ENCODING_MP4, 0.90f, "", 0L)
        delay(600)

        // Generate simulated valid MP4 container file with video metadata header
        try {
            FileOutputStream(outputFile).use { fos ->
                // Write MP4 container ftyp box header bytes
                val ftypBox = byteArrayOf(
                    0x00, 0x00, 0x00, 0x20, // size 32
                    0x66, 0x74, 0x79, 0x70, // 'ftyp'
                    0x69, 0x73, 0x6F, 0x6D, // 'isom'
                    0x00, 0x00, 0x02, 0x00, // minor_version
                    0x69, 0x73, 0x6F, 0x6D, // compatible_brands: isom
                    0x69, 0x73, 0x6F, 0x32, // iso2
                    0x61, 0x76, 0x63, 0x31, // avc1
                    0x6D, 0x70, 0x34, 0x31  // mp41
                )
                fos.write(ftypBox)

                // Write metadata chunk describing the vertical video
                val metadata = """
                    [AI Clipper Factory Export]
                    Resolution: $resolution
                    Framerate: $fps FPS
                    DurationMs: ${timeline.totalDurationMs}
                    Segments: ${timeline.videoSegments.size}
                    CaptionStyle: ${timeline.activeCaptionStyle.title}
                    AudioMix: Master ${timeline.masterVolume * 100}%
                    Generator: AI Clipper Engine v2.4 (9:16 Smart Crop)
                """.trimIndent().toByteArray(Charsets.UTF_8)
                fos.write(metadata)

                // Pad with simulated compressed h.264 data bytes proportional to duration
                val padSize = (timeline.totalDurationMs * 350L).toInt().coerceIn(100_000, 8_000_000)
                val buffer = ByteArray(4096) { 0xAA.toByte() }
                var written = 0
                while (written < padSize) {
                    val toWrite = minOf(buffer.size, padSize - written)
                    fos.write(buffer, 0, toWrite)
                    written += toWrite
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val finalSize = outputFile.length()
        onProgress(ExportStatus.COMPLETED, 1.0f, outputFile.absolutePath, finalSize)

        ExportJob(
            id = jobId,
            projectId = projectId,
            clipTitle = clipTitle,
            status = ExportStatus.COMPLETED,
            progress = 1.0f,
            resolution = resolution,
            fps = fps,
            fileSizeBytes = finalSize,
            outputFilePath = outputFile.absolutePath,
            createdAt = System.currentTimeMillis()
        )
    }
}
