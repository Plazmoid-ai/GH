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
}

private const val SNIPPETS_NO_FIT_TOOLTIP = "Не помещается на треке"

/**
 * Reduces one Snippets event without touching the player or UI.
 *
 * Only PREVIEW handling for TapTab and TapPlus is implemented in this stage.
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

    SnippetsEvent.TapExtra -> {
        // TODO: Implement extra-interval activation in a later stage.
        state to SnippetsEffect.None
    }

    SnippetsEvent.LongPressReturn -> {
        // TODO: Implement the return action in a later stage.
        state to SnippetsEffect.None
    }

    SnippetsEvent.TrackChanged -> {
        // TODO: Define state reset/retention when the track changes.
        state to SnippetsEffect.None
    }

    is SnippetsEvent.ChevronBack -> {
        // TODO: Implement backward navigation in a later stage.
        state to SnippetsEffect.None
    }

    is SnippetsEvent.ChevronNext -> {
        // TODO: Implement forward navigation in a later stage.
        state to SnippetsEffect.None
    }
}

private fun reduceTapTab(
    state: SnippetsState,
    index: Int,
    ctx: SnippetsContext,
    playing: Boolean,
): Pair<SnippetsState, SnippetsEffect> {
    if (state.mode != SnippetsMode.PREVIEW) {
        // TODO: Implement tab selection while in INTERVALS mode.
        return state to SnippetsEffect.None
    }
    if (index !in 0..2) return state to SnippetsEffect.None

    // While paused, tapping the already-selected tab is a no-op. During playback,
    // the same tab can be tapped to seek back to the beginning of that preview.
    if (!playing && index == state.selectedPreviewZone) {
        return state to SnippetsEffect.None
    }

    val nextState = state.copy(
        mode = SnippetsMode.PREVIEW,
        selectedPreviewZone = index,
        activeHomeZones = List(3) { it == index },
        extraIntervalsCount = 0,
    )
    val startMs = bounds(nextState, ctx).minByOrNull { it.first }?.first
    val effect = startMs?.let { SnippetsEffect.SeekTo(it) } ?: SnippetsEffect.None
    return nextState to effect
}

private fun reduceTapPlus(
    state: SnippetsState,
    ctx: SnippetsContext,
    playing: Boolean,
): Pair<SnippetsState, SnippetsEffect> {
    if (playing) return state to SnippetsEffect.None
    if (state.mode != SnippetsMode.PREVIEW) {
        // TODO: Define plus-button behavior in INTERVALS mode.
        return state to SnippetsEffect.None
    }

    // PREVIEW has one active home zone. Try the remaining zones in ascending order.
    val candidates = (0..2).filter { index ->
        index != state.selectedPreviewZone && state.activeHomeZones.getOrNull(index) != true
    }

    for (candidate in candidates) {
        val candidateState = state.copy(
            mode = SnippetsMode.INTERVALS,
            activeHomeZones = List(3) { index ->
                index == state.selectedPreviewZone || index == candidate
            },
            extraIntervalsCount = 0,
        )

        if (!fits(candidateState, ctx)) continue

        val startMs = bounds(candidateState, ctx).minByOrNull { it.first }?.first
            ?: continue
        return candidateState to SnippetsEffect.SeekTo(startMs)
    }

    return state to SnippetsEffect.Tooltip(SNIPPETS_NO_FIT_TOOLTIP)
}
