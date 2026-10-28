/*
 *     Copyright (C) 2026 OpenAI
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetsIntervalLogicTest {
    @Test
    fun threeHomeZonesUseExistingPreviewStartsAndPrintThem() {
        val state = SnippetsState(
            mode = SnippetsMode.INTERVALS,
            selectedPreviewZone = 0,
            activeHomeZones = listOf(true, true, true),
            extraIntervalsCount = 0,
        )

        val starts = bounds(
            state,
            SnippetsContext(durationMs = 240_000L, lengthMs = 10_000L),
        ).map { it.first }

        // Integer division in calculateSnippetBounds() makes each start 1 ms earlier
        // than the rounded 35/115/195-second values.
        assertEquals(listOf(34_999L, 114_999L, 194_999L), starts)
        println("Snippets Preview: T=240000 ms, L=10000 ms, home zones=111; starts(ms)=$starts")
        println("Snippets Preview: starts(s)=${starts.map { it / 1_000.0 }}")
    }

    @Test
    fun fourIntervalsUseEvenGapsWhenNominalGapIsAtLeastTenSeconds() {
        val state = fourIntervalState()

        val starts = bounds(
            state,
            SnippetsContext(durationMs = 235_000L, lengthMs = 10_000L),
        ).map { it.first }

        assertEquals(listOf(39_000L, 88_000L, 137_000L, 186_000L), starts)
        assertTrue(fits(state, SnippetsContext(235_000L, 10_000L)))
        println("Snippets Intervals: n=4, T=235000 ms, L=10000 ms; starts(ms)=$starts")
    }

    @Test
    fun fourIntervalsUseTenSecondInternalGapsWhenTrackIsShorter() {
        val state = fourIntervalState()

        val starts = bounds(
            state,
            SnippetsContext(durationMs = 70_000L, lengthMs = 10_000L),
        ).map { it.first }

        assertEquals(listOf(0L, 20_000L, 40_000L, 60_000L), starts)
        assertTrue(fits(state, SnippetsContext(70_000L, 10_000L)))
        println("Snippets Intervals: n=4, T=70000 ms, L=10000 ms; starts(ms)=$starts")
    }

    @Test
    fun fourIntervalsDoNotFitWhenMinimumGapsCannotFit() {
        val state = fourIntervalState()
        assertFalse(fits(state, SnippetsContext(69_999L, 10_000L)))
        assertTrue(bounds(state, SnippetsContext(69_999L, 10_000L)).isEmpty())
    }

    @Test
    fun onePreviewDoesNotFitWhenTrackIsShorterThanConfiguredLength() {
        val state = SnippetsState()
        assertFalse(fits(state, SnippetsContext(9_999L, 10_000L)))
    }

    @Test
    fun threePreviewZonesFitOnFourMinuteTrack() {
        val state = SnippetsState(
            mode = SnippetsMode.INTERVALS,
            activeHomeZones = listOf(true, true, true),
        )
        assertTrue(fits(state, SnippetsContext(240_000L, 10_000L)))
    }

    private fun fourIntervalState() = SnippetsState(
        mode = SnippetsMode.INTERVALS,
        selectedPreviewZone = 0,
        activeHomeZones = listOf(true, true, true),
        extraIntervalsCount = 1,
    )
}
