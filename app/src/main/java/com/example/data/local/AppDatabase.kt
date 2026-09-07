package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ProjectEntity::class,
        ClipEntity::class,
        TimelineEntity::class,
        ExportJobEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun clipDao(): ClipDao
    abstract fun timelineDao(): TimelineDao
    abstract fun exportJobDao(): ExportJobDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_clipper_factory.db"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed initial demo project
                        CoroutineScope(Dispatchers.IO).launch {
                            seedInitialData(getInstance(context))
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedInitialData(database: AppDatabase) {
            val projectId = "demo-project-hormozi"
            val project = ProjectEntity(
                id = projectId,
                title = "Alex Hormozi: 0 to $100M Value Equation",
                videoTitle = "The Grand Slam Offer Deep-Dive (Ep. 182).mp4",
                videoDurationSec = 2540,
                targetPlatform = "TIKTOK",
                clipsCount = 4,
                status = "ANALYZED",
                createdAt = System.currentTimeMillis() - 86400000L
            )
            database.projectDao().insertProject(project)

            val clips = listOf(
                ClipEntity(
                    id = "clip-1",
                    projectId = projectId,
                    title = "The #1 Reason Most Offers Fail in 30 Seconds",
                    startMs = 245000L,
                    endMs = 282000L,
                    viralScore = 96,
                    hookStrength = 98,
                    retentionPotential = 94,
                    standaloneScore = 92,
                    topic = "Pricing & Positioning",
                    aiExplanation = "Powerful pattern interrupt question in first 1.5 seconds ('You are charging too little because you have fear'). High emotional payoff with immediate business takeaway.",
                    hookQuote = "\"You're not losing deals to competition, you're losing deals to obscurity.\"",
                    recommendedStyle = "HORMOZI_YELLOW",
                    thumbnailGradientIndex = 0
                ),
                ClipEntity(
                    id = "clip-2",
                    projectId = projectId,
                    title = "Why Being Cheap Destroys Customer Retention",
                    startMs = 612000L,
                    endMs = 648000L,
                    viralScore = 91,
                    hookStrength = 89,
                    retentionPotential = 93,
                    standaloneScore = 95,
                    topic = "Customer Psychology",
                    aiExplanation = "Counter-intuitive argument that triggers immediate debate in comments. Ideal for TikTok and Instagram Reels comment loops.",
                    hookQuote = "\"People who pay $20 complain the most. People who pay $2,000 get the best results.\"",
                    recommendedStyle = "BEAST_GREEN",
                    thumbnailGradientIndex = 1
                ),
                ClipEntity(
                    id = "clip-3",
                    projectId = projectId,
                    title = "The 4-Step Scarcity Formula for High-Ticket",
                    startMs = 930000L,
                    endMs = 968000L,
                    viralScore = 87,
                    hookStrength = 88,
                    retentionPotential = 86,
                    standaloneScore = 90,
                    topic = "Sales Psychology",
                    aiExplanation = "Clear step-by-step listicle structure keeps viewer glued through visual captions until step 4 payoff.",
                    hookQuote = "\"Never discount price. Instead, increase the bonus stack until it feels ridiculous.\"",
                    recommendedStyle = "RED_ALERT",
                    thumbnailGradientIndex = 2
                ),
                ClipEntity(
                    id = "clip-4",
                    projectId = projectId,
                    title = "How I Made $1M in a Weekend Without An Ad Budget",
                    startMs = 1420000L,
                    endMs = 1462000L,
                    viralScore = 94,
                    hookStrength = 96,
                    retentionPotential = 92,
                    standaloneScore = 89,
                    topic = "Case Study / Proof",
                    aiExplanation = "Intense curiosity gap driven by personal storytelling and real metric reveal. Strong visual punch potential.",
                    hookQuote = "\"I sent one email to 400 old clients who said no six months prior.\"",
                    recommendedStyle = "NEON_CYAN",
                    thumbnailGradientIndex = 3
                )
            )
            database.clipDao().insertClips(clips)

            val exportJob = ExportJobEntity(
                id = "demo-export-1",
                projectId = projectId,
                clipTitle = "The #1 Reason Most Offers Fail in 30 Seconds",
                status = "COMPLETED",
                progress = 1.0f,
                resolution = "1080x1920 (9:16)",
                fps = 60,
                fileSizeBytes = 28400000L,
                outputFilePath = "/storage/emulated/0/Movies/AI_Clipper_Hormozi_01.mp4",
                createdAt = System.currentTimeMillis() - 3600000L
            )
            database.exportJobDao().insertJob(exportJob)
        }
    }
}
