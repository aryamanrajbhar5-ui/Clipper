package com.example.data.ai

import com.example.data.model.AudioTrackItem
import com.example.data.model.CaptionBlock
import com.example.data.model.CaptionStylePreset
import com.example.data.model.CaptionWord
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

class GeminiEditingPlanService(
    private val httpClient: OkHttpClient
) : EditingPlanService {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun createAiEditingPlan(request: EditingPlanRequest): Result<TimelineState> = withContext(Dispatchers.IO) {
        val cleanKey = request.apiKey.trim()
        val clip = request.clip
        val clipDurationMs = clip.endMs - clip.startMs

        // Find transcript segments that fall inside this clip
        val clipTranscripts = request.transcriptSegments.filter {
            it.endMs >= clip.startMs && it.startMs <= clip.endMs
        }

        if (cleanKey.isNotBlank()) {
            val transcriptList = clipTranscripts.joinToString("\n") {
                val offsetStart = (it.startMs - clip.startMs).coerceAtLeast(0L)
                val offsetEnd = (it.endMs - clip.startMs).coerceAtMost(clipDurationMs)
                "[${offsetStart}ms - ${offsetEnd}ms]: ${it.text}"
            }

            val prompt = """
                You are an expert short-form video editor creating an automated 9:16 vertical editing plan for a viral clip.
                
                CLIP SPECIFICATIONS:
                - Title: "${clip.title}"
                - Duration: ${clipDurationMs}ms (${clipDurationMs / 1000} seconds)
                - Style: ${request.selectedStyle.name}
                - Dominant Framing: ${request.visualAnalysis.dominantFraming}
                - Motion: ${request.visualAnalysis.motionIntensity}

                EXTRACTED SPEECH SEGMENTS:
                $transcriptList

                Determine the precise editing cuts, zoom punches, and caption emphasis:
                1. Split the video into 3-5 visual segments to maintain high viewer retention.
                2. Apply zoom punches (1.15x - 1.35x) on key emphasis points or transitions (ZOOM_SNAP, WHITE_FLASH).
                3. Position the horizontal crop focus (0.0 left to 1.0 right, default 0.50 center).
                4. Create a catchy uppercase hook headline banner for the first 3-5 seconds.

                RESPOND STRICTLY with JSON:
                {
                  "headlineHook": "CATCHY VIRAL HEADLINE",
                  "segments": [
                    {
                      "startOffsetMs": 0,
                      "endOffsetMs": 4500,
                      "zoomScale": 1.25,
                      "transition": "ZOOM_SNAP",
                      "cropFocusX": 0.50
                    }
                  ],
                  "emphasisWords": ["crucial", "never", "secret", "money", "growth"]
                }
            """.trimIndent()

            val url = "https://generativelanguage.googleapis.com/v1beta/models/${request.model}:generateContent?key=$cleanKey"
            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        }
                        put("parts", parts)
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
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

                if (response.isSuccessful) {
                    val root = JSONObject(body)
                    val text = root.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text").orEmpty()

                    val cleanJson = text.trim()
                        .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

                    val obj = JSONObject(cleanJson)
                    val headline = obj.optString("headlineHook", clip.hookQuote.take(30).uppercase())
                    val segArray = obj.optJSONArray("segments")

                    val videoSegments = mutableListOf<VideoSegment>()
                    if (segArray != null && segArray.length() > 0) {
                        for (i in 0 until segArray.length()) {
                            val segObj = segArray.getJSONObject(i)
                            val startOffset = segObj.optLong("startOffsetMs", 0L)
                            val endOffset = segObj.optLong("endOffsetMs", clipDurationMs)
                            val zoom = segObj.optDouble("zoomScale", 1.0).toFloat().coerceIn(1.0f, 1.4f)
                            val transStr = segObj.optString("transition", "NONE")
                            val trans = try { VideoTransition.valueOf(transStr) } catch (_: Exception) { VideoTransition.NONE }
                            val focusX = segObj.optDouble("cropFocusX", 0.50).toFloat().coerceIn(0f, 1f)

                            videoSegments.add(
                                VideoSegment(
                                    id = UUID.randomUUID().toString(),
                                    sourceStartMs = clip.startMs + startOffset,
                                    sourceEndMs = (clip.startMs + endOffset).coerceAtMost(clip.endMs),
                                    zoomScale = zoom,
                                    transition = trans,
                                    cropFocusX = focusX
                                )
                            )
                        }
                    }

                    // Build dynamic captions from real transcript segments
                    val captions = buildCaptionsFromTranscripts(clipTranscripts, clip.startMs, request.selectedStyle)

                    if (videoSegments.isNotEmpty()) {
                        return@withContext Result.success(
                            TimelineState(
                                videoSegments = videoSegments,
                                captionBlocks = captions,
                                audioTracks = listOf(
                                    AudioTrackItem(
                                        title = "Original Audio (Boosted)",
                                        assetUrlOrName = "voice_master",
                                        startMs = 0L,
                                        endMs = clipDurationMs,
                                        volume = 1.0f,
                                        isDuckingEnabled = false
                                    )
                                ),
                                headlineHook = headline,
                                activeCaptionStyle = request.selectedStyle,
                                canvasRatio = "9:16",
                                masterVolume = 1.0f,
                                sourceVideoUri = clip.sourceVideoUri
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Fallback to deterministic algorithmic plan below
            }
        }

        // Deterministic algorithmic fallback plan based on actual clip timestamps and transcript
        val third = clipDurationMs / 3
        val segments = listOf(
            VideoSegment(
                id = UUID.randomUUID().toString(),
                sourceStartMs = clip.startMs,
                sourceEndMs = clip.startMs + third,
                zoomScale = 1.25f,
                transition = VideoTransition.ZOOM_SNAP,
                cropFocusX = 0.50f
            ),
            VideoSegment(
                id = UUID.randomUUID().toString(),
                sourceStartMs = clip.startMs + third,
                sourceEndMs = clip.startMs + (third * 2),
                zoomScale = 1.05f,
                transition = VideoTransition.NONE,
                cropFocusX = 0.48f
            ),
            VideoSegment(
                id = UUID.randomUUID().toString(),
                sourceStartMs = clip.startMs + (third * 2),
                sourceEndMs = clip.endMs,
                zoomScale = 1.18f,
                transition = VideoTransition.WHITE_FLASH,
                cropFocusX = 0.52f
            )
        )

        val captions = buildCaptionsFromTranscripts(clipTranscripts, clip.startMs, request.selectedStyle)

        Result.success(
            TimelineState(
                videoSegments = segments,
                captionBlocks = captions,
                audioTracks = listOf(
                    AudioTrackItem(
                        title = "Original Audio (Direct)",
                        assetUrlOrName = "voice_master",
                        startMs = 0L,
                        endMs = clipDurationMs,
                        volume = 1.0f,
                        isDuckingEnabled = false
                    )
                ),
                headlineHook = clip.title.uppercase(),
                activeCaptionStyle = request.selectedStyle,
                canvasRatio = "9:16",
                masterVolume = 1.0f,
                sourceVideoUri = clip.sourceVideoUri
            )
        )
    }

    private fun buildCaptionsFromTranscripts(
        transcripts: List<com.example.data.transcription.TranscriptSegment>,
        clipStartMs: Long,
        style: CaptionStylePreset
    ): List<CaptionBlock> {
        val captions = mutableListOf<CaptionBlock>()
        if (transcripts.isNotEmpty()) {
            transcripts.forEach { seg ->
                val start = (seg.startMs - clipStartMs).coerceAtLeast(0L)
                val end = (seg.endMs - clipStartMs).coerceAtLeast(start + 500L)
                val words = seg.text.split("\\s+".toRegex()).filter { it.isNotBlank() }
                val perWordMs = (end - start) / words.size.coerceAtLeast(1)

                val captionWords = words.mapIndexed { idx, word ->
                    CaptionWord(
                        word = word,
                        startOffsetMs = start + (idx * perWordMs),
                        endOffsetMs = start + ((idx + 1) * perWordMs),
                        isEmphasized = word.length > 5 || word.any { it.isDigit() || it == '$' || it == '%' }
                    )
                }

                captions.add(
                    CaptionBlock(
                        id = UUID.randomUUID().toString(),
                        text = seg.text,
                        startMs = start,
                        endMs = end,
                        words = captionWords,
                        stylePreset = style
                    )
                )
            }
        } else {
            // Standard default subtitle block
            captions.add(
                CaptionBlock(
                    id = UUID.randomUUID().toString(),
                    text = "Key takeaway from this discussion",
                    startMs = 0L,
                    endMs = 3000L,
                    words = listOf(
                        CaptionWord("Key", 0L, 500L),
                        CaptionWord("takeaway", 500L, 1200L, true),
                        CaptionWord("from", 1200L, 1600L),
                        CaptionWord("this", 1600L, 2000L),
                        CaptionWord("discussion", 2000L, 3000L, true)
                    ),
                    stylePreset = style
                )
            )
        }
        return captions
    }
}
