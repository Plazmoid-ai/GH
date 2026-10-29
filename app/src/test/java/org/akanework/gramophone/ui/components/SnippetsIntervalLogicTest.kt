/*
 *     Copyright (C) 2026 OpenAI
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import androidx.media3.common.C
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

        val zones = bounds(
            state,
            SnippetsContext(durationMs = 240_000L, lengthMs = 10_000L),
        )
        val starts = zones.map { it.first }

        // Integer division in calculateSnippetBounds() makes each start 1 ms earlier
        // than the rounded 35/115/195-second values.
        assertEquals(listOf(34_999L, 114_999L, 194_999L), starts)
        assertTrue(zones.all { it.endExclusive - it.first == 10_000L })
        println("Snippets Preview: T=240000 ms, L=10000 ms, home zones=111; starts(ms)=$starts")
        println("Snippets Preview: starts(s)=${starts.map { it / 1_000.0 }}")
    }

    @Test
    fun fourIntervalsUseEvenGapsWhenNominalGapIsAtLeastTenSeconds() {
        val state = fourIntervalState()
        val ctx = SnippetsContext(durationMs = 235_000L, lengthMs = 10_000L)

        val zones = bounds(state, ctx)
        val starts = zones.map { it.first }

        assertEquals(listOf(39_000L, 88_000L, 137_000L, 186_000L), starts)
        assertTrue(zones.all { it.endExclusive - it.first == ctx.lengthMs })
        assertTrue(fits(state, ctx))
        println("Snippets Intervals: n=4, T=235000 ms, L=10000 ms; starts(ms)=$starts")
    }

    @Test
    fun fourIntervalsUseTenSecondInternalGapsWhenTrackIsShorter() {
        val state = fourIntervalState()
        val ctx = SnippetsContext(durationMs = 70_000L, lengthMs = 10_000L)

        val zones = bounds(state, ctx)
        val starts = zones.map { it.first }

        assertEquals(listOf(0L, 20_000L, 40_000L, 60_000L), starts)
        assertTrue(zones.all { it.endExclusive - it.first == ctx.lengthMs })
        assertTrue(fits(state, ctx))
        println("Snippets Intervals: n=4, T=70000 ms, L=10000 ms; starts(ms)=$starts")
    }

    @Test
    fun fourIntervalsDoNotFitWhenMinimumGapsCannotFit() {
        val state = fourIntervalState()
        val ctx = SnippetsContext(69_999L, 10_000L)
        assertFalse(fits(state, ctx))
        assertTrue(bounds(state, ctx).isEmpty())
    }

    @Test
    fun invalidDurationsReturnNoBoundsAndDoNotFit() {
        val state = SnippetsState()
        val invalidDurations = listOf(C.TIME_UNSET, 0L, -1L, -10_000L)

        invalidDurations.forEach { duration ->
            val ctx = SnippetsContext(durationMs = duration, lengthMs = 10_000L)
            assertTrue("Expected empty bounds for duration=$duration", bounds(state, ctx).isEmpty())
            assertFalse("Expected fits=false for duration=$duration", fits(state, ctx))
        }
    }

    @Test
    fun nonPositiveLengthReturnsNoBoundsAndDoesNotFit() {
        val state = SnippetsState()
        listOf(0L, -1L).forEach { length ->
            val ctx = SnippetsContext(durationMs = 240_000L, lengthMs = length)
            assertTrue(bounds(state, ctx).isEmpty())
            assertFalse(fits(state, ctx))
        }
    }

    @Test
    fun previewStateWithTrackShorterThanLengthDoesNotFit() {
        val state = SnippetsState()
        assertFalse(fits(state, SnippetsContext(9_999L, 10_000L)))
    }

    @Test
    fun validStatePassesIsValid() {
        assertTrue(SnippetsState().isValid())
        assertTrue(
            SnippetsState(
                mode = SnippetsMode.INTERVALS,
                activeHomeZones = listOf(true, true, true),
            ).isValid(),
        )
        assertTrue(fourIntervalState().isValid())
    }

    @Test
    fun invalidPreviewStateCanBeConstructedAndNormalized() {
        val invalid = SnippetsState(
            mode = SnippetsMode.PREVIEW,
            selectedPreviewZone = 8,
            activeHomeZones = listOf(false, true, true, true),
            extraIntervalsCount = 5,
        )

        assertFalse(invalid.isValid())
        val normalized = invalid.normalized()
        assertEquals(SnippetsMode.PREVIEW, normalized.mode)
        assertEquals(2, normalized.selectedPreviewZone)
        assertEquals(listOf(false, false, true), normalized.activeHomeZones)
        assertEquals(0, normalized.extraIntervalsCount)
        assertTrue(normalized.isValid())
    }

    @Test
    fun positiveExtraCountNormalizesToIntervalsWithAllHomeZonesActive() {
        val invalid = SnippetsState(
            mode = SnippetsMode.PREVIEW,
            selectedPreviewZone = 1,
            activeHomeZones = listOf(true),
            extraIntervalsCount = 2,
        )

        assertFalse(invalid.isValid())
        val normalized = invalid.normalized()
        assertEquals(SnippetsMode.INTERVALS, normalized.mode)
        assertEquals(1, normalized.selectedPreviewZone)
        assertEquals(listOf(true, true, true), normalized.activeHomeZones)
        assertEquals(2, normalized.extraIntervalsCount)
        assertTrue(normalized.isValid())
    }

    @Test
    fun intervalsWithoutActiveHomesNormalizeToSelectedHomeZone() {
        val invalid = SnippetsState(
            mode = SnippetsMode.INTERVALS,
            selectedPreviewZone = 2,
            activeHomeZones = listOf(false, false, false),
            extraIntervalsCount = -4,
        )

        assertFalse(invalid.isValid())
        val normalized = invalid.normalized()
        assertEquals(listOf(false, false, true), normalized.activeHomeZones)
        assertEquals(0, normalized.extraIntervalsCount)
        assertTrue(normalized.isValid())
    }

    private fun fourIntervalState() = SnippetsState(
        mode = SnippetsMode.INTERVALS,
        selectedPreviewZone = 0,
        activeHomeZones = listOf(true, true, true),
        extraIntervalsCount = 1,
    )
}
