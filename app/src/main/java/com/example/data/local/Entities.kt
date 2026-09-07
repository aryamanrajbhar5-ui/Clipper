package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val videoTitle: String,
    val videoDurationSec: Int,
    val targetPlatform: String,
    val clipsCount: Int,
    val status: String,
    val videoUri: String = "",
    val createdAt: Long
)

@Entity(tableName = "discovered_clips")
data class ClipEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val viralScore: Int,
    val hookStrength: Int,
    val retentionPotential: Int,
    val standaloneScore: Int,
    val topic: String,
    val aiExplanation: String,
    val hookQuote: String,
    val recommendedStyle: String,
    val thumbnailGradientIndex: Int,
    val sourceVideoUri: String = ""
)

@Entity(tableName = "timelines")
data class TimelineEntity(
    @PrimaryKey val clipId: String,
    val projectId: String,
    val jsonContent: String,
    val lastUpdated: Long
)

@Entity(tableName = "export_jobs")
data class ExportJobEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val clipTitle: String,
    val status: String,
    val progress: Float,
    val resolution: String,
    val fps: Int,
    val fileSizeBytes: Long,
    val outputFilePath: String,
    val createdAt: Long
)
