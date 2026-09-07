package com.example.ui.screens

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlatformTarget
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
fun NewProjectScreen(
    viewModel: ClipperViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf("Podcast Masterclass Ep. 42") }
    var videoName by remember { mutableStateOf("alex_hormozi_scale_1080p.mp4") }
    var durationSec by remember { mutableStateOf(1800) } // 30 minutes
    var selectedPlatform by remember { mutableStateOf(PlatformTarget.TIKTOK) }
    var aiInstructions by remember { mutableStateOf("Find the top high-conviction hooks and mindsets with intense emotional delivery.") }
    var resolutionAndSize by remember { mutableStateOf("1920x1080 • Auto 9:16 Smart Cropping") }
    var isUploadedFromDevice by remember { mutableStateOf(false) }

    val isScanning by viewModel.isScanningClips.collectAsState()

    // Android Zero-Permission Photo & Video Picker
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val (extractedName, extractedDur, info) = extractVideoInfo(context, uri)
            videoName = extractedName
            durationSec = extractedDur
            resolutionAndSize = info
            isUploadedFromDevice = true
            val cleanTitle = extractedName.substringBeforeLast(".").replace("_", " ").replace("-", " ")
            if (title == "Podcast Masterclass Ep. 42" || title.isBlank()) {
                title = cleanTitle.replaceFirstChar { it.uppercase() }
            }
            viewModel.showNotice("Uploaded video asset: $extractedName (${durationSec / 60}m)")
        }
    }

    // Fallback file browser
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val (extractedName, extractedDur, info) = extractVideoInfo(context, uri)
            videoName = extractedName
            durationSec = extractedDur
            resolutionAndSize = info
            isUploadedFromDevice = true
            val cleanTitle = extractedName.substringBeforeLast(".").replace("_", " ").replace("-", " ")
            if (title == "Podcast Masterclass Ep. 42" || title.isBlank()) {
                title = cleanTitle.replaceFirstChar { it.uppercase() }
            }
            viewModel.showNotice("Loaded video asset: $extractedName (${durationSec / 60}m)")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "NEW CLIP PROJECT",
            style = MaterialTheme.typography.labelSmall.copy(
                color = ElectricCyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        )
        Text(
            text = "Upload & Analyze Long Video",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Upload Dropzone
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (isUploadedFromDevice) EmeraldSuccess else ElectricCyan.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("video_upload_dropzone")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            if (isUploadedFromDevice) EmeraldSuccess.copy(alpha = 0.15f)
                            else ElectricCyan.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isUploadedFromDevice) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                        contentDescription = "Upload Video",
                        tint = if (isUploadedFromDevice) EmeraldSuccess else ElectricCyan,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = videoName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "MP4 / MOV • ${durationSec / 60}m ${durationSec % 60}s • $resolutionAndSize",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )

                if (isUploadedFromDevice) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = EmeraldSuccess.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ready for AI Moment Detection & Auto-Clipping", color = EmeraldSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Upload Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Gallery / Photo Picker button
                    Surface(
                        onClick = {
                            pickMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = ElectricCyan,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_video_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Video", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Files browser button
                    Surface(
                        onClick = {
                            getContentLauncher.launch("video/*")
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("browse_files_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Browse Files", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Preset Sample Footage Options
        Text(
            text = "OR CHOOSE SAMPLE LONG-FORM FOOTAGE:",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextMuted)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val samples = listOf(
                Triple("Hormozi Podcast", "alex_hormozi_scale_1080p.mp4", 1800),
                Triple("MrBeast Pacing", "mrbeast_challenge_raw_4k.mp4", 2400),
                Triple("Founder Pitch", "ycombinator_demo_day_hd.mp4", 1500)
            )
            samples.forEach { (label, fname, dur) ->
                val isSelected = videoName == fname
                Surface(
                    onClick = {
                        videoName = fname
                        durationSec = dur
                        resolutionAndSize = "1920x1080 • Auto 9:16 Smart Cropping"
                        isUploadedFromDevice = false
                        title = label
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) ElectricCyan.copy(alpha = 0.18f) else DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) ElectricCyan else DarkCardBorder
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "${dur / 60} min",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Form Fields
        Text(
            text = "PROJECT TITLE",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextMuted)
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = DarkCardBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("project_title_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Target Platform Selection
        Text(
            text = "TARGET PLATFORM & ASPECT RATIO",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextMuted)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlatformTarget.values().forEach { platform ->
                val isSelected = selectedPlatform == platform
                Surface(
                    color = if (isSelected) ElectricCyan.copy(alpha = 0.2f) else DarkSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) ElectricCyan else DarkCardBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedPlatform = platform }
                        .testTag("platform_chip_${platform.name.lowercase()}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = platform.displayName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = platform.aspectRatio,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isSelected) ElectricCyan else TextSecondary,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom AI Prompt for clip discovery
        Text(
            text = "AI DISCOVERY FOCUS (OPTIONAL)",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextMuted)
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = aiInstructions,
            onValueChange = { aiInstructions = it },
            placeholder = { Text("What moments should Gemini prioritize? (e.g., hooks, humor, contrarian advice)") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = DarkCardBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_instructions_input")
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Primary Analyze Button
        Button(
            onClick = {
                viewModel.createProjectAndScan(
                    title = title,
                    videoTitle = videoName,
                    durationSec = durationSec,
                    targetPlatform = selectedPlatform,
                    userPrompt = aiInstructions
                )
            },
            enabled = !isScanning && title.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_ai_analysis_button")
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.Black,
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Gemini Scanning Video Timeline...", color = Color.Black, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyze Video & Find Viral Moments", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

private fun extractVideoInfo(context: Context, uri: Uri): Triple<String, Int, String> {
    var name = "device_video_${System.currentTimeMillis() % 10000}.mp4"
    var sizeStr = ""
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    cursor.getString(nameIndex)?.let { name = it }
                }
                if (sizeIndex != -1) {
                    val bytes = cursor.getLong(sizeIndex)
                    if (bytes > 0) {
                        sizeStr = " • ${String.format("%.1f", bytes / (1024.0 * 1024.0))} MB"
                    }
                }
            }
        }
    } catch (_: Exception) {}

    var durationSec = 180 // default fallback: 3 min
    var resStr = "1920x1080"
    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val durMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        if (!durMsStr.isNullOrBlank()) {
            val durMs = durMsStr.toLongOrNull() ?: 0L
            if (durMs > 0) {
                durationSec = (durMs / 1000).toInt().coerceAtLeast(15)
            }
        }
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        if (!width.isNullOrBlank() && !height.isNullOrBlank()) {
            resStr = "${width}x${height}"
        }
        retriever.release()
    } catch (_: Exception) {}

    return Triple(name, durationSec, "$resStr$sizeStr")
}

