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

                // 2. Fully elapsed zones are filled independently, never across the gaps.
                displayedBounds.forEach { range ->
                    val startMs = range.first
                    val endMs = range.endMsExclusive
                    if (endMs <= drawPositionMs && endMs > startMs) {
                        val startX = xFor(startMs)
                        val endX = xFor(endMs)
                        if (endX > startX) {
                            drawRoundRect(
                                color = zoneColor,
                                topLeft = Offset(startX, inset),
                                size = Size(endX - startX, innerHeight),
                                cornerRadius = CornerRadius(innerRadius, innerRadius),
                            )
                        }
                    }
                }

                // 3. Only a position inside a zone makes that zone current.
                // If position is in a gap, currentIndex is -1 and no partial zone is filled.
                if (drawCurrentIndex >= 0 && drawCurrentIndex < displayedBounds.size) {
                    val currentRange = displayedBounds[drawCurrentIndex]
                    val startMs = currentRange.first
                    val endMs = currentRange.endMsExclusive
                    val startX = xFor(startMs)
                    val endX = xFor(endMs)

                    if (endMs > startMs && endX > startX) {
                        // Outline the complete current zone first.
                        drawRoundRect(
                            color = zoneColor,
                            topLeft = Offset(startX, inset),
                            size = Size(endX - startX, innerHeight),
                            cornerRadius = CornerRadius(innerRadius, innerRadius),
                            style = Stroke(width = strokeWidth),
                        )

                        // Then fill only the elapsed portion within this zone.
                        val progressInZone = (
                            (drawPositionMs - startMs).toFloat() /
                                (endMs - startMs).toFloat()
                            ).coerceIn(0f, 1f)
                        val progressX = startX + (endX - startX) * progressInZone
                        if (progressX > startX) {
                            drawRoundRect(
                                color = zoneColor,
                                topLeft = Offset(startX, inset),
                                size = Size(progressX - startX, innerHeight),
                                cornerRadius = CornerRadius(innerRadius, innerRadius),
                            )
                        }
                    }
                }

                // 4. Static interval blocks are drawn LAST over the stepped fill/outline.
                displayedBounds.forEach { range ->
                    val startX = xFor(range.first)
                    val endX = xFor(range.endMsExclusive)
                    if (endX > startX) {
                        drawRoundRect(
                            color = zoneColor,
                            topLeft = Offset(startX, inset),
                            size = Size(endX - startX, innerHeight),
                            cornerRadius = CornerRadius(innerRadius, innerRadius),
                        )
                    }
                }
            },
    )
}
