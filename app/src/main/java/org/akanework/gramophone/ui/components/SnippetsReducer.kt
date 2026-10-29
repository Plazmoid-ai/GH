/*
 *     Copyright (C) 2026 OpenAI
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

/**
 * Inputs to the Snippets state reducer.
 *
 * This model contains no player or UI references. The caller translates effects
 * into playback/UI operations.
 */
sealed class SnippetsEvent {
    data class TapTab(val i: Int) : SnippetsEvent()
    object TapPlus : SnippetsEvent()
    object TapExtra : SnippetsEvent()
    object LongPressReturn : SnippetsEvent()
    object TrackChanged : SnippetsEvent()
    data class ChevronBack(val positionMs: Long) : SnippetsEvent()
    data class ChevronNext(val positionMs: Long) : SnippetsEvent()
}

/** Side effects requested by the reducer; none of them are performed here. */
sealed class SnippetsEffect {
    data class SeekTo(val ms: Long) : SnippetsEffect()
    data class Tooltip(val text: String) : SnippetsEffect()
    object None : SnippetsEffect()

    /**
     * Ordered effects for actions that need both a seek and a message.
     * Consumers should process this list from first to last.
     */
    data class Multiple(val effects: List<SnippetsEffect>) : SnippetsEffect()
}

data class TrimResult(
    val state: SnippetsState,
    val removedCount: Int,
)

private const val SNIPPETS_NO_FIT_TOOLTIP = "Не помещается на треке"
private const val SNIPPETS_LAST_INTERVAL_TOOLTIP = "Нужен хотя бы один активный интервал"

private fun homeZoneLabel(index: Int): String = when (index) {
    0 -> "начало"
    1 -> "середина"
    else -> "конец"
}

/**
 * Reduces one Snippets event without touching the player or UI.
 *
 * Only the requested PREVIEW and INTERVALS actions are implemented. TrackChanged,
 * ChevronBack and ChevronNext deliberately remain no-ops for their later stages.
 * [playing] represents the player's playWhenReady value.
 */
fun reduce(
    state: SnippetsState,
    event: SnippetsEvent,
    ctx: SnippetsContext,
    playing: Boolean,
): Pair<SnippetsState, SnippetsEffect> = when (event) {
    is SnippetsEvent.TapTab -> reduceTapTab(state, event.i, ctx, playing)
    SnippetsEvent.TapPlus -> reduceTapPlus(state, ctx, playing)
    SnippetsEvent.TapExtra -> reduceTapExtra(state, ctx, playing)
    SnippetsEvent.LongPressReturn -> reduceLongPressReturn(state, ctx, playing)

    SnippetsEvent.TrackChanged -> {
        // TODO: Track-change behavior belongs to a later stage.
        state to SnippetsEffect.None
    }

    is SnippetsEvent.ChevronBack -> {
        // TODO: Chevron navigation belongs to a later stage.
        state to SnippetsEffect.None
    }

    is SnippetsEvent.ChevronNext -> {
        // TODO: Chevron navigation belongs to a later stage.
        state to SnippetsEffect.None
    }
}

private fun reduceTapTab(
    state: SnippetsState,
    index: Int,
    ctx: SnippetsContext,
    playing: Boolean,
): Pair<SnippetsState, SnippetsEffect> = when (state.mode) {
    SnippetsMode.PREVIEW -> reducePreviewTapTab(state, index, ctx, playing)
    SnippetsMode.INTERVALS -> reduceIntervalsTapTab(state, index, ctx, playing)
}

private fun reducePreviewTapTab(
    state: SnippetsState,
    index: Int,
    ctx: SnippetsContext,
    playing: Boolean,
): Pair<SnippetsState, SnippetsEffect> {
    if (index !in 0..2) return state to SnippetsEffect.None

    // While paused, tapping the already-selected tab is a no-op. During playback,
    // tapping it seeks back to the beginning of that preview.
    if (!playing && index == state.selectedPreviewZone) {
        return state to SnippetsEffect.None
    }

    val nextState = state.copy(
        mode = SnippetsMode.PREVIEW,
        selectedPreviewZone = index,
        activeHomeZones = List(3) { it == index },
        extraIntervalsCount = 0,
    )
    return nextState to seekToFirstZone(nextState, ctx)
}

