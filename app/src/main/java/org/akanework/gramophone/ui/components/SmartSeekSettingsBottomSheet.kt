/*
 *     Copyright (C) 2026 Plazmoid-ai
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3
 *     of the License, or (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import android.content.Context
import android.content.SharedPreferences
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale

private object IntervalPlayPalette {
    val background = Color(0xFF121212)
    val accent = Color(0xFF9B8AF5)
    val inactive = Color(0xFF1C1C24)
    val outline = Color(0xFF2A2A35)
    val grayText = Color(0xFFA8A8A8)
    val previewBackground = Color(0xFF18181B)
    val previewSkip = Color(0xFF4A4D52)
    val darkText = Color(0xFF121212)
    val whiteText = Color(0xFFF3F3F6)
}

private data class IntervalPlayPreset(
    val playSeconds: Int,
    val skipSeconds: Int
) {
    fun label(): String = "$playSeconds / $skipSeconds"
}

/**
 * IntervalPlay settings UI prototype.
 *
 * SharedPreferences and the save callback remain in the constructor to preserve existing callers,
 * but this screen intentionally uses only local Compose state and does not persist settings.
 */
class SmartSeekSettingsBottomSheet(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") preferences: SharedPreferences,
    @Suppress("UNUSED_PARAMETER") onSaved: (() -> Unit)? = null
) {
    fun show(): BottomSheetDialog {
        val dialog = BottomSheetDialog(context)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
            )
            setContent {
                IntervalPlaySettingsContent(
                    onCancel = { dialog.dismiss() },
                    onSave = { dialog.dismiss() }
                )
            }
        }
        dialog.setContentView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        dialog.setOnShowListener {
            dialog.behavior.isDraggable = false
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }
        dialog.show()
        return dialog
    }
}

