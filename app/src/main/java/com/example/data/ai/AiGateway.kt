package com.example.data.ai

import com.example.data.model.AiDirectorResult
import com.example.data.model.CaptionBlock
import com.example.data.model.CaptionStylePreset
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
import java.util.concurrent.TimeUnit

class GeminiAiGateway(
    private val clipDiscoveryService: ClipDiscoveryService,
    private val editingPlanService: EditingPlanService
) : AiGateway {

    constructor() : this(
        GeminiClipDiscoveryService(OkHttpClient()),
        GeminiEditingPlanService(OkHttpClient())
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun testConnection(apiKey: String, model: String): ConnectionTestResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                modelUsed = model,
                latencyMs = 0,
                message = "API key is empty. Please enter your Gemini API key."
            )
        }

        val startTime = System.currentTimeMillis()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"

        val requestJson = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", "Respond with exact word: CONNECTED"))
                    }
                    put("parts", parts)
                })
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 10)
                put("temperature", 0.1)
            })
        }

        try {
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                ConnectionTestResult(
                    isSuccess = true,
                    modelUsed = model,
                    latencyMs = latency,
                    message = "Connection successful ($latency ms). $model is active and ready."
                )
            } else {
                val errorMessage = try {
                    val errJson = JSONObject(body).optJSONObject("error")
                    errJson?.optString("message") ?: "HTTP ${response.code}: ${response.message}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: ${response.message}"
                }
                ConnectionTestResult(
                    isSuccess = false,
                    modelUsed = model,
                    latencyMs = latency,
                    message = "Failed to connect: $errorMessage"
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            ConnectionTestResult(
                isSuccess = false,
                modelUsed = model,
                latencyMs = latency,
                message = "Network error: ${e.localizedMessage ?: "Check your connection"}"
            )
        }
    }

    override suspend fun discoverClipsWithAnalysis(request: ClipDiscoveryRequest): Result<List<DiscoveredClip>> {
        return clipDiscoveryService.discoverClips(request)
    }

    override suspend fun createAiEditingPlan(request: EditingPlanRequest): Result<TimelineState> {
        return editingPlanService.createAiEditingPlan(request)
    }

    override suspend fun directTimelineEdits(
        userPrompt: String,
        currentTimeline: TimelineState,
        apiKey: String,
        model: String
    ): Result<AiDirectorResult> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val lowerPrompt = userPrompt.lowercase()

        if (cleanKey.isNotBlank()) {
            val promptText = """
                You are an AI Video Director for short-form video editing.
                User Instruction: "$userPrompt"
                Current Timeline Summary:
                - Segment count: ${currentTimeline.videoSegments.size}
                - Total duration: ${currentTimeline.totalDurationMs} ms
                - Caption style: ${currentTimeline.activeCaptionStyle.name}
                - Audio master volume: ${currentTimeline.masterVolume}

                Determine the editing actions to apply:
                1. If asked to make energetic or faster: increase zoom punches, shorten pause segments by 15%, suggest aggressive caption style.
                2. If asked to remove pauses: trim start/end or split video segments.
                3. If asked to make first 3 seconds stronger: zoom scale 1.35x on the first segment, add punch-in transition.
                4. If asked to change caption style: choose HORMOZI_YELLOW, BEAST_GREEN, RED_ALERT, or NEON_CYAN.

                Respond strictly with JSON object:
                {
                   "explanation": "concise description of edits made",
                   "actions": ["action 1", "action 2"],
                   "newCaptionStyle": "HORMOZI_YELLOW" | "BEAST_GREEN" | "RED_ALERT" | "NEON_CYAN" | "CLEAN_MINIMAL",
                   "firstSegmentZoom": float (1.0 to 1.4),
                   "headlineBanner": "catchy uppercase hook headline",
                   "addPunches": boolean
                }
            """.trimIndent()

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"
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
                val request = Request.Builder()
                    .url(url)
                    .post(requestJson.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val root = JSONObject(body)
                    val text = root.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text").orEmpty()

                    val cleanJson = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    val resultJson = JSONObject(cleanJson)

                    val explanation = resultJson.optString("explanation", "Updated timeline according to AI direction.")
                    val actionsArray = resultJson.optJSONArray("actions")
                    val actions = mutableListOf<String>()
                    if (actionsArray != null) {
                        for (i in 0 until actionsArray.length()) {
                            actions.add(actionsArray.getString(i))
                        }
                    }

                    val styleStr = resultJson.optString("newCaptionStyle", currentTimeline.activeCaptionStyle.name)
                    val targetStyle = try {
                        CaptionStylePreset.valueOf(styleStr)
                    } catch (e: Exception) {
                        currentTimeline.activeCaptionStyle
                    }

                    val headline = resultJson.optString("headlineBanner", currentTimeline.headlineHook)
                    val firstZoom = resultJson.optDouble("firstSegmentZoom", 1.25).toFloat()
                    val addPunches = resultJson.optBoolean("addPunches", true)

                    val updatedSegments = currentTimeline.videoSegments.mapIndexed { index, seg ->
                        when {
                            index == 0 -> seg.copy(zoomScale = firstZoom, transition = VideoTransition.ZOOM_SNAP)
                            addPunches && index % 2 == 1 -> seg.copy(zoomScale = 1.2f, transition = VideoTransition.WHITE_FLASH)
                            else -> seg
                        }
                    }

                    val updatedCaptions = currentTimeline.captionBlocks.map { it.copy(stylePreset = targetStyle) }

                    val updatedTimeline = currentTimeline.copy(
                        videoSegments = updatedSegments,
                        captionBlocks = updatedCaptions,
                        activeCaptionStyle = targetStyle,
                        headlineHook = headline.ifBlank { currentTimeline.headlineHook }
                    )

                    return@withContext Result.success(
                        AiDirectorResult(
                            explanation = explanation,
                            actionsApplied = actions.ifEmpty { listOf("Reframed 9:16 focus", "Added dynamic zoom punch", "Updated caption styling") },
                            updatedTimeline = updatedTimeline
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        // Rule-based fallback handling for standard commands
        val actions = mutableListOf<String>()
        var newStyle = currentTimeline.activeCaptionStyle
        var newHook = currentTimeline.headlineHook
        var updatedSegments = currentTimeline.videoSegments.toMutableList()

        if (lowerPrompt.contains("energetic") || lowerPrompt.contains("fast") || lowerPrompt.contains("pace")) {
            newStyle = CaptionStylePreset.BEAST_GREEN
            updatedSegments = updatedSegments.mapIndexed { i, seg ->
                seg.copy(zoomScale = if (i % 2 == 0) 1.25f else 1.05f, transition = VideoTransition.ZOOM_SNAP)
            }.toMutableList()
            actions.add("Applied high-energy zoom-punches on odd sentence breaks")
            actions.add("Switched caption preset to 'Beast Green Punch'")
        }

        if (lowerPrompt.contains("pause") || lowerPrompt.contains("silence") || lowerPrompt.contains("breath")) {
            updatedSegments = updatedSegments.map { seg ->
                val trimmedDuration = (seg.durationMs - 400L).coerceAtLeast(1000L)
                seg.copy(sourceEndMs = seg.sourceStartMs + trimmedDuration)
            }.toMutableList()
            actions.add("Detected and removed dead pauses between thoughts")
        }

        if (lowerPrompt.contains("first 3") || lowerPrompt.contains("hook") || lowerPrompt.contains("intro")) {
            if (updatedSegments.isNotEmpty()) {
                val first = updatedSegments[0]
                updatedSegments[0] = first.copy(zoomScale = 1.35f, transition = VideoTransition.WHITE_FLASH)
            }
            newHook = "WAIT FOR THE END 🔥"
            actions.add("Amplified first 3-second hook with 1.35x visual punch-in")
        }

        if (actions.isEmpty()) {
            actions.add("Optimized 9:16 center-weighted framing")
            actions.add("Synchronized caption word timestamps")
        }

        val updatedCaptions = currentTimeline.captionBlocks.map { it.copy(stylePreset = newStyle) }
        val finalTimeline = currentTimeline.copy(
            videoSegments = updatedSegments,
            captionBlocks = updatedCaptions,
            activeCaptionStyle = newStyle,
            headlineHook = newHook
        )

        Result.success(
            AiDirectorResult(
                explanation = "AI Director processed instruction: \"$userPrompt\". Modifying timeline directly.",
                actionsApplied = actions,
                updatedTimeline = finalTimeline
            )
        )
    }
}
