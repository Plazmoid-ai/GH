/*
 *     Copyright (C) 2023 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// TODO очистка: удалить скрытый debug-hook после завершения песочницы.
private const val SANDBOX_MAP_DEBUG_ENABLED = false

@Composable
fun SnippetsTrackMap(
    bounds: List<LongRange>,
    colors: SnippetsColors,
    durationMs: Long,
    currentPositionMs: Long,
    playWhenReady: Boolean,
    positionProvider: (() -> Long)? = null,
) {
    // TODO очистка: временные тестовые наборы отключены флагом.
    val backgroundColor = colors.mapTrack
    val zoneColor = colors.accent
    val outlineColor = colors.accent.copy(alpha = 0.55f)

    val displayedBounds = bounds

    // currentPositionMs is supplied from Compose state updated by FullBottomSheet's
    // existing positionRunnable, so position changes invalidate this composable.
    val currentIndex by remember(displayedBounds, durationMs, currentPositionMs) {
        derivedStateOf {
            if (durationMs <= 0L) {
                -1
            } else {
                displayedBounds.indexOfFirst { range ->
                    currentPositionMs >= range.first &&
                        currentPositionMs < range.endMsExclusive
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .drawBehind {
                if (durationMs <= 0L) return@drawBehind
                val drawPositionMs = positionProvider?.invoke() ?: currentPositionMs
                val drawCurrentIndex = displayedBounds.indexOfFirst { it.first <= drawPositionMs && drawPositionMs < it.endMsExclusive }

                val width = size.width
                val heightPx = size.height
                val inset = 5.dp.toPx()
                val innerHeight = (heightPx - inset * 2f).coerceAtLeast(0f)
                val innerRadius = 3.dp.toPx()
                val strokeWidth = 1.5.dp.toPx()

                fun xFor(positionMs: Long): Float =
                    (positionMs.toDouble() / durationMs.toDouble() * width)
                        .toFloat()
                        .coerceIn(0f, width)

                // 1. Base track: all future space remains gray.
                drawRoundRect(
                    color = backgroundColor,
                    topLeft = Offset.Zero,
                    size = Size(width, heightPx),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                )

                // Resolve the logical current zone during drawing from the live player position.
                val drawCurrentIndex = displayedBounds.indexOfFirst { drawPositionMs < it.endMsExclusive }
                val currentZone = if (drawCurrentIndex >= 0) displayedBounds[drawCurrentIndex] else null
                val previousBoundaryMs = if (drawCurrentIndex > 0) {
                    val previousEnd = displayedBounds[drawCurrentIndex - 1].endMsExclusive
                    previousEnd + (displayedBounds[drawCurrentIndex].first - previousEnd) / 2L
                } else 0L
                val nextBoundaryMs = if (drawCurrentIndex >= 0 && drawCurrentIndex < displayedBounds.lastIndex) {
                    val currentEnd = displayedBounds[drawCurrentIndex].endMsExclusive
                    val nextStart = displayedBounds[drawCurrentIndex + 1].first
                    currentEnd + (nextStart - currentEnd) / 2L
                } else durationMs

                // 2. Step fill: everything before the current-zone boundary is filled.
                val fillEndMs = if (currentZone == null) durationMs else previousBoundaryMs
                if (fillEndMs > 0L) {
                    drawRoundRect(
                        color = zoneColor,
                        topLeft = Offset.Zero,
                        size = Size(xFor(fillEndMs), heightPx),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    )
                }

                // 3. Outline runs from the beginning through the boundary after current zone.
                if (currentZone != null) {
                    drawRoundRect(
                        color = zoneColor,
                        topLeft = Offset.Zero,
                        size = Size(xFor(nextBoundaryMs), heightPx),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                        style = Stroke(width = strokeWidth),
                    )
                }

                // 4. Static interval blocks are drawn LAST over the fill and outline.
                displayedBounds.forEach { range ->
                    val startX = xFor(range.first)
                    val endX = xFor(range.endMsExclusive)
                    if (endX > startX) {
                        drawRoundRect(
                            color = zoneColor.copy(alpha = 0.22f),
                            topLeft = Offset(startX, inset),
                            size = Size(endX - startX, innerHeight),
                            cornerRadius = CornerRadius(innerRadius, innerRadius),
                        )
                        drawRoundRect(
                            color = outlineColor,
                            topLeft = Offset(startX, inset),
                            size = Size(endX - startX, innerHeight),
                            cornerRadius = CornerRadius(innerRadius, innerRadius),
                            style = Stroke(width = strokeWidth),
                        )
                    }
                }
            },
    )
}
