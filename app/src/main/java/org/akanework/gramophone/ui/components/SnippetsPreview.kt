/*
 *     Copyright (C) 2026 OpenAI
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.SystemClock

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

        SnippetProgressCanvas(menuStyle = true)

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Акцент",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        val accents = listOf(
            "По умолчанию" to Color(0xFF9B8AF5),
            "Градиент" to Color(0xFF9B8AF5),
            "Прозрачный" to Color.Transparent,
            "Черный" to Color(0xFF000000),
            "Красный" to Color(0xFFE53935),
            "Оранжевый" to Color(0xFFFF9800),
            "Голубой" to Color(0xFF29B6F6),
            "Зеленый" to Color(0xFF43A047),
            "Розовый" to Color(0xFFEC407A),
            "Пурпурный" to Color(0xFFAB47BC),
            "Синий" to Color(0xFF1E88E5),
        )
        accents.chunked(4).forEach { rowAccents ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            ) {
                rowAccents.forEach { (name, color) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { SnippetsPreviewState.selectedAccentName = name },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val selected = SnippetsPreviewState.selectedAccentName == name
                        val swatchModifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .then(
                                if (name == "Градиент") {
                                    Modifier.background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF9B8AF5), Color(0xFFEC407A), Color(0xFF29B6F6)),
                                        ),
                                    )
                                } else {
                                    Modifier.background(color)
                                },
                            )
                            .then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                        Box(modifier = swatchModifier)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            maxLines = 1,
                        )
                    }
                }
                repeat(4 - rowAccents.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
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
                    label = { Text("Секунды (1–600)") },
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

@Composable
private fun DurationAdjustButton(label: String, step: Int) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        coroutineScope {
                            var isPressed = true
                            var repeated = false
                            val pressedAt = SystemClock.elapsedRealtime()
                            val repeatJob = launch {
                                while (isPressed) {
                                    delay(100)
                                    if (!isPressed) break
                                    val heldMs = SystemClock.elapsedRealtime() - pressedAt
                                    val accelerationStep = when {
                                        heldMs > 20_000L -> 10
                                        heldMs >= 10_000L -> 5
                                        else -> 1
                                    }
                                    SnippetsPreviewState.updateDuration(
                                        SnippetsPreviewState.previewDurationSec + step * accelerationStep,
                                    )
                                    repeated = true
                                }
                            }
                            try {
                                tryAwaitRelease()
                            } finally {
                                isPressed = false
                                repeatJob.cancel()
                            }
                            if (!repeated) {
                                SnippetsPreviewState.updateDuration(
                                    SnippetsPreviewState.previewDurationSec + step,
                                )
                            }
                        }
                    },
                )
            },
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
fun SnippetProgressCanvas(
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    height: Dp = 32.dp,
    menuStyle: Boolean = false,
) {
    val zones = SnippetsPreviewState.activeZones
    val currentPositionMs = SnippetsPreviewState.currentPositionMs
    val trackDurationMs = SnippetsPreviewState.trackDurationMs
    val snippetBounds = SnippetsPreviewState.snippetBounds
    val previewDurationSec = SnippetsPreviewState.previewDurationSec
    val selectedAccentName = SnippetsPreviewState.selectedAccentName
    val cursorProgressTarget = if (trackDurationMs > 0L) {
        (currentPositionMs.toFloat() / trackDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val cursorProgress by animateFloatAsState(
        targetValue = cursorProgressTarget,
        animationSpec = tween(durationMillis = 70, easing = LinearEasing),
        label = "snippetsPlayerCursor",
    )
    val selectedAccentColor = SnippetsPreviewState.selectedAccentColor
    val accentBrush = if (selectedAccentName == "Градиент") {
        Brush.horizontalGradient(
            listOf(Color(0xFF9B8AF5), Color(0xFFEC407A), Color(0xFF29B6F6)),
        )
    } else {
        Brush.horizontalGradient(listOf(selectedAccentColor, selectedAccentColor))
    }

    var canvasWidthPx by remember { mutableFloatStateOf(1f) }
    var canvasModifier = modifier
        .fillMaxWidth()
        .height(height)
        .onSizeChanged { canvasWidthPx = it.width.toFloat() }
    if (interactive) {
        canvasModifier = canvasModifier.pointerInput(canvasWidthPx) {
            detectTapGestures { position ->
                val zoneIndex = (position.x / (canvasWidthPx / 3f)).toInt().coerceIn(0, 2)
                SnippetsPreviewState.toggleZone(zoneIndex)
            }
        }
    }

    Canvas(modifier = canvasModifier) {
        val segmentWidth = size.width / 3f
        val barHeight = if (menuStyle) size.height else 10.dp.toPx()
        val top = if (menuStyle) 0f else (size.height - barHeight) / 2f
        val radius = CornerRadius(if (menuStyle) 8.dp.toPx() else barHeight / 2f)

        if (menuStyle) {
            // Keep the three separate, tappable capsule switches in the settings menu.
            for (index in 0..2) {
                val gap = 2.dp.toPx()
                val left = index * segmentWidth + gap / 2f
                val right = (index + 1) * segmentWidth - gap / 2f
                if (zones[index]) {
                    drawRoundRect(
                        brush = accentBrush,
                        topLeft = Offset(left, top),
                        size = Size((right - left).coerceAtLeast(0f), barHeight),
                        cornerRadius = radius,
                    )
                } else {
                    drawRoundRect(
                        color = Color(0xFFA8A8A8),
                        topLeft = Offset(left, top),
                        size = Size((right - left).coerceAtLeast(0f), barHeight),
                        cornerRadius = radius,
                    )
                }
                if (zones[index]) {
                    val centerX = (index + 0.5f) * segmentWidth
                    val centerY = size.height / 2f
                    val triangleWidth = 12.dp.toPx()
                    val triangleHeight = 16.dp.toPx()
                    val play = Path().apply {
                        moveTo(centerX - triangleWidth / 2f, centerY - triangleHeight / 2f)
                        lineTo(centerX - triangleWidth / 2f, centerY + triangleHeight / 2f)
                        lineTo(centerX + triangleWidth / 2f, centerY)
                        close()
                    }
                    drawPath(play, Color.White)
                }
            }
        } else {
            // One continuous grey track, with active sections painted over it.
            drawRoundRect(
                color = Color(0xFF555555),
                topLeft = Offset(0f, top),
                size = Size(size.width, barHeight),
                cornerRadius = radius,
            )
            if (trackDurationMs > 0L && selectedAccentColor.alpha > 0f) {
                snippetBounds.forEach { (startMs, endMs) ->
                    val left = (startMs.toFloat() / trackDurationMs.toFloat()) * size.width
                    val right = (endMs.toFloat() / trackDurationMs.toFloat()) * size.width
                    if (right > left) {
                        drawRoundRect(
                            brush = accentBrush,
                            topLeft = Offset(left, top),
                            size = Size(right - left, barHeight),
                            cornerRadius = CornerRadius(barHeight / 2f),
                        )
                    }
                }
            }
            val cursorX = (cursorProgress * size.width).coerceIn(0f, size.width)
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(cursorX, size.height / 2f),
            )
        }
    }
}

@Composable
fun SnippetCapsuleOverlay(modifier: Modifier = Modifier) {
    var capsuleOffsetX by remember { mutableFloatStateOf(0f) }
    var capsuleOffsetY by remember { mutableFloatStateOf(0f) }
    var capsuleSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val maxX = (with(density) { maxWidth.toPx() } - capsuleSize.width)
            .coerceAtLeast(0f)
        val maxY = (with(density) { maxHeight.toPx() } - capsuleSize.height)
            .coerceAtLeast(0f)
        val minOffsetX = -maxX / 2f
        val maxOffsetX = maxX / 2f

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset {
                    IntOffset(capsuleOffsetX.roundToInt(), capsuleOffsetY.roundToInt())
                }
                .onSizeChanged { capsuleSize = it }
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragEnd = {
                            capsuleOffsetX = if (capsuleOffsetX <= 0f) minOffsetX else maxOffsetX
                            capsuleOffsetY = capsuleOffsetY.coerceIn(0f, maxY)
                        },
                        onDragCancel = {
                            capsuleOffsetX = if (capsuleOffsetX <= 0f) minOffsetX else maxOffsetX
                            capsuleOffsetY = capsuleOffsetY.coerceIn(0f, maxY)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            capsuleOffsetX =
                                (capsuleOffsetX + dragAmount.x).coerceIn(minOffsetX, maxOffsetX)
                            capsuleOffsetY =
                                (capsuleOffsetY + dragAmount.y).coerceIn(0f, maxY)
                        },
                    )
                }
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.2f),
                    shape = CircleShape,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Сниппет: ${SnippetsPreviewState.previewDurationSec}с",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun formatSnippetDuration(seconds: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
