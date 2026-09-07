package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CaptionBlock
import com.example.data.model.TimelineState
import com.example.data.model.VideoSegment
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonRose
import com.example.ui.theme.TrackAudioColor
import com.example.ui.theme.TrackCaptionColor
import com.example.ui.theme.TrackVideoColor
import com.example.ui.theme.ViralAmber

@Composable
fun TimelineTracksView(
    timeline: TimelineState,
    currentPositionMs: Long,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSplitAtPlayhead: () -> Unit,
    onToggleZoom: (Int) -> Unit,
    onCycleTransition: (Int) -> Unit,
    onDeleteSegment: (Int) -> Unit,
    onOpenAiDirector: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSegmentIndex by remember { mutableStateOf(0) }

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Action Toolbar (Split, Zoom, Transition, Delete, Undo, Redo, AI Director)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Undo / Redo
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("undo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) ElectricCyan else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("redo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) ElectricCyan else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(DarkCardBorder)
                )

                // Split at playhead
                FilledTonalButton(
                    onClick = onSplitAtPlayhead,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ElectricCyan.copy(alpha = 0.15f),
                        contentColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("split_button")
                ) {
                    Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Split", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // Zoom Punch Toggle
                FilledTonalButton(
                    onClick = { onToggleZoom(selectedSegmentIndex) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = DarkSurfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.testTag("zoom_punch_button")
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Zoom Punch", fontSize = 12.sp)
                }

                // Transition
                FilledTonalButton(
                    onClick = { onCycleTransition(selectedSegmentIndex) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = DarkSurfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.testTag("transition_button")
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Transition", fontSize = 12.sp)
                }

                // Delete segment
                IconButton(
                    onClick = { onDeleteSegment(selectedSegmentIndex) },
                    enabled = timeline.videoSegments.size > 1,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("delete_segment_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Segment",
                        tint = if (timeline.videoSegments.size > 1) NeonRose else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // AI Director shortcut
                FilledTonalButton(
                    onClick = onOpenAiDirector,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ViralAmber.copy(alpha = 0.2f),
                        contentColor = ViralAmber
                    ),
                    modifier = Modifier.testTag("open_ai_director_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI Director", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Multi-Track Timeline Visualizer
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // TRACK 1: VIDEO TRACK
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VIDEO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        ),
                        modifier = Modifier.width(46.dp)
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        timeline.videoSegments.forEachIndexed { index, seg ->
                            val isSelected = index == selectedSegmentIndex
                            val weight = seg.durationMs.coerceAtLeast(500L).toFloat()

                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) TrackVideoColor else TrackVideoColor.copy(alpha = 0.65f)
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) ElectricCyan else DarkCardBorder,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedSegmentIndex = index }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                                    Text(
                                        text = "Cut #${index + 1} (${seg.durationMs / 1000}s)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (seg.zoomScale > 1.1f) {
                                            Text(
                                                text = "${(seg.zoomScale * 100).toInt()}% Zoom",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = ViralAmber,
                                                    fontSize = 8.sp
                                                )
                                            )
                                        }
                                        if (seg.transition != com.example.data.model.VideoTransition.NONE) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "• ${seg.transition.label}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = ElectricCyan,
                                                    fontSize = 8.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TRACK 2: CAPTION TRACK
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CAPTIONS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ViralAmber
                        ),
                        modifier = Modifier.width(46.dp)
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (timeline.captionBlocks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No Subtitle track",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        } else {
                            timeline.captionBlocks.forEachIndexed { cIdx, cap ->
                                val duration = (cap.endMs - cap.startMs).coerceAtLeast(1000L).toFloat()
                                Box(
                                    modifier = Modifier
                                        .weight(duration)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(TrackCaptionColor)
                                        .border(1.dp, ViralAmber.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = cap.text,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White,
                                            fontSize = 9.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // TRACK 3: AUDIO TRACK
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AUDIO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.ui.theme.EmeraldSuccess
                        ),
                        modifier = Modifier.width(46.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        val audioTitle = timeline.audioTracks.firstOrNull()?.title ?: "Background Music Track (Ducked -12dB)"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(TrackAudioColor)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$audioTitle • Master ${(timeline.masterVolume * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontSize = 9.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
