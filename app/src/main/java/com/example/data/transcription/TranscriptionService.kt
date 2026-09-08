package com.example.data.transcription

/**
 * Clean data model for timestamped speech segments.
 */
data class TranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float = 0.95f
)

data class VideoVisualAnalysis(
    val detectedScenesCount: Int = 0,
    val dominantFraming: String = "Medium Close-Up (Speaker Focused)",
    val motionIntensity: String = "Moderate",
    val silenceIntervals: List<Pair<Long, Long>> = emptyList(),
    val summary: String = ""
)

data class VideoAnalysisResult(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val fileSizeBytes: Long,
    val transcriptSegments: List<TranscriptSegment>,
    val visualAnalysis: VideoVisualAnalysis
)

/**
 * Modular interface for speech transcription services.
 * Easily switchable or extensible to Whisper, Google Cloud Speech, Gemini Audio, or on-device engines.
 */
interface TranscriptionService {
    suspend fun transcribeVideo(
        videoUriOrPath: String,
        durationMs: Long,
        apiKey: String = "",
        model: String = "gemini-2.5-flash"
    ): Result<List<TranscriptSegment>>
}
