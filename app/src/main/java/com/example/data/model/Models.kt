package com.example.data.model

import java.util.UUID

enum class PlatformTarget(val displayName: String, val aspectRatio: String, val idealDurationSec: Int) {
    TIKTOK("TikTok", "9:16", 30),
    INSTAGRAM_REELS("Instagram Reels", "9:16", 45),
    YOUTUBE_SHORTS("YouTube Shorts", "9:16", 50)
}

enum class CaptionStylePreset(
    val title: String,
    val textColorHex: String,
    val highlightColorHex: String,
    val backgroundColorHex: String,
    val isUppercase: Boolean,
    val fontSizeSp: Int,
    val isBlackBoxStyle: Boolean = false,
    val isBounceStyle: Boolean = false,
    val isNeonGlowStyle: Boolean = false,
    val isTypewriterStyle: Boolean = false
) {
    HORMOZI_YELLOW("Hormozi Impact", "#FFFFFF", "#FACC15", "#CC000000", true, 22),
    CAPCUT_BLACK_BOX("CapCut Black Box", "#FFFFFF", "#FACC15", "#E6000000", true, 22, isBlackBoxStyle = true),
    CAPCUT_BOUNCE("CapCut Pop Bounce", "#FFFFFF", "#22C55E", "#CC0A0A0B", true, 24, isBounceStyle = true),
    TIKTOK_NEON("TikTok Neon Glow", "#FFFFFF", "#06B6D4", "#E60A0A0B", true, 22, isNeonGlowStyle = true),
    ALI_ABDAAL("Ali Abdaal Clean", "#F8FAFC", "#38BDF8", "#591E293B", false, 19),
    BEAST_GREEN("Beast Green Punch", "#FFFFFF", "#22C55E", "#E60F172A", true, 24),
    RED_ALERT("Viral Red Alert", "#FFFFFF", "#F43F5E", "#B3000000", true, 22),
    RETRO_TYPEWRITER("CapCut Typewriter", "#A7F3D0", "#10B981", "#E6052E16", false, 19, isTypewriterStyle = true),
    LUXURY_GOLD("Luxury Cinema Gold", "#FEF08A", "#EAB308", "#E61C1917", true, 21),
    NEON_CYAN("Cyber Cyan", "#000000", "#38BDF8", "#E6FFFFFF", false, 20),
    CLEAN_MINIMAL("Clean Subtitle", "#F8FAFC", "#94A3B8", "#66000000", false, 18)
}

enum class VideoTransition(val label: String) {
    NONE("Hard Cut"),
    CROSSFADE("Crossfade"),
    ZOOM_SNAP("Zoom Snap"),
    WHITE_FLASH("White Flash")
}

data class VideoSegment(
    val id: String = UUID.randomUUID().toString(),
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val zoomScale: Float = 1.0f,
    val transition: VideoTransition = VideoTransition.NONE,
    val cropFocusX: Float = 0.5f // 0.0 left to 1.0 right for smart 9:16 pan
) {
    val durationMs: Long get() = (sourceEndMs - sourceStartMs).coerceAtLeast(0)
}

data class CaptionWord(
    val word: String,
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val isEmphasized: Boolean = false
)

data class CaptionBlock(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val words: List<CaptionWord> = emptyList(),
    val stylePreset: CaptionStylePreset = CaptionStylePreset.HORMOZI_YELLOW
)

data class AudioTrackItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val assetUrlOrName: String,
    val startMs: Long,
    val endMs: Long,
    val volume: Float = 0.6f, // 0.0 to 1.0
    val isDuckingEnabled: Boolean = true
)

data class TimelineState(
    val videoSegments: List<VideoSegment> = emptyList(),
    val captionBlocks: List<CaptionBlock> = emptyList(),
    val audioTracks: List<AudioTrackItem> = emptyList(),
    val headlineHook: String = "",
    val activeCaptionStyle: CaptionStylePreset = CaptionStylePreset.HORMOZI_YELLOW,
    val canvasRatio: String = "9:16",
    val masterVolume: Float = 1.0f,
    val sourceVideoUri: String = ""
) {
    val totalDurationMs: Long get() = videoSegments.sumOf { it.durationMs }.coerceAtLeast(1000L)
}

data class DiscoveredClip(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val viralScore: Int, // 0 - 100
    val hookStrength: Int, // 0 - 100
    val retentionPotential: Int, // 0 - 100
    val standaloneScore: Int, // 0 - 100
    val topic: String,
    val aiExplanation: String,
    val hookQuote: String,
    val recommendedStyle: CaptionStylePreset = CaptionStylePreset.HORMOZI_YELLOW,
    val thumbnailGradientIndex: Int = 0,
    val sourceVideoUri: String = ""
) {
    val durationSec: Int get() = ((endMs - startMs) / 1000).toInt()
}

data class CreatorProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val videoTitle: String,
    val videoDurationSec: Int,
    val targetPlatform: PlatformTarget = PlatformTarget.TIKTOK,
    val clipsCount: Int = 0,
    val status: String = "READY",
    val videoUri: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class ExportJob(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val clipTitle: String,
    val status: ExportStatus = ExportStatus.QUEUED,
    val progress: Float = 0f,
    val resolution: String = "1080x1920 (9:16)",
    val fps: Int = 60,
    val fileSizeBytes: Long = 0L,
    val outputFilePath: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class ExportStatus {
    QUEUED,
    ANALYZING,
    SMART_CROPPING,
    BURNING_CAPTIONS,
    ENCODING_MP4,
    COMPLETED,
    FAILED
}

data class StockAsset(
    val id: String,
    val title: String,
    val category: String, // "BGM", "SFX", "STICKER"
    val durationOrTag: String,
    val energyLevel: String // "Chill", "Hype", "Dramatic", "Pop"
)

data class StyleTemplate(
    val id: String,
    val name: String,
    val description: String,
    val captionPreset: CaptionStylePreset,
    val defaultZoom: Float,
    val defaultTransition: VideoTransition,
    val recommendedBgm: String,
    val badge: String
)

data class AiDirectorResult(
    val explanation: String,
    val actionsApplied: List<String>,
    val updatedTimeline: TimelineState
)
