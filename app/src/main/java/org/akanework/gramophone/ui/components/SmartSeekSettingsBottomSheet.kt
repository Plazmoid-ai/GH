/*
 *     Copyright (C) 2026 Plazmoid-ai
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import android.content.Context
import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.SmartSeekConfig

enum class TrimSide { START, END }

data class TrimState(
    val isEnabled: Boolean = true,
    val isSymmetric: Boolean = true,
    val startTrim: Int = 5,
    val endTrim: Int = 5,
    val presets: List<Int> = listOf(5, 10, 15, 30),
    val activeSide: TrimSide = TrimSide.START
)

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
)

private data class SimpleIntervalPreset(
    val value: Int,
    val isPercent: Boolean
)

private data class AccentOption(
    val label: String,
    val color: Color,
    val kind: AccentKind = AccentKind.SOLID
)

private enum class AccentKind {
    SOLID,
    DEFAULT,
    DYNAMIC,
    TRANSPARENT
}

private val intervalAccentOptions = listOf(
    AccentOption("По умолчанию", IntervalPlayPalette.accent, AccentKind.DEFAULT),
    AccentOption("Динамический", Color.White, AccentKind.DYNAMIC),
    AccentOption("Прозрачный", Color.Transparent, AccentKind.TRANSPARENT),
    AccentOption("Чёрный", Color(0xFF000000)),
    AccentOption("Красный", Color(0xFFF44336)),
    AccentOption("Оранжевый", Color(0xFFFF9800)),
    AccentOption("Голубой", Color(0xFF4FC3F7)),
    AccentOption("Зелёный", Color(0xFF4CAF50)),
    AccentOption("Розовый", Color(0xFFF06292)),
    AccentOption("Фиолетовый", Color(0xFFAB47BC)),
    AccentOption("Синий", Color(0xFF2979FF))
)

private val dynamicAccentBrush = Brush.sweepGradient(
    listOf(
        Color(0xFFF44336),
        Color(0xFFFF9800),
        Color(0xFFFFEB3B),
        Color(0xFF4CAF50),
        Color(0xFF00BCD4),
        Color(0xFF2979FF),
        Color(0xFFAB47BC),
        Color(0xFFF44336)
    )
)

/**
 * IntervalPlay settings UI prototype.
 *
 * SharedPreferences and the save callback remain in the constructor to preserve existing callers,
 * but this screen intentionally uses only local Compose state and does not persist settings.
 */
