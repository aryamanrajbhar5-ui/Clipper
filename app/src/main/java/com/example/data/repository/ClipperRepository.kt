package com.example.data.repository

import android.content.Context
import com.example.data.ai.AiGateway
import com.example.data.ai.ClipDiscoveryRequest
import com.example.data.ai.ConnectionTestResult
import com.example.data.ai.EditingPlanRequest
import com.example.data.ai.GeminiAiGateway
import com.example.data.ai.GeminiClipDiscoveryService
import com.example.data.ai.GeminiEditingPlanService
import com.example.data.analysis.DefaultVideoAnalysisService
import com.example.data.analysis.VideoAnalysisService
import com.example.data.local.AppDatabase
import com.example.data.local.ClipEntity
import com.example.data.local.ExportJobEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.TimelineEntity
import com.example.data.model.AiDirectorResult
import com.example.data.model.AudioTrackItem
import com.example.data.model.CaptionBlock
import com.example.data.model.CaptionStylePreset
import com.example.data.model.CaptionWord
import com.example.data.model.CreatorProject
import com.example.data.model.DiscoveredClip
import com.example.data.model.ExportJob
import com.example.data.model.ExportStatus
import com.example.data.model.PlatformTarget
import com.example.data.model.StockAsset
import com.example.data.model.StyleTemplate
import com.example.data.model.TimelineState
import com.example.data.model.VideoSegment
import com.example.data.model.VideoTransition
import com.example.data.render.RenderEngine
import com.example.data.security.ApiKeyStore
import com.example.data.transcription.DefaultTranscriptionService
import com.example.data.transcription.TranscriptionService
import com.example.data.transcription.VideoAnalysisResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ClipperRepository(private val context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val apiKeyStore = ApiKeyStore(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val transcriptionService: TranscriptionService = DefaultTranscriptionService(context)
    private val videoAnalysisService: VideoAnalysisService = DefaultVideoAnalysisService(context, transcriptionService)

    private val clipDiscoveryService = GeminiClipDiscoveryService(httpClient)
    private val editingPlanService = GeminiEditingPlanService(httpClient)
    private val aiGateway: AiGateway = GeminiAiGateway(clipDiscoveryService, editingPlanService)
    private val renderEngine = RenderEngine(context)

    // Cache of real media analysis by video URI or project ID
    private val analysisCache = ConcurrentHashMap<String, VideoAnalysisResult>()

    // BYOK API Key Methods
    fun getUserApiKey(): String = apiKeyStore.getUserApiKey()
    fun saveUserApiKey(key: String) = apiKeyStore.saveUserApiKey(key)
    fun clearUserApiKey() = apiKeyStore.clearUserApiKey()
    fun hasCustomUserKey(): Boolean = apiKeyStore.hasCustomUserKey()
    fun isKeyConfigured(): Boolean = apiKeyStore.isConfigured()
    fun getMaskedKey(): String = apiKeyStore.getMaskedKey()
    fun getSelectedModel(): String = apiKeyStore.getSelectedModel()
    fun setSelectedModel(model: String) = apiKeyStore.setSelectedModel(model)

    suspend fun testConnection(key: String = getUserApiKey(), model: String = getSelectedModel()): ConnectionTestResult {
        return aiGateway.testConnection(key, model)
    }

    // Projects
    fun getAllProjects(): Flow<List<CreatorProject>> {
        return database.projectDao().getAllProjects().map { list ->
            list.map { entity ->
                CreatorProject(
                    id = entity.id,
                    title = entity.title,
                    videoTitle = entity.videoTitle,
                    videoDurationSec = entity.videoDurationSec,
                    targetPlatform = try {
                        PlatformTarget.valueOf(entity.targetPlatform)
                    } catch (e: Exception) {
                        PlatformTarget.TIKTOK
                    },
                    clipsCount = entity.clipsCount,
                    status = entity.status,
                    videoUri = entity.videoUri,
                    createdAt = entity.createdAt
                )
            }
        }
    }

    suspend fun createProject(
        title: String,
        videoTitle: String,
        durationSec: Int,
        targetPlatform: PlatformTarget,
        videoUri: String = ""
    ): CreatorProject {
        val projectId = UUID.randomUUID().toString()
        val project = CreatorProject(
            id = projectId,
            title = title,
            videoTitle = videoTitle,
            videoDurationSec = durationSec,
            targetPlatform = targetPlatform,
            clipsCount = 0,
            status = "ANALYZING",
            videoUri = videoUri,
            createdAt = System.currentTimeMillis()
        )
        database.projectDao().insertProject(
            ProjectEntity(
                id = project.id,
                title = project.title,
                videoTitle = project.videoTitle,
                videoDurationSec = project.videoDurationSec,
                targetPlatform = project.targetPlatform.name,
                clipsCount = 0,
                status = project.status,
                videoUri = project.videoUri,
                createdAt = project.createdAt
            )
        )
        return project
    }

    suspend fun deleteProject(projectId: String) {
        database.clipDao().deleteClipsForProject(projectId)
        database.projectDao().deleteProject(projectId)
    }

    // Clips
    fun getClipsForProject(projectId: String): Flow<List<DiscoveredClip>> {
        return database.clipDao().getClipsForProject(projectId).map { list ->
            list.map { entity ->
                DiscoveredClip(
                    id = entity.id,
                    projectId = entity.projectId,
                    title = entity.title,
                    startMs = entity.startMs,
                    endMs = entity.endMs,
                    viralScore = entity.viralScore,
                    hookStrength = entity.hookStrength,
                    retentionPotential = entity.retentionPotential,
                    standaloneScore = entity.standaloneScore,
                    topic = entity.topic,
                    aiExplanation = entity.aiExplanation,
                    hookQuote = entity.hookQuote,
                    recommendedStyle = try {
                        CaptionStylePreset.valueOf(entity.recommendedStyle)
                    } catch (e: Exception) {
                        CaptionStylePreset.HORMOZI_YELLOW
                    },
                    thumbnailGradientIndex = entity.thumbnailGradientIndex,
                    sourceVideoUri = entity.sourceVideoUri
                )
            }
        }
    }

    suspend fun scanVideoForClips(
        project: CreatorProject,
        userPrompt: String = ""
    ): Result<List<DiscoveredClip>> {
        val key = getUserApiKey()
        val model = getSelectedModel()

        // 1. Get or compute real media analysis & timestamped transcript
        val cacheKey = if (project.videoUri.isNotBlank()) project.videoUri else project.id
        val cachedAnalysis = analysisCache[cacheKey]
        val analysis = if (cachedAnalysis != null) {
            cachedAnalysis
        } else {
            val analysisRes = videoAnalysisService.analyzeVideo(project.videoUri, key)
            if (analysisRes.isFailure) {
                return Result.failure(
                    analysisRes.exceptionOrNull() ?: IllegalStateException("Failed to analyze media stream")
                )
            }
            val res = analysisRes.getOrThrow()
            analysisCache[cacheKey] = res
            res
        }

        // 2. Formulate discovery request with genuine timestamped transcript & visual information
        val discoveryRequest = ClipDiscoveryRequest(
            projectId = project.id,
            projectTitle = project.title,
            videoTitle = project.videoTitle,
            videoDurationSec = (analysis.durationMs / 1000).toInt().coerceAtLeast(project.videoDurationSec),
            videoUri = project.videoUri,
            targetPlatform = project.targetPlatform,
            transcriptSegments = analysis.transcriptSegments,
            visualAnalysis = analysis.visualAnalysis,
            userPrompt = userPrompt,
            apiKey = key,
            model = model
        )

        val result = aiGateway.discoverClipsWithAnalysis(discoveryRequest)

        result.onSuccess { clips ->
            val entities = clips.map { clip ->
                ClipEntity(
                    id = clip.id,
                    projectId = clip.projectId,
                    title = clip.title,
                    startMs = clip.startMs,
                    endMs = clip.endMs,
                    viralScore = clip.viralScore,
                    hookStrength = clip.hookStrength,
                    retentionPotential = clip.retentionPotential,
                    standaloneScore = clip.standaloneScore,
                    topic = clip.topic,
                    aiExplanation = clip.aiExplanation,
                    hookQuote = clip.hookQuote,
                    recommendedStyle = clip.recommendedStyle.name,
                    thumbnailGradientIndex = clip.thumbnailGradientIndex,
                    sourceVideoUri = project.videoUri
                )
            }
            database.clipDao().deleteClipsForProject(project.id)
            database.clipDao().insertClips(entities)
            database.projectDao().updateClipsCount(project.id, clips.size)
        }

        return result
    }

    // Timeline Persistence & Initial AI Plan Generation
    suspend fun getOrCreateTimelineForClip(clip: DiscoveredClip): TimelineState {
        val stored = database.timelineDao().getTimeline(clip.id)
        if (stored != null) {
            val loaded = deserializeTimeline(stored.jsonContent)
            // Ensure sourceVideoUri is preserved if not in older saved timeline
            return if (loaded.sourceVideoUri.isBlank() && clip.sourceVideoUri.isNotBlank()) {
                loaded.copy(sourceVideoUri = clip.sourceVideoUri)
            } else {
                loaded
            }
        }

        // Generate AI editing plan for the clip using actual transcript and visual analysis
        val key = getUserApiKey()
        val model = getSelectedModel()
        val cacheKey = if (clip.sourceVideoUri.isNotBlank()) clip.sourceVideoUri else clip.projectId
        val analysis = analysisCache[cacheKey]

        val initialTimelineResult = aiGateway.createAiEditingPlan(
            EditingPlanRequest(
                clip = clip,
                transcriptSegments = analysis?.transcriptSegments ?: emptyList(),
                visualAnalysis = analysis?.visualAnalysis ?: com.example.data.transcription.VideoVisualAnalysis(
                    detectedScenesCount = 3,
                    dominantFraming = "9:16 Reframe",
                    motionIntensity = "Moderate",
                    summary = "Source video clip segment"
                ),
                selectedStyle = clip.recommendedStyle,
                apiKey = key,
                model = model
            )
        )

        val initialTimeline = initialTimelineResult.getOrElse {
            createFallbackEditingPlan(clip)
        }.copy(sourceVideoUri = clip.sourceVideoUri)

        saveTimeline(clip.id, clip.projectId, initialTimeline)
        return initialTimeline
    }

    suspend fun saveTimeline(clipId: String, projectId: String, timeline: TimelineState) {
        val json = serializeTimeline(timeline)
        database.timelineDao().saveTimeline(
            TimelineEntity(
                clipId = clipId,
                projectId = projectId,
                jsonContent = json,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }

    // AI Director
    suspend fun directTimelineEdits(userPrompt: String, currentTimeline: TimelineState): Result<AiDirectorResult> {
        val key = getUserApiKey()
        val model = getSelectedModel()
        return aiGateway.directTimelineEdits(userPrompt, currentTimeline, key, model)
    }

    // Export Jobs
    fun getAllExportJobs(): Flow<List<ExportJob>> {
        return database.exportJobDao().getAllJobs().map { list ->
            list.map { entity ->
                ExportJob(
                    id = entity.id,
                    projectId = entity.projectId,
                    clipTitle = entity.clipTitle,
                    status = try {
                        ExportStatus.valueOf(entity.status)
                    } catch (e: Exception) {
                        ExportStatus.COMPLETED
                    },
                    progress = entity.progress,
                    resolution = entity.resolution,
                    fps = entity.fps,
                    fileSizeBytes = entity.fileSizeBytes,
                    outputFilePath = entity.outputFilePath,
                    createdAt = entity.createdAt
                )
            }
        }
    }

    suspend fun startExport(
        projectId: String,
        clipTitle: String,
        timeline: TimelineState,
        sourceUriOrPath: String = "",
        resolution: String = "1080x1920 (9:16)",
        fps: Int = 30,
        onProgress: suspend (ExportStatus, Float) -> Unit
    ): ExportJob {
        val jobId = UUID.randomUUID().toString()
        val initialJob = ExportJobEntity(
            id = jobId,
            projectId = projectId,
            clipTitle = clipTitle,
            status = ExportStatus.QUEUED.name,
            progress = 0f,
            resolution = resolution,
            fps = fps,
            fileSizeBytes = 0L,
            outputFilePath = "",
            createdAt = System.currentTimeMillis()
        )
        database.exportJobDao().insertJob(initialJob)

        return renderEngine.renderMp4(
            jobId = jobId,
            projectId = projectId,
            clipTitle = clipTitle,
            timeline = timeline,
            sourceUriOrPath = sourceUriOrPath.ifBlank { timeline.sourceVideoUri },
            resolution = resolution,
            fps = fps
        ) { status, progress, outputPath, fileSize ->
            database.exportJobDao().updateJobProgress(
                jobId = jobId,
                status = status.name,
                progress = progress,
                outputPath = outputPath,
                fileSize = fileSize
            )
            onProgress(status, progress)
        }
    }

    private fun createFallbackEditingPlan(clip: DiscoveredClip): TimelineState {
        val duration = clip.endMs - clip.startMs
        val thirdDuration = duration / 3

        val segments = listOf(
            VideoSegment(
                id = UUID.randomUUID().toString(),
                sourceStartMs = clip.startMs,
                sourceEndMs = clip.startMs + thirdDuration,
                zoomScale = 1.25f,
                transition = VideoTransition.ZOOM_SNAP,
                cropFocusX = 0.5f
            ),
            VideoSegment(
                id = UUID.randomUUID().toString(),
                sourceStartMs = clip.startMs + thirdDuration,
                sourceEndMs = clip.startMs + (thirdDuration * 2),
                zoomScale = 1.05f,
                transition = VideoTransition.NONE,
                cropFocusX = 0.48f
            ),
            VideoSegment(
                id = UUID.randomUUID().toString(),
                sourceStartMs = clip.startMs + (thirdDuration * 2),
                sourceEndMs = clip.endMs,
                zoomScale = 1.18f,
                transition = VideoTransition.WHITE_FLASH,
                cropFocusX = 0.52f
            )
        )

        val rawWords = clip.hookQuote.replace("\"", "").split(" ")
        val captions = mutableListOf<CaptionBlock>()
        val wordsPerBlock = 5
        var currentOffset = 0L
        val blockDuration = 2400L

        rawWords.chunked(wordsPerBlock).forEach { chunk ->
            val chunkText = chunk.joinToString(" ")
            val blockStart = currentOffset
            val blockEnd = currentOffset + blockDuration
            val wordDuration = blockDuration / chunk.size.coerceAtLeast(1)

            val words = chunk.mapIndexed { wIdx, word ->
                CaptionWord(
                    word = word,
                    startOffsetMs = blockStart + (wIdx * wordDuration),
                    endOffsetMs = blockStart + ((wIdx + 1) * wordDuration),
                    isEmphasized = word.length > 5 || word.contains("$") || word.contains("%")
                )
            }

            captions.add(
                CaptionBlock(
                    id = UUID.randomUUID().toString(),
                    text = chunkText,
                    startMs = blockStart,
                    endMs = blockEnd,
                    words = words,
                    stylePreset = clip.recommendedStyle
                )
            )
            currentOffset += blockDuration + 100L
        }

        return TimelineState(
            videoSegments = segments,
            captionBlocks = captions,
            audioTracks = listOf(
                AudioTrackItem(
                    title = "Original Audio",
                    assetUrlOrName = "voice",
                    startMs = 0L,
                    endMs = duration,
                    volume = 1.0f,
                    isDuckingEnabled = false
                )
            ),
            headlineHook = clip.title.uppercase(),
            activeCaptionStyle = clip.recommendedStyle,
            canvasRatio = "9:16",
            masterVolume = 1.0f,
            sourceVideoUri = clip.sourceVideoUri
        )
    }

    // JSON Serialization for Timeline
    private fun serializeTimeline(timeline: TimelineState): String {
        val root = JSONObject().apply {
            put("headlineHook", timeline.headlineHook)
            put("activeCaptionStyle", timeline.activeCaptionStyle.name)
            put("canvasRatio", timeline.canvasRatio)
            put("masterVolume", timeline.masterVolume.toDouble())
            put("sourceVideoUri", timeline.sourceVideoUri)

            val segArray = JSONArray()
            timeline.videoSegments.forEach { seg ->
                segArray.put(
                    JSONObject().apply {
                        put("id", seg.id)
                        put("sourceStartMs", seg.sourceStartMs)
                        put("sourceEndMs", seg.sourceEndMs)
                        put("zoomScale", seg.zoomScale.toDouble())
                        put("transition", seg.transition.name)
                        put("cropFocusX", seg.cropFocusX.toDouble())
                    }
                )
            }
            put("videoSegments", segArray)

            val capArray = JSONArray()
            timeline.captionBlocks.forEach { cap ->
                val wordsArray = JSONArray()
                cap.words.forEach { w ->
                    wordsArray.put(
                        JSONObject().apply {
                            put("word", w.word)
                            put("startOffsetMs", w.startOffsetMs)
                            put("endOffsetMs", w.endOffsetMs)
                            put("isEmphasized", w.isEmphasized)
                        }
                    )
                }
                capArray.put(
                    JSONObject().apply {
                        put("id", cap.id)
                        put("text", cap.text)
                        put("startMs", cap.startMs)
                        put("endMs", cap.endMs)
                        put("stylePreset", cap.stylePreset.name)
                        put("words", wordsArray)
                    }
                )
            }
            put("captionBlocks", capArray)

            val audioArray = JSONArray()
            timeline.audioTracks.forEach { aud ->
                audioArray.put(
                    JSONObject().apply {
                        put("id", aud.id)
                        put("title", aud.title)
                        put("assetUrlOrName", aud.assetUrlOrName)
                        put("startMs", aud.startMs)
                        put("endMs", aud.endMs)
                        put("volume", aud.volume.toDouble())
                        put("isDuckingEnabled", aud.isDuckingEnabled)
                    }
                )
            }
            put("audioTracks", audioArray)
        }
        return root.toString()
    }

    private fun deserializeTimeline(jsonStr: String): TimelineState {
        return try {
            val root = JSONObject(jsonStr)
            val headline = root.optString("headlineHook", "")
            val stylePresetStr = root.optString("activeCaptionStyle", "HORMOZI_YELLOW")
            val stylePreset = try {
                CaptionStylePreset.valueOf(stylePresetStr)
            } catch (e: Exception) {
                CaptionStylePreset.HORMOZI_YELLOW
            }
            val ratio = root.optString("canvasRatio", "9:16")
            val masterVol = root.optDouble("masterVolume", 1.0).toFloat()
            val sourceVideoUri = root.optString("sourceVideoUri", "")

            val segArray = root.optJSONArray("videoSegments")
            val segments = mutableListOf<VideoSegment>()
            if (segArray != null) {
                for (i in 0 until segArray.length()) {
                    val obj = segArray.getJSONObject(i)
                    segments.add(
                        VideoSegment(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            sourceStartMs = obj.getLong("sourceStartMs"),
                            sourceEndMs = obj.getLong("sourceEndMs"),
                            zoomScale = obj.optDouble("zoomScale", 1.0).toFloat(),
                            transition = try {
                                VideoTransition.valueOf(obj.optString("transition", "NONE"))
                            } catch (e: Exception) {
                                VideoTransition.NONE
                            },
                            cropFocusX = obj.optDouble("cropFocusX", 0.5).toFloat()
                        )
                    )
                }
            }

            val capArray = root.optJSONArray("captionBlocks")
            val captions = mutableListOf<CaptionBlock>()
            if (capArray != null) {
                for (i in 0 until capArray.length()) {
                    val obj = capArray.getJSONObject(i)
                    val wordsList = mutableListOf<CaptionWord>()
                    val wordsArr = obj.optJSONArray("words")
                    if (wordsArr != null) {
                        for (w in 0 until wordsArr.length()) {
                            val wObj = wordsArr.getJSONObject(w)
                            wordsList.add(
                                CaptionWord(
                                    word = wObj.getString("word"),
                                    startOffsetMs = wObj.getLong("startOffsetMs"),
                                    endOffsetMs = wObj.getLong("endOffsetMs"),
                                    isEmphasized = wObj.optBoolean("isEmphasized", false)
                                )
                            )
                        }
                    }
                    captions.add(
                        CaptionBlock(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            text = obj.getString("text"),
                            startMs = obj.getLong("startMs"),
                            endMs = obj.getLong("endMs"),
                            words = wordsList,
                            stylePreset = try {
                                CaptionStylePreset.valueOf(obj.optString("stylePreset", stylePresetStr))
                            } catch (e: Exception) {
                                stylePreset
                            }
                        )
                    )
                }
            }

            val audArray = root.optJSONArray("audioTracks")
            val audioList = mutableListOf<AudioTrackItem>()
            if (audArray != null) {
                for (i in 0 until audArray.length()) {
                    val obj = audArray.getJSONObject(i)
                    audioList.add(
                        AudioTrackItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.getString("title"),
                            assetUrlOrName = obj.optString("assetUrlOrName", "bgm"),
                            startMs = obj.getLong("startMs"),
                            endMs = obj.getLong("endMs"),
                            volume = obj.optDouble("volume", 0.6).toFloat(),
                            isDuckingEnabled = obj.optBoolean("isDuckingEnabled", true)
                        )
                    )
                }
            }

            TimelineState(
                videoSegments = segments,
                captionBlocks = captions,
                audioTracks = audioList,
                headlineHook = headline,
                activeCaptionStyle = stylePreset,
                canvasRatio = ratio,
                masterVolume = masterVol,
                sourceVideoUri = sourceVideoUri
            )
        } catch (e: Exception) {
            TimelineState()
        }
    }

    // Stock BGM and SFX catalog
    fun getAvailableStockAssets(): List<StockAsset> = listOf(
        StockAsset("bgm-1", "Phonk Bassline 140", "BGM", "0:45", "Hype"),
        StockAsset("bgm-2", "Lo-Fi Deep Thoughts", "BGM", "1:15", "Chill"),
        StockAsset("bgm-3", "Cinematic Suspense Sub", "BGM", "0:30", "Dramatic"),
        StockAsset("bgm-4", "Upbeat Silicon Valley", "BGM", "0:50", "Pop"),
        StockAsset("sfx-1", "Whoosh Transition 01", "SFX", "0:01", "Hype"),
        StockAsset("sfx-2", "Camera Shutter Snap", "SFX", "0:01", "Chill"),
        StockAsset("sfx-3", "Deep Sub Impact", "SFX", "0:02", "Dramatic"),
        StockAsset("sfx-4", "Cash Register Cha-Ching", "SFX", "0:01", "Pop")
    )
    fun getStockAssets(): List<StockAsset> = getAvailableStockAssets()

    // Preset Style Templates
    fun getStyleTemplates(): List<StyleTemplate> = listOf(
        StyleTemplate(
            id = "hormozi-impact",
            name = "Hormozi Impact",
            description = "High-energy bold yellow text, aggressive 1.25x punch-ins, zero silence pauses.",
            captionPreset = CaptionStylePreset.HORMOZI_YELLOW,
            defaultZoom = 1.25f,
            defaultTransition = VideoTransition.ZOOM_SNAP,
            recommendedBgm = "Phonk Bassline 140",
            badge = "🔥 HIGHEST CONVERSION"
        ),
        StyleTemplate(
            id = "beast-viral",
            name = "MrBeast Punch",
            description = "High-velocity emerald highlights, white flash cuts, extreme retention pacing.",
            captionPreset = CaptionStylePreset.BEAST_GREEN,
            defaultZoom = 1.30f,
            defaultTransition = VideoTransition.WHITE_FLASH,
            recommendedBgm = "Upbeat Silicon Valley",
            badge = "⚡ VIRAL VELOCITY"
        ),
        StyleTemplate(
            id = "abdaal-cinema",
            name = "Ali Abdaal Clean",
            description = "Clean minimalistic lower-third subtitles, calm pacing, high clarity.",
            captionPreset = CaptionStylePreset.ALI_ABDAAL,
            defaultZoom = 1.05f,
            defaultTransition = VideoTransition.CROSSFADE,
            recommendedBgm = "Lo-Fi Deep Thoughts",
            badge = "☕ THOUGHT LEADER"
        ),
        StyleTemplate(
            id = "red-alert-hook",
            name = "Viral Red Alert",
            description = "Urgent high-stakes red and white contrast, maximum pattern interrupt for cold viewers.",
            captionPreset = CaptionStylePreset.RED_ALERT,
            defaultZoom = 1.35f,
            defaultTransition = VideoTransition.ZOOM_SNAP,
            recommendedBgm = "Cinematic Suspense Sub",
            badge = "🚨 SCROLL STOPPER"
        )
    )
    fun getTemplates(): List<StyleTemplate> = getStyleTemplates()
}