private fun reduceIntervalsTapTab(
    state: SnippetsState,
    index: Int,
    ctx: SnippetsContext,
    playing: Boolean,
): Pair<SnippetsState, SnippetsEffect> {
    if (playing || index !in 0..2) return state to SnippetsEffect.None

    val base = state.normalized()

    // While extra intervals exist, tapping any home tab removes one extra interval.
    if (base.extraIntervalsCount > 0) {
        val nextState = base.copy(
            mode = SnippetsMode.INTERVALS,
            activeHomeZones = listOf(true, true, true),
            extraIntervalsCount = base.extraIntervalsCount - 1,
        )
        return nextState to seekToFirstZone(nextState, ctx)
    }

    val isActive = base.activeHomeZones[index]
    if (isActive) {
        if (base.activeHomeZones.count { it } == 1) {
            return state to SnippetsEffect.Tooltip(SNIPPETS_LAST_INTERVAL_TOOLTIP)
        }

        val nextState = base.copy(
            activeHomeZones = base.activeHomeZones.mapIndexed { zoneIndex, active ->
                active && zoneIndex != index
            },
        )
        return nextState to seekToFirstZone(nextState, ctx)
    }

    val candidate = base.copy(
        activeHomeZones = base.activeHomeZones.mapIndexed { zoneIndex, active ->
            active || zoneIndex == index
        },
    )
    if (!fits(candidate, ctx)) {
        return state to SnippetsEffect.Tooltip(SNIPPETS_NO_FIT_TOOLTIP)
    }
    return candidate to seekToFirstZone(candidate, ctx)
}

private fun reduceTapPlus(
    state: SnippetsState,
    ctx: SnippetsContext,
    playing: Boolean,
): Pair<SnippetsState, SnippetsEffect> {
    if (state.mode == SnippetsMode.PREVIEW) {
        if (playing) return state to SnippetsEffect.None

        val base = state.normalized()
        // In PREVIEW, the selected home zone is active. Add the first other home
        // zone that fits, preserving the existing preview-to-interval transition.
        val candidates = (0..2).filter { index ->
            index != base.selectedPreviewZone && !base.activeHomeZones[index]
        }
        for (index in candidates) {
            val candidate = base.copy(
                mode = SnippetsMode.INTERVALS,
                activeHomeZones = base.activeHomeZones.mapIndexed { zoneIndex, active ->
                    active || zoneIndex == index
                },
                extraIntervalsCount = 0,
            )
            if (fits(candidate, ctx)) {
                return candidate to seekToFirstZone(candidate, ctx)
            }
        }

        return state to SnippetsEffect.Tooltip(SNIPPETS_NO_FIT_TOOLTIP)
    }

    if (playing) return state to SnippetsEffect.None

    val base = state.normalized()
    if (base.extraIntervalsCount > 0) {
        val candidate = base.copy(extraIntervalsCount = base.extraIntervalsCount + 1)
        if (!fits(candidate, ctx)) {
            return state to SnippetsEffect.Tooltip(SNIPPETS_NO_FIT_TOOLTIP)
        }
        return candidate to seekToFirstZone(candidate, ctx)
    }

    val freeHome = base.activeHomeZones.indices.firstOrNull { !base.activeHomeZones[it] }
    if (freeHome != null) {
        val candidate = base.copy(
            activeHomeZones = base.activeHomeZones.mapIndexed { index, active ->
                active || index == freeHome
            },
        )
        if (!fits(candidate, ctx)) {
            return state to SnippetsEffect.Tooltip(SNIPPETS_NO_FIT_TOOLTIP)
        }
        return candidate to seekToFirstZone(candidate, ctx)
    }

    // All three home zones are active; try adding the first extra interval.
    val candidate = base.copy(
        activeHomeZones = listOf(true, true, true),
        extraIntervalsCount = 1,
    )
    if (!fits(candidate, ctx)) {
        return state to SnippetsEffect.Tooltip(SNIPPETS_NO_FIT_TOOLTIP)
    }
    return candidate to seekToFirstZone(candidate, ctx)
}

