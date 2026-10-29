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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlin.math.max

private val DEBUG_ZONE_COUNTS = listOf(1, 3, 4, 5, 7)

@Composable
fun SnippetsTrackMap(
    bounds: List<LongRange>,
    durationMs: Long,
    positionProvider: () -> Long,
    playWhenReady: Boolean,
) {
    // TODO этап 9: удалить — temporary long-press cycle for visual verification.
    var debugSetIndex by remember { mutableIntStateOf(-1) }
    val colors = MaterialTheme.colorScheme
    val backgroundColor = colors.surfaceVariant
    val zoneColor = colors.primary
    val fillColor = colors.tertiaryContainer
    val outlineColor = colors.tertiary

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
        SnippetsIntervalLogic.bounds(
            debugState,
            SnippetsContext(durationMs = durationMs, lengthMs = lengthMs),
        )
    }

    val zoneIndexAtPosition by remember(displayedBounds, durationMs, playWhenReady) {
        derivedStateOf {
            // Evaluated only from the draw phase below; position is never read in composition.
            val position = positionProvider()
            displayedBounds.indexOfLast { it.first <= position }
                .coerceIn(-1, displayedBounds.lastIndex)
        }
    }

    Modifier
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .padding(0.dp)
            .pointerInput(debugSetIndex, displayedBounds, durationMs) {
                detectTapGestures(
                    onLongPress = {
                        debugSetIndex = (debugSetIndex + 1) % DEBUG_ZONE_COUNTS.size
                    },
                )
            }
            .drawBehind {
                val trackDuration = durationMs
                if (trackDuration <= 0L) return@drawBehind

                val position = positionProvider()
                val currentIndex = displayedBounds.indexOfLast { it.first <= position }
                    .coerceIn(-1, displayedBounds.lastIndex)

                val width = size.width
                val heightPx = size.height
                val outerRadius = heightPx / 2f
                val innerInset = 5.dp.toPx()
                val innerTop = innerInset
                val innerHeight = (heightPx - innerInset * 2f).coerceAtLeast(0f)
                val innerRadius = 3.dp.toPx()

                drawRoundRect(
                    color = backgroundColor,
                    topLeft = Offset.Zero,
                    size = Size(width, heightPx),
                    cornerRadius = CornerRadius(outerRadius, outerRadius),
                )

                displayedBounds.forEach { range ->
                    val left = (range.first.toDouble() / trackDuration.toDouble() * width).toFloat()
                        .coerceIn(0f, width)
                    val right = (range.endMsExclusive.toDouble() / trackDuration.toDouble() * width).toFloat()
                        .coerceIn(0f, width)
                    if (right > left) {
                        drawRoundRect(
                            color = zoneColor,
                            topLeft = Offset(left, innerTop),
                            size = Size(right - left, innerHeight),
                            cornerRadius = CornerRadius(innerRadius, innerRadius),
                        )
                    }
                }

                if (currentIndex >= 0 && displayedBounds.isNotEmpty()) {
                    val currentRange = displayedBounds[currentIndex]
                    val fillBoundary = if (currentIndex == displayedBounds.lastIndex) {
                        width
                    } else {
                        val nextStart = displayedBounds[currentIndex + 1].first
                        val currentEnd = currentRange.endMsExclusive
                        (((currentEnd + nextStart) / 2.0) / trackDuration.toDouble() * width)
                            .toFloat().coerceIn(0f, width)
                    }
                    val outlineBoundary = if (currentIndex == displayedBounds.lastIndex) {
                        width
                    } else {
                        val nextStart = displayedBounds[currentIndex + 1].first
                        val currentEnd = currentRange.endMsExclusive
                        (((currentEnd + nextStart) / 2.0) / trackDuration.toDouble() * width)
                            .toFloat().coerceIn(0f, width)
                    }
                    if (position > displayedBounds.first().first) {
                        val fillPath = Path().apply {
                            val radius = outerRadius
                            moveTo(0f, 0f)
                            lineTo((fillBoundary - radius).coerceAtLeast(0f), 0f)
                            quadraticTo(fillBoundary, 0f, fillBoundary, radius)
                            lineTo(fillBoundary, heightPx - radius)
                            quadraticTo(fillBoundary, heightPx, (fillBoundary - radius).coerceAtLeast(0f), heightPx)
                            lineTo(0f, heightPx)
                            close()
                        }
                        drawPath(fillPath, fillColor)
                    }
                    val outlinePath = Path().apply {
                        moveTo(outerRadius, 1.dp.toPx())
                        lineTo((outlineBoundary - 6.dp.toPx()).coerceAtLeast(outerRadius), 1.dp.toPx())
                        quadraticTo(outlineBoundary, 1.dp.toPx(), outlineBoundary, 6.dp.toPx())
                        lineTo(outlineBoundary, heightPx - 6.dp.toPx())
                        quadraticTo(outlineBoundary, heightPx - 1.dp.toPx(), (outlineBoundary - 6.dp.toPx()).coerceAtLeast(outerRadius), heightPx - 1.dp.toPx())
                        lineTo(outerRadius, heightPx - 1.dp.toPx())
                        quadraticTo(1.dp.toPx(), heightPx - 1.dp.toPx(), 1.dp.toPx(), heightPx - outerRadius)
                        lineTo(1.dp.toPx(), outerRadius)
                        quadraticTo(1.dp.toPx(), 1.dp.toPx(), outerRadius, 1.dp.toPx())
                        close()
                    }
                    drawPath(
                        path = outlinePath,
                        color = outlineColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            },
    )
}
