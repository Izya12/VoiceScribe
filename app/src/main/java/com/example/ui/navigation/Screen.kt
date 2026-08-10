package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val titleRu: String, val icon: ImageVector) {
    object Home : Screen("home", "Распознать", Icons.Default.Mic)
    object Jobs : Screen("jobs", "История", Icons.AutoMirrored.Filled.List)
    object Models : Screen("models", "Модели AI", Icons.Default.Download)
    object Benchmarks : Screen("benchmarks", "Аппаратура", Icons.Default.Analytics)

    companion object {
        fun detailRoute(jobId: String): String = "transcript_detail/$jobId"
    }
}
