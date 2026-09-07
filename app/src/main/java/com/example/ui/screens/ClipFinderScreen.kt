package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DiscoveredClip
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViralAmber
import com.example.viewmodel.AppSection
import com.example.viewmodel.ClipperViewModel

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import com.example.ui.theme.DarkCardBorderSubtle
import com.example.ui.theme.DarkInnerContainer
import com.example.ui.theme.ElectricCyanBright

@Composable
fun ClipFinderScreen(
    viewModel: ClipperViewModel,
    modifier: Modifier = Modifier
) {
    val clips by viewModel.projectClips.collectAsState()
    val activeProject by viewModel.selectedProject.collectAsState()
    val selectedClip by viewModel.selectedClip.collectAsState()
    val isScanning by viewModel.isScanningClips.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Header matching Immersive UI
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AI Clip Finder",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "(${clips.size} found)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                )
            }

            Surface(
                onClick = { viewModel.rescanCurrentProject() },
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = ElectricCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Text(
                            text = "+",
                            color = ElectricCyanBright,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isScanning) "Analyzing..." else "Refine Analysis",
                        color = ElectricCyanBright,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ElectricCyan)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Gemini is analyzing transcript pacing and emotional peaks...", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else if (clips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No clips found yet. Click 'Refine Analysis' to analyze the video.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(clips) { index, clip ->
                    val isSelected = clip.id == selectedClip?.id
                    ClipDiscoveryCard(
                        rank = index + 1,
                        clip = clip,
                        isSelected = isSelected,
                        onSelectAndEdit = {
                            viewModel.selectClip(clip)
                            viewModel.navigateTo(AppSection.EDITOR)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipDiscoveryCard(
    rank: Int,
    clip: DiscoveredClip,
    isSelected: Boolean,
    onSelectAndEdit: () -> Unit
) {
    val durationSec = ((clip.endMs - clip.startMs) / 1000).toInt()
    val durMin = durationSec / 60
    val durSecRem = durationSec % 60
    val durationFormatted = String.format("%02d:%02d", durMin, durSecRem)

    val startMin = (clip.startMs / 60000).toInt()
    val startSec = ((clip.startMs % 60000) / 1000).toInt()
    val endMin = (clip.endMs / 60000).toInt()
    val endSec = ((clip.endMs % 60000) / 1000).toInt()
    val timeLabel = String.format("%02d:%02d → %02d:%02d", startMin, startSec, endMin, endSec)

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) ElectricCyan else DarkCardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("clip_card_$rank")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Subtle Top Gradient Highlight Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                ElectricCyan.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(modifier = Modifier.padding(20.dp)) {
                // Header: Viral Potential Badge + Score + Title + Duration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Pill badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ElectricCyan)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "VIRAL POTENTIAL",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            // Big Viral Score
                            Text(
                                text = "${clip.viralScore}%",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = (-0.5).sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Title with quote
                        Text(
                            text = "\"${clip.title}\"",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontStyle = FontStyle.Italic,
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 14.sp
                            )
                        )
                    }

                    // Duration on top right
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "DURATION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = durationFormatted,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = ElectricCyanBright,
                                fontSize = 14.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hook Strength Meter Bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Hook Strength",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = if (clip.hookStrength >= 90) "Extreme (${clip.hookStrength}%)" else "Strong (${clip.hookStrength}%)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((clip.hookStrength / 100f).coerceIn(0.1f, 1f))
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Inner Quote / Reason Container
                Surface(
                    color = DarkInnerContainer,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "WHY: ",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp,
                                    color = ElectricCyanBright
                                )
                            )
                            Text(
                                text = clip.aiExplanation.ifBlank { "Strong emotional hook combined with rapid pacing ideal for high retention." },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            )
                        }

                        if (clip.hookQuote.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Hook: \"${clip.hookQuote}\"",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontStyle = FontStyle.Italic,
                                    color = ElectricCyanBright.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Breakdown Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ScorePill("Retention", "${clip.retentionPotential}%")
                        ScorePill("Standalone", "${clip.standaloneScore}%")
                    }
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.4f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary CTA Button matching the Immersive UI design (Clean high contrast button)
                Button(
                    onClick = onSelectAndEdit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (rank == 1) Color.White else ElectricCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("select_clip_${rank}_button")
                ) {
                    Text(
                        text = "Open in Editor",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "→",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ScorePill(label: String, score: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = score,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

