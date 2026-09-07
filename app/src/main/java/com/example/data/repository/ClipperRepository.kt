package com.example.data.repository

import android.content.Context
import com.example.data.ai.AiGateway
import com.example.data.ai.ConnectionTestResult
import com.example.data.ai.GeminiAiGateway
import com.example.data.local.AppDatabase
import com.example.data.local.ClipEntity
import com.example.data.local.ExportJobEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.TimelineEntity
import com.example.data.model.AiDirectorResult
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ClipperRepository(context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val apiKeyStore = ApiKeyStore(context)
    private val aiGateway: AiGateway = GeminiAiGateway()
    private val renderEngine = RenderEngine(context)

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
                    createdAt = entity.createdAt
                )
            }
        }
    }

    suspend fun createProject(
        title: String,
        videoTitle: String,
        videoDurationSec: Int,
        targetPlatform: PlatformTarget
    ): CreatorProject {
        val projectId = UUID.randomUUID().toString()
        val project = CreatorProject(
            id = projectId,
            title = title,
            videoTitle = videoTitle,
            videoDurationSec = videoDurationSec,
            targetPlatform = targetPlatform,
            clipsCount = 0,
            status = "ANALYZING",
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
                    thumbnailGradientIndex = entity.thumbnailGradientIndex
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
        val result = aiGateway.discoverClips(
            projectId = project.id,
            projectTitle = project.title,
            videoTitle = project.videoTitle,
            videoDurationSec = project.videoDurationSec,
            targetPlatform = project.targetPlatform,
            userPrompt = userPrompt,
            apiKey = key,
            model = model
        )

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
                    thumbnailGradientIndex = clip.thumbnailGradientIndex
                )
            }
            database.clipDao().deleteClipsForProject(project.id)
            database.clipDao().insertClips(entities)
            database.projectDao().updateClipsCount(project.id, clips.size)
        }
        return result
    }

    // Timeline Persistence & Initial Plan Generation
    suspend fun getOrCreateTimelineForClip(clip: DiscoveredClip): TimelineState {
        val stored = database.timelineDao().getTimeline(clip.id)
        if (stored != null) {
            return deserializeTimeline(stored.jsonContent)
        }

        // Generate initial smart editing plan for the clip
        val initialTimeline = createSmartEditingPlan(clip)
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
        resolution: String = "1080x1920 (9:16)",
        fps: Int = 60,
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

    // Helper to generate a realistic initial vertical editing plan from clip
    private fun createSmartEditingPlan(clip: DiscoveredClip): TimelineState {
        val duration = clip.endMs - clip.startMs
        val thirdDuration = duration / 3

        // Video segments with smart cuts and intro zoom punch
        val segments = listOf(
            VideoSegment(
                id = UUID.randomUUID().toString(),
                sourceStartMs = clip.startMs,
                sourceEndMs = clip.startMs + thirdDuration,
                zoomScale = 1.25f, // Smart 9:16 hook zoom punch
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

        // Captions broken down with emphasis words
        val rawWords = clip.hookQuote.replace("\"", "").split(" ")
        val captions = mutableListOf<CaptionBlock>()
        val wordsPerBlock = 5
        var currentOffset = 0L
        val blockDuration = 2400L

        rawWords.chunked(wordsPerBlock).forEachIndexed { index, chunk ->
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

        // Add additional body captions to match clip length
        if (captions.size < 3) {
            captions.add(
                CaptionBlock(
                    id = UUID.randomUUID().toString(),
                    text = "Most people overlook this simple fact",
                    startMs = currentOffset,
                    endMs = currentOffset + 2800L,
                    words = listOf(
                        CaptionWord("Most", currentOffset, currentOffset + 500L),
                        CaptionWord("people", currentOffset + 500L, currentOffset + 1000L),
                        CaptionWord("overlook", currentOffset + 1000L, currentOffset + 1800L, isEmphasized = true),
                        CaptionWord("this", currentOffset + 1800L, currentOffset + 2200L),
                        CaptionWord("fact", currentOffset + 2200L, currentOffset + 2800L, isEmphasized = true)
                    ),
                    stylePreset = clip.recommendedStyle
                )
            )
        }

        val audioTracks = listOf(
            com.example.data.model.AudioTrackItem(
                id = UUID.randomUUID().toString(),
                title = "Upbeat Phonk Motivation",
                assetUrlOrName = "phonk_energy.mp3",
                startMs = 0L,
                endMs = duration,
                volume = 0.35f,
                isDuckingEnabled = true
            )
        )

        return TimelineState(
            videoSegments = segments,
            captionBlocks = captions,
            audioTracks = audioTracks,
            headlineHook = clip.topic.uppercase(),
            activeCaptionStyle = clip.recommendedStyle,
            canvasRatio = "9:16",
            masterVolume = 1.0f
        )
    }

    private fun serializeTimeline(timeline: TimelineState): String {
        val root = JSONObject()
        root.put("headlineHook", timeline.headlineHook)
        root.put("activeCaptionStyle", timeline.activeCaptionStyle.name)
        root.put("canvasRatio", timeline.canvasRatio)
        root.put("masterVolume", timeline.masterVolume.toDouble())

        val segmentsArr = JSONArray()
        timeline.videoSegments.forEach { seg ->
            val segObj = JSONObject()
            segObj.put("id", seg.id)
            segObj.put("sourceStartMs", seg.sourceStartMs)
            segObj.put("sourceEndMs", seg.sourceEndMs)
            segObj.put("zoomScale", seg.zoomScale.toDouble())
            segObj.put("transition", seg.transition.name)
            segObj.put("cropFocusX", seg.cropFocusX.toDouble())
            segmentsArr.put(segObj)
        }
        root.put("videoSegments", segmentsArr)

        val captionsArr = JSONArray()
        timeline.captionBlocks.forEach { cap ->
            val capObj = JSONObject()
            capObj.put("id", cap.id)
            capObj.put("text", cap.text)
            capObj.put("startMs", cap.startMs)
            capObj.put("endMs", cap.endMs)
            capObj.put("stylePreset", cap.stylePreset.name)
            val wordsArr = JSONArray()
            cap.words.forEach { w ->
                val wObj = JSONObject()
                wObj.put("word", w.word)
                wObj.put("startOffsetMs", w.startOffsetMs)
                wObj.put("endOffsetMs", w.endOffsetMs)
                wObj.put("isEmphasized", w.isEmphasized)
                wordsArr.put(wObj)
            }
            capObj.put("words", wordsArr)
            captionsArr.put(capObj)
        }
        root.put("captionBlocks", captionsArr)

        val audioArr = JSONArray()
        timeline.audioTracks.forEach { aud ->
            val audObj = JSONObject()
            audObj.put("id", aud.id)
            audObj.put("title", aud.title)
            audObj.put("assetUrlOrName", aud.assetUrlOrName)
            audObj.put("startMs", aud.startMs)
            audObj.put("endMs", aud.endMs)
            audObj.put("volume", aud.volume.toDouble())
            audObj.put("isDuckingEnabled", aud.isDuckingEnabled)
            audioArr.put(audObj)
        }
        root.put("audioTracks", audioArr)

        return root.toString()
    }

    private fun deserializeTimeline(jsonStr: String): TimelineState {
        return try {
            val root = JSONObject(jsonStr)
            val headlineHook = root.optString("headlineHook", "")
            val activeCaptionStyle = try {
                CaptionStylePreset.valueOf(root.optString("activeCaptionStyle", "HORMOZI_YELLOW"))
            } catch (e: Exception) {
                CaptionStylePreset.HORMOZI_YELLOW
            }
            val canvasRatio = root.optString("canvasRatio", "9:16")
            val masterVolume = root.optDouble("masterVolume", 1.0).toFloat()

            val segments = mutableListOf<VideoSegment>()
            val segArr = root.optJSONArray("videoSegments")
            if (segArr != null) {
                for (i in 0 until segArr.length()) {
                    val o = segArr.getJSONObject(i)
                    segments.add(
                        VideoSegment(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            sourceStartMs = o.optLong("sourceStartMs", 0L),
                            sourceEndMs = o.optLong("sourceEndMs", 10000L),
                            zoomScale = o.optDouble("zoomScale", 1.0).toFloat(),
                            transition = try {
                                VideoTransition.valueOf(o.optString("transition", "NONE"))
                            } catch (e: Exception) {
                                VideoTransition.NONE
                            },
                            cropFocusX = o.optDouble("cropFocusX", 0.5).toFloat()
                        )
                    )
                }
            }

            val captions = mutableListOf<CaptionBlock>()
            val capArr = root.optJSONArray("captionBlocks")
            if (capArr != null) {
                for (i in 0 until capArr.length()) {
                    val o = capArr.getJSONObject(i)
                    val words = mutableListOf<CaptionWord>()
                    val wArr = o.optJSONArray("words")
                    if (wArr != null) {
                        for (j in 0 until wArr.length()) {
                            val wo = wArr.getJSONObject(j)
                            words.add(
                                CaptionWord(
                                    word = wo.optString("word", ""),
                                    startOffsetMs = wo.optLong("startOffsetMs", 0L),
                                    endOffsetMs = wo.optLong("endOffsetMs", 500L),
                                    isEmphasized = wo.optBoolean("isEmphasized", false)
                                )
                            )
                        }
                    }
                    captions.add(
                        CaptionBlock(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            text = o.optString("text", ""),
                            startMs = o.optLong("startMs", 0L),
                            endMs = o.optLong("endMs", 2000L),
                            words = words,
                            stylePreset = try {
                                CaptionStylePreset.valueOf(o.optString("stylePreset", "HORMOZI_YELLOW"))
                            } catch (e: Exception) {
                                activeCaptionStyle
                            }
                        )
                    )
                }
            }

            val audio = mutableListOf<com.example.data.model.AudioTrackItem>()
            val audArr = root.optJSONArray("audioTracks")
            if (audArr != null) {
                for (i in 0 until audArr.length()) {
                    val o = audArr.getJSONObject(i)
                    audio.add(
                        com.example.data.model.AudioTrackItem(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            title = o.optString("title", "Audio"),
                            assetUrlOrName = o.optString("assetUrlOrName", ""),
                            startMs = o.optLong("startMs", 0L),
                            endMs = o.optLong("endMs", 10000L),
                            volume = o.optDouble("volume", 0.5).toFloat(),
                            isDuckingEnabled = o.optBoolean("isDuckingEnabled", true)
                        )
                    )
                }
            }

            TimelineState(
                videoSegments = segments,
                captionBlocks = captions,
                audioTracks = audio,
                headlineHook = headlineHook,
                activeCaptionStyle = activeCaptionStyle,
                canvasRatio = canvasRatio,
                masterVolume = masterVolume
            )
        } catch (e: Exception) {
            TimelineState()
        }
    }

    // Asset and Template Catalogues
    fun getStockAssets(): List<StockAsset> = listOf(
        StockAsset("sfx-1", "Heavy Whoosh Transition", "SFX", "0.4s", "Impact"),
        StockAsset("sfx-2", "Cash Register Ka-Ching", "SFX", "0.8s", "Reward"),
        StockAsset("sfx-pop", "Pop Bubble Effect", "SFX", "0.2s", "Viral Pop"),
        StockAsset("sfx-4", "Sub Bass 808 Drop", "SFX", "1.2s", "Bass Punch"),
        StockAsset("sfx-3", "Vinyl Scratch Stop", "SFX", "0.6s", "Interrupt"),
        StockAsset("sfx-camera", "Camera Shutter Click", "SFX", "0.3s", "Snap"),
        StockAsset("sfx-boom", "Cinematic Deep Boom", "SFX", "1.4s", "Dramatic"),
        StockAsset("sfx-glitch", "Digital Glitch Buzz", "SFX", "0.5s", "Tech"),
        StockAsset("bgm-1", "Phonk Gym Motivation", "BGM", "02:14", "Hype"),
        StockAsset("bgm-2", "Lo-Fi Coffee Study", "BGM", "01:52", "Chill"),
        StockAsset("bgm-3", "Cinematic Pulse Suspense", "BGM", "02:30", "Dramatic"),
        StockAsset("bgm-4", "Trap Viral Boom Beat", "BGM", "01:45", "Hype"),
        StockAsset("sticker-1", "WAIT FOR THE END 🔥", "STICKER", "Text Overlay", "Attention"),
        StockAsset("sticker-2", "99% MISS THIS ❌", "STICKER", "Text Overlay", "Pattern Interrupt"),
        StockAsset("sticker-3", "MIND BLOWN 🤯", "STICKER", "Badge", "Reaction")
    )

    fun getTemplates(): List<StyleTemplate> = listOf(
        StyleTemplate(
            id = "tpl-hormozi",
            name = "Hormozi Impact",
            description = "High-contrast yellow/green words, 1.25x snap zoom punch on sentence hooks, aggressive volume ducking.",
            captionPreset = CaptionStylePreset.HORMOZI_YELLOW,
            defaultZoom = 1.25f,
            defaultTransition = VideoTransition.ZOOM_SNAP,
            recommendedBgm = "Phonk Gym Motivation",
            badge = "Viral #1"
        ),
        StyleTemplate(
            id = "tpl-tiktok-fast",
            name = "Fast-Paced TikTok",
            description = "Sub-2.5s rapid cuts, white flash transitions, high saturation words, maximum viewer retention.",
            captionPreset = CaptionStylePreset.BEAST_GREEN,
            defaultZoom = 1.3f,
            defaultTransition = VideoTransition.WHITE_FLASH,
            recommendedBgm = "Trap Viral Boom Beat",
            badge = "Trending"
        ),
        StyleTemplate(
            id = "tpl-red-alert",
            name = "Red Urgency",
            description = "High-stakes bold red alerts, bold uppercase captions, dramatic pauses removed automatically.",
            captionPreset = CaptionStylePreset.RED_ALERT,
            defaultZoom = 1.2f,
            defaultTransition = VideoTransition.CROSSFADE,
            recommendedBgm = "Cinematic Pulse Suspense",
            badge = "High CTR"
        ),
        StyleTemplate(
            id = "tpl-cyber-clean",
            name = "Cyber Minimalist",
            description = "Clean modern tech aesthetic with cyan subtitles, subtle letterbox breathing, chill lo-fi backing.",
            captionPreset = CaptionStylePreset.NEON_CYAN,
            defaultZoom = 1.1f,
            defaultTransition = VideoTransition.NONE,
            recommendedBgm = "Lo-Fi Coffee Study",
            badge = "Clean"
        )
    )
}
