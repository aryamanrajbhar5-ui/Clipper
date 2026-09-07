package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorderSubtle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanBright
import com.example.ui.theme.TextMuted
import com.example.viewmodel.AppSection

@Composable
fun AppBottomNav(
    currentSection: AppSection,
    onSelectSection: (AppSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(
                section = AppSection.DASHBOARD,
                icon = Icons.Default.Dashboard,
                isSelected = currentSection == AppSection.DASHBOARD,
                onClick = { onSelectSection(AppSection.DASHBOARD) }
            )
            NavItem(
                section = AppSection.PROJECTS,
                icon = Icons.Default.VideoLibrary,
                isSelected = currentSection == AppSection.PROJECTS,
                onClick = { onSelectSection(AppSection.PROJECTS) }
            )
            NavItem(
                section = AppSection.NEW_PROJECT,
                icon = Icons.Default.AddCircle,
                isSelected = currentSection == AppSection.NEW_PROJECT,
                onClick = { onSelectSection(AppSection.NEW_PROJECT) }
            )
            NavItem(
                section = AppSection.CLIP_FINDER,
                icon = Icons.Default.AutoAwesome,
                isSelected = currentSection == AppSection.CLIP_FINDER,
                onClick = { onSelectSection(AppSection.CLIP_FINDER) }
            )
            NavItem(
                section = AppSection.EDITOR,
                icon = Icons.Default.ContentCut,
                isSelected = currentSection == AppSection.EDITOR,
                onClick = { onSelectSection(AppSection.EDITOR) }
            )
            NavItem(
                section = AppSection.ASSETS,
                icon = Icons.Default.GraphicEq,
                isSelected = currentSection == AppSection.ASSETS,
                onClick = { onSelectSection(AppSection.ASSETS) }
            )
            NavItem(
                section = AppSection.TEMPLATES,
                icon = Icons.Default.Style,
                isSelected = currentSection == AppSection.TEMPLATES,
                onClick = { onSelectSection(AppSection.TEMPLATES) }
            )
            NavItem(
                section = AppSection.SETTINGS,
                icon = Icons.Default.Key,
                isSelected = currentSection == AppSection.SETTINGS,
                onClick = { onSelectSection(AppSection.SETTINGS) }
            )
        }
    }
}

@Composable
private fun NavItem(
    section: AppSection,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val activeColor = if (isSelected) ElectricCyanBright else Color.White.copy(alpha = 0.38f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("nav_item_${section.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = section.title,
                tint = activeColor,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = section.title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp,
                    color = activeColor
                )
            )
            Spacer(modifier = Modifier.height(3.dp))
            // Active Cyan Indicator Dot
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) ElectricCyanBright else Color.Transparent)
            )
        }
    }
}

