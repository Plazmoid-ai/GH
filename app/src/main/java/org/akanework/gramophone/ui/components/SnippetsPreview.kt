/*
 *     Copyright (C) 2026 OpenAI
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetsMenuLayer(onDismiss: () -> Unit) {
    if (SnippetsPreviewState.showSnippetsMenu) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SnippetsMenuContent()
        }
    }
}

@Composable
private fun SnippetsMenuContent() {
    var showDurationDialog by remember { mutableStateOf(false) }
    var durationInput by remember(SnippetsPreviewState.previewDurationSec) {
        mutableStateOf(SnippetsPreviewState.previewDurationSec.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Snippets Preview",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            DurationAdjustButton(label = "−", step = -1)
            Text(
                text = formatSnippetDuration(SnippetsPreviewState.previewDurationSec),
                modifier = Modifier.clickable {
                    durationInput = SnippetsPreviewState.previewDurationSec.toString()
                    showDurationDialog = true
                },
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            DurationAdjustButton(label = "+", step = 1)
        }

        Spacer(Modifier.height(24.dp))

        SnippetProgressCanvas()

        Spacer(Modifier.height(18.dp))

        SnippetCapsule()

        Spacer(Modifier.height(12.dp))
    }

    if (showDurationDialog) {
        AlertDialog(
            onDismissRequest = { showDurationDialog = false },
            title = { Text("Длительность, секунд") },
            text = {
                OutlinedTextField(
                    value = durationInput,
                    onValueChange = { input ->
                        durationInput = input.filter(Char::isDigit).take(3)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Секунды (1–180)") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val seconds = durationInput.toIntOrNull()
                        if (seconds != null) {
                            SnippetsPreviewState.updateDuration(seconds)
                        }
                        showDurationDialog = false
                    },
                ) {
                    Text("Применить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDurationDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DurationAdjustButton(label: String, step: Int) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .combinedClickable(
                onClick = {
                    SnippetsPreviewState.updateDuration(
                        SnippetsPreviewState.previewDurationSec + step,
                    )
                },
                onLongClick = {
                    SnippetsPreviewState.updateDuration(
                        SnippetsPreviewState.previewDurationSec + step * 5,
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun SnippetProgressCanvas() {
    val totalMs = SnippetsPreviewState.DUMMY_TRACK_DURATION_MS
    val startMs = SnippetsPreviewState.snippetStartMs
    val endMs = SnippetsPreviewState.snippetEndMs
    var trackWidthPx by remember { mutableFloatStateOf(1f) }

    val moveToPointer: (Float) -> Unit = { x ->
        if (trackWidthPx > 0f) {
            val fraction = (x / trackWidthPx).coerceIn(0f, 1f)
            SnippetsPreviewState.moveSnippetTo((fraction * totalMs).toLong())
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(trackWidthPx) {
                detectTapGestures { position -> moveToPointer(position.x) }
            }
            .pointerInput(trackWidthPx) {
                detectDragGestures(
                    onDragStart = { position -> moveToPointer(position.x) },
                    onDrag = { change, _ ->
                        change.consume()
                        moveToPointer(change.position.x)
                    },
                )
            },
    ) {
        val barHeight = 10.dp.toPx()
        val top = (size.height - barHeight) / 2f
        val startX = (startMs.toFloat() / totalMs.toFloat() * size.width)
            .coerceIn(0f, size.width)
        val endX = (endMs.toFloat() / totalMs.toFloat() * size.width)
            .coerceIn(startX, size.width)
        val radius = CornerRadius(barHeight / 2f, barHeight / 2f)

        if (startX > 0f) {
            drawRoundRect(
                color = Color(0xFFB0B0B0),
                topLeft = Offset(0f, top),
                size = Size(startX, barHeight),
                cornerRadius = radius,
            )
        }
        if (endX > startX) {
            drawRoundRect(
                color = Color(0xFF8B5CF6),
                topLeft = Offset(startX, top),
                size = Size(endX - startX, barHeight),
                cornerRadius = radius,
            )
        }
        if (endX < size.width) {
            drawRoundRect(
                color = Color(0xFFB0B0B0),
                topLeft = Offset(endX, top),
                size = Size(size.width - endX, barHeight),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun SnippetCapsule() {
    var capsuleOffsetX by remember { mutableFloatStateOf(0f) }
    var capsuleOffsetY by remember { mutableFloatStateOf(0f) }
    var capsuleSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        val maxX = (with(density) { maxWidth.toPx() } - capsuleSize.width)
            .coerceAtLeast(0f)
        val maxY = (with(density) { maxHeight.toPx() } - capsuleSize.height)
            .coerceAtLeast(0f)

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(capsuleOffsetX.roundToInt(), capsuleOffsetY.roundToInt())
                }
                .onSizeChanged { capsuleSize = it }
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragEnd = {
                            capsuleOffsetX = if (capsuleOffsetX <= maxX / 2f) 0f else maxX
                            capsuleOffsetY = capsuleOffsetY.coerceIn(0f, maxY)
                        },
                        onDragCancel = {
                            capsuleOffsetX = if (capsuleOffsetX <= maxX / 2f) 0f else maxX
                            capsuleOffsetY = capsuleOffsetY.coerceIn(0f, maxY)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            capsuleOffsetX =
                                (capsuleOffsetX + dragAmount.x).coerceIn(0f, maxX)
                            capsuleOffsetY =
                                (capsuleOffsetY + dragAmount.y).coerceIn(0f, maxY)
                        },
                    )
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Сниппет: ${SnippetsPreviewState.previewDurationSec}с",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun formatSnippetDuration(seconds: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
