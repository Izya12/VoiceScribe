package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun AudioWaveformVisualizer(
    progress: Float = 0.5f,
    barCount: Int = 40,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(vertical = 4.dp)
    ) {
        val width = size.width
        val height = size.height
        val barWidth = (width / barCount) * 0.6f
        val gap = (width / barCount) * 0.4f

        for (i in 0 until barCount) {
            val t = i.toDouble() / barCount
            // Generate simulated acoustic waveform envelope
            val amplitude = (0.2 + 0.7 * absSin(i * 0.3) * absSin(i * 0.15)).toFloat()
            val barHeight = height * amplitude.coerceIn(0.15f, 0.95f)
            val x = i * (barWidth + gap)
            val y = (height - barHeight) / 2f

            val isPassed = t <= progress
            val color = if (isPassed) activeColor else inactiveColor

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

private fun absSin(v: Double): Double = kotlin.math.abs(sin(v))
