package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.navigation.Screen
import com.example.ui.screens.BenchmarkScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.JobsScreen
import com.example.ui.screens.ModelManagerScreen
import com.example.ui.screens.TranscriptDetailScreen
import com.example.ui.theme.VoiceScribeTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VoiceScribeTheme {
                MainAppStructure(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppStructure(viewModel: MainViewModel) {
    var currentTab by remember { mutableStateOf<Screen>(Screen.Home) }
    var selectedJobDetailId by remember { mutableStateOf<String?>(null) }

    val tabs = listOf(Screen.Home, Screen.Jobs, Screen.Models, Screen.Benchmarks)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (selectedJobDetailId == null) {
                NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                    tabs.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.titleRu) },
                            label = { Text(screen.titleRu) },
                            selected = currentTab == screen,
                            onClick = { currentTab = screen },
                            modifier = Modifier.testTag("nav_${screen.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (selectedJobDetailId != null) {
                TranscriptDetailScreen(
                    jobId = selectedJobDetailId!!,
                    viewModel = viewModel,
                    onBack = { selectedJobDetailId = null }
                )
            } else {
                when (currentTab) {
                    Screen.Home -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToJobs = { currentTab = Screen.Jobs }
                    )
                    Screen.Jobs -> JobsScreen(
                        viewModel = viewModel,
                        onSelectJob = { jobId -> selectedJobDetailId = jobId }
                    )
                    Screen.Models -> ModelManagerScreen(
                        viewModel = viewModel
                    )
                    Screen.Benchmarks -> BenchmarkScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