class SmartSeekSettingsBottomSheet(
    private val context: Context,
    preferences: SharedPreferences,
    @Suppress("UNUSED_PARAMETER") onSaved: (() -> Unit)? = null
) {
    private val wheelSwipeEnabled = SmartSeekConfig.load(preferences).wheelSwipeEnabled

    fun show(): BottomSheetDialog {
        val dialog = BottomSheetDialog(context)
        dialog.setCanceledOnTouchOutside(false)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
            )
            setContent {
                IntervalPlaySettingsContent(
                    wheelSwipeEnabled = wheelSwipeEnabled,
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
    wheelSwipeEnabled: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    var fadeDurationMs by remember { mutableStateOf(1000L) }
    var isFadeEnabled by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) }

    var isAutoMode by remember { mutableStateOf(false) }
    var isMuteMode by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(true) }
    var activeIntervalSec by remember { mutableStateOf(30) }
    var activeIntervalPercent by remember { mutableStateOf(20) }
    var silenceIntervalSec by remember { mutableStateOf(10) }
    var autoCount by remember { mutableStateOf(5) }

    var isBufferIncreased by remember { mutableStateOf(false) }
    var selectedFixedPreset by remember { mutableStateOf(0) }
    var fixedPresets by remember {
        mutableStateOf(
            listOf<IntervalPlayPreset?>(
                IntervalPlayPreset(30, 10),
                IntervalPlayPreset(45, 15),
                null
            )
        )
    }
    var selectedAccent by remember { mutableStateOf(0) }
    var trimState by remember { mutableStateOf(TrimState()) }
    var showTrimSettings by remember { mutableStateOf(false) }

    var previewDurationSec by remember { mutableStateOf(30) }
    var positionMode by remember { mutableStateOf("START") }
    var isShiftEnabled by remember { mutableStateOf(false) }
    var shiftStepSec by remember { mutableStateOf(10) }
    val presetDurations = remember { mutableStateListOf(15, 30, 45) }

    LaunchedEffect(positionMode) {
        if (positionMode == "RANDOM") {
            isShiftEnabled = false
        }
    }

    // TODO: Если isMuteMode == true и isFadeEnabled == true, использовать fade-out/fade-in
    // длительностью fadeDurationMs при смене интервала; в тишине громкость равна 0.
    // TODO: Если isMuteMode == true и isFadeEnabled == false, отключить обычный Fade,
    // но оставить защитный микро-фейд 100 мс от щелчков.
    // TODO: Если isMuteMode == false (Пропуск) или isAutoMode == true, использовать
    // микро-фейд 100 мс при seek. Реализацию аудио оставить будущему плееру.

    fun recalcFromCount(count: Int) {
        val safeCount = count.coerceIn(1, 20)
        val totalParts = 2 * safeCount - 1
        autoCount = safeCount
        activeIntervalPercent = (100.0 / totalParts).roundToInt().coerceIn(0, 100)
    }

    fun recalcFromPercent(percent: Int) {
        // A 0% interval is invalid for the calculation; normalize it to 1%.
        val safePercent = percent.coerceIn(0, 100).coerceAtLeast(1)
        activeIntervalPercent = safePercent
        autoCount = floor((100.0 / safePercent + 1.0) / 2.0)
            .toInt()
            .coerceIn(1, 20)
    }

    // Keep the defaults consistent. Count is the source of truth on initialization
    // and whenever Auto mode is enabled; playback will map the percentage to real time.
    LaunchedEffect(Unit) {
        recalcFromCount(autoCount)
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1C1C24))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeTab(
                modifier = Modifier.weight(1f),
                label = "Интервалы",
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 }
            )
            ModeTab(
                modifier = Modifier.weight(1f),
                label = "Превью",
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 }
            )
        }
        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (selectedTab == 0) {
                IntervalsContent(
                    wheelSwipeEnabled = wheelSwipeEnabled,
                    isAutoMode = isAutoMode,
                    onAutoModeChange = { enabled ->
                        isAutoMode = enabled
                        if (enabled) recalcFromCount(autoCount)
                    },
                    isMuteMode = isMuteMode,
                    onMuteModeChange = { isMuteMode = it },
                    isLocked = isLocked,
                    onLockToggle = { isLocked = !isLocked },
                    activeIntervalSec = activeIntervalSec,
                    activeIntervalPercent = activeIntervalPercent,
                    silenceIntervalSec = silenceIntervalSec,
                    autoCount = autoCount,
                    onActiveIntervalChange = { requested ->
                        val next = requested.coerceIn(1, 600)
                        val delta = next - activeIntervalSec
                        activeIntervalSec = next
                        if (isLocked) {
                            silenceIntervalSec = (silenceIntervalSec + delta).coerceIn(1, 600)
                        }
                    },
                    onActivePercentChange = { requested -> recalcFromPercent(requested) },
                    onSilenceIntervalChange = { requested ->
                        val next = requested.coerceIn(1, 600)
                        val delta = next - silenceIntervalSec
                        silenceIntervalSec = next
                        if (isLocked) {
                            activeIntervalSec = (activeIntervalSec + delta).coerceIn(1, 600)
                        }
                    },
                    onAutoCountChange = { requested -> recalcFromCount(requested) }
                )

                PresetSelector(
                    title = "Шаблоны",
                    hint = "Тап — применить, долгое нажатие — сохранить",
                    selectedIndex = selectedFixedPreset,
                    onSelect = { index ->
                        fixedPresets[index]?.let { preset ->
                            val requestedDuration = preset.playSeconds.coerceIn(1, 600)
                            silenceIntervalSec = preset.skipSeconds.coerceIn(1, 600)
                            // Presets store seconds, so the active side is applied only in manual mode.
                            // Auto mode stays percentage-based because the actual track length is unknown.
                            if (!isAutoMode) {
                                activeIntervalSec = requestedDuration
                            }
                            selectedFixedPreset = index
                        }
                    },
                    onSave = { index ->
                        val updated = fixedPresets.toMutableList()
                        updated[index] = IntervalPlayPreset(
                            playSeconds = activeIntervalSec,
                            skipSeconds = silenceIntervalSec
                        )
                        fixedPresets = updated
                        selectedFixedPreset = index
                    },
                    content = { index -> FixedPresetCardContent(fixedPresets[index]) },
                    isEmpty = { fixedPresets[it] == null }
                )

                TrimSection(
                    state = trimState,
                    onStateChange = { trimState = it },
                    onOpenSettings = { showTrimSettings = true }
                )

                AccentSelector(
                    selectedIndex = selectedAccent,
                    onSelect = { selectedAccent = it }
                )

                ExperimentalSettingsSection(
                    bufferIncreased = isBufferIncreased,
                    onBufferChange = { isBufferIncreased = it },
                    isFadeEnabled = isFadeEnabled,
                    onFadeEnabledChange = { isFadeEnabled = it },
                    fadeDurationMs = fadeDurationMs,
                    onFadeDurationChange = { fadeDurationMs = it }
                )
            } else {
                PreviewContent(
                    presetDurations = presetDurations,
                    previewDurationSec = previewDurationSec,
                    onPreviewDurationChange = { previewDurationSec = it },
                    positionMode = positionMode,
                    onPositionModeChange = { positionMode = it },
                    isShiftEnabled = isShiftEnabled,
                    onShiftEnabledChange = { isShiftEnabled = it },
                    shiftStepSec = shiftStepSec,
                    onShiftStepChange = { shiftStepSec = it.coerceIn(1, 120) }
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

        if (showTrimSettings) {
            TrimSettingsSheet(
                state = trimState,
                onStateChange = { trimState = it },
                onDismiss = { showTrimSettings = false }
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
            .height(36.dp)
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
            text = label,
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

/** A UI preview of the simple-mode interval; it does not drive playback. */
@Composable
private fun PreviewBar(
    playLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PreviewCapsule(
            modifier = Modifier.weight(1f),
            label = playLabel,
            isPlay = true
        )
        PreviewCapsule(
            modifier = Modifier.weight(1f),
            label = "00:10",
            isPlay = false
        )
    }
}

@Composable
private fun PreviewCapsule(
    modifier: Modifier,
    label: String,
    isPlay: Boolean
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isPlay) IntervalPlayPalette.accent else IntervalPlayPalette.previewSkip
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPlay) IntervalPlayPalette.darkText else IntervalPlayPalette.whiteText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SimpleSettings(
    isPercentMode: Boolean,
    wheelSwipeEnabled: Boolean,
    minutes: Int,
    seconds: Int,
    percent: Int,
    onPercentModeChange: (Boolean) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit,
    onPercentChange: (Int) -> Unit,
    onAdjust: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Воспроизведение",
            color = IntervalPlayPalette.grayText,
            fontSize = 12.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .width(62.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IntervalPlayPalette.inactive)
                    .padding(4.dp),
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

            if (isPercentMode) {
                HybridWheelPicker(
                    value = percent,
                    valueRange = 0..100,
                    cyclic = true,
                    onValueChange = onPercentChange,
                    modifier = Modifier.weight(1f),
                    swipeEnabled = wheelSwipeEnabled
                )
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HybridWheelPicker(
                        value = minutes,
                        valueRange = 0..10,
                        cyclic = true,
                        onValueChange = onMinutesChange,
                        modifier = Modifier.weight(1f),
                        swipeEnabled = wheelSwipeEnabled
                    )
                    Text(
                        text = ":",
                        color = IntervalPlayPalette.whiteText.copy(alpha = 0.6f),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    HybridWheelPicker(
                        value = seconds,
                        valueRange = 0..59,
                        cyclic = true,
                        onValueChange = onSecondsChange,
                        modifier = Modifier.weight(1f),
                        swipeEnabled = wheelSwipeEnabled
                    )
                }
            }

            Stepper(
                modifier = Modifier.width(76.dp),
                onDecrease = { onAdjust(-1) },
                onIncrease = { onAdjust(1) }
            )
        }
    }
}

