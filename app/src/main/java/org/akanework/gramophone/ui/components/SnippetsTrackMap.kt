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
import androidx.compose.material3.MaterialTheme
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
    durationMs: Long,
    positionProvider: () -> Long,
    playWhenReady: Boolean,
) {
    // TODO этап 9: удалить — временный цикл тестовых наборов по долгому нажатию.
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

    val currentIndexState = remember(displayedBounds, durationMs, playWhenReady) {
        derivedStateOf {
            // positionProvider is evaluated only when this state is read in drawBehind.
            val position = positionProvider()
            displayedBounds.indexOfLast { it.first <= position }
                .coerceIn(-1, displayedBounds.lastIndex)
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

                // Reading the derived state here registers a draw-phase dependency, not a composition dependency.
                val currentIndex = currentIndexState.value
                val positionMs = positionProvider()

                val width = size.width
                val heightPx = size.height
                val outerRadius = heightPx / 2f
                val inset = 5.dp.toPx()
                val innerHeight = (heightPx - inset * 2f).coerceAtLeast(0f)
                val innerRadius = 3.dp.toPx()

                drawRoundRect(
                    color = backgroundColor,
                    topLeft = Offset.Zero,
                    size = Size(width, heightPx),
                    cornerRadius = CornerRadius(outerRadius, outerRadius),
                )

                displayedBounds.forEach { range ->
                    val left = (range.first.toDouble() / durationMs.toDouble() * width)
                        .toFloat().coerceIn(0f, width)
                    val right = (range.endMsExclusive.toDouble() / durationMs.toDouble() * width)
                        .toFloat().coerceIn(0f, width)
                    if (right > left) {
                        drawRoundRect(
                            color = zoneColor,
                            topLeft = Offset(left, inset),
                            size = Size(right - left, innerHeight),
                            cornerRadius = CornerRadius(innerRadius, innerRadius),
                        )
                    }
                }

                if (currentIndex >= 0 && displayedBounds.isNotEmpty()) {
                    val currentRange = displayedBounds[currentIndex]
                    val fillBoundary = if (currentIndex == 0) {
                        0f
                    } else {
                        val previousEnd = displayedBounds[currentIndex - 1].endMsExclusive
                        (((previousEnd + currentRange.first) / 2.0) / durationMs.toDouble() * width)
                            .toFloat().coerceIn(0f, width)
                    }
                    val outlineBoundary = if (currentIndex == displayedBounds.lastIndex) {
                        width
                    } else {
                        val nextStart = displayedBounds[currentIndex + 1].first
                        (((currentRange.endMsExclusive + nextStart) / 2.0) / durationMs.toDouble() * width)
                            .toFloat().coerceIn(0f, width)
                    }
                    val afterLastZone = currentIndex == displayedBounds.lastIndex &&
                        positionMs >= currentRange.endMsExclusive
                    val effectiveFillBoundary = if (afterLastZone) width else fillBoundary

                    if (positionMs > displayedBounds.first().first && effectiveFillBoundary > 0f) {
                        val fillPath = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(effectiveFillBoundary, 0f)
                            lineTo(effectiveFillBoundary, heightPx)
                            lineTo(0f, heightPx)
                            quadraticTo(0f, heightPx, 0f, heightPx - outerRadius)
                            lineTo(0f, outerRadius)
                            quadraticTo(0f, 0f, outerRadius, 0f)
                            close()
                        }
                        drawPath(fillPath, fillColor)
                    }

                    val stroke = 2.dp.toPx()
                    val leftRadius = outerRadius
                    val rightRadius = 6.dp.toPx()
                    val outlinePath = Path().apply {
                        moveTo(leftRadius, stroke / 2f)
                        lineTo((outlineBoundary - rightRadius).coerceAtLeast(leftRadius), stroke / 2f)
                        quadraticTo(outlineBoundary, stroke / 2f, outlineBoundary, rightRadius)
                        lineTo(outlineBoundary, heightPx - rightRadius)
                        quadraticTo(outlineBoundary, heightPx - stroke / 2f, (outlineBoundary - rightRadius).coerceAtLeast(leftRadius), heightPx - stroke / 2f)
                        lineTo(leftRadius, heightPx - stroke / 2f)
                        quadraticTo(stroke / 2f, heightPx - stroke / 2f, stroke / 2f, heightPx - leftRadius)
                        lineTo(stroke / 2f, leftRadius)
                        quadraticTo(stroke / 2f, stroke / 2f, leftRadius, stroke / 2f)
                        close()
                    }
                    drawPath(
                        path = outlinePath,
                        color = outlineColor,
                        style = Stroke(width = stroke),
                    )
                }
            },
    )
}
