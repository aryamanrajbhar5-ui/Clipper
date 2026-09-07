package com.example.data.ai

import com.example.data.model.AiDirectorResult
import com.example.data.model.CaptionStylePreset
import com.example.data.model.DiscoveredClip
import com.example.data.model.PlatformTarget
import com.example.data.model.TimelineState
import com.example.data.transcription.TranscriptSegment
import com.example.data.transcription.VideoVisualAnalysis

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val modelUsed: String,
    val latencyMs: Long,
    val message: String
)

data class ClipDiscoveryRequest(
    val projectId: String,
    val projectTitle: String,
    val videoTitle: String,
    val videoDurationSec: Int,
    val videoUri: String,
    val targetPlatform: PlatformTarget,
    val transcriptSegments: List<TranscriptSegment>,
    val visualAnalysis: VideoVisualAnalysis,
    val userPrompt: String,
    val apiKey: String,
    val model: String
)

data class EditingPlanRequest(
    val clip: DiscoveredClip,
    val transcriptSegments: List<TranscriptSegment>,
    val visualAnalysis: VideoVisualAnalysis,
    val selectedStyle: CaptionStylePreset,
    val apiKey: String,
    val model: String
)

/**
 * Service for discovering candidate viral clips using real transcript and visual data
 */
interface ClipDiscoveryService {
    suspend fun discoverClips(request: ClipDiscoveryRequest): Result<List<DiscoveredClip>>
}

/**
 * Service for generating structured AI editing instructions
 */
interface EditingPlanService {
    suspend fun createAiEditingPlan(request: EditingPlanRequest): Result<TimelineState>
}

/**
 * Centralized AI Gateway orchestrating specialized AI services.
 */
interface AiGateway {
    suspend fun testConnection(apiKey: String, model: String): ConnectionTestResult
    suspend fun discoverClipsWithAnalysis(request: ClipDiscoveryRequest): Result<List<DiscoveredClip>>
    suspend fun createAiEditingPlan(request: EditingPlanRequest): Result<TimelineState>
    suspend fun directTimelineEdits(
        userPrompt: String,
        currentTimeline: TimelineState,
        apiKey: String,
        model: String
    ): Result<AiDirectorResult>
}
