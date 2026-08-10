package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.SpeakerEntity
import com.example.data.local.entity.TranscriptSegmentEntity
import com.example.data.model.ExportFormat
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.components.SpeakerBadge
import com.example.ui.viewmodel.MainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptDetailScreen(
    jobId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val jobFlow = remember(jobId) { viewModel.getJobFlow(jobId) }
    val segmentsFlow = remember(jobId) { viewModel.getSegmentsFlow(jobId) }
    val speakersFlow = remember(jobId) { viewModel.getSpeakersFlow(jobId) }

    val job by jobFlow.collectAsStateWithLifecycle()
    val segments by segmentsFlow.collectAsStateWithLifecycle()
    val speakers by speakersFlow.collectAsStateWithLifecycle()
    val exportedContent by viewModel.exportedContent.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableStateOf(0.35f) }

    var renameSpeakerTarget by remember { mutableStateOf<SpeakerEntity?>(null) }
    var showExportSheet by remember { mutableStateOf(false) }

    val speakerMap = remember(speakers) {
        speakers.associateBy({ it.speakerCode }, { it })
    }

    val filteredSegments = remember(segments, searchQuery, speakerMap) {
        if (searchQuery.isBlank()) {
            segments
        } else {
            segments.filter { seg ->
                val spName = speakerMap[seg.speakerId]?.displayName ?: seg.speakerId
                seg.text.contains(searchQuery, ignoreCase = true) || spName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = job?.mediaName ?: "Транскрипция",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "Модель: ${job?.modelName ?: "Whisper"} • Язык: ${job?.detectedLanguage ?: "ru"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showExportSheet = true },
                        modifier = Modifier.testTag("export_top_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Экспорт")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showExportSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("export_fab")
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Экспорт", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("detail_screen_container")
        ) {
            // Audio Playback Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isPlayingAudio = !isPlayingAudio },
                        modifier = Modifier.testTag("play_pause_audio_button")
                    ) {
                        Icon(
                            imageVector = if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlayingAudio) "Пауза" else "Воспроизвести",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        AudioWaveformVisualizer(progress = playbackProgress)
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск по тексту и спикерам...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("search_transcript_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Transcript Segments List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredSegments, key = { it.id }) { seg ->
                    val spEntity = speakerMap[seg.speakerId]
                    val spName = spEntity?.displayName ?: "Спикер 1"
                    val colorIdx = spEntity?.colorIndex ?: 0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("segment_item_${seg.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SpeakerBadge(
                                    displayName = spName,
                                    colorIndex = colorIdx,
                                    onClick = { renameSpeakerTarget = spEntity }
                                )

                                Text(
                                    text = formatTimestamp(seg.startTimeUs),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = seg.text,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Rename Speaker Dialog
    renameSpeakerTarget?.let { speaker ->
        var newNameInput by remember { mutableStateOf(speaker.displayName) }

        AlertDialog(
            onDismissRequest = { renameSpeakerTarget = null },
            title = { Text("Переименовать спикера") },
            text = {
                OutlinedTextField(
                    value = newNameInput,
                    onValueChange = { newNameInput = it },
                    label = { Text("Имя спикера") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newNameInput.isNotBlank()) {
                            viewModel.renameSpeaker(speaker.id, newNameInput)
                        }
                        renameSpeakerTarget = null
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameSpeakerTarget = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Export Bottom Sheet
    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showExportSheet = false
                viewModel.clearExportedContent()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Выберите формат экспорта",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                ExportFormat.values().forEach { fmt ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { viewModel.exportTranscript(jobId, fmt) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = fmt.label, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                exportedContent?.let { content ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = content,
                                fontSize = 11.sp,
                                maxLines = 6,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Transcript Export", content)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Текст скопирован в буфер обмена!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Скопировать текст")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(us: Long): String {
    val totalMs = us / 1000
    val totalSec = totalMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
