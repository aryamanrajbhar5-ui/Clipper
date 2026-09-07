package com.example.data.ai

import com.example.data.model.CaptionBlock
import com.example.data.model.CaptionStylePreset
import com.example.data.model.CaptionWord
import com.example.data.model.DiscoveredClip
import com.example.data.model.TimelineState
import com.example.data.model.VideoSegment
import com.example.data.model.VideoTransition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class GeminiClipDiscoveryService(
    private val httpClient: OkHttpClient
) : ClipDiscoveryService {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun discoverClips(request: ClipDiscoveryRequest): Result<List<DiscoveredClip>> = withContext(Dispatchers.IO) {
        val cleanKey = request.apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is required for AI clip discovery. Please set your BYOK key in Settings."))
        }

        // Prepare structured timestamped transcript representation
        val transcriptFormatted = StringBuilder()
        request.transcriptSegments.forEach { seg ->
            val startSec = seg.startMs / 1000.0
            val endSec = seg.endMs / 1000.0
            transcriptFormatted.append(String.format("[%.1fs - %.1fs]: %s\n", startSec, endSec, seg.text))
        }

        val videoDurationMs = request.videoDurationSec * 1000L
        val promptText = """
            You are an elite short-form video editor and viral content strategist analyzing a long-form video.
            
            REAL VIDEO MEDIA INFORMATION:
            - Title: "${request.videoTitle}"
            - Project Context: "${request.projectTitle}"
            - Total Duration: ${request.videoDurationSec} seconds (${videoDurationMs} ms)
            - Visual Scene Analysis: ${request.visualAnalysis.summary}
            - Dominant Framing: ${request.visualAnalysis.dominantFraming}
            - Motion Intensity: ${request.visualAnalysis.motionIntensity}
            - Target Platform: ${request.targetPlatform.displayName} (${request.targetPlatform.aspectRatio}, ideal: ${request.targetPlatform.idealDurationSec}s)
            - User Custom Instructions: "${request.userPrompt.ifBlank { "Find the top 3-4 most viral, engaging clips with strong opening hooks and high retention." }}"

            TIMESTAMPED AUDIO TRANSCRIPT EXTRACTED FROM MEDIA:
            ${if (transcriptFormatted.isNotBlank()) transcriptFormatted.toString() else "Transcript pending audio stream analysis."}

            TASK:
            Select the top 3-4 candidate viral clips from the timestamped transcript.
            
            RULES & VALIDATION:
            1. "startMs" must be >= 0 and correspond to a strong opening sentence in the transcript.
            2. "endMs" must be > startMs and <= $videoDurationMs.
            3. Clip duration must be between 15000ms (15s) and 60000ms (60s).
            4. Scores ("viralScore", "hookStrength", "retentionPotential", "standaloneScore") must be integers strictly between 0 and 100.
            5. "hookQuote" must be the exact opening 1-2 sentences from the transcript.
            6. "recommendedStyle" must be one of ["HORMOZI_YELLOW", "BEAST_GREEN", "RED_ALERT", "NEON_CYAN", "CLEAN_MINIMAL"].

            RESPOND STRICTLY with a valid JSON array of objects. Do NOT include markdown code fences or explanatory text.
            Example schema:
            [
              {
                "startMs": 12000,
                "endMs": 42000,
                "title": "Clear Punchy Title",
                "hookQuote": "Exact sentence from transcript...",
                "topic": "Strategy",
                "viralScore": 92,
                "hookStrength": 94,
                "retentionPotential": 89,
                "standaloneScore": 91,
                "aiExplanation": "Why this moment hooks the viewer and holds retention.",
                "recommendedStyle": "HORMOZI_YELLOW"
              }
            ]
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/${request.model}:generateContent?key=$cleanKey"
        val requestJson = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", promptText))
                    }
                    put("parts", parts)
                })
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("responseMimeType", "application/json")
            })
        }

        try {
            val httpRequest = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errMessage = try {
                    JSONObject(body).optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (_: Exception) {
                    "HTTP ${response.code}: ${response.message}"
                }
                return@withContext Result.failure(IllegalStateException("Gemini API error ($errMessage)"))
            }

            val root = JSONObject(body)
            val candidateText = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text").orEmpty()

            val cleanJson = candidateText.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            val array = JSONArray(cleanJson)
            val clips = mutableListOf<DiscoveredClip>()

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val rawStart = item.optLong("startMs", -1L)
                val rawEnd = item.optLong("endMs", -1L)

                // Validate timestamps strictly according to requirements:
                // start >= 0, end > start, end <= duration
                if (rawStart < 0L || rawEnd <= rawStart) continue
                val start = rawStart.coerceIn(0L, videoDurationMs)
                val end = rawEnd.coerceIn(start + 5000L, videoDurationMs)

                val viralScore = item.optInt("viralScore", 85).coerceIn(0, 100)
                val hookStrength = item.optInt("hookStrength", 85).coerceIn(0, 100)
                val retentionPotential = item.optInt("retentionPotential", 85).coerceIn(0, 100)
                val standaloneScore = item.optInt("standaloneScore", 85).coerceIn(0, 100)

                val styleStr = item.optString("recommendedStyle", "HORMOZI_YELLOW")
                val style = try {
                    CaptionStylePreset.valueOf(styleStr)
                } catch (_: Exception) {
                    CaptionStylePreset.HORMOZI_YELLOW
                }

                clips.add(
                    DiscoveredClip(
                        id = UUID.randomUUID().toString(),
                        projectId = request.projectId,
                        title = item.optString("title", "High-Impact Short #${i + 1}"),
                        startMs = start,
                        endMs = end,
                        viralScore = viralScore,
                        hookStrength = hookStrength,
                        retentionPotential = retentionPotential,
                        standaloneScore = standaloneScore,
                        topic = item.optString("topic", "Key Insight"),
                        aiExplanation = item.optString("aiExplanation", "Identified through transcript pace & vocal emphasis."),
                        hookQuote = item.optString("hookQuote", "Important realization about scale."),
                        recommendedStyle = style,
                        thumbnailGradientIndex = i % 4,
                        sourceVideoUri = request.videoUri
                    )
                )
            }

            if (clips.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Gemini returned no clips matching the duration requirements."))
            }

            Result.success(clips)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
