package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AppBottomNav
import com.example.ui.components.AppTopBar
import com.example.ui.screens.AssetsScreen
import com.example.ui.screens.ClipFinderScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.NewProjectScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TemplatesScreen
import com.example.ui.theme.AIClipperTheme
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.ElectricCyan
import com.example.viewmodel.AppSection
import com.example.viewmodel.ClipperViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIClipperTheme {
                val viewModel: ClipperViewModel = viewModel()
                ClipperApp(viewModel)
            }
        }
    }
}

@Composable
fun ClipperApp(viewModel: ClipperViewModel) {
    val currentSection by viewModel.currentSection.collectAsState()
    val activeProject by viewModel.selectedProject.collectAsState()
    val uiNotice by viewModel.uiNotice.collectAsState()
    val isKeyConfigured = viewModel.isKeyConfigured()
    val hasCustomUserKey = viewModel.hasCustomUserKey()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                activeProjectTitle = activeProject?.title,
                isKeyConfigured = isKeyConfigured,
                hasCustomUserKey = hasCustomUserKey,
                onKeyChipClick = { viewModel.navigateTo(AppSection.SETTINGS) }
            )
        },
        bottomBar = {
            AppBottomNav(
                currentSection = currentSection,
                onSelectSection = { viewModel.navigateTo(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = currentSection,
                animationSpec = tween(220),
                label = "screen_crossfade"
            ) { section ->
                when (section) {
                    AppSection.DASHBOARD -> DashboardScreen(viewModel)
                    AppSection.PROJECTS -> ProjectsScreen(viewModel)
                    AppSection.NEW_PROJECT -> NewProjectScreen(viewModel)
                    AppSection.CLIP_FINDER -> ClipFinderScreen(viewModel)
                    AppSection.EDITOR -> EditorScreen(viewModel)
                    AppSection.ASSETS -> AssetsScreen(viewModel)
                    AppSection.TEMPLATES -> TemplatesScreen(viewModel)
                    AppSection.SETTINGS -> SettingsScreen(viewModel)
                }
            }

            // Floating Feedback Toast
            if (uiNotice != null) {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
                ) {
                    Text(
                        text = uiNotice!!,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
