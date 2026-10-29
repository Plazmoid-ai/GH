/*
 *     Copyright (C) 2023 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private val DEBUG_ZONE_COUNTS = listOf(1, 3, 4, 5, 7)

@Composable
fun SnippetsTrackMap(
    bounds: List<LongRange>,
    colors: SnippetsColors,
    durationMs: Long,
    currentPositionMs: Long,
    playWhenReady: Boolean,
) {
    // TODO этап 9: удалить — временный цикл тестовых наборов по долгому нажатию.
    var debugSetIndex by remember { mutableIntStateOf(-1) }
    val backgroundColor = colors.mapTrack
    val zoneColor = colors.accent
    val outlineColor = colors.accent.copy(alpha = 0.55f)

    val displayedBounds = if (debugSetIndex < 0) {
        bounds
    } else {
        val requestedCount = DEBUG_ZONE_COUNTS[debugSetIndex]
        val lengthMs = bounds.firstOrNull()?.let { it.endMsExclusive - it.first } ?: 0L
        val debugState = when (requestedCount) {
            1 -> SnippetsState(
                mode = SnippetsMode.PREVIEW,
                selectedPreviewZone = 0,
                activeHomeZones = listOf(true, false, false),
                extraIntervalsCount = 0,
            )
            else -> SnippetsState(
                mode = SnippetsMode.INTERVALS,
                selectedPreviewZone = 0,
                activeHomeZones = listOf(true, true, true),
                extraIntervalsCount = requestedCount - 3,
            )
        }.normalized()
        bounds(
            debugState,
            SnippetsContext(durationMs = durationMs, lengthMs = lengthMs),
        )
    }

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
            .pointerInput(debugSetIndex, displayedBounds, durationMs) {
                detectTapGestures(
                    onLongPress = {
                        debugSetIndex = (debugSetIndex + 1) % DEBUG_ZONE_COUNTS.size
                    },
                )
            }
            .drawBehind {
                if (durationMs <= 0L) return@drawBehind

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
                    if (endMs <= currentPositionMs && endMs > startMs) {
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
                if (currentIndex >= 0 && currentIndex < displayedBounds.size) {
                    val currentRange = displayedBounds[currentIndex]
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
                            (currentPositionMs - startMs).toFloat() /
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

                // 4. Future zones get subtle markers; no opaque zone blocks are drawn
                // over the progress fill or current-zone outline.
                displayedBounds.forEach { range ->
                    if (range.first > currentPositionMs) {
                        val startX = xFor(range.first)
                        val endX = xFor(range.endMsExclusive)
                        if (endX > startX) {
                            drawRoundRect(
                                color = outlineColor,
                                topLeft = Offset(startX, inset),
                                size = Size(endX - startX, innerHeight),
                                cornerRadius = CornerRadius(innerRadius, innerRadius),
                                style = Stroke(width = strokeWidth),
                            )
                        }
                    }
                }
            },
    )
}
