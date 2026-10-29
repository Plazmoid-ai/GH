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

class SnippetsReducerTest {
    private val standardContext = SnippetsContext(
        durationMs = 240_000L,
        lengthMs = 10_000L,
    )

    @Test
    fun pausedTapOnSelectedTabDoesNothing() {
        val state = previewState(selected = 0)

        val (actualState, effect) = reduce(
            state,
            SnippetsEvent.TapTab(0),
            standardContext,
            playing = false,
        )

        assertEquals("Paused tap on the selected tab must preserve state; actual=$actualState", state, actualState)
        assertEquals("Paused tap on the selected tab must produce None; actual=$effect", SnippetsEffect.None, effect)
    }

    @Test
    fun pausedTapOnDifferentTabSelectsOnlyThatZoneAndSeeksToItsStart() {
        val state = previewState(selected = 0)
        val expectedState = previewState(selected = 2)
        val expectedStart = bounds(expectedState, standardContext).single().first

        val (actualState, effect) = reduce(
            state,
            SnippetsEvent.TapTab(2),
            standardContext,
            playing = false,
        )

        assertEquals("Expected PREVIEW state with only home zone 2 active; actual=$actualState", expectedState, actualState)
        assertEquals("Expected SeekTo($expectedStart) for the selected zone start; actual=$effect", SnippetsEffect.SeekTo(expectedStart), effect)
    }

    @Test
    fun playingTapOnDifferentTabSelectsZoneAndSeeksToItsStart() {
        val state = previewState(selected = 0)
        val expectedState = previewState(selected = 1)
        val expectedStart = bounds(expectedState, standardContext).single().first

        val (actualState, effect) = reduce(
            state,
            SnippetsEvent.TapTab(1),
            standardContext,
            playing = true,
        )

        assertEquals("While playing, expected only home zone 1 to be active; actual=$actualState", expectedState, actualState)
        assertEquals("While playing, expected SeekTo($expectedStart); actual=$effect", SnippetsEffect.SeekTo(expectedStart), effect)
    }

    @Test
    fun playingTapOnSelectedTabSeeksBackToItsStart() {
        val state = previewState(selected = 1)
        val expectedStart = bounds(state, standardContext).single().first

        val (actualState, effect) = reduce(
            state,
            SnippetsEvent.TapTab(1),
            standardContext,
            playing = true,
        )

        assertEquals("Playing tap on selected tab must preserve the selected state; actual=$actualState", state, actualState)
        assertEquals("Playing tap on selected tab must seek to its beginning; expected=$expectedStart, actual=$effect", SnippetsEffect.SeekTo(expectedStart), effect)
    }

    @Test
    fun pausedTapPlusAppliesFirstFreeZoneThatFits() {
        val state = previewState(selected = 0)
        val expectedState = state.copy(
            mode = SnippetsMode.INTERVALS,
            activeHomeZones = listOf(true, true, false),
            extraIntervalsCount = 0,
        )
        assertTrue("The first free candidate should fit for context=$standardContext", fits(expectedState, standardContext))
        val expectedStart = bounds(expectedState, standardContext).minByOrNull { it.first }!!.first

        val (actualState, effect) = reduce(
            state,
            SnippetsEvent.TapPlus,
            standardContext,
            playing = false,
        )

        assertEquals("Expected first free home zone to be added; actual=$actualState", expectedState, actualState)
        assertEquals("Expected SeekTo($expectedStart) for the first active zone; actual=$effect", SnippetsEffect.SeekTo(expectedStart), effect)
    }