/**
 * Existing HybridWheelPicker API:
 * - [value] is the externally owned selected integer (the composable's value/setter input).
 * - [onValueChange] reports user selection back to the owner for two-way state binding.
 * - [valueRange] is the inclusive permitted range; [cyclic] controls wraparound.
 * - [modifier], [containerHeight], [itemHeight], and [swipeEnabled] configure presentation/input.
 * - There is no imperative setter and no separate minute-digit lock: this is a single-integer
 *   picker. Interval UI passes total seconds in 1..600, so a minute-column lock is not applicable.
 * The value is observed by LaunchedEffect and the LazyColumn scrolls to external value updates.
 */
@Composable
private fun HybridWheelPicker(
    value: Int,
    valueRange: IntRange,
    cyclic: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerHeight: Dp = 168.dp,
    itemHeight: Dp = 54.dp,
    swipeEnabled: Boolean = false
) {
    val rangeSize = valueRange.last - valueRange.first + 1
    val itemCount = if (cyclic) rangeSize * 1000 else rangeSize
    val middleCycleStart = if (cyclic) {
        (itemCount / 2 / rangeSize) * rangeSize
    } else {
        0
    }

    fun indexFor(target: Int): Int {
        val safeValue = target.coerceIn(valueRange)
        return if (cyclic) {
            middleCycleStart + safeValue - valueRange.first
        } else {
            safeValue - valueRange.first
        }
    }

    fun valueFor(index: Int): Int {
        return if (cyclic) {
            valueRange.first + Math.floorMod(index, rangeSize)
        } else {
            valueRange.first + index
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = indexFor(value.coerceIn(valueRange))
    )
    val wheelScrollScope = rememberCoroutineScope()
    val centeredIndex by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { item -> abs(item.offset + item.size / 2 - center) }
                ?.index ?: -1
        }
    }

    val density = LocalDensity.current
    val centerOffsetPx = with(density) {
        ((containerHeight - itemHeight) / 2).roundToPx()
    }
    val hapticView = LocalView.current
    val latestValue = rememberUpdatedState(value.coerceIn(valueRange))
    val latestOnValueChange = rememberUpdatedState(onValueChange)
    val suppressScrollCallbacks = remember { mutableStateOf(true) }

    LaunchedEffect(value, valueRange.first, valueRange.last, cyclic) {
        val targetValue = value.coerceIn(valueRange)
        if (centeredIndex >= 0 && valueFor(centeredIndex) == targetValue) {
            suppressScrollCallbacks.value = false
            return@LaunchedEffect
        }

        suppressScrollCallbacks.value = true
        listState.animateScrollToItem(
            index = indexFor(targetValue),
            scrollOffset = -centerOffsetPx
        )
        suppressScrollCallbacks.value = false
    }

    LaunchedEffect(listState, valueRange.first, valueRange.last, cyclic) {
        var lastReportedValue: Int = latestValue.value
        snapshotFlow { centeredIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index >= 0) {
                    val centeredValue: Int = valueFor(index)
                    if (suppressScrollCallbacks.value) {
                        lastReportedValue = centeredValue
                    } else if (lastReportedValue != centeredValue) {
                        lastReportedValue = centeredValue
                        hapticView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (centeredValue != latestValue.value) {
                            latestOnValueChange.value(centeredValue)
                        }
                    }
                }
            }
    }

    val fadeMask = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.22f to Color.Black,
            0.78f to Color.Black,
            1f to Color.Transparent
        )
    )

    val swipeGestureModifier = if (swipeEnabled) {
        Modifier.pointerInput(listState, valueRange.first, valueRange.last, cyclic) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                val activePointerId = down.id
                var previousY = down.position.y
                var accumulatedY = 0f
                var isDragging = false
                var released = false

                while (!released) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == activePointerId } ?: break
                    val deltaY = change.position.y - previousY
                    previousY = change.position.y

                    if (!change.pressed) {
                        if (isDragging) {
                            event.changes.forEach { it.consume() }
                        }
                        released = true
                    } else if (!isDragging) {
                        accumulatedY += deltaY
                        if (abs(accumulatedY) > touchSlop) {
                            isDragging = true
                            event.changes.forEach { it.consume() }
                            val overSlop = if (accumulatedY > 0f) {
                                accumulatedY - touchSlop
                            } else {
                                accumulatedY + touchSlop
                            }
                            if (overSlop != 0f) {
                                listState.dispatchRawDelta(-overSlop)
                            }
                        }
                    } else {
                        event.changes.forEach { it.consume() }
                        if (deltaY != 0f) {
                            listState.dispatchRawDelta(-deltaY)
                        }
                    }
                }

                if (isDragging) {
                    val layoutInfo = listState.layoutInfo
                    val viewportCenter =
                        (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                    val targetIndex = layoutInfo.visibleItemsInfo
                        .minByOrNull { item ->
                            abs(item.offset + item.size / 2 - viewportCenter)
                        }
                        ?.index
                    if (targetIndex != null) {
                        wheelScrollScope.launch {
                            listState.animateScrollToItem(
                                index = targetIndex,
                                scrollOffset = -centerOffsetPx
                            )
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(containerHeight)
            .then(swipeGestureModifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = fadeMask,
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(vertical = (containerHeight - itemHeight) / 2),
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
            ) {
                items(
                    count = itemCount,
                    key = { index -> index }
                ) { index ->
                    val distanceFromCenter = if (centeredIndex >= 0) {
                        abs(index - centeredIndex)
                    } else {
                        Int.MAX_VALUE
                    }
                    val selected = distanceFromCenter == 0
                    val fontSize = when (distanceFromCenter) {
                        0 -> 42.sp
                        1 -> 28.sp
                        else -> 20.sp
                    }
                    val alpha = when (distanceFromCenter) {
                        0 -> 1f
                        1 -> 0.4f
                        else -> 0.18f
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = valueFor(index).toString(),
                            color = IntervalPlayPalette.whiteText.copy(alpha = alpha),
                            fontSize = fontSize,
                            fontWeight = if (selected) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(IntervalPlayPalette.outline)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(IntervalPlayPalette.outline)
            )
        }

        fun stepValue(delta: Int) {
            val currentValue = latestValue.value
            val nextValue = if (cyclic) {
                valueRange.first + Math.floorMod(
                    currentValue + delta - valueRange.first,
                    rangeSize
                )
            } else {
                (currentValue + delta).coerceIn(valueRange)
            }
            if (nextValue != currentValue) {
                latestOnValueChange.value(nextValue)
            }
        }

        WheelEdgeControl(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            height = (containerHeight - itemHeight) / 2,
            onStep = { stepValue(-1) }
        )
        WheelEdgeControl(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            height = (containerHeight - itemHeight) / 2,
            onStep = { stepValue(1) }
        )
    }
}

@Composable
private fun WheelEdgeControl(
    modifier: Modifier,
    height: Dp,
    onStep: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val latestOnStep = rememberUpdatedState(onStep)
    val repeatScope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    var isLongPress by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    isPressed = true
                    isLongPress = false
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel -> {
                    isPressed = false
                    isLongPress = false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .height(height)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { latestOnStep.value() },
                onLongClick = {
                    isLongPress = true
                    repeatScope.launch {
                        while (isPressed && isLongPress) {
                            latestOnStep.value()
                            delay(60)
                        }
                    }
                }
            )
    )
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
        if (label == "Часы") {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = "Часы",
                tint = if (selected) {
                    IntervalPlayPalette.darkText
                } else {
                    IntervalPlayPalette.grayText
                },
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = label,
                color = if (selected) {
                    IntervalPlayPalette.darkText
                } else {
                    IntervalPlayPalette.grayText
                },
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FixedSettings(
    playValue: Int,
    skipValue: Int,
    isLocked: Boolean,
    wheelSwipeEnabled: Boolean,
    onLockToggle: () -> Unit,
    onPlayValueChange: (Int) -> Unit,
    onSkipValueChange: (Int) -> Unit,
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
            WheelTimePicker(
                modifier = Modifier.weight(1f),
                totalSeconds = playValue,
                onValueChange = onPlayValueChange,
                wheelSwipeEnabled = wheelSwipeEnabled
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(IntervalPlayPalette.inactive)
                    .border(
                        width = 1.dp,
                        color = if (isLocked) {
                            IntervalPlayPalette.accent
                        } else {
                            IntervalPlayPalette.outline
                        },
                        shape = RoundedCornerShape(9.dp)
                    )
                    .clickable(onClick = onLockToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLocked) {
                        Icons.Default.Lock
                    } else {
                        Icons.Default.LockOpen
                    },
                    contentDescription = if (isLocked) "Замок закрыт" else "Замок открыт",
                    tint = if (isLocked) {
                        IntervalPlayPalette.accent
                    } else {
                        IntervalPlayPalette.grayText
                    },
                    modifier = Modifier.size(21.dp)
                )
            }

            WheelTimePicker(
                modifier = Modifier.weight(1f),
                totalSeconds = skipValue,
                onValueChange = onSkipValueChange,
                wheelSwipeEnabled = wheelSwipeEnabled
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
private fun WheelTimePicker(
    totalSeconds: Int,
    onValueChange: (Int) -> Unit,
    wheelSwipeEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val minutes = (totalSeconds / 60).coerceIn(0, 10)
    val seconds = totalSeconds % 60

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HybridWheelPicker(
            value = minutes,
            valueRange = 0..10,
            cyclic = false,
            onValueChange = { newMinutes ->
                onValueChange(newMinutes * 60 + seconds)
            },
            modifier = Modifier.weight(1f),
            swipeEnabled = wheelSwipeEnabled,
            containerHeight = 168.dp,
            itemHeight = 54.dp
        )

        Text(
            text = ":",
            color = IntervalPlayPalette.whiteText,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        HybridWheelPicker(
            value = seconds,
            valueRange = 0..59,
            cyclic = true,
            onValueChange = { newSeconds ->
                onValueChange(minutes * 60 + newSeconds)
            },
            modifier = Modifier.weight(1f),
            swipeEnabled = wheelSwipeEnabled,
            containerHeight = 168.dp,
            itemHeight = 54.dp
        )
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
    val latestOnClick = rememberUpdatedState(onClick)
    val repeatScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .height(44.dp)
            .pointerInput(repeatScope) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    var repeated = false
                    val repeatJob = repeatScope.launch {
                        delay(420)
                        repeated = true
                        while (true) {
                            latestOnClick.value()
                            delay(85)
                        }
                    }

                    val released = try {
                        waitForUpOrCancellation()
                    } finally {
                        repeatJob.cancel()
                    }

                    if (released != null && !repeated) {
                        latestOnClick.value()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = IntervalPlayPalette.whiteText,
            fontSize = 30.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
private fun PresetSelector(
    title: String,
    hint: String,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onSave: (Int) -> Unit,
    content: @Composable (Int) -> Unit,
    isEmpty: (Int) -> Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = IntervalPlayPalette.grayText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = hint,
            color = IntervalPlayPalette.grayText,
            fontSize = 11.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { index ->
                val selected = selectedIndex == index
                val empty = isEmpty(index)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (selected) Color(0xFF29243B) else IntervalPlayPalette.inactive
                        )
                        .then(
                            if (empty) {
                                Modifier.drawBehind {
                                    drawRoundRect(
                                        color = if (selected) IntervalPlayPalette.accent
                                        else IntervalPlayPalette.outline,
                                        cornerRadius = CornerRadius(9.dp.toPx()),
                                        style = Stroke(
                                            width = if (selected) 1.5.dp.toPx() else 1.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(
                                                floatArrayOf(5.dp.toPx(), 4.dp.toPx())
                                            )
                                        )
                                    )
                                }
                            } else {
                                Modifier.border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) IntervalPlayPalette.accent
                                    else IntervalPlayPalette.outline,
                                    shape = RoundedCornerShape(9.dp)
                                )
                            }
                        )
                        .combinedClickable(
                            onClick = { if (!empty) onSelect(index) },
                            onLongClick = { onSave(index) }
                        )
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (empty) {
                        Text(
                            text = "Пусто",
                            color = if (selected) IntervalPlayPalette.accent
                            else IntervalPlayPalette.grayText,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        content(index)
                    }
                }
            }
        }
    }
}

@Composable
private fun SimplePresetCardContent(
    preset: SimpleIntervalPreset?
) {
    if (preset == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(30.dp),
            contentAlignment = Alignment.Center
        ) {
            if (preset.isPercent) {
                Text(
                    text = "%",
                    color = IntervalPlayPalette.accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = "Часы",
                    tint = IntervalPlayPalette.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(vertical = 7.dp)
                .width(1.dp)
                .height(22.dp)
                .background(IntervalPlayPalette.outline)
        )

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val valueText = if (preset.isPercent) {
                    preset.value.toString()
                } else {
                    formatSimpleTime(preset.value)
                }
                val fontSize = (maxWidth.value / valueText.length * 1.35f)
                    .coerceIn(11f, 13f)
                Text(
                    text = valueText,
                    color = IntervalPlayPalette.whiteText,
                    fontSize = fontSize.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun FixedPresetCardContent(
    preset: IntervalPlayPreset?
) {
    if (preset == null) return

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val playText = formatPresetInterval(preset.playSeconds)
        val skipText = formatPresetInterval(preset.skipSeconds)
        val combinedLength = (playText.length + skipText.length + 3).coerceAtLeast(1)
        val fontSize = (maxWidth.value / combinedLength * 1.45f)
            .coerceIn(10f, 14f)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = playText,
                color = IntervalPlayPalette.accent,
                fontSize = fontSize.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = " / ",
                color = IntervalPlayPalette.grayText,
                fontSize = fontSize.sp,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = skipText,
                color = IntervalPlayPalette.grayText,
                fontSize = fontSize.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun AccentSelector(
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Акцент",
                modifier = Modifier.weight(1f),
                color = IntervalPlayPalette.grayText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = intervalAccentOptions
                    .getOrNull(selectedIndex)
                    ?.label
                    .orEmpty(),
                color = IntervalPlayPalette.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                8.dp,
                Alignment.CenterHorizontally
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            intervalAccentOptions.take(6).forEachIndexed { index, option ->
                AccentSwatch(
                    option = option,
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                8.dp,
                Alignment.CenterHorizontally
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            intervalAccentOptions.drop(6).forEachIndexed { rowIndex, option ->
                val index = rowIndex + 6
                AccentSwatch(
                    option = option,
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) }
                )
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    option: AccentOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val swatchModifier = when (option.kind) {
        AccentKind.DYNAMIC -> Modifier.background(dynamicAccentBrush, CircleShape)
        AccentKind.TRANSPARENT -> Modifier.background(IntervalPlayPalette.background, CircleShape)
        else -> Modifier.background(option.color, CircleShape)
    }

    Box(
        modifier = Modifier
            .size(35.dp)
            .clip(CircleShape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) IntervalPlayPalette.whiteText
                else IntervalPlayPalette.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(CircleShape)
                .then(swatchModifier)
                .then(
                    if (option.kind == AccentKind.TRANSPARENT) {
                        Modifier.border(1.dp, IntervalPlayPalette.grayText, CircleShape)
                    } else {
                        Modifier
                    }
                )
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        if (option.kind == AccentKind.SOLID && option.color == Color(0xFF000000)) {
                            Color.White
                        } else {
                            IntervalPlayPalette.darkText
                        }
                    )
            )
        }
    }
}

@Composable
private fun ExperimentalBufferSetting(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Экспериментально",
            color = IntervalPlayPalette.grayText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(IntervalPlayPalette.inactive)
                .border(1.dp, IntervalPlayPalette.outline, RoundedCornerShape(10.dp))
                .clickable { onCheckedChange(!checked) }
                .padding(start = 12.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Увеличить буфер",
                modifier = Modifier.weight(1f),
                color = IntervalPlayPalette.whiteText,
                fontSize = 13.sp
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = IntervalPlayPalette.darkText,
                    checkedTrackColor = IntervalPlayPalette.accent,
                    uncheckedThumbColor = IntervalPlayPalette.grayText,
                    uncheckedTrackColor = IntervalPlayPalette.outline,
                    uncheckedBorderColor = IntervalPlayPalette.outline
                )
            )
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

private fun formatSimpleTime(seconds: Int): String =
    if (seconds < 60) {
        seconds.toString()
    } else {
        String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }

private fun formatPresetInterval(seconds: Int): String =
    formatSimpleTime(seconds)


@Composable
private fun IntervalsContent(
    wheelSwipeEnabled: Boolean,
    isAutoMode: Boolean,
    onAutoModeChange: (Boolean) -> Unit,
    isMuteMode: Boolean,
    onMuteModeChange: (Boolean) -> Unit,
    isLocked: Boolean,
    onLockToggle: () -> Unit,
    activeIntervalSec: Int,
    activeIntervalPercent: Int,
    silenceIntervalSec: Int,
    autoCount: Int,
    onActiveIntervalChange: (Int) -> Unit,
    onActivePercentChange: (Int) -> Unit,
    onSilenceIntervalChange: (Int) -> Unit,
    onAutoCountChange: (Int) -> Unit
) {
    val accent = Color(0xFF9B8AF5)
    val inactive = Color(0xFF1C1C24)
    val outline = Color(0xFF4A4D52)
    val gray = Color(0xFFA8A8A8)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Активный",
                color = gray,
                fontSize = 13.sp
            )
            if (isAutoMode) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HybridWheelPicker(
                            value = activeIntervalPercent.coerceIn(0, 100),
                            valueRange = 0..100,
                            cyclic = false,
                            onValueChange = onActivePercentChange,
                            modifier = Modifier.weight(1f),
                            containerHeight = 168.dp,
                            itemHeight = 54.dp,
                            swipeEnabled = wheelSwipeEnabled
                        )
                        Text(
                            text = "%",
                            color = gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoCountControl(
                            count = autoCount,
                            onIncrease = { onAutoCountChange(autoCount + 1) },
                            onDecrease = { onAutoCountChange(autoCount - 1) }
                        )
                    }
                }
            } else {
                // Manual mode uses two independent wheel pickers; the owner synchronizes
                // their values when isLocked is true.
                HybridWheelPicker(
                    value = activeIntervalSec.coerceIn(1, 600),
                    valueRange = 1..600,
                    cyclic = false,
                    onValueChange = onActiveIntervalChange,
                    modifier = Modifier.weight(1f),
                    containerHeight = 168.dp,
                    itemHeight = 54.dp,
                    swipeEnabled = wheelSwipeEnabled
                )

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onLockToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isLocked) "Интервалы связаны" else "Интервалы не связаны",
                        tint = if (isLocked) accent else gray,
                        modifier = Modifier.size(22.dp)
                    )
                }

                HybridWheelPicker(
                    value = silenceIntervalSec.coerceIn(1, 600),
                    valueRange = 1..600,
                    cyclic = false,
                    onValueChange = onSilenceIntervalChange,
                    modifier = Modifier.weight(1f),
                    containerHeight = 168.dp,
                    itemHeight = 54.dp,
                    swipeEnabled = wheelSwipeEnabled
                )
            }
        }
    }
}