@Composable
private fun IntervalPlaySettingsContent(
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val isIndicatorOn = remember { mutableStateOf(true) }
    val isPersistentOn = remember { mutableStateOf(false) }
    val activeTab = remember { mutableStateOf(0) }
    val isScissorsActive = remember { mutableStateOf(false) }
    val isPercentMode = remember { mutableStateOf(false) }
    val simpleValue = remember { mutableStateOf(30) }
    val isLocked = remember { mutableStateOf(true) }
    val playValue = remember { mutableStateOf(30) }
    val skipValue = remember { mutableStateOf(10) }
    val selectedPreset = remember { mutableStateOf(0) }
    val presets = remember {
        mutableStateOf(
            listOf<IntervalPlayPreset?>(
                IntervalPlayPreset(30, 10),
                IntervalPlayPreset(45, 15),
                null
            )
        )
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = screenHeight * 0.92f)
            .background(IntervalPlayPalette.background)
            .padding(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 14.dp)
    ) {
        Text(
            text = "Настройки IntervalPlay",
            color = IntervalPlayPalette.whiteText,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp, bottom = 14.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StateCapsule(
                    modifier = Modifier.weight(1f),
                    label = "Показывать",
                    state = isIndicatorOn
                )
                StateCapsule(
                    modifier = Modifier.weight(1f),
                    label = "Постоянный",
                    state = isPersistentOn
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Тип интервалов",
                    color = IntervalPlayPalette.grayText,
                    fontSize = 12.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(IntervalPlayPalette.inactive)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ModeTab(
                        modifier = Modifier.weight(1f),
                        label = "Простой",
                        selected = activeTab.value == 0,
                        onClick = { activeTab.value = 0 }
                    )
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = 1.dp,
                                color = if (isScissorsActive.value) {
                                    IntervalPlayPalette.accent
                                } else {
                                    IntervalPlayPalette.outline
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                isScissorsActive.value = !isScissorsActive.value
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.ContentCut,
                            contentDescription = "Ножницы",
                            tint = if (isScissorsActive.value) {
                                IntervalPlayPalette.accent
                            } else {
                                IntervalPlayPalette.grayText
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    ModeTab(
                        modifier = Modifier.weight(1f),
                        label = "Фиксированные",
                        selected = activeTab.value == 1,
                        onClick = { activeTab.value = 1 }
                    )
                }
            }

            PreviewBar()

            if (activeTab.value == 0) {
                SimpleSettings(
                    isPercentMode = isPercentMode.value,
                    onPercentModeChange = { isPercentMode.value = it },
                    simpleValue = simpleValue.value,
                    onSimpleValueChange = { simpleValue.value = it.coerceAtLeast(0) }
                )
            } else {
                FixedSettings(
                    playValue = playValue.value,
                    skipValue = skipValue.value,
                    isLocked = isLocked.value,
                    onLockToggle = { isLocked.value = !isLocked.value },
                    onAdjust = { isPlayValue, delta ->
                        if (isLocked.value) {
                            playValue.value = (playValue.value + delta).coerceAtLeast(0)
                            skipValue.value = (skipValue.value + delta).coerceAtLeast(0)
                        } else if (isPlayValue) {
                            playValue.value = (playValue.value + delta).coerceAtLeast(0)
                        } else {
                            skipValue.value = (skipValue.value + delta).coerceAtLeast(0)
                        }
                    }
                )

                PresetSelector(
                    presets = presets.value,
                    selectedIndex = selectedPreset.value,
                    onSelect = { index ->
                        presets.value[index]?.let { preset ->
                            playValue.value = preset.playSeconds
                            skipValue.value = preset.skipSeconds
                            selectedPreset.value = index
                        }
                    },
                    onSave = { index ->
                        val updated = presets.value.toMutableList()
                        updated[index] = IntervalPlayPreset(
                            playSeconds = playValue.value,
                            skipSeconds = skipValue.value
                        )
                        presets.value = updated
                        selectedPreset.value = index
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BottomActionButton(
                modifier = Modifier.weight(1f),
                label = "Отмена",
                primary = false,
                onClick = onCancel
            )
            BottomActionButton(
                modifier = Modifier.weight(1f),
                label = "Сохранить",
                primary = true,
                onClick = onSave
            )
        }
    }
}

@Composable
private fun StateCapsule(
    modifier: Modifier,
    label: String,
    state: MutableState<Boolean>
) {
    val enabled = state.value
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (enabled) IntervalPlayPalette.accent else IntervalPlayPalette.inactive
            )
            .then(
                if (enabled) Modifier
                else Modifier.border(
                    1.dp,
                    IntervalPlayPalette.outline,
                    RoundedCornerShape(24.dp)
                )
            )
            .clickable { state.value = !state.value },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$label: ${if (enabled) "Вкл" else "Выкл"}",
            color = if (enabled) IntervalPlayPalette.darkText else IntervalPlayPalette.grayText,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun ModeTab(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) IntervalPlayPalette.accent else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) IntervalPlayPalette.darkText else IntervalPlayPalette.grayText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun PreviewBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(25.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(IntervalPlayPalette.previewBackground)
            .padding(start = 0.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(IntervalPlayPalette.accent)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(IntervalPlayPalette.previewSkip)
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = "12 инт.",
            color = IntervalPlayPalette.grayText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "03:45",
            color = IntervalPlayPalette.whiteText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun SimpleSettings(
    isPercentMode: Boolean,
    onPercentModeChange: (Boolean) -> Unit,
    simpleValue: Int,
    onSimpleValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Воспроизведение",
            color = IntervalPlayPalette.grayText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .width(58.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(IntervalPlayPalette.inactive)
                    .padding(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                UnitModeOption(
                    label = "Часы",
                    selected = !isPercentMode,
                    onClick = { onPercentModeChange(false) }
                )
                UnitModeOption(
                    label = "%",
                    selected = isPercentMode,
                    onClick = { onPercentModeChange(true) }
                )
            }
            Text(
                text = simpleValue.toString(),
                modifier = Modifier.weight(1f),
                color = IntervalPlayPalette.whiteText,
                fontSize = 44.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Stepper(
                modifier = Modifier.width(112.dp),
                onDecrease = { onSimpleValueChange(simpleValue - 1) },
                onIncrease = { onSimpleValueChange(simpleValue + 1) }
            )
        }
    }
}

@Composable
private fun UnitModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(29.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) IntervalPlayPalette.accent else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) IntervalPlayPalette.darkText else IntervalPlayPalette.grayText,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun FixedSettings(
    playValue: Int,
    skipValue: Int,
    isLocked: Boolean,
    onLockToggle: () -> Unit,
    onAdjust: (isPlayValue: Boolean, delta: Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Воспроизведение",
                modifier = Modifier.weight(1f),
                color = IntervalPlayPalette.grayText,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(44.dp))
            Text(
                text = "Пропуск",
                modifier = Modifier.weight(1f),
                color = IntervalPlayPalette.grayText,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            TimeValueField(
                value = formatInterval(playValue),
                isLocked = isLocked,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(IntervalPlayPalette.inactive)
                    .border(
                        width = 1.dp,
                        color = if (isLocked) IntervalPlayPalette.accent else IntervalPlayPalette.outline,
                        shape = RoundedCornerShape(9.dp)
                    )
                    .clickable(onClick = onLockToggle),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (isLocked) {
                        androidx.compose.material.icons.Icons.Default.Lock
                    } else {
                        androidx.compose.material.icons.Icons.Default.LockOpen
                    },
                    contentDescription = if (isLocked) "Замок закрыт" else "Замок открыт",
                    tint = if (isLocked) IntervalPlayPalette.accent else IntervalPlayPalette.grayText,
                    modifier = Modifier.size(21.dp)
                )
            }
            TimeValueField(
                value = formatInterval(skipValue),
                isLocked = isLocked,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(49.dp)
        ) {
            Stepper(
                modifier = Modifier.weight(1f),
                onDecrease = { onAdjust(true, -1) },
                onIncrease = { onAdjust(true, 1) }
            )
            Stepper(
                modifier = Modifier.weight(1f),
                onDecrease = { onAdjust(false, -1) },
                onIncrease = { onAdjust(false, 1) }
            )
        }
    }
}

@Composable
private fun TimeValueField(
    value: String,
    isLocked: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(IntervalPlayPalette.inactive)
            .drawBehind {
                if (isLocked) {
                    drawRoundRect(
                        color = IntervalPlayPalette.accent,
                        cornerRadius = CornerRadius(9.dp.toPx()),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(5.dp.toPx(), 4.dp.toPx())
                            )
                        )
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value,
            color = IntervalPlayPalette.whiteText,
            fontSize = 22.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Stepper(
    modifier: Modifier = Modifier,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton(
            label = "−",
            modifier = Modifier.weight(1f),
            onClick = onDecrease
        )
        StepperButton(
            label = "+",
            modifier = Modifier.weight(1f),
            onClick = onIncrease
        )
    }
}

@Composable
private fun StepperButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(IntervalPlayPalette.inactive)
            .border(1.dp, IntervalPlayPalette.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = IntervalPlayPalette.whiteText,
            fontSize = 21.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun PresetSelector(
    presets: List<IntervalPlayPreset?>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onSave: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Тап — применить, долгое нажатие — сохранить",
            color = IntervalPlayPalette.grayText,
            fontSize = 11.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEachIndexed { index, preset ->
                val selected = selectedIndex == index
                val empty = preset == null
                val buttonLabel = preset?.label() ?: "Пусто"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (selected) Color(0xFF29243B) else IntervalPlayPalette.inactive
                        )
                        .then(
                            if (empty) {
                                Modifier.drawBehind {
                                    drawRoundRect(
                                        color = IntervalPlayPalette.outline,
                                        cornerRadius = CornerRadius(9.dp.toPx()),
                                        style = Stroke(
                                            width = 1.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(
                                                floatArrayOf(5.dp.toPx(), 4.dp.toPx())
                                            )
                                        )
                                    )
                                }
                            } else {
                                Modifier.border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) IntervalPlayPalette.accent else IntervalPlayPalette.outline,
                                    shape = RoundedCornerShape(9.dp)
                                )
                            }
                        )
                        .combinedClickable(
                            onClick = { onSelect(index) },
                            onLongClick = { onSave(index) }
                        )
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = buttonLabel,
                        color = if (selected) IntervalPlayPalette.accent else IntervalPlayPalette.grayText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomActionButton(
    modifier: Modifier = Modifier,
    label: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (primary) IntervalPlayPalette.accent else IntervalPlayPalette.inactive
            )
            .then(
                if (primary) Modifier else Modifier.border(
                    1.dp,
                    IntervalPlayPalette.outline,
                    RoundedCornerShape(12.dp)
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (primary) IntervalPlayPalette.darkText else IntervalPlayPalette.whiteText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatInterval(seconds: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