    @Test
    fun pausedTapPlusSkipsFirstCandidateWhenItDoesNotFit() {
        val ctx = SnippetsContext(durationMs = 240_000L, lengthMs = 75_000L)
        val state = previewState(selected = 0)
        val firstCandidate = state.copy(
            mode = SnippetsMode.INTERVALS,
            activeHomeZones = listOf(true, true, false),
            extraIntervalsCount = 0,
        )
        val secondCandidate = state.copy(
            mode = SnippetsMode.INTERVALS,
            activeHomeZones = listOf(true, false, true),
            extraIntervalsCount = 0,
        )

        assertFalse("Expected candidate zones 0+1 not to fit; actual fits=${fits(firstCandidate, ctx)}", fits(firstCandidate, ctx))
        assertTrue("Expected candidate zones 0+2 to fit; actual fits=${fits(secondCandidate, ctx)}", fits(secondCandidate, ctx))
        val expectedStart = bounds(secondCandidate, ctx).minByOrNull { it.first }!!.first

        val (actualState, effect) = reduce(
            state,
            SnippetsEvent.TapPlus,
            ctx,
            playing = false,
        )

        assertEquals("Expected the reducer to skip candidate 1 and choose candidate 2; actual=$actualState", secondCandidate, actualState)
        assertEquals("Expected SeekTo($expectedStart) for the first active zone; actual=$effect", SnippetsEffect.SeekTo(expectedStart), effect)
    }

    @Test
    fun pausedTapPlusShowsTooltipAndPreservesStateWhenNoCandidateFits() {
        val ctx = SnippetsContext(durationMs = 20_000L, lengthMs = 5_000L)
        val state = previewState(selected = 0)
        val candidateOne = state.copy(
            mode = SnippetsMode.INTERVALS,
            activeHomeZones = listOf(true, true, false),
            extraIntervalsCount = 0,
        )
        val candidateTwo = state.copy(
            mode = SnippetsMode.INTERVALS,
            activeHomeZones = listOf(true, false, true),
            extraIntervalsCount = 0,
        )
        assertFalse("Expected candidate zones 0+1 not to fit; actual fits=${fits(candidateOne, ctx)}", fits(candidateOne, ctx))
        assertFalse("Expected candidate zones 0+2 not to fit; actual fits=${fits(candidateTwo, ctx)}", fits(candidateTwo, ctx))

        val (actualState, effect) = reduce(
            state,
            SnippetsEvent.TapPlus,
            ctx,
            playing = false,
        )

        assertEquals("When no candidate fits, expected the original state; actual=$actualState", state, actualState)
        assertEquals("When no candidate fits, expected tooltip text 'Не помещается на треке'; actual=$effect", SnippetsEffect.Tooltip("Не помещается на треке"), effect)
    }

    @Test
    fun playingTapPlusDoesNothing() {
        val state = previewState(selected = 0)

        val (actualState, effect) = reduce(
            state,
            SnippetsEvent.TapPlus,
            standardContext,
            playing = true,
        )

        assertEquals("TapPlus while playing must preserve state; actual=$actualState", state, actualState)
        assertEquals("TapPlus while playing must produce None; actual=$effect", SnippetsEffect.None, effect)
    }

    @Test
    fun unsupportedEventsReturnOriginalStateAndNone() {
        val state = previewState(selected = 1)
        val events = listOf(
            SnippetsEvent.TapExtra,
            SnippetsEvent.LongPressReturn,
            SnippetsEvent.TrackChanged,
            SnippetsEvent.ChevronBack(positionMs = 12_345L),
            SnippetsEvent.ChevronNext(positionMs = 54_321L),
        )

        events.forEach { event ->
            val (actualState, effect) = reduce(state, event, standardContext, playing = false)
            assertEquals("Unsupported event $event must preserve state; actual=$actualState", state, actualState)
            assertEquals("Unsupported event $event must produce None; actual=$effect", SnippetsEffect.None, effect)
        }
    }

    @Test
    fun tabAndPlusAreNotImplementedForIntervalsMode() {
        val state = SnippetsState(
            mode = SnippetsMode.INTERVALS,
            selectedPreviewZone = 0,
            activeHomeZones = listOf(true, true, true),
            extraIntervalsCount = 0,
        )

        listOf(SnippetsEvent.TapTab(2), SnippetsEvent.TapPlus).forEach { event ->
            val (actualState, effect) = reduce(state, event, standardContext, playing = false)
            assertEquals("INTERVALS-mode event $event must preserve state in this stage; actual=$actualState", state, actualState)
            assertEquals("INTERVALS-mode event $event must produce None in this stage; actual=$effect", SnippetsEffect.None, effect)
        }
    }

    private fun previewState(selected: Int): SnippetsState = SnippetsState(
        mode = SnippetsMode.PREVIEW,
        selectedPreviewZone = selected,
        activeHomeZones = List(3) { it == selected },
        extraIntervalsCount = 0,
    )
}
