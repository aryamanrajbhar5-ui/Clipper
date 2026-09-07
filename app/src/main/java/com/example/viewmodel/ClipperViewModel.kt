package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ConnectionTestResult
import com.example.data.model.AiDirectorResult
import com.example.data.model.CaptionBlock
import com.example.data.model.CaptionStylePreset
import com.example.data.model.CreatorProject
import com.example.data.model.DiscoveredClip
import com.example.data.model.ExportJob
import com.example.data.model.PlatformTarget
import com.example.data.model.StockAsset
import com.example.data.model.StyleTemplate
import com.example.data.model.TimelineState
import com.example.data.model.VideoSegment
import com.example.data.model.VideoTransition
import com.example.data.repository.ClipperRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppSection(val title: String) {
    DASHBOARD("Dashboard"),
    PROJECTS("Projects"),
    NEW_PROJECT("New Project"),
    CLIP_FINDER("AI Clip Finder"),
    EDITOR("Clip Editor"),
    ASSETS("Assets"),
    TEMPLATES("Templates"),
    SETTINGS("Settings")
}

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val speed: Float = 1.0f
)

class ClipperViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClipperRepository(application)

    // Navigation
    private val _currentSection = MutableStateFlow(AppSection.DASHBOARD)
    val currentSection: StateFlow<AppSection> = _currentSection.asStateFlow()

    // Projects
    val projects: StateFlow<List<CreatorProject>> = repository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProject = MutableStateFlow<CreatorProject?>(null)
    val selectedProject: StateFlow<CreatorProject?> = _selectedProject.asStateFlow()

    // Clips for selected project
    private val _projectClips = MutableStateFlow<List<DiscoveredClip>>(emptyList())
    val projectClips: StateFlow<List<DiscoveredClip>> = _projectClips.asStateFlow()

    private val _isScanningClips = MutableStateFlow(false)
    val isScanningClips: StateFlow<Boolean> = _isScanningClips.asStateFlow()

    private val _selectedClip = MutableStateFlow<DiscoveredClip?>(null)
    val selectedClip: StateFlow<DiscoveredClip?> = _selectedClip.asStateFlow()

    // Timeline and Undo/Redo Stacks
    private val _timelineState = MutableStateFlow(TimelineState())
    val timelineState: StateFlow<TimelineState> = _timelineState.asStateFlow()

    private val undoStack = mutableListOf<TimelineState>()
    private val redoStack = mutableListOf<TimelineState>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // Playback state
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    private var playbackJob: Job? = null

    // AI Director
    private val _isDirectorProcessing = MutableStateFlow(false)
    val isDirectorProcessing: StateFlow<Boolean> = _isDirectorProcessing.asStateFlow()

    private val _lastDirectorResult = MutableStateFlow<AiDirectorResult?>(null)
    val lastDirectorResult: StateFlow<AiDirectorResult?> = _lastDirectorResult.asStateFlow()

    // Exports
    val exportJobs: StateFlow<List<ExportJob>> = repository.getAllExportJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _exportStatusText = MutableStateFlow("")
    val exportStatusText: StateFlow<String> = _exportStatusText.asStateFlow()

    // BYOK Settings
    private val _apiKeyInput = MutableStateFlow(repository.getUserApiKey())
    val apiKeyInput: StateFlow<String> = _apiKeyInput.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<ConnectionTestResult?>(null)
    val connectionTestResult: StateFlow<ConnectionTestResult?> = _connectionTestResult.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _selectedModel = MutableStateFlow(repository.getSelectedModel())
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    // User Message / Snackbar
    private val _uiNotice = MutableStateFlow<String?>(null)
    val uiNotice: StateFlow<String?> = _uiNotice.asStateFlow()

    init {
        // Automatically monitor projects to set default active project
        viewModelScope.launch {
            projects.collect { list ->
                if (_selectedProject.value == null && list.isNotEmpty()) {
                    selectProject(list.first())
                }
            }
        }
    }

    fun navigateTo(section: AppSection) {
        _currentSection.value = section
        if (section != AppSection.EDITOR) {
            pausePlayback()
        }
    }

    fun selectProject(project: CreatorProject) {
        _selectedProject.value = project
        viewModelScope.launch {
            repository.getClipsForProject(project.id).collect { clips ->
                _projectClips.value = clips
                if (_selectedClip.value == null && clips.isNotEmpty()) {
                    selectClip(clips.first())
                }
            }
        }
    }

    fun selectClip(clip: DiscoveredClip) {
        _selectedClip.value = clip
        viewModelScope.launch {
            val timeline = repository.getOrCreateTimelineForClip(clip)
            undoStack.clear()
            redoStack.clear()
            _canUndo.value = false
            _canRedo.value = false
            _timelineState.value = timeline
            _playbackState.value = PlaybackState(isPlaying = false, currentPositionMs = 0L)
        }
    }

    fun createProjectAndScan(
        title: String,
        videoTitle: String,
        durationSec: Int,
        targetPlatform: PlatformTarget,
        userPrompt: String = "",
        videoUri: String = ""
    ) {
        viewModelScope.launch {
            _isScanningClips.value = true
            val project = repository.createProject(title, videoTitle, durationSec, targetPlatform, videoUri)
            _selectedProject.value = project
            _currentSection.value = AppSection.CLIP_FINDER

            val result = repository.scanVideoForClips(project, userPrompt)
            result.onSuccess { clips ->
                _projectClips.value = clips
                if (clips.isNotEmpty()) {
                    selectClip(clips.first())
                }
                showNotice("Discovered ${clips.size} viral clips!")
            }.onFailure { err ->
                showNotice("Scan completed with standard heuristic models: ${err.message}")
            }
            _isScanningClips.value = false
        }
    }

    fun rescanCurrentProject(customPrompt: String = "") {
        val current = _selectedProject.value ?: return
        viewModelScope.launch {
            _isScanningClips.value = true
            val result = repository.scanVideoForClips(current, customPrompt)
            result.onSuccess { clips ->
                _projectClips.value = clips
                if (clips.isNotEmpty()) {
                    selectClip(clips.first())
                }
                showNotice("AI re-scan discovered ${clips.size} new viral clips!")
            }
            _isScanningClips.value = false
        }
    }

    // Playback control
    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun seekTo(positionMs: Long) {
        val maxMs = _timelineState.value.totalDurationMs
        val clamped = positionMs.coerceIn(0L, maxMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = clamped)
    }

    private fun startPlayback() {
        playbackJob?.cancel()
        _playbackState.value = _playbackState.value.copy(isPlaying = true)
        playbackJob = viewModelScope.launch {
            val totalDuration = _timelineState.value.totalDurationMs
            while (_playbackState.value.isPlaying) {
                delay(50)
                val nextPos = _playbackState.value.currentPositionMs + 50
                if (nextPos >= totalDuration) {
                    _playbackState.value = _playbackState.value.copy(isPlaying = false, currentPositionMs = 0L)
                    break
                } else {
                    _playbackState.value = _playbackState.value.copy(currentPositionMs = nextPos)
                }
            }
        }
    }

    fun pausePlayback() {
        playbackJob?.cancel()
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    // Timeline editing with Undo/Redo
    private fun mutateTimeline(block: (TimelineState) -> TimelineState) {
        val current = _timelineState.value
        undoStack.add(current)
        if (undoStack.size > 20) undoStack.removeAt(0)
        redoStack.clear()
        _canUndo.value = true
        _canRedo.value = false

        val next = block(current)
        _timelineState.value = next

        // Auto save to database
        _selectedClip.value?.let { clip ->
            viewModelScope.launch {
                repository.saveTimeline(clip.id, clip.projectId, next)
            }
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(_timelineState.value)
            _timelineState.value = previous
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = true
            showNotice("Action undone")
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(_timelineState.value)
            _timelineState.value = next
            _canUndo.value = true
            _canRedo.value = redoStack.isNotEmpty()
            showNotice("Action redone")
        }
    }

    fun splitCurrentSegmentAtPlayhead() {
        val currentPos = _playbackState.value.currentPositionMs
        mutateTimeline { state ->
            var accumulated = 0L
            val newSegments = mutableListOf<VideoSegment>()
            var splitApplied = false

            for (seg in state.videoSegments) {
                val segDuration = seg.durationMs
                if (!splitApplied && currentPos > accumulated + 800L && currentPos < accumulated + segDuration - 800L) {
                    val splitOffset = currentPos - accumulated
                    val seg1 = seg.copy(
                        id = UUID.randomUUID().toString(),
                        sourceEndMs = seg.sourceStartMs + splitOffset
                    )
                    val seg2 = seg.copy(
                        id = UUID.randomUUID().toString(),
                        sourceStartMs = seg.sourceStartMs + splitOffset,
                        zoomScale = if (seg.zoomScale > 1.1f) 1.0f else 1.25f,
                        transition = VideoTransition.ZOOM_SNAP
                    )
                    newSegments.add(seg1)
                    newSegments.add(seg2)
                    splitApplied = true
                } else {
                    newSegments.add(seg)
                }
                accumulated += segDuration
            }

            if (splitApplied) {
                showNotice("Segment split at ${currentPos / 1000}s with auto-zoom snap")
                state.copy(videoSegments = newSegments)
            } else {
                showNotice("Move playhead inside a segment (at least 0.8s from edge) to split")
                state
            }
        }
    }

    fun trimSegment(segmentIndex: Int, trimStartDeltaMs: Long, trimEndDeltaMs: Long) {
        mutateTimeline { state ->
            if (segmentIndex in state.videoSegments.indices) {
                val seg = state.videoSegments[segmentIndex]
                val newStart = (seg.sourceStartMs + trimStartDeltaMs).coerceAtLeast(0L)
                val newEnd = (seg.sourceEndMs - trimEndDeltaMs).coerceAtLeast(newStart + 500L)
                val updated = state.videoSegments.toMutableList().apply {
                    this[segmentIndex] = seg.copy(sourceStartMs = newStart, sourceEndMs = newEnd)
                }
                state.copy(videoSegments = updated)
            } else {
                state
            }
        }
    }

    fun deleteSegment(segmentIndex: Int) {
        mutateTimeline { state ->
            if (state.videoSegments.size <= 1) {
                showNotice("Cannot delete the only segment")
                state
            } else if (segmentIndex in state.videoSegments.indices) {
                val updated = state.videoSegments.toMutableList().apply { removeAt(segmentIndex) }
                showNotice("Segment removed")
                state.copy(videoSegments = updated)
            } else {
                state
            }
        }
    }

    fun toggleZoomPunch(segmentIndex: Int) {
        mutateTimeline { state ->
            if (segmentIndex in state.videoSegments.indices) {
                val seg = state.videoSegments[segmentIndex]
                val nextZoom = when {
                    seg.zoomScale < 1.1f -> 1.25f
                    seg.zoomScale < 1.3f -> 1.38f
                    else -> 1.0f
                }
                val updated = state.videoSegments.toMutableList().apply {
                    this[segmentIndex] = seg.copy(zoomScale = nextZoom)
                }
                showNotice("Zoom set to ${(nextZoom * 100).toInt()}%")
                state.copy(videoSegments = updated)
            } else {
                state
            }
        }
    }

    fun cycleTransition(segmentIndex: Int) {
        mutateTimeline { state ->
            if (segmentIndex in state.videoSegments.indices) {
                val seg = state.videoSegments[segmentIndex]
                val values = VideoTransition.values()
                val nextTransition = values[(seg.transition.ordinal + 1) % values.size]
                val updated = state.videoSegments.toMutableList().apply {
                    this[segmentIndex] = seg.copy(transition = nextTransition)
                }
                showNotice("Transition: ${nextTransition.label}")
                state.copy(videoSegments = updated)
            } else {
                state
            }
        }
    }

    fun setCaptionStyle(style: CaptionStylePreset) {
        mutateTimeline { state ->
            val updatedCaptions = state.captionBlocks.map { it.copy(stylePreset = style) }
            showNotice("Caption style: ${style.title}")
            state.copy(captionBlocks = updatedCaptions, activeCaptionStyle = style)
        }
    }

    fun updateCaptionText(captionIndex: Int, newText: String) {
        mutateTimeline { state ->
            if (captionIndex in state.captionBlocks.indices) {
                val block = state.captionBlocks[captionIndex]
                val updated = state.captionBlocks.toMutableList().apply {
                    this[captionIndex] = block.copy(text = newText)
                }
                state.copy(captionBlocks = updated)
            } else {
                state
            }
        }
    }

    fun setHeadlineHook(newHook: String) {
        mutateTimeline { state ->
            state.copy(headlineHook = newHook)
        }
    }

    fun setMasterVolume(volume: Float) {
        mutateTimeline { state ->
            state.copy(masterVolume = volume.coerceIn(0f, 1f))
        }
    }

    fun applyTemplate(template: StyleTemplate) {
        mutateTimeline { state ->
            val updatedSegments = state.videoSegments.mapIndexed { idx, seg ->
                seg.copy(
                    zoomScale = if (idx == 0) template.defaultZoom else 1.05f,
                    transition = template.defaultTransition
                )
            }
            val updatedCaptions = state.captionBlocks.map { it.copy(stylePreset = template.captionPreset) }
            showNotice("Applied template '${template.name}'")
            state.copy(
                videoSegments = updatedSegments,
                captionBlocks = updatedCaptions,
                activeCaptionStyle = template.captionPreset
            )
        }
    }

    // AI Director
    fun submitDirectorInstruction(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _isDirectorProcessing.value = true
            val result = repository.directTimelineEdits(prompt, _timelineState.value)
            result.onSuccess { dirResult ->
                _lastDirectorResult.value = dirResult
                mutateTimeline {
                    dirResult.updatedTimeline
                }
                showNotice("AI Director: Applied ${dirResult.actionsApplied.size} edits")
            }.onFailure { err ->
                showNotice("AI Director notice: ${err.message}")
            }
            _isDirectorProcessing.value = false
        }
    }

    // Exporting
    fun startExport(resolution: String = "1080x1920 (9:16)", fps: Int = 30) {
        val project = _selectedProject.value ?: return
        val clip = _selectedClip.value ?: return
        viewModelScope.launch {
            _isExporting.value = true
            _exportProgress.value = 0f
            _exportStatusText.value = "Starting MP4 Render..."

            val effectiveSourceUri = when {
                _timelineState.value.sourceVideoUri.isNotBlank() -> _timelineState.value.sourceVideoUri
                clip.sourceVideoUri.isNotBlank() -> clip.sourceVideoUri
                else -> project.videoUri
            }

            val job = repository.startExport(
                projectId = project.id,
                clipTitle = clip.title,
                timeline = _timelineState.value,
                sourceUriOrPath = effectiveSourceUri,
                resolution = resolution,
                fps = fps
            ) { status, progress ->
                _exportProgress.value = progress
                _exportStatusText.value = when (status) {
                    com.example.data.model.ExportStatus.ANALYZING -> "Analyzing timeline cuts (${(progress * 100).toInt()}%)"
                    com.example.data.model.ExportStatus.SMART_CROPPING -> "Smart 9:16 vertical crop & tracking (${(progress * 100).toInt()}%)"
                    com.example.data.model.ExportStatus.BURNING_CAPTIONS -> "Burning animated captions & effects (${(progress * 100).toInt()}%)"
                    com.example.data.model.ExportStatus.ENCODING_MP4 -> "Encoding H.264 MP4 bitstream (${(progress * 100).toInt()}%)"
                    com.example.data.model.ExportStatus.COMPLETED -> "Render Complete!"
                    else -> "Processing..."
                }
            }

            _isExporting.value = false
            if (job.status == com.example.data.model.ExportStatus.COMPLETED) {
                showNotice("Export complete! File saved: ${job.outputFilePath}")
            } else {
                showNotice("Export finished with status: ${job.status.name}")
            }
        }
    }

    fun dismissExport() {
        _isExporting.value = false
    }

    // BYOK Settings Actions
    fun updateApiKeyInput(key: String) {
        _apiKeyInput.value = key
    }

    fun saveApiKey() {
        val key = _apiKeyInput.value.trim()
        repository.saveUserApiKey(key)
        _connectionTestResult.value = null
        showNotice(if (key.isBlank()) "API Key removed" else "API Key saved securely!")
    }

    fun testConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            val testKey = _apiKeyInput.value.ifBlank { repository.getUserApiKey() }
            val res = repository.testConnection(testKey, _selectedModel.value)
            _connectionTestResult.value = res
            _isTestingConnection.value = false
        }
    }

    fun selectModel(model: String) {
        _selectedModel.value = model
        repository.setSelectedModel(model)
        showNotice("Model changed to $model")
    }

    fun getMaskedKey(): String = repository.getMaskedKey()
    fun isKeyConfigured(): Boolean = repository.isKeyConfigured()
    fun hasCustomUserKey(): Boolean = repository.hasCustomUserKey()
    fun getStockAssets(): List<StockAsset> = repository.getStockAssets()
    fun getTemplates(): List<StyleTemplate> = repository.getTemplates()

    fun showNotice(msg: String) {
        _uiNotice.value = msg
        viewModelScope.launch {
            delay(3500)
            if (_uiNotice.value == msg) {
                _uiNotice.value = null
            }
        }
    }

    fun dismissNotice() {
        _uiNotice.value = null
    }
}
