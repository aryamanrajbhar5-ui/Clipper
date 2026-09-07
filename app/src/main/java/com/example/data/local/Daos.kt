package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Query("UPDATE projects SET clipsCount = :count WHERE id = :projectId")
    suspend fun updateClipsCount(projectId: String, count: Int)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: String)
}

@Dao
interface ClipDao {
    @Query("SELECT * FROM discovered_clips WHERE projectId = :projectId ORDER BY viralScore DESC")
    fun getClipsForProject(projectId: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM discovered_clips WHERE id = :id LIMIT 1")
    suspend fun getClipById(id: String): ClipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClips(clips: List<ClipEntity>)

    @Query("DELETE FROM discovered_clips WHERE projectId = :projectId")
    suspend fun deleteClipsForProject(projectId: String)
}

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timelines WHERE clipId = :clipId LIMIT 1")
    suspend fun getTimeline(clipId: String): TimelineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTimeline(timeline: TimelineEntity)
}

@Dao
interface ExportJobDao {
    @Query("SELECT * FROM export_jobs ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<ExportJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: ExportJobEntity)

    @Query("UPDATE export_jobs SET status = :status, progress = :progress, outputFilePath = :outputPath, fileSizeBytes = :fileSize WHERE id = :jobId")
    suspend fun updateJobProgress(jobId: String, status: String, progress: Float, outputPath: String, fileSize: Long)

    @Query("DELETE FROM export_jobs WHERE id = :id")
    suspend fun deleteJob(id: String)
}
