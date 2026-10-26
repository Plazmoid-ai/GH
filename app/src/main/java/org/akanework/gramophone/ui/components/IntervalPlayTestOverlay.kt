/*
 *     Copyright (C) 2026 Plazmoid-ai
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of the License,
 *     or (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt

@Composable
fun IntervalPlayTestOverlay(
    isIntervalPlayEnabled: Boolean,
    isMuteMode: Boolean,
    collapsedOrigin: IntOffset,
    coverCenter: IntOffset,
    onIntervalPlayEnabledChange: (Boolean) -> Unit,
    onMuteModeChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    var isOverlayExpanded by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var expandedPanelSize by remember { mutableStateOf(IntSize.Zero) }

    val shape = RoundedCornerShape(12.dp)
    val primaryText = Color(0xFFF3F3F6)
    val secondaryText = Color(0xFFA8A8A8)
    val accent = Color(0xFF9B8AF5)
    val panelBackground = Color(0xFF18181B).copy(alpha = if (isOverlayExpanded) 0.9f else 0.8f)

    if (!isIntervalPlayEnabled) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        var collapsedCapsuleSize by remember { mutableStateOf(IntSize.Zero) }

        if (!isOverlayExpanded) {
            Row(
                modifier = Modifier
                    .offset {
                        val maxX = (maxWidthPx - collapsedCapsuleSize.width).coerceAtLeast(0f)
                        val maxY = (maxHeightPx - collapsedCapsuleSize.height).coerceAtLeast(0f)
                        IntOffset(
                            (collapsedOrigin.x + offsetX.roundToInt()).coerceIn(0, maxX.roundToInt()),
                            (collapsedOrigin.y + offsetY.roundToInt()).coerceIn(0, maxY.roundToInt())
                        )
                    }
                    .onSizeChanged { collapsedCapsuleSize = it }
                    .width(220.dp)
                    .height(36.dp)
                    .clip(shape)
                    .background(panelBackground)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress { change, dragAmount ->
                            change.consume()
                            val capsuleWidth = if (collapsedCapsuleSize.width > 0) collapsedCapsuleSize.width.toFloat() else with(density) { 220.dp.toPx() }
                            val capsuleHeight = if (collapsedCapsuleSize.height > 0) collapsedCapsuleSize.height.toFloat() else with(density) { 36.dp.toPx() }
                            val minOffsetX = -collapsedOrigin.x.toFloat()
                            val maxOffsetX = (maxWidthPx - capsuleWidth - collapsedOrigin.x).coerceAtLeast(minOffsetX)
                            val minOffsetY = -collapsedOrigin.y.toFloat()
                            val maxOffsetY = (maxHeightPx - capsuleHeight - collapsedOrigin.y).coerceAtLeast(minOffsetY)
                            offsetX = (offsetX + dragAmount.x).coerceIn(minOffsetX, maxOffsetX)
                            offsetY = (offsetY + dragAmount.y).coerceIn(minOffsetY, maxOffsetY)
                        }
                    }
                    .clickable { isOverlayExpanded = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isIntervalPlayEnabled) "Ручной" else "IntervalPlay выкл.",
                    color = if (isIntervalPlayEnabled) accent else secondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "30с/10с",
                        color = primaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (isMuteMode) {
                        Text(text = "🔇", color = primaryText, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            coverCenter.x - expandedPanelSize.width / 2,
                            coverCenter.y - expandedPanelSize.height / 2
                        )
                    }
                    .onSizeChanged { expandedPanelSize = it }
                    .width(228.dp)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .clip(shape)
                    .background(panelBackground)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isOverlayExpanded = false },
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
                        Text(text = "🔇", color = primaryText, fontSize = 12.sp, maxLines = 1)
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

                Text(
                    text = "Сменить режим",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { /* UI placeholder for future preset/mode selection. */ }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    color = primaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

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
                        text = "🔇 Mute: направление индикатора обратное",
                        color = secondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                // UI placeholder only; it is not connected to actual audio fading.
                Text(
                    text = "Fade: 1s (ON)",
                    color = secondaryText,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}