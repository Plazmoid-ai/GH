/*
 *     Copyright (C) 2026 OpenAI
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import kotlin.math.roundToLong

/**
 * Supplies interval boundaries and fit checks to the Snippets model.
 *
 * The real implementation preserves the existing calculation behavior.
 * The stub implementation is isolated for UI sandbox use.
 */
interface SnippetsEngine {
    fun bounds(state: SnippetsState, ctx: SnippetsContext): List<LongRange>
    fun fits(state: SnippetsState, ctx: SnippetsContext): Boolean
}

/** Delegates directly to the existing production algorithms. */
object RealSnippetsEngine : SnippetsEngine {
    override fun bounds(state: SnippetsState, ctx: SnippetsContext): List<LongRange> =
        org.akanework.gramophone.ui.components.bounds(state, ctx)

    override fun fits(state: SnippetsState, ctx: SnippetsContext): Boolean =
        org.akanework.gramophone.ui.components.fits(state, ctx)
}

/**
 * Predictable boundaries and fit checks for isolated UI sandbox use.
 * This class intentionally does not read or mutate SnippetsPreviewState.
 */
class StubSnippetsEngine(
    var forceNoFit: Boolean = false,
) : SnippetsEngine {
    override fun bounds(state: SnippetsState, ctx: SnippetsContext): List<LongRange> {
        val durationMs = ctx.durationMs
        val lengthMs = ctx.lengthMs
        if (durationMs <= 0L || lengthMs <= 0L || lengthMs > durationMs) {
            return emptyList()
        }

        val extraCount = state.extraIntervalsCount.coerceAtLeast(0)
        val count = if (extraCount > 0) {
            3L + extraCount.toLong()
        } else {
            state.activeHomeZones.count { it }.toLong()
        }
        if (count <= 0L || count > Int.MAX_VALUE.toLong()) return emptyList()

        val maxStartMs = durationMs - lengthMs
        fun intervalAt(centerNumerator: Long, centerDenominator: Long): LongRange {
            val centerMs = durationMs.toDouble() * centerNumerator / centerDenominator
            val rawStartMs = (centerMs - lengthMs.toDouble() / 2.0).roundToLong()
            val startMs = rawStartMs.coerceIn(0L, maxStartMs)
            return startMs..(startMs + lengthMs - 1L)
        }

        if (extraCount > 0) {
            val denominator = count + 1L
            return (0L until count).map { index ->
                intervalAt(index + 1L, denominator)
            }
        }

        return state.activeHomeZones.mapIndexedNotNull { index, active ->
            if (!active) null else intervalAt(2L * index.toLong() + 1L, 6L)
        }
    }

    override fun fits(state: SnippetsState, ctx: SnippetsContext): Boolean {
        if (forceNoFit || ctx.durationMs <= 0L || ctx.lengthMs <= 0L) return false
        val extraCount = state.extraIntervalsCount.coerceAtLeast(0)
        val count = if (extraCount > 0) {
            3L + extraCount.toLong()
        } else {
            state.activeHomeZones.count { it }.toLong()
        }
        return count in 1L..7L && ctx.lengthMs <= ctx.durationMs / 2L
    }
}
