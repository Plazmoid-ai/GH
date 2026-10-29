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
 * Valid PREVIEW state has exactly one active home zone and no extra intervals.
 * If extra intervals are present, all home zones must be active in INTERVALS mode.
 *
 * Constructor deliberately does not enforce invariants: callers can validate with isValid()
 * or repair input with normalized().
 */
data class SnippetsState(
    val mode: SnippetsMode = SnippetsMode.PREVIEW,
    val selectedPreviewZone: Int = 0,
    val activeHomeZones: List<Boolean> = listOf(true, false, false),
    val extraIntervalsCount: Int = 0,
) {
    fun isValid(): Boolean {
        if (selectedPreviewZone !in 0..2) return false
        if (activeHomeZones.size != 3) return false
        if (activeHomeZones.none { it }) return false
        if (extraIntervalsCount < 0) return false

        if (mode == SnippetsMode.PREVIEW) {
            if (activeHomeZones.count { it } != 1 || extraIntervalsCount != 0) return false
            if (!activeHomeZones[selectedPreviewZone]) return false
        }
        if (extraIntervalsCount > 0) {
            if (mode != SnippetsMode.INTERVALS || !activeHomeZones.all { it }) return false
        }
        return true
    }

    /**
     * Returns a state with the mode invariants repaired.
     * PREVIEW keeps only the selected home zone and clears extra intervals.
     * Any positive extra count switches to INTERVALS and enables all home zones.
     */
    fun normalized(): SnippetsState {
        val selected = selectedPreviewZone.coerceIn(0, 2)
        val homeZones = activeHomeZones.take(3).toMutableList().apply {
            while (size < 3) add(false)
        }
        val extras = extraIntervalsCount.coerceAtLeast(0)

        return when {
            extras > 0 -> copy(
                mode = SnippetsMode.INTERVALS,
                selectedPreviewZone = selected,
                activeHomeZones = listOf(true, true, true),
                extraIntervalsCount = extras,
            )

            mode == SnippetsMode.PREVIEW -> copy(
                mode = SnippetsMode.PREVIEW,
                selectedPreviewZone = selected,
                activeHomeZones = List(3) { it == selected },
                extraIntervalsCount = 0,
            )

            else -> {
                if (homeZones.none { it }) homeZones[selected] = true
                copy(
                    mode = SnippetsMode.INTERVALS,
                    selectedPreviewZone = selected,
                    activeHomeZones = homeZones.toList(),
                    extraIntervalsCount = 0,
                )
            }
        }
    }
}

data class SnippetsContext(
    val durationMs: Long,
    val lengthMs: Long,
    val engine: SnippetsEngine = RealSnippetsEngine,
)

/** Inclusive LongRange end represented as the first millisecond position after the range. */
val LongRange.endMsExclusive: Long
    get() = last + 1L

/**
 * Returns the active zones as inclusive LongRanges. The range endpoints are inclusive,
 * so a zone of lengthMs contains exactly lengthMs millisecond positions when it fits.
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

    if (candidates.size == 1) {
        return candidates.mapNotNull { (start, endMsExclusive) ->
            start.toInclusiveRange(endMsExclusive)
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
        val endMsExclusiveMs = startMs + ctx.lengthMs
        result.add(startMs..(endMsExclusiveMs - 1L))
        startMs = endMsExclusiveMs + gapMs
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
                range.endMsExclusive > ctx.durationMs ||
                range.endMsExclusive - range.first < ctx.lengthMs
        }
    ) {
        return false
    }

    return calculated.zipWithNext().all { (current, next) ->
        next.first - current.endMsExclusive >= MIN_SNIPPET_GAP_MS
    }
}

private fun Long.toInclusiveRange(endMsExclusive: Long): LongRange? {
    if (endMsExclusive <= this) return null
    return this..(endMsExclusive - 1L)
}
