/*
 *     Copyright (C) 2026 OpenAI
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

private const val MIN_SNIPPET_GAP_MS = 10_000L

enum class SnippetsMode {
    PREVIEW,
    INTERVALS,
}

/**
 * Pure model of the preview/interval selection. This does not own playback or UI state.
 *
 * PREVIEW always has exactly one active home zone and no extra intervals.
 * Extra intervals are only valid in INTERVALS mode, where all three home zones are active.
 */
data class SnippetsState(
    val mode: SnippetsMode = SnippetsMode.PREVIEW,
    val selectedPreviewZone: Int = 0,
    val activeHomeZones: List<Boolean> = listOf(true, false, false),
    val extraIntervalsCount: Int = 0,
) {
    init {
        require(selectedPreviewZone in 0..2) {
            "selectedPreviewZone must be in 0..2"
        }
        require(activeHomeZones.size == 3) {
            "activeHomeZones must contain exactly three entries"
        }
        require(activeHomeZones.any { it }) {
            "At least one home zone must be active"
        }
        require(extraIntervalsCount >= 0) {
            "extraIntervalsCount cannot be negative"
        }
        if (mode == SnippetsMode.PREVIEW) {
            require(activeHomeZones.count { it } == 1 && extraIntervalsCount == 0) {
                "PREVIEW requires exactly one active home zone and no extra intervals"
            }
            require(activeHomeZones[selectedPreviewZone]) {
                "The selected preview zone must be the only active home zone"
            }
        }
        if (extraIntervalsCount > 0) {
            require(mode == SnippetsMode.INTERVALS && activeHomeZones.all { it }) {
                "Extra intervals require INTERVALS mode and all three home zones active"
            }
        }
    }
}

data class SnippetsContext(
    val durationMs: Long,
    val lengthMs: Long,
)

/**
 * Returns the active zones as LongRanges. The range endpoints are inclusive, so each
 * returned range contains exactly lengthMs millisecond positions when it fits.
 *
 * For up to three active zones, starts are taken from the existing calculateSnippetBounds()
 * algorithm. We deliberately do not call resolveActiveSnippetBounds(): it reads and mutates
 * the global Compose activeZones list, which would make this model dependent on UI state.
 *
 * Four or more zones use evenly distributed gaps across the full track.
 */
fun bounds(state: SnippetsState, ctx: SnippetsContext): List<LongRange> {
    if (ctx.durationMs <= 0L || ctx.lengthMs <= 0L) return emptyList()

    if (state.extraIntervalsCount > 0) {
        return autoCenteredBounds(
            intervalCount = 3L + state.extraIntervalsCount.toLong(),
            ctx = ctx,
        )
    }

    val candidates = SnippetsPreviewState.calculateSnippetBounds(
        trackDurationMs = ctx.durationMs,
        previewDurationMs = ctx.lengthMs,
    )
    if (candidates.isEmpty()) return emptyList()

    // The existing calculator collapses to the whole track when length >= duration.
    // Keep that real boundary; fits() will reject it if the requested interval cannot fit.
    if (candidates.size == 1) {
        return candidates.mapNotNull { (start, endExclusive) ->
            start.toInclusiveRange(endExclusive)
        }
    }

    return state.activeHomeZones.mapIndexedNotNull { index, active ->
        if (!active) return@mapIndexedNotNull null
        val candidate = candidates.getOrNull(index) ?: return@mapIndexedNotNull null
        candidate.first.toInclusiveRange(candidate.second)
    }.sortedBy { it.first }
}

private fun autoCenteredBounds(
    intervalCount: Long,
    ctx: SnippetsContext,
): List<LongRange> {
    if (
        intervalCount <= 0L ||
        intervalCount > Int.MAX_VALUE.toLong() ||
        intervalCount > ctx.durationMs / ctx.lengthMs
    ) {
        return emptyList()
    }

    val occupiedMs = intervalCount * ctx.lengthMs
    val unusedMs = ctx.durationMs - occupiedMs
    val nominalGapMs = unusedMs / (intervalCount + 1L)

    val edgeMs: Long
    val gapMs: Long
    if (nominalGapMs >= MIN_SNIPPET_GAP_MS) {
        edgeMs = nominalGapMs
        gapMs = nominalGapMs
    } else {
        val requiredInternalGapsMs = (intervalCount - 1L) * MIN_SNIPPET_GAP_MS
        val remainingForEdgesMs = unusedMs - requiredInternalGapsMs
        if (remainingForEdgesMs < 0L) return emptyList()
        edgeMs = remainingForEdgesMs / 2L
        gapMs = MIN_SNIPPET_GAP_MS
    }

    val count = intervalCount.toInt()
    val result = ArrayList<LongRange>(count)
    var startMs = edgeMs
    repeat(count) {
        val endExclusiveMs = startMs + ctx.lengthMs
        result.add(startMs..(endExclusiveMs - 1L))
        startMs = endExclusiveMs + gapMs
    }
    return result
}

/**
 * Checks the actual calculated boundaries before a proposed state change is applied.
 * Every interval must have its configured length, lie inside the track, and be separated
 * from the next interval by at least 10,000 ms.
 */
fun fits(state: SnippetsState, ctx: SnippetsContext): Boolean {
    if (ctx.durationMs <= 0L || ctx.lengthMs <= 0L) return false

    val expectedCount = if (state.extraIntervalsCount > 0) {
        3L + state.extraIntervalsCount.toLong()
    } else {
        state.activeHomeZones.count { it }.toLong()
    }
    val calculated = bounds(state, ctx)
    if (calculated.size.toLong() != expectedCount || calculated.isEmpty()) return false

    if (calculated.any { range ->
            range.first < 0L ||
                range.last >= ctx.durationMs ||
                range.last - range.first + 1L < ctx.lengthMs
        }
    ) {
        return false
    }

    return calculated.zipWithNext().all { (current, next) ->
        next.first - current.last - 1L >= MIN_SNIPPET_GAP_MS
    }
}

private fun Long.toInclusiveRange(endExclusive: Long): LongRange? {
    if (endExclusive <= this) return null
    return this..(endExclusive - 1L)
}
