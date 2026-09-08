package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.GeminiAiGateway
import com.example.data.model.CaptionBlock
import com.example.data.model.CaptionStylePreset
import com.example.data.model.CaptionWord
import com.example.data.model.ExportStatus
import com.example.data.model.PlatformTarget
import com.example.data.model.TimelineState
import com.example.data.model.VideoSegment
import com.example.data.model.VideoTransition
import com.example.data.render.RenderEngine
import com.example.data.security.ApiKeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ClipperFactoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testApiKeyStore_obfuscatesAndMasksKey() {
        val store = ApiKeyStore(context)
        val rawKey = "AIzaSySecretTestKey123456789"

        store.saveUserApiKey(rawKey)
        assertTrue(store.hasCustomUserKey())
        assertTrue(store.isConfigured())

        // Retrieval recovers exact key
        assertEquals(rawKey, store.getUserApiKey())

        // Masked key does not expose full secret
        val masked = store.getMaskedKey()
        assertTrue(masked.startsWith("AIzaSy"))
        assertTrue(masked.contains("••••"))
        assertNotEquals(rawKey, masked)

        // Clear key
        store.clearUserApiKey()
        assertFalse(store.hasCustomUserKey())
    }

    @Test
    fun testTimelineState_durationCalculation() {
        val segments = listOf(
            VideoSegment(
                id = "seg-1",
                sourceStartMs = 0L,
                sourceEndMs = 12000L,
                zoomScale = 1.25f,
                transition = VideoTransition.ZOOM_SNAP
            ),
            VideoSegment(
                id = "seg-2",
                sourceStartMs = 12000L,
                sourceEndMs = 26000L,
                zoomScale = 1.0f,
                transition = VideoTransition.NONE
            )
        )

        val timeline = TimelineState(
            videoSegments = segments,
            activeCaptionStyle = CaptionStylePreset.HORMOZI_YELLOW,
            canvasRatio = "9:16"
        )

        assertEquals(26000L, timeline.totalDurationMs)
        assertEquals(2, timeline.videoSegments.size)
    }

    @Test
    fun testAiDirector_appliesDirectivesToTimeline() = runBlocking {
        val gateway = GeminiAiGateway()
        val initialTimeline = TimelineState(
            videoSegments = listOf(
                VideoSegment("s1", 0L, 10000L, 1.0f, VideoTransition.NONE),
                VideoSegment("s2", 10000L, 20000L, 1.0f, VideoTransition.NONE)
            ),
            activeCaptionStyle = CaptionStylePreset.CLEAN_MINIMAL,
            headlineHook = "TIPS"
        )

        val result = gateway.directTimelineEdits(
            userPrompt = "Make this more energetic and zoom in on the hook",
            currentTimeline = initialTimeline,
            apiKey = "",
            model = "gemini-2.5-flash"
        )

        assertTrue(result.isSuccess)
        val dirResult = result.getOrNull()
        assertNotNull(dirResult)
        assertTrue(dirResult!!.actionsApplied.isNotEmpty())
        // Should have updated style to higher energy
        assertEquals(CaptionStylePreset.BEAST_GREEN, dirResult.updatedTimeline.activeCaptionStyle)
        // Hook zoom punch applied to first segment
        assertTrue(dirResult.updatedTimeline.videoSegments[0].zoomScale > 1.1f)
    }

    @Test
    fun testRenderEngine_failsClearlyWhenNoSourceProvided() = runBlocking {
        val engine = RenderEngine(context)
        val timeline = TimelineState(
            videoSegments = listOf(
                VideoSegment("s1", 0L, 8000L, 1.25f, VideoTransition.ZOOM_SNAP),
                VideoSegment("s2", 8000L, 15000L, 1.05f, VideoTransition.NONE)
            ),
            sourceVideoUri = ""
        )

        var failureReported = false
        val job = engine.renderMp4(
            jobId = UUID.randomUUID().toString(),
            projectId = "proj-test",
            clipTitle = "Viral Short Test",
            timeline = timeline,
            sourceUriOrPath = "",
            resolution = "1080x1920 (9:16)",
            fps = 60
        ) { status, _, _, _ ->
            if (status == ExportStatus.FAILED) {
                failureReported = true
            }
        }

        // Must fail with FAILED and empty path instead of generating fake bytes
        assertEquals(ExportStatus.FAILED, job.status)
        assertEquals(0f, job.progress)
        assertEquals(0L, job.fileSizeBytes)
        assertTrue(job.outputFilePath.isEmpty())
        assertTrue(failureReported)
    }

    @Test
    fun testTranscriptionService_failsWhenNoApiKeyConfigured() = runBlocking {
        val service = com.example.data.transcription.DefaultTranscriptionService(context)
        val result = service.transcribeVideo(
            videoUriOrPath = "content://media/external/video/media/1",
            durationMs = 30000L,
            apiKey = ""
        )

        // Must fail clearly rather than returning fabricated synthetic hook sentences
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception!!.message!!.contains("No transcription provider configured") || exception.message!!.contains("Gemini API Key"))
    }
}

