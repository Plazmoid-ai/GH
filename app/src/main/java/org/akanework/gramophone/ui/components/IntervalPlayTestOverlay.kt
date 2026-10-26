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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val shape = RoundedCornerShape(12.dp)
    val panelBackground = Color(0xFF18181B).copy(alpha = 0.8f)
    val primaryText = Color(0xFFF3F3F6)
    val secondaryText = Color(0xFFA8A8A8)
    val accent = Color(0xFF9B8AF5)
    val status = when {
        !isIntervalPlayEnabled -> "IntervalPlay: выкл."
        isMuteMode -> "Режим: Тишина"
        else -> "Режим: Пропуск"
    }

    Column(
        modifier = Modifier
            .width(218.dp)
            .clip(shape)
            .background(panelBackground)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = status,
                modifier = Modifier.weight(1f),
                color = if (isIntervalPlayEnabled) accent else secondaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Настройки IntervalPlay",
                    tint = primaryText,
                    modifier = Modifier.size(18.dp)
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
    }
}
