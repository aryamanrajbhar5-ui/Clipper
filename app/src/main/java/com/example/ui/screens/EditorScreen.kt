package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CaptionStylePreset
import com.example.ui.components.AiDirectorSheet
import com.example.ui.components.ExportDialog
import com.example.ui.components.TimelineTracksView
import com.example.ui.components.VideoCanvasPreview
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViralAmber
import com.example.viewmodel.ClipperViewModel

@Composable
fun EditorScreen(
    viewModel: ClipperViewModel,
    modifier: Modifier = Modifier
) {
    val timeline by viewModel.timelineState.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    val clip by viewModel.selectedClip.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val isDirectorProcessing by viewModel.isDirectorProcessing.collectAsState()
    val lastDirectorResult by viewModel.lastDirectorResult.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportStatusText by viewModel.exportStatusText.collectAsState()

    var showAiDirectorSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showHookEditor by remember { mutableStateOf(false) }
    var hookInput by remember { mutableStateOf(timeline.headlineHook) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Action Bar
        Surface(
            color = DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = ElectricCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "9:16 VERTICAL",
                                color = ElectricCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = clip?.title ?: "Short-Form Editor",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // AI Director Button
                    FilledTonalButton(
                        onClick = { showAiDirectorSheet = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ViralAmber.copy(alpha = 0.2f),
                            contentColor = ViralAmber
                        ),
                        modifier = Modifier.testTag("editor_open_ai_director")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI Director", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Export MP4 Button
                    Button(
                        onClick = { showExportDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        modifier = Modifier.testTag("editor_export_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export MP4", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 9:16 Video Canvas Preview
        VideoCanvasPreview(
            timeline = timeline,
            isPlaying = playback.isPlaying,
            currentPositionMs = playback.currentPositionMs,
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSeek = { viewModel.seekTo(it) }
        )

        // 3-Track Timeline with Split / Zoom / Transition / Undo / Redo controls
        TimelineTracksView(
            timeline = timeline,
            currentPositionMs = playback.currentPositionMs,
            canUndo = canUndo,
            canRedo = canRedo,
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            onSplitAtPlayhead = { viewModel.splitCurrentSegmentAtPlayhead() },
            onToggleZoom = { viewModel.toggleZoomPunch(it) },
            onCycleTransition = { viewModel.cycleTransition(it) },
            onDeleteSegment = { viewModel.deleteSegment(it) },
            onOpenAiDirector = { showAiDirectorSheet = true }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Styling Panels: Caption Style Presets & Headline Banner
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Caption Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CAPTION PRESETS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = ViralAmber,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = timeline.activeCaptionStyle.title,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CaptionStylePreset.values().forEach { preset ->
                        val isSelected = timeline.activeCaptionStyle == preset
                        Surface(
                            color = if (isSelected) ElectricCyan.copy(alpha = 0.2f) else DarkSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) ElectricCyan else DarkCardBorder
                            ),
                            modifier = Modifier
                                .clickable { viewModel.setCaptionStyle(preset) }
                                .testTag("caption_style_${preset.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            when (preset) {
                                                CaptionStylePreset.HORMOZI_YELLOW,
                                                CaptionStylePreset.CAPCUT_BLACK_BOX,
                                                CaptionStylePreset.LUXURY_GOLD -> ViralAmber
                                                CaptionStylePreset.BEAST_GREEN,
                                                CaptionStylePreset.CAPCUT_BOUNCE,
                                                CaptionStylePreset.RETRO_TYPEWRITER -> EmeraldSuccess
                                                CaptionStylePreset.RED_ALERT -> com.example.ui.theme.NeonRose
                                                CaptionStylePreset.TIKTOK_NEON,
                                                CaptionStylePreset.ALI_ABDAAL,
                                                CaptionStylePreset.NEON_CYAN -> ElectricCyan
                                                CaptionStylePreset.CLEAN_MINIMAL -> Color.White
                                            },
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = preset.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Headline Banner Customization
                Text(
                    text = "HOOK BANNER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hookInput,
                        onValueChange = {
                            hookInput = it
                            viewModel.setHeadlineHook(it)
                        },
                        placeholder = { Text("e.g. WAIT FOR THE END 🔥", color = Color.Gray, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("hook_banner_input")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Master Audio Loudness Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Master Audio Mix: ${(timeline.masterVolume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Slider(
                        value = timeline.masterVolume,
                        onValueChange = { viewModel.setMasterVolume(it) },
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldSuccess,
                            activeTrackColor = EmeraldSuccess,
                            inactiveTrackColor = DarkCardBorder
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // AI Director Sheet Modal
    if (showAiDirectorSheet) {
        AiDirectorSheet(
            isProcessing = isDirectorProcessing,
            lastResult = lastDirectorResult,
            onDismiss = { showAiDirectorSheet = false },
            onSubmitPrompt = { prompt ->
                viewModel.submitDirectorInstruction(prompt)
            }
        )
    }

    // Export MP4 Dialog Modal
    if (showExportDialog || isExporting) {
        ExportDialog(
            isExporting = isExporting,
            progress = exportProgress,
            statusText = exportStatusText,
            onStartExport = { res, fps ->
                viewModel.startExport(res, fps)
            },
            onDismiss = {
                showExportDialog = false
                viewModel.dismissExport()
            }
        )
    }
}
