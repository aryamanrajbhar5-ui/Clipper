package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.audio.AudioPreviewPlayer
import com.example.data.model.StockAsset
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

@Composable
fun AssetsScreen(
    viewModel: ClipperViewModel,
    modifier: Modifier = Modifier
) {
    val assets = remember { viewModel.getStockAssets() }
    var selectedCategory by remember { mutableStateOf("ALL") }
    val currentlyPlayingId by AudioPreviewPlayer.currentlyPlayingId.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            AudioPreviewPlayer.stop()
        }
    }

    val categories = listOf("ALL", "BGM", "SFX", "STICKER")
    val filtered = if (selectedCategory == "ALL") assets else assets.filter { it.category == selectedCategory }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Text(
            text = "ROYALTY-FREE LIBRARY",
            style = MaterialTheme.typography.labelSmall.copy(
                color = ElectricCyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        )
        Text(
            text = "Short-Form Assets",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val isSelected = selectedCategory == cat
                Surface(
                    color = if (isSelected) ElectricCyan.copy(alpha = 0.2f) else DarkSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) ElectricCyan else DarkCardBorder
                    ),
                    modifier = Modifier
                        .clickable { selectedCategory = cat }
                        .testTag("asset_cat_${cat.lowercase()}")
                ) {
                    Text(
                        text = when (cat) {
                            "ALL" -> "All Assets"
                            "BGM" -> "Viral BGM Beats"
                            "SFX" -> "Sound Effects"
                            "STICKER" -> "Text Badges & Hooks"
                            else -> cat
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filtered) { item ->
                val isPlaying = currentlyPlayingId == item.id
                AssetItemCard(
                    asset = item,
                    isPlaying = isPlaying,
                    onTogglePlay = { AudioPreviewPlayer.togglePlayAsset(item.id) },
                    onAdd = {
                        AudioPreviewPlayer.stop()
                        if (item.category == "STICKER") {
                            viewModel.setHeadlineHook(item.title)
                            viewModel.showNotice("Updated Hook Banner to '${item.title}'")
                        } else {
                            viewModel.showNotice("Added '${item.title}' to Timeline Audio Track")
                        }
                        viewModel.navigateTo(AppSection.EDITOR)
                    }
                )
            }
        }
    }
}

@Composable
private fun AssetItemCard(
    asset: StockAsset,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onAdd: () -> Unit
) {
    val isAudio = asset.category == "SFX" || asset.category == "BGM"

    Surface(
        color = if (isPlaying) DarkSurfaceVariant else DarkSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPlaying) ElectricCyan else DarkCardBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            isPlaying -> ElectricCyan.copy(alpha = 0.25f)
                            asset.category == "BGM" -> EmeraldSuccess.copy(alpha = 0.15f)
                            asset.category == "SFX" -> ElectricCyan.copy(alpha = 0.15f)
                            else -> ViralAmber.copy(alpha = 0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isPlaying -> Icons.Default.VolumeUp
                        asset.category == "BGM" -> Icons.Default.MusicNote
                        asset.category == "SFX" -> Icons.Default.SurroundSound
                        else -> Icons.Default.Sell
                    },
                    contentDescription = null,
                    tint = when {
                        isPlaying -> ElectricCyan
                        asset.category == "BGM" -> EmeraldSuccess
                        asset.category == "SFX" -> ElectricCyan
                        else -> ViralAmber
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isPlaying) "▶ Playing Preview... • ${asset.category}" else "${asset.category} • ${asset.durationOrTag} • ${asset.energyLevel}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isPlaying) ElectricCyan else TextSecondary,
                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }

            // Play Preview Button (for SFX and BGM)
            if (isAudio) {
                Surface(
                    onClick = onTogglePlay,
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPlaying) ElectricCyan else DarkSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isPlaying) ElectricCyan else DarkCardBorder
                    ),
                    modifier = Modifier
                        .testTag("preview_audio_${asset.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop Preview" else "Play Preview",
                            tint = if (isPlaying) Color.Black else ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPlaying) "Stop" else "Listen",
                            color = if (isPlaying) Color.Black else ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
            }

            // Apply to timeline button
            Surface(
                onClick = onAdd,
                shape = RoundedCornerShape(8.dp),
                color = ElectricCyan.copy(alpha = 0.15f),
                modifier = Modifier.testTag("apply_asset_${asset.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Apply", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
