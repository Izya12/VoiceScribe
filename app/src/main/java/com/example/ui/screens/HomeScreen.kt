package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ProgressCard
import com.example.ui.viewmodel.MainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToJobs: () -> Unit
) {
    val selectedMedia by viewModel.selectedMedia.collectAsStateWithLifecycle()
    val activeJob by viewModel.activeJob.collectAsStateWithLifecycle()
    val config by viewModel.transcriptionConfig.collectAsStateWithLifecycle()
    val allModels by viewModel.allModels.collectAsStateWithLifecycle()

    var currentPickedUri by remember { mutableStateOf<Uri?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            currentPickedUri = uri
            viewModel.selectMediaUri(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("home_screen_container")
    ) {
        // Active Running Job Card
        activeJob?.let { job ->
            ProgressCard(
                job = job,
                onCancel = { viewModel.cancelJob(it) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Header Title
        Text(
            text = "Локальная AI Транскрипция",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "100% Офлайн распознавание аудио и видео на базе Whisper",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // File Selection Section
        if (selectedMedia == null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { filePickerLauncher.launch("*/*") }
                    .testTag("select_media_button"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AudioFile,
                            contentDescription = "Выбрать файл",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Выберите аудио или видео файл",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Поддерживаются MP3, WAV, M4A, FLAC, AAC, MP4, MKV, WEBM",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Selected Media Card
            val media = selectedMedia!!
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("selected_media_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (media.isVideo) Icons.Default.VideoFile else Icons.Default.AudioFile,
                        contentDescription = "Тип медиа",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = media.fileName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Длительность: ${formatMs(media.durationMs)} • ${(media.sizeBytes / (1024 * 1024))} МБ",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { viewModel.clearSelectedMedia() },
                        modifier = Modifier.testTag("clear_media_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Удалить медиа",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Transcription Configuration Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("config_panel"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Параметры",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Параметры распознавания",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Model Selector
                Text(
                    text = "Модель Whisper:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                var expandedModelMenu by remember { mutableStateOf(false) }
                val activeModel = allModels.find { it.id == config.modelId } ?: allModels.firstOrNull()

                ExposedDropdownMenuBox(
                    expanded = expandedModelMenu,
                    onExpandedChange = { expandedModelMenu = !expandedModelMenu }
                ) {
                    TextField(
                        value = activeModel?.name ?: "Whisper Base",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModelMenu) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("model_selector_dropdown")
                    )

                    ExposedDropdownMenu(
                        expanded = expandedModelMenu,
                        onDismissRequest = { expandedModelMenu = false }
                    ) {
                        allModels.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(model.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Память: ${(model.estimatedRamBytes / (1024 * 1024))}МБ • ${if (model.isDownloaded) "Установлена" else "Требует скачивания"}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.updateConfig(config.copy(modelId = model.id))
                                    expandedModelMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Language Mode Selection
                Text(
                    text = "Язык аудио:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = config.isAutoLanguage,
                        onClick = { viewModel.updateConfig(config.copy(isAutoLanguage = true)) },
                        label = { Text("Автовыбор") },
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("lang_auto_chip")
                    )

                    FilterChip(
                        selected = !config.isAutoLanguage && config.selectedLanguage == "ru",
                        onClick = { viewModel.updateConfig(config.copy(isAutoLanguage = false, selectedLanguage = "ru")) },
                        label = { Text("Русский") },
                        modifier = Modifier.testTag("lang_ru_chip")
                    )

                    FilterChip(
                        selected = !config.isAutoLanguage && config.selectedLanguage == "en",
                        onClick = { viewModel.updateConfig(config.copy(isAutoLanguage = false, selectedLanguage = "en")) },
                        label = { Text("English") },
                        modifier = Modifier.testTag("lang_en_chip")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Diarization Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Диаризация спикеров",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Разделение диалогов по спикерам (Спикер 1, Спикер 2)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = config.enableDiarization,
                        onCheckedChange = { viewModel.updateConfig(config.copy(enableDiarization = it)) },
                        modifier = Modifier.testTag("diarization_switch")
                    )
                }

                AnimatedVisibility(visible = config.enableDiarization) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = "Количество спикеров: ${if (config.expectedSpeakerCount == 0) "Авто" else config.expectedSpeakerCount.toString()}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = config.expectedSpeakerCount.toFloat(),
                            onValueChange = { viewModel.updateConfig(config.copy(expectedSpeakerCount = it.toInt())) },
                            valueRange = 0f..6f,
                            steps = 5,
                            modifier = Modifier.testTag("speaker_count_slider")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // VAD Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Детекция голоса (VAD)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Пропуск тишины для ускорения работы",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = config.enableVAD,
                        onCheckedChange = { viewModel.updateConfig(config.copy(enableVAD = it)) },
                        modifier = Modifier.testTag("vad_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Autonomous Debug & Logging Section
                com.example.ui.components.DebugSettingsSection()
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Start Button
        Button(
            onClick = {
                currentPickedUri?.let { uri ->
                    viewModel.startTranscription(uri)
                    onNavigateToJobs()
                }
            },
            enabled = selectedMedia != null && activeJob == null,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("start_transcription_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Начать транскрипцию",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%d:%02d", min, sec)
}