@Composable
private fun AutoCountControl(
    count: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    val inactive = Color(0xFF1C1C24)
    val outline = Color(0xFF2A2A35)
    val gray = Color(0xFFA8A8A8)

    Row(
        modifier = Modifier.width(108.dp).height(104.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 80.dp, height = 70.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(inactive)
                .border(1.dp, outline, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .alpha(if (count < 20) 1f else 0.3f)
                    .clickable(enabled = count < 20, onClick = onIncrease),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = gray, fontSize = 24.sp)
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .alpha(if (count > 1) 1f else 0.3f)
                    .clickable(enabled = count > 1, onClick = onDecrease),
                contentAlignment = Alignment.Center
            ) {
                Text("−", color = gray, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun ExperimentalSettingsSection(
    bufferIncreased: Boolean,
    onBufferChange: (Boolean) -> Unit,
    isFadeEnabled: Boolean,
    onFadeEnabledChange: (Boolean) -> Unit,
    fadeDurationMs: Long,
    onFadeDurationChange: (Long) -> Unit
) {
    val inactive = Color(0xFF1C1C24)
    val outline = Color(0xFF2A2A35)
    val accent = Color(0xFF9B8AF5)
    val gray = Color(0xFFA8A8A8)
    val disabledOutline = Color(0xFF4A4D52)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF18181B))
            .border(1.dp, outline, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Экспериментально",
            color = gray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(inactive)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Звук и Буфер", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Увеличить буфер", color = Color.White, fontSize = 14.sp)
                Switch(
                    checked = bufferIncreased,
                    onCheckedChange = onBufferChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF121212),
                        checkedTrackColor = accent,
                        uncheckedThumbColor = gray,
                        uncheckedTrackColor = outline,
                        uncheckedBorderColor = outline
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Плавное затухание (Fade)", color = Color.White, fontSize = 14.sp)
                Switch(
                    checked = isFadeEnabled,
                    onCheckedChange = onFadeEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF121212),
                        checkedTrackColor = accent,
                        uncheckedThumbColor = gray,
                        uncheckedTrackColor = outline,
                        uncheckedBorderColor = outline
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(500L, 1000L, 2000L).forEach { durationMs ->
                    val selected = fadeDurationMs == durationMs
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .alpha(if (isFadeEnabled) 1f else 0.3f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(inactive)
                            .border(
                                width = if (isFadeEnabled && selected) 1.5.dp else 1.dp,
                                color = if (isFadeEnabled && selected) accent else disabledOutline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = isFadeEnabled) { onFadeDurationChange(durationMs) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (durationMs) {
                                500L -> "0.5с"
                                1000L -> "1с"
                                else -> "2с"
                            },
                            color = if (isFadeEnabled && selected) accent else gray,
                            fontSize = 12.sp,
                            fontWeight = if (isFadeEnabled && selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PreviewContent(
    presetDurations: SnapshotStateList<Int>,
    previewDurationSec: Int,
    onPreviewDurationChange: (Int) -> Unit,
    positionMode: String,
    onPositionModeChange: (String) -> Unit,
    isShiftEnabled: Boolean,
    onShiftEnabledChange: (Boolean) -> Unit,
    shiftStepSec: Int,
    onShiftStepChange: (Int) -> Unit
) {
    val inactive = Color(0xFF1C1C24)
    val outline = Color(0xFF4A4D52)
    val accent = Color(0xFF9B8AF5)
    val gray = Color(0xFFA8A8A8)
    val shiftBlocked = positionMode == "RANDOM"

    // TODO: When each new track starts, advance the start position by shiftStepSec
    // whenever isShiftEnabled is true. This setting is UI/state only until player integration.
    // TODO: RANDOM must choose a random start in 0..(trackDuration - previewDuration).

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Длительность", color = gray, fontSize = 13.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FlowRow(
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetDurations.forEach { seconds ->
                            val selected = previewDurationSec == seconds
                            val shape = RoundedCornerShape(8.dp)
                            Box(
                                modifier = Modifier
                                    .size(width = 60.dp, height = 32.dp)
                                    .clip(shape)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (selected) accent else outline,
                                        shape = shape
                                    )
                                    .combinedClickable(
                                        onClick = { onPreviewDurationChange(seconds) },
                                        onLongClick = { presetDurations.remove(seconds) }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${seconds}с",
                                    color = if (selected) accent else gray,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        if (presetDurations.size < 4) {
                            Box(
                                modifier = Modifier
                                    .size(width = 60.dp, height = 32.dp)
                                    .drawBehind {
                                        drawRoundRect(
                                            color = outline,
                                            cornerRadius = CornerRadius(8.dp.toPx()),
                                            style = Stroke(
                                                width = 1.5.dp.toPx(),
                                                cap = StrokeCap.Round,
                                                pathEffect = PathEffect.dashPathEffect(
                                                    floatArrayOf(5.dp.toPx(), 4.dp.toPx())
                                                )
                                            )
                                        )
                                    }
                                    .clickable {
                                        if (previewDurationSec !in presetDurations &&
                                            presetDurations.size < 4
                                        ) {
                                            presetDurations.add(previewDurationSec)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", color = gray, fontSize = 20.sp)
                            }
                        }
                    }

                    Text(
                        text = "Долгий тап — удалить шаблон",
                        fontSize = 10.sp,
                        color = Color(0xFF666666)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 120.dp, height = 72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(inactive)
                            .border(1.dp, Color(0xFF2A2A35), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format(
                                Locale.ROOT,
                                "%02d:%02d",
                                previewDurationSec / 60,
                                previewDurationSec % 60
                            ),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "+",
                            modifier = Modifier.clickable {
                                onPreviewDurationChange((previewDurationSec + 1).coerceIn(5, 300))
                            },
                            fontSize = 24.sp,
                            color = gray
                        )
                        Text(
                            text = "-",
                            modifier = Modifier.clickable {
                                onPreviewDurationChange((previewDurationSec - 1).coerceIn(5, 300))
                            },
                            fontSize = 24.sp,
                            color = gray
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Позиция", color = gray, fontSize = 13.sp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    listOf("START" to "Начало", "MIDDLE" to "Середина"),
                    listOf("END" to "Конец", "RANDOM" to "Случайно")
                ).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { (mode, label) ->
                            val selected = positionMode == mode
                            val shape = RoundedCornerShape(8.dp)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(shape)
                                    .background(inactive)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) accent else outline,
                                        shape = shape
                                    )
                                    .clickable { onPositionModeChange(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) accent else gray,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (shiftBlocked) 0.3f else 1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val shiftShape = RoundedCornerShape(20.dp)
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 40.dp)
                        .clip(shiftShape)
                        .background(inactive)
                        .border(
                            width = 1.dp,
                            color = if (isShiftEnabled && !shiftBlocked) accent else outline,
                            shape = shiftShape
                        )
                        .clickable(enabled = !shiftBlocked) {
                            onShiftEnabledChange(!isShiftEnabled)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = if (isShiftEnabled && !shiftBlocked) accent else gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Смещение",
                            color = if (isShiftEnabled && !shiftBlocked) accent else gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(width = 216.dp, height = 40.dp)
                        .clip(shiftShape)
                        .background(inactive)
                        .border(1.dp, Color(0xFF2A2A35), shiftShape),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "−",
                            modifier = Modifier
                                .clickable(enabled = !shiftBlocked) {
                                    onShiftStepChange((shiftStepSec - 1).coerceAtLeast(1))
                                }
                                .padding(horizontal = 12.dp),
                            color = gray,
                            fontSize = 22.sp
                        )
                        Text(
                            text = "${shiftStepSec}с",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                        Text(
                            text = "+",
                            modifier = Modifier
                                .clickable(enabled = !shiftBlocked) {
                                    onShiftStepChange((shiftStepSec + 1).coerceAtMost(120))
                                }
                                .padding(horizontal = 12.dp),
                            color = gray,
                            fontSize = 22.sp
                        )
                    }
                }
            }

            if (shiftBlocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-12).dp)
                        .size(24.dp)
                        .background(Color.Black, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Смещение заблокировано для случайной позиции",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewChoiceButton(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = Color(0xFF9B8AF5)
    val gray = Color(0xFFA8A8A8)
    val outline = Color(0xFF4A4D52)
    Box(
        modifier = modifier.height(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.width(50.dp).height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1C1C24))
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) accent else outline,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) accent else gray,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun PreviewStepperButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(32.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color(0xFFA8A8A8), fontSize = 20.sp)
    }
}

@Composable
fun TrimSection(
    state: TrimState,
    onStateChange: (TrimState) -> Unit,
    onOpenSettings: () -> Unit
) {
    val background = Color(0xFF18181B)
    val outline = Color(0xFF2A2A35)
    val accent = Color(0xFF9B8AF5)
    val inactiveOutline = Color(0xFF4A4D52)
    val mutedText = Color(0xFFA8A8A8)
    val defaults = listOf(5, 10, 15, 30)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, outline, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .height(48.dp)
                    .clickable { onStateChange(state.copy(isEnabled = !state.isEnabled)) },
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            color = if (state.isEnabled) accent else mutedText,
                            shape = CircleShape
                        )
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        tint = if (state.isEnabled) Color.White else mutedText,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = "Обрезка",
                        color = if (state.isEnabled) Color.White else mutedText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            onStateChange(state.copy(isSymmetric = !state.isSymmetric))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.dp, inactiveOutline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = if (state.isSymmetric) {
                                "Симметричная обрезка"
                            } else {
                                "Раздельная обрезка"
                            },
                            tint = if (state.isSymmetric) accent else mutedText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Настройки обрезки",
                        tint = mutedText,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (state.isEnabled) 1f else 0.3f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.isSymmetric) {
                TrimSymmetricPresets(
                    state = state,
                    defaults = defaults,
                    onPresetClick = { index ->
                        val preset = state.presets.getOrElse(index) { defaults[index] }
                            .coerceIn(0, 100)
                        onStateChange(
                            state.copy(
                                startTrim = preset,
                                endTrim = preset,
                                activeSide = TrimSide.START
                            )
                        )
                    }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        TrimIndividualPreset(
                            modifier = Modifier.weight(1f),
                            state = state,
                            index = index,
                            preset = state.presets.getOrElse(index) { defaults[index] },
                            onPresetClick = { selectedIndex ->
                                val preset = state.presets
                                    .getOrElse(selectedIndex) { defaults[selectedIndex] }
                                    .coerceIn(0, 100)
                                val activeSide = if (selectedIndex < 2) {
                                    TrimSide.START
                                } else {
                                    TrimSide.END
                                }
                                onStateChange(
                                    if (activeSide == TrimSide.START) {
                                        state.copy(
                                            startTrim = preset,
                                            activeSide = activeSide
                                        )
                                    } else {
                                        state.copy(
                                            endTrim = preset,
                                            activeSide = activeSide
                                        )
                                    }
                                )
                            }
                        )
                    }
                }
            }

            TrimProgressBar(
                state = state,
                onStateChange = onStateChange
            )
        }
    }
}