private fun reduceTapExtra(
    state: SnippetsState,
    ctx: SnippetsContext,
    playing: Boolean,
): Pair<SnippetsState, SnippetsEffect> {
    if (playing || state.mode != SnippetsMode.INTERVALS) {
        return state to SnippetsEffect.None
    }

    val base = state.normalized()
    if (base.extraIntervalsCount <= 0) return state to SnippetsEffect.None

    // Removing the last extra interval leaves INTERVALS mode with all three home zones.
    val nextState = base.copy(
        mode = SnippetsMode.INTERVALS,
        activeHomeZones = listOf(true, true, true),
        extraIntervalsCount = base.extraIntervalsCount - 1,
    )
    return nextState to seekToFirstZone(nextState, ctx)
}

private fun reduceLongPressReturn(
    state: SnippetsState,
    ctx: SnippetsContext,
    playing: Boolean,
): Pair<SnippetsState, SnippetsEffect> {
    if (playing || state.mode != SnippetsMode.INTERVALS) {
        return state to SnippetsEffect.None
    }

    val base = state.normalized()
    val selected = base.selectedPreviewZone
    val nextState = base.copy(
        mode = SnippetsMode.PREVIEW,
        activeHomeZones = List(3) { it == selected },
        extraIntervalsCount = 0,
    ).normalized()

    val startMs = firstZoneStart(nextState, ctx)
    val effects = listOf(
        SnippetsEffect.SeekTo(startMs),
        SnippetsEffect.Tooltip("Превью: ${homeZoneLabel(selected)}"),
    )
    return nextState to SnippetsEffect.Multiple(effects)
}

private fun seekToFirstZone(
    state: SnippetsState,
    ctx: SnippetsContext,
): SnippetsEffect.SeekTo = SnippetsEffect.SeekTo(firstZoneStart(state, ctx))

private fun firstZoneStart(state: SnippetsState, ctx: SnippetsContext): Long =
    bounds(state, ctx).minByOrNull { it.first }?.first ?: 0L

/**
 * Reduces an INTERVALS state until its selected zones fit the track.
 *
 * Extra intervals are removed first. Once N reaches zero, home zones are removed
 * from the highest index down, while retaining at least one active home zone.
 * PREVIEW is not reduced: it is returned unchanged unless its configured length
 * exceeds the track duration.
 */
fun trimToFit(state: SnippetsState, ctx: SnippetsContext): TrimResult? {
    if (state.mode == SnippetsMode.PREVIEW) {
        if (ctx.lengthMs > ctx.durationMs) return null
        return TrimResult(state, removedCount = 0)
    }

    var current = state.normalized()
    var removedCount = 0

    while (!fits(current, ctx)) {
        if (current.extraIntervalsCount > 0) {
            current = current.copy(
                mode = SnippetsMode.INTERVALS,
                activeHomeZones = listOf(true, true, true),
                extraIntervalsCount = current.extraIntervalsCount - 1,
            )
            removedCount++
            continue
        }

        if (current.activeHomeZones.count { it } <= 1) return null

        val lastActiveIndex = current.activeHomeZones.indexOfLast { it }
        current = current.copy(
            activeHomeZones = current.activeHomeZones.mapIndexed { index, active ->
                active && index != lastActiveIndex
            },
        )
        removedCount++
    }

    return TrimResult(current, removedCount)
}
