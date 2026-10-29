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

class SnippetsStubEngineTest {
    private val engine = StubSnippetsEngine()

    @Test
    fun threeHomeZonesUseFixedOneSixthCentersAndConfiguredLength() {
        val state = SnippetsState(
            mode = SnippetsMode.INTERVALS,
            selectedPreviewZone = 0,
            activeHomeZones = listOf(true, true, true),
            extraIntervalsCount = 0,
        )
        val ctx = SnippetsContext(durationMs = 180_000L, lengthMs = 10_000L)

        val zones = engine.bounds(state, ctx)

        assertEquals(listOf(25_000L, 85_000L, 145_000L), zones.map { it.first })
        assertEquals(listOf(35_000L, 95_000L, 155_000L), zones.map { it.endMsExclusive })
        assertTrue(zones.all { it.endMsExclusive - it.first == ctx.lengthMs })
    }

    @Test
    fun fiveZonesAreEvenlyCenteredWhenExtrasArePresent() {
        val state = SnippetsState(
            mode = SnippetsMode.INTERVALS,
            selectedPreviewZone = 0,
            activeHomeZones = listOf(true, true, true),
            extraIntervalsCount = 2,
        )
        val ctx = SnippetsContext(durationMs = 180_000L, lengthMs = 10_000L)

        val zones = engine.bounds(state, ctx)

        assertEquals(listOf(25_000L, 55_000L, 85_000L, 115_000L, 145_000L), zones.map { it.first })
        assertEquals(5, zones.size)
        assertTrue(zones.all { it.endMsExclusive - it.first == ctx.lengthMs })
    }

    @Test
    fun fitsAppliesCountAndHalfTrackLimitsAndForceNoFit() {
        val ctx = SnippetsContext(durationMs = 200_000L, lengthMs = 100_000L)
        val sevenZones = SnippetsState(
            mode = SnippetsMode.INTERVALS,
            activeHomeZones = listOf(true, true, true),
            extraIntervalsCount = 4,
        )
        val eightZones = sevenZones.copy(extraIntervalsCount = 5)
        assertTrue(engine.fits(sevenZones, ctx))
        assertFalse(engine.fits(eightZones, ctx))
        assertFalse(engine.fits(sevenZones, ctx.copy(lengthMs = 100_001L)))

        engine.forceNoFit = true
        assertFalse(engine.fits(sevenZones, ctx))
        engine.forceNoFit = false
        assertTrue(engine.fits(sevenZones, ctx))
    }
}
