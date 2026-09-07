package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CaptionBlock
import com.example.data.model.CaptionStylePreset
import com.example.data.model.TimelineState
import com.example.data.model.VideoSegment
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonRose
import com.example.ui.theme.ViralAmber

@Composable
fun VideoCanvasPreview(
    timeline: TimelineState,
    isPlaying: Boolean,
    currentPositionMs: Long,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalMs = timeline.totalDurationMs.coerceAtLeast(1000L)

    // Identify active video segment
    var accumulated = 0L
    var activeSegment: VideoSegment? = null
    for (seg in timeline.videoSegments) {
        val segDuration = seg.durationMs
        if (currentPositionMs in accumulated..(accumulated + segDuration)) {
            activeSegment = seg
            break
        }
        accumulated += segDuration
    }
    val currentZoom = activeSegment?.zoomScale ?: 1.0f
    val animatedZoom by animateFloatAsState(
        targetValue = currentZoom,
        animationSpec = spring(stiffness = 500f),
        label = "canvas_zoom"
    )

    // Identify active caption block
    val activeCaption: CaptionBlock? = timeline.captionBlocks.firstOrNull { block ->
        currentPositionMs in block.startMs..block.endMs
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 9:16 Canvas Phone Container
        Box(
            modifier = Modifier
                .height(340.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, DarkCardBorder, RoundedCornerShape(20.dp))
                .background(Color.Black)
                .clickable { onTogglePlayPause() }
                .testTag("video_canvas_preview"),
            contentAlignment = Alignment.Center
        ) {
            // Video Frame Simulation (Center Cropped 9:16)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(animatedZoom)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E293B),
                                Color(0xFF0F172A),
                                Color(0xFF1E1B4B)
                            )
                        )
                    )
            ) {
                // Video speaker silhouette representation
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(ElectricCyan.copy(alpha = 0.35f), Color(0xFF3B82F6).copy(alpha = 0.25f))
                            )
                        )
                        .align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Speaker audio frequency",
                        tint = ElectricCyan.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(54.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            // Top Headline Hook Banner
            if (timeline.headlineHook.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ViralAmber.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp, start = 12.dp, end = 12.dp)
                ) {
                    Text(
                        text = "🔥 ${timeline.headlineHook.uppercase()} 🔥",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = ViralAmber,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Zoom Badge Indicator
            if (currentZoom > 1.1f) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonRose.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom active",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${(currentZoom * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    )
                }
            }

            // Animated Captions Overlay
            if (activeCaption != null) {
                val style = timeline.activeCaptionStyle
                val textColor = try {
                    Color(android.graphics.Color.parseColor(style.textColorHex))
                } catch (e: Exception) {
                    Color.White
                }
                val highlightColor = try {
                    Color(android.graphics.Color.parseColor(style.highlightColorHex))
                } catch (e: Exception) {
                    ViralAmber
                }
                val bgColor = try {
                    Color(android.graphics.Color.parseColor(style.backgroundColorHex))
                } catch (e: Exception) {
                    Color.Black.copy(alpha = 0.8f)
                }

                val rawWords = remember(activeCaption.text) {
                    activeCaption.text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
                }
                val totalWords = rawWords.size.coerceAtLeast(1)
                val blockDuration = (activeCaption.endMs - activeCaption.startMs).coerceAtLeast(1000L)
                val elapsedInBlock = (currentPositionMs - activeCaption.startMs).coerceIn(0L, blockDuration)
                val activeIndex = ((elapsedInBlock.toFloat() / blockDuration) * totalWords).toInt().coerceIn(0, totalWords - 1)

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 90.dp)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        // CapCut Black Box: Individual dark badge pills for each word with active word in radiant highlight
                        style.isBlackBoxStyle -> {
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.Center,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth(0.92f)
                            ) {
                                rawWords.forEachIndexed { idx, word ->
                                    val isActive = idx == activeIndex
                                    val displayText = if (style.isUppercase) word.uppercase() else word
                                    Surface(
                                        color = if (isActive) highlightColor else Color.Black.copy(alpha = 0.88f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = if (isActive) androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.9f)) else null,
                                        modifier = Modifier.padding(horizontal = 3.dp)
                                    ) {
                                        Text(
                                            text = displayText,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = if (isActive) (style.fontSizeSp + 2).sp else style.fontSizeSp.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (isActive) Color.Black else Color.White,
                                                letterSpacing = 0.5.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // CapCut Pop & Bounce: Active word scales up with bounce highlight and shadow
                        style.isBounceStyle -> {
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.Center,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgColor)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                rawWords.forEachIndexed { idx, word ->
                                    val isActive = idx == activeIndex
                                    val displayText = if (style.isUppercase) word.uppercase() else word
                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = if (isActive) (style.fontSizeSp + 4).sp else style.fontSizeSp.sp,
                                            fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold,
                                            color = if (isActive) highlightColor else textColor,
                                            shadow = if (isActive) androidx.compose.ui.graphics.Shadow(
                                                color = highlightColor.copy(alpha = 0.8f),
                                                blurRadius = 14f
                                            ) else null
                                        ),
                                        modifier = Modifier.padding(horizontal = 3.dp)
                                    )
                                }
                            }
                        }

                        // TikTok Neon Glow: Double-glow neon shadow with dark canvas
                        style.isNeonGlowStyle -> {
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.Center,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.85f))
                                    .border(1.dp, highlightColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                rawWords.forEachIndexed { idx, word ->
                                    val isActive = idx == activeIndex
                                    val displayText = if (style.isUppercase) word.uppercase() else word
                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = style.fontSizeSp.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isActive) highlightColor else Color.White,
                                            shadow = androidx.compose.ui.graphics.Shadow(
                                                color = if (isActive) highlightColor else NeonRose,
                                                blurRadius = if (isActive) 16f else 6f
                                            )
                                        ),
                                        modifier = Modifier.padding(horizontal = 3.dp)
                                    )
                                }
                            }
                        }

                        // CapCut Typewriter: Monospace with blinking terminal cursor
                        style.isTypewriterStyle -> {
                            val wordsUpToActive = rawWords.take(activeIndex + 1).joinToString(" ")
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgColor)
                                    .border(1.dp, highlightColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "$wordsUpToActive ▎",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = style.fontSizeSp.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = highlightColor
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Standard / Hormozi / Beast / Clean: Highlight active word
                        else -> {
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.Center,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgColor)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                rawWords.forEachIndexed { idx, word ->
                                    val isActive = idx == activeIndex
                                    val displayText = if (style.isUppercase) word.uppercase() else word
                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = if (isActive) (style.fontSizeSp + 2).sp else style.fontSizeSp.sp,
                                            fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold,
                                            color = if (isActive) highlightColor else textColor,
                                            shadow = if (isActive) androidx.compose.ui.graphics.Shadow(
                                                color = Color.Black,
                                                blurRadius = 8f
                                            ) else null
                                        ),
                                        modifier = Modifier.padding(horizontal = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Center Play Icon when Paused
            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = ElectricCyan,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Playback Scrubber & Time Display
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("play_pause_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = ElectricCyan,
                    modifier = Modifier.size(28.dp)
                )
            }

            Slider(
                value = currentPositionMs.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..totalMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = DarkCardBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("playback_slider")
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Timestamp mm:ss.s
            Text(
                text = "${formatTimeMs(currentPositionMs)} / ${formatTimeMs(totalMs)}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

fun formatTimeMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val dec = (ms % 1000) / 100
    return String.format("%02d:%02d.%d", min, sec, dec)
}
