/*
 *     Copyright (C) 2026 Plazmoid-ai
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IntervalPlayTestOverlay(
    isIntervalPlayEnabled: Boolean,
    isMuteMode: Boolean,
    onIntervalPlayEnabledChange: (Boolean) -> Unit,
    onMuteModeChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    var isOverlayExpanded by remember { mutableStateOf(false) }
    // No mute-phase signal is exposed to this UI-only test overlay yet.
    // Keep the countdown at zero rather than pretending silence is active continuously.
    val muteCountdownSeconds = 0

    val shape = RoundedCornerShape(12.dp)
    val primaryText = Color(0xFFF3F3F6)
    val secondaryText = Color(0xFFA8A8A8)
    val accent = Color(0xFF9B8AF5)
    val panelBackground = Color(0xFF18181B).copy(alpha = if (isOverlayExpanded) 0.9f else 0.8f)

    if (!isOverlayExpanded) {
        Row(
            modifier = Modifier
                .widthIn(min = 84.dp, max = 120.dp)
                .height(32.dp)
                .clip(shape)
                .background(panelBackground)
                .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                .clickable { isOverlayExpanded = true }
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = if (isIntervalPlayEnabled) "30с/10с" else "Выкл.",
                color = if (isIntervalPlayEnabled) accent else secondaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
            if (isMuteMode) {
                Text(
                    text = "🔇",
                    color = primaryText,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .width(228.dp)
                .clip(shape)
                .background(panelBackground)
                .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isIntervalPlayEnabled) "Ручной · 30с/10с" else "IntervalPlay: выкл.",
                    modifier = Modifier.weight(1f),
                    color = if (isIntervalPlayEnabled) accent else secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
                if (isMuteMode) {
                    Text(
                        text = "🔇",
                        color = primaryText,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                IconButton(
                    onClick = { isOverlayExpanded = false },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Свернуть",
                        tint = primaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Настройки IntervalPlay",
                        tint = primaryText,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IntervalPlay",
                    modifier = Modifier.weight(1f),
                    color = primaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Switch(
                    checked = isIntervalPlayEnabled,
                    onCheckedChange = onIntervalPlayEnabledChange
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mute (Тишина)",
                    modifier = Modifier.weight(1f),
                    color = primaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Switch(
                    checked = isMuteMode,
                    onCheckedChange = onMuteModeChange
                )
            }

            if (isIntervalPlayEnabled && isMuteMode) {
                Text(
                    text = "🔇 Тишина: ${muteCountdownSeconds}с",
                    color = secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }

            // This is a visual placeholder; it is not connected to audio fading.
            Text(
                text = "Fade: 1s (ON)",
                color = secondaryText,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}
