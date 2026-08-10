package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SpeakerColors

@Composable
fun SpeakerBadge(
    displayName: String,
    colorIndex: Int = 0,
    onClick: (() -> Unit)? = null
) {
    val badgeColor = SpeakerColors[colorIndex.coerceIn(0, SpeakerColors.size - 1)]

    Box(
        modifier = Modifier
            .testTag("speaker_badge_${displayName.lowercase()}")
            .clip(RoundedCornerShape(6.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = displayName,
            color = badgeColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
