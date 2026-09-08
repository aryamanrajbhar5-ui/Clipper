package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.audio.AudioProcessor
import com.example.data.model.CaptionBlock
import com.example.data.model.CaptionStylePreset
import com.example.data.model.ExportStatus
import com.example.data.model.TimelineState
import com.example.data.transcription.TranscriptSegment
import com.example.data.model.VideoSegment
import com.example.data.model.VideoTransition
import com.example.data.render.RenderEngine
import com.example.data.transcription.DefaultTranscriptionService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioPipelineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testAudioValidation_failsWhenFileEmptyOrMissing() {
        val missingFile = File(context.cacheDir, "non_existent_${UUID.randomUUID()}.mp4")
        var failed = false
        try {
            AudioProcessor.validateExportedMp4(missingFile, 10000L, sourceHadAudio = true)
        } catch (e: IllegalStateException) {
            failed = true
            assertTrue(e.message!!.contains("does not exist"))
        }
        assertTrue(failed)

        val emptyFile = File(context.cacheDir, "empty_${UUID.randomUUID()}.mp4")
        emptyFile.createNewFile()
        failed = false
        try {
            AudioProcessor.validateExportedMp4(emptyFile, 10000L, sourceHadAudio = true)
        } catch (e: IllegalStateException) {
            failed = true
            assertTrue(e.message!!.contains("0 bytes"))
        }
        assertTrue(failed)
        emptyFile.delete()
    }

    @Test
    fun testTranscription_noAudioTrack_returnsGracefulEmptyTranscript() {
        runBlocking {
            // When video source has no audio track, service must gracefully return emptyList() without calling Gemini
            val dummyFile = File(context.cacheDir, "no_audio_${UUID.randomUUID()}.txt")
            dummyFile.writeText("no video content")

            val service = DefaultTranscriptionService(context)
            // With an API key provided, non-audio video returns empty transcript rather than fabricating sentences
            val result = service.transcribeVideo(dummyFile.absolutePath, 60000L, apiKey = "AIzaSyDummyKeyForTesting12345")

            assertTrue(result.isSuccess)
            val segments = result.getOrNull()
            assertNotNull(segments)
            assertTrue(segments!!.isEmpty())

            dummyFile.delete()
        }
    }

    @Test
    fun testRenderEngine_audioPipelineFailure_failsExportExplicitly() {
        runBlocking {
            val engine = RenderEngine(context)
            // Non-existent source video
            val timeline = TimelineState(
                videoSegments = listOf(
                    VideoSegment("seg-1", 0L, 5000L, 1.0f, VideoTransition.NONE)
                ),
                sourceVideoUri = "/data/local/tmp/fake_video_${UUID.randomUUID()}.mp4"
            )

            var failedStatus = false
            val job = engine.renderMp4(
                projectId = "test-proj",
                clipTitle = "Test Clip",
                timeline = timeline
            ) { status, _, _, _ ->
                if (status == ExportStatus.FAILED) {
                    failedStatus = true
                }
            }

            assertEquals(ExportStatus.FAILED, job.status)
            assertEquals(0L, job.fileSizeBytes)
            assertTrue(job.outputFilePath.isEmpty())
            assertTrue(failedStatus)
        }
    }

    @Test
    fun testDefaultTranscriptionService_cacheStoresAndReusesTranscripts() {
        runBlocking {
            val service = DefaultTranscriptionService(context)
            val testVideoPath = "/test/path/video_cached_${UUID.randomUUID()}.mp4"

            // Prepopulate cache by saving disk cache file
            val cacheDir = File(context.cacheDir, "transcripts").apply { mkdirs() }
            val testCacheKey = "tx_path_${testVideoPath.hashCode()}_120000"
            val cacheFile = File(cacheDir, "$testCacheKey.json")

            val sampleJson = """
                [
                  {"startMs": 1000, "endMs": 4500, "text": "Welcome to the future of mobile clipper engines.", "confidence": 0.98},
                  {"startMs": 4800, "endMs": 9200, "text": "Here is how to extract and export real audio synchronized with video.", "confidence": 0.95}
                ]
            """.trimIndent()
            cacheFile.writeText(sampleJson)

            // Call transcribeVideo with no API key - it should succeed purely from cache!
            val result = service.transcribeVideo(testVideoPath, 120000L, apiKey = "")
            assertTrue(result.isSuccess)
            val segments = result.getOrNull()
            assertNotNull(segments)
            assertEquals(2, segments!!.size)
            assertEquals(1000L, segments[0].startMs)
            assertEquals(4500L, segments[0].endMs)
            assertEquals("Welcome to the future of mobile clipper engines.", segments[0].text)

            cacheFile.delete()
        }
    }
}