@Composable
private fun TrimSymmetricPresets(
    state: TrimState,
    defaults: List<Int>,
    onPresetClick: (Int) -> Unit
) {
    val accent = Color(0xFF9B8AF5)
    val gray = Color(0xFFA8A8A8)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .drawBehind {
                val capsuleHeight = 26.dp.toPx()
                val top = (size.height - capsuleHeight) / 2f
                drawRoundRect(
                    color = Color(0xFF18181B),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, capsuleHeight),
                    cornerRadius = CornerRadius(capsuleHeight / 2f)
                )
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, capsuleHeight),
                    cornerRadius = CornerRadius(capsuleHeight / 2f),
                    style = Stroke(width = 1.dp.toPx())
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            val preset = state.presets.getOrElse(index) { defaults[index] }.coerceIn(0, 100)
            val selected = state.startTrim == preset && state.endTrim == preset
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(enabled = state.isEnabled) { onPresetClick(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${preset} с",
                    color = if (selected) Color.White else gray,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun TrimIndividualPreset(
    modifier: Modifier,
    state: TrimState,
    index: Int,
    preset: Int,
    onPresetClick: (Int) -> Unit
) {
    val accent = Color(0xFF9B8AF5)
    val grayOutline = Color(0xFF4A4D52)
    val grayText = Color(0xFFA8A8A8)
    val value = preset.coerceIn(0, 100)
    val activeValue = if (state.activeSide == TrimSide.START) {
        state.startTrim
    } else {
        state.endTrim
    }
    val selected = activeValue == value && (
        (state.activeSide == TrimSide.START && index < 2) ||
            (state.activeSide == TrimSide.END && index >= 2)
        )

    Box(
        modifier = modifier
            .height(48.dp)
            .drawBehind {
                val capsuleHeight = 26.dp.toPx()
                val top = (size.height - capsuleHeight) / 2f
                drawRoundRect(
                    color = Color(0xFF18181B),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, capsuleHeight),
                    cornerRadius = CornerRadius(capsuleHeight / 2f)
                )
                drawRoundRect(
                    color = if (selected) accent else grayOutline,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, capsuleHeight),
                    cornerRadius = CornerRadius(capsuleHeight / 2f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clickable(enabled = state.isEnabled) { onPresetClick(index) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${value} с",
            color = if (selected) Color.White else grayText,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun TrimProgressBar(
    state: TrimState,
    onStateChange: (TrimState) -> Unit
) {
    val accent = Color(0xFF9B8AF5)
    val trimmedColor = Color(0xFFA8A8A8)
    val activeDotSize = 10.dp
    val inactiveDotSize = 8.dp

    val boundedStart = state.startTrim.coerceIn(0, 100).toFloat()
    val boundedEnd = state.endTrim.coerceIn(0, 100).toFloat()
    val total = boundedStart + boundedEnd
    val startFraction = if (total > 100f) {
        (boundedStart / total)
    } else {
        boundedStart / 100f
    }
    val endFraction = if (total > 100f) {
        (boundedEnd / total)
    } else {
        boundedEnd / 100f
    }
    val canSelectPoint = state.isEnabled && !state.isSymmetric

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val trackWidth = (maxWidth - 48.dp).coerceAtLeast(0.dp)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(4.dp)
                .align(Alignment.Center)
        ) {
            val leftWidth = size.width * startFraction
            val rightWidth = size.width * endFraction
            val centerWidth = (size.width - leftWidth - rightWidth).coerceAtLeast(0f)
            drawRoundRect(
                color = trimmedColor,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(size.height / 2f)
            )
            if (centerWidth > 0f) {
                drawRect(
                    color = accent,
                    topLeft = Offset(leftWidth, 0f),
                    size = Size(centerWidth, size.height)
                )
            }
        }

        val startActive = !state.isSymmetric && state.activeSide == TrimSide.START
        val endActive = !state.isSymmetric && state.activeSide == TrimSide.END
        Box(
            modifier = Modifier
                .offset(x = trackWidth * startFraction)
                .size(48.dp)
                .clickable(enabled = canSelectPoint) {
                    onStateChange(state.copy(activeSide = TrimSide.START))
                }
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (startActive) activeDotSize else inactiveDotSize)
                    .clip(CircleShape)
                    .background(if (startActive) accent else Color.White)
            )
        }
        Box(
            modifier = Modifier
                .offset(x = trackWidth * (1f - endFraction))
                .size(48.dp)
                .clickable(enabled = canSelectPoint) {
                    onStateChange(state.copy(activeSide = TrimSide.END))
                }
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (endActive) activeDotSize else inactiveDotSize)
                    .clip(CircleShape)
                    .background(if (endActive) accent else Color.White)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimSettingsSheet(
    state: TrimState,
    onStateChange: (TrimState) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val defaults = listOf(5, 10, 15, 30)
    var presetTexts by remember {
        mutableStateOf(List(4) { state.presets.getOrElse(it) { defaults[it] }.toString() })
    }
    val background = Color(0xFF18181B)
    val outline = Color(0xFF2A2A35)
    val accent = Color(0xFF9B8AF5)
    val mainText = Color(0xFFF3F3F6)
    val secondaryText = Color(0xFFA8A8A8)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = background,
        contentColor = mainText,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Настройка пресетов",
                color = mainText,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
            )

            listOf(0, 2).forEach { rowStart ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (rowStart..rowStart + 1).forEach { index ->
                        OutlinedTextField(
                            value = presetTexts[index],
                            onValueChange = { rawValue ->
                                val cleaned = rawValue.filter { it.isDigit() }.take(3)
                                presetTexts = presetTexts.toMutableList().also {
                                    it[index] = cleaned
                                }
                                cleaned.toIntOrNull()?.let { number ->
                                    val clamped = number.coerceIn(0, 100)
                                    val updatedPresets = List(4) { presetIndex ->
                                        state.presets.getOrElse(presetIndex) { defaults[presetIndex] }
                                    }.toMutableList()
                                    updatedPresets[index] = clamped
                                    presetTexts = presetTexts.toMutableList().also {
                                        it[index] = clamped.toString()
                                    }
                                    onStateChange(state.copy(presets = updatedPresets))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    text = "Пресет ${index + 1}, с",
                                    color = secondaryText
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = mainText,
                                unfocusedTextColor = mainText,
                                focusedBorderColor = accent,
                                unfocusedBorderColor = outline,
                                cursorColor = accent,
                                focusedLabelColor = accent,
                                unfocusedLabelColor = secondaryText,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF121212)
                )
            ) {
                Text(
                    text = "Готово",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
