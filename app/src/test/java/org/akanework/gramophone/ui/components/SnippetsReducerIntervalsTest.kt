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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetsReducerIntervalsTest {
    private val standardContext = SnippetsContext(
        durationMs = 240_000L,
        lengthMs = 10_000L,
    )

    @Test
    fun pausedTapTabDisablesActiveHomeAndSeeksUsingNewStateBounds() {
        val state = intervalsState(active = listOf(true, true, true))
        val expected = state.copy(activeHomeZones = listOf(true, false, true))

        val (actual, effect) = reduce(
            state,
            SnippetsEvent.TapTab(1),
            standardContext,
            playing = false,
        )

        assertEquals("Expected home zone 1 to be disabled; actual=$actual", expected, actual)
        assertSeekMatchesNewState(effect, actual, standardContext, "TapTab disabling an active home")
    }

    @Test
    fun pausedTapTabEnablesInactiveHomeWhenCandidateFits() {
        val state = intervalsState(active = listOf(true, false, false))
        val expected = state.copy(activeHomeZones = listOf(true, true, false))

        val (actual, effect) = reduce(
            state,
            SnippetsEvent.TapTab(1),
            standardContext,
            playing = false,
        )

        assertEquals("Expected home zone 1 to be enabled; actual=$actual", expected, actual)
        assertSeekMatchesNewState(effect, actual, standardContext, "TapTab enabling an inactive home")
    }

    @Test
    fun pausedTapOnOnlyActiveHomeIsBlocked() {
        val state = intervalsState(active = listOf(false, true, false))

        val (actual, effect) = reduce(
            state,
            SnippetsEvent.TapTab(1),
            standardContext,
            playing = false,
        )

        assertEquals("The last active home zone must remain active; actual=$actual", state, actual)
        assertEquals(
            "Expected the last-active-zone tooltip; actual=$effect",
            SnippetsEffect.Tooltip("Нужен хотя бы один активный интервал"),
            effect,
        )
    }

    @Test
    fun pausedTapOnInactiveHomeIsRejectedWhenCandidateDoesNotFit() {
        val ctx = SnippetsContext(durationMs = 25_000L, lengthMs = 10_000L)
        val state = intervalsState(active = listOf(true, false, false))
        val candidate = state.copy(activeHomeZones = listOf(true, true, false))
        assertFalse(
            "Expected candidate with home zones 0+1 not to fit; actual fits=${fits(candidate, ctx)}",
            fits(candidate, ctx),
        )

        val (actual, effect) = reduce(
            state,
            SnippetsEvent.TapTab(1),
            ctx,
            playing = false,
        )

        assertEquals("A rejected activation must preserve the original state; actual=$actual", state, actual)
        assertEquals(
            "Expected the no-fit tooltip and no SeekTo; actual=$effect",
            SnippetsEffect.Tooltip("Не помещается на треке"),
            effect,
        )
    }

    @Test
    fun pausedTapPlusSkipsFreeHomeThatDoesNotFitAndUsesNextFittingHome() {
        val ctx = SnippetsContext(durationMs = 240_000L, lengthMs = 75_000L)
        val state = intervalsState(active = listOf(true, false, false))
        val firstCandidate = state.copy(activeHomeZones = listOf(true, true, false))
        val secondCandidate = state.copy(activeHomeZones = listOf(true, false, true))
        assertFalse(
            "Expected enabling home zone 1 not to fit; actual fits=${fits(firstCandidate, ctx)}",
            fits(firstCandidate, ctx),
        )
        assertTrue(
            "Expected enabling home zone 2 to fit; actual fits=${fits(secondCandidate, ctx)}",
            fits(secondCandidate, ctx),
        )

        val (actual, effect) = reduce(
            state,
            SnippetsEvent.TapPlus,
            ctx,
            playing = false,
        )

        assertEquals("TapPlus must skip the non-fitting lower-index home and enable zone 2; actual=$actual", secondCandidate, actual)
        assertSeekMatchesNewState(effect, actual, ctx, "TapPlus choosing the next fitting home")
    }

    @Test
    fun pausedTapPlusShowsTooltipWhenNoExtraIntervalCanFit() {
        val ctx = SnippetsContext(durationMs = 65_000L, lengthMs = 10_000L)
        val state = intervalsState(active = listOf(true, true, true))
        val candidate = state.copy(extraIntervalsCount = 1)
        assertTrue(
            "Expected the three-home base to fit; actual fits=${fits(state, ctx)}",
            fits(state, ctx),
        )
        assertFalse(
            "Expected adding a fourth interval to fail; actual fits=${fits(candidate, ctx)}",
            fits(candidate, ctx),
        )

        val (actual, effect) = reduce(
            state,
            SnippetsEvent.TapPlus,
            ctx,
            playing = false,
        )

        assertEquals("Rejected TapPlus must preserve state; actual=$actual", state, actual)
        assertEquals(
            "Expected no-fit tooltip and no SeekTo; actual=$effect",
            SnippetsEffect.Tooltip("Не помещается на треке"),
            effect,
        )
    }

    @Test
    fun plusAndIntervalEventsFollowPreviewToFiveZonesAndBackToThreeHomes() {
        val ctx = SnippetsContext(durationMs = 100_000L, lengthMs = 10_000L)
        var state = SnippetsState(
            mode = SnippetsMode.PREVIEW,
            selectedPreviewZone = 0,
            activeHomeZones = listOf(true, false, false),
            extraIntervalsCount = 0,
        )

        // First plus: PREVIEW -> INTERVALS with two active home zones.
        var result = reduce(state, SnippetsEvent.TapPlus, ctx, playing = false)
        state = result.first
        assertEquals(
            "First plus must enter INTERVALS with home zones 0 and 1 active; actual=$state",
            intervalsState(active = listOf(true, true, false)),
            state,
        )
        assertSeekMatchesNewState(result.second, state, ctx, "First plus from PREVIEW")

        // Second plus activates the third home zone.
        result = reduce(state, SnippetsEvent.TapPlus, ctx, playing = false)
        state = result.first
        assertEquals(
            "Second plus must activate all three home zones with N=0; actual=$state",
            intervalsState(active = listOf(true, true, true)),
            state,
        )
        assertSeekMatchesNewState(result.second, state, ctx, "Second plus")

        // Third plus creates four total zones (three home + N=1).
        result = reduce(state, SnippetsEvent.TapPlus, ctx, playing = false)
        state = result.first
        assertEquals("Third plus must add the first extra interval; actual=$state", 1, state.extraIntervalsCount)
        assertEquals("All home zones must remain active; actual=$state", listOf(true, true, true), state.activeHomeZones)
        assertSeekMatchesNewState(result.second, state, ctx, "Third plus to four total zones")

        // Another plus creates five total zones (N=2).
        result = reduce(state, SnippetsEvent.TapPlus, ctx, playing = false)
        state = result.first
        assertEquals("Fourth plus must increase extra count to N=2; actual=$state", 2, state.extraIntervalsCount)
        assertSeekMatchesNewState(result.second, state, ctx, "Plus to five total zones")

        // Tapping any valid home tab removes one extra and leaves all homes active.
        result = reduce(state, SnippetsEvent.TapTab(2), ctx, playing = false)
        state = result.first
        assertEquals("TapTab with N>0 must decrease N to 1; actual=$state", 1, state.extraIntervalsCount)
        assertEquals("TapTab with N>0 must keep all home zones active; actual=$state", listOf(true, true, true), state.activeHomeZones)
        assertSeekMatchesNewState(result.second, state, ctx, "TapTab removing one extra")

        // TapExtra removes the last extra, but must not return to PREVIEW.
        result = reduce(state, SnippetsEvent.TapExtra, ctx, playing = false)
        state = result.first
        assertEquals("TapExtra must reduce N to zero; actual=$state", 0, state.extraIntervalsCount)
        assertEquals("Removing the last extra must retain INTERVALS mode; actual=$state", SnippetsMode.INTERVALS, state.mode)
        assertEquals("Removing the last extra must leave all three homes active; actual=$state", listOf(true, true, true), state.activeHomeZones)
        assertSeekMatchesNewState(result.second, state, ctx, "TapExtra removing the last extra")
    }

    @Test
    fun playingInIntervalsIgnoresTabPlusExtraAndReturn() {
        val state = intervalsState(active = listOf(true, true, true), extras = 1)
        val events = listOf(
            SnippetsEvent.TapTab(1),
            SnippetsEvent.TapPlus,
            SnippetsEvent.TapExtra,
            SnippetsEvent.LongPressReturn,
        )

        events.forEach { event ->
            val (actual, effect) = reduce(state, event, standardContext, playing = true)
            assertEquals("While playing, $event must preserve state; actual=$actual", state, actual)
            assertEquals("While playing, $event must produce None; actual=$effect", SnippetsEffect.None, effect)
        }
    }

    @Test
    fun tapPlusWithExtraIntervalsShowsTooltipWhenIncreasingNDoesNotFit() {
        val ctx = SnippetsContext(durationMs = 70_000L, lengthMs = 10_000L)
        val state = intervalsState(active = listOf(true, true, true), extras = 1)
        val nextCountCandidate = state.copy(extraIntervalsCount = 2)
        assertTrue(
            "Expected the current four-zone state to fit; actual fits=${fits(state, ctx)}",
            fits(state, ctx),
        )
        assertFalse(
            "Expected adding a fifth zone not to fit; actual fits=${fits(nextCountCandidate, ctx)}",
            fits(nextCountCandidate, ctx),
        )

        val (actual, effect) = reduce(state, SnippetsEvent.TapPlus, ctx, playing = false)

        assertEquals("No-fit plus must preserve the state with N=1; actual=$actual", state, actual)
        assertEquals("Expected no-fit tooltip; actual=$effect", SnippetsEffect.Tooltip("Не помещается на треке"), effect)
    }

    @Test
    fun longPressReturnRestoresSelectedPreviewZoneAndOrdersEffects() {
        for (selected in 0..2) {
            val state = intervalsState(
                selected = selected,
                active = listOf(true, true, true),
                extras = 2,
            )
            val expected = SnippetsState(
                mode = SnippetsMode.PREVIEW,
                selectedPreviewZone = selected,
                activeHomeZones = List(3) { it == selected },
                extraIntervalsCount = 0,
            )
            val expectedStart = bounds(expected, standardContext).minByOrNull { it.first }!!.first

            val (actual, effect) = reduce(
                state,
                SnippetsEvent.LongPressReturn,
                standardContext,
                playing = false,
            )

            assertEquals("Return must restore selected preview zone $selected; actual=$actual", expected, actual)
            assertEquals(
                "Effects must be ordered SeekTo then Tooltip for selected zone $selected; actual=$effect",
                SnippetsEffect.Multiple(
                    listOf(
                        SnippetsEffect.SeekTo(expectedStart),
                        SnippetsEffect.Tooltip(
                            "Превью: ${listOf("начало", "середина", "конец")[selected]}",
                        ),
                    ),
                ),
                effect,
            )
        }
    }

    @Test
    fun longPressReturnDoesNothingInPreview() {
        val state = SnippetsState(
            mode = SnippetsMode.PREVIEW,
            selectedPreviewZone = 2,
            activeHomeZones = listOf(false, false, true),
            extraIntervalsCount = 0,
        )

        val (actual, effect) = reduce(state, SnippetsEvent.LongPressReturn, standardContext, playing = false)

        assertEquals("LongPressReturn in PREVIEW must preserve state; actual=$actual", state, actual)
        assertEquals("LongPressReturn in PREVIEW must produce None; actual=$effect", SnippetsEffect.None, effect)
    }

    @Test
    fun trimToFitRemovesExtrasBeforeTouchingHomeZones() {
        val ctx = SnippetsContext(durationMs = 65_000L, lengthMs = 10_000L)
        val state = intervalsState(active = listOf(true, true, true), extras = 2)

        val result = trimToFit(state, ctx)

        assertNotNull("Expected trimming to find a fitting three-home state", result)
        val trimmed = result ?: throw AssertionError("Expected a non-null trim result")
        assertEquals("Expected both extra intervals to be removed first; actual=$trimmed", 2, trimmed.removedCount)
        assertEquals("Expected N=0 after trimming; actual=$trimmed", 0, trimmed.state.extraIntervalsCount)
        assertEquals("Home zones should not be removed while extras can be removed; actual=$trimmed", listOf(true, true, true), trimmed.state.activeHomeZones)
        assertTrue("Trim result must fit context=$ctx; state=${trimmed.state}", fits(trimmed.state, ctx))
    }

    @Test
    fun trimToFitRemovesHomesFromRightToLeftAndKeepsOneActive() {
        val ctx = SnippetsContext(durationMs = 39_000L, lengthMs = 10_000L)
        val state = intervalsState(active = listOf(true, true, true))

        val result = trimToFit(state, ctx)

        assertNotNull("Expected one home zone to fit; result=$result", result)
        val trimmed = result ?: throw AssertionError("Expected a non-null trim result")
        assertEquals("Expected two removed zones; actual=$trimmed", 2, trimmed.removedCount)
        assertEquals(
            "Right-to-left trimming must leave home zone 0 active; actual=$trimmed",
            listOf(true, false, false),
            trimmed.state.activeHomeZones,
        )
        assertEquals("Trimming must retain INTERVALS mode; actual=$trimmed", SnippetsMode.INTERVALS, trimmed.state.mode)
        assertTrue("One active home zone must fit context=$ctx; state=${trimmed.state}", fits(trimmed.state, ctx))
    }

    @Test
    fun trimToFitReturnsNullWhenEvenOneIntervalCannotFit() {
        val ctx = SnippetsContext(durationMs = 9_999L, lengthMs = 10_000L)
        val state = intervalsState(active = listOf(true, false, false))

        val result = trimToFit(state, ctx)

        assertNull("Expected null when the only remaining home zone cannot fit; actual=$result", result)
    }

    @Test
    fun previewIsNotTrimmedAndReturnsNullOnlyWhenLengthExceedsTrack() {
        val state = SnippetsState(
            mode = SnippetsMode.PREVIEW,
            selectedPreviewZone = 1,
            activeHomeZones = listOf(false, true, false),
            extraIntervalsCount = 0,
        )

        val fittingResult = trimToFit(state, standardContext)
        assertNotNull("A PREVIEW zone should not be trimmed on a long-enough track", fittingResult)
        assertEquals("A fitting PREVIEW must remain unchanged", TrimResult(state, 0), fittingResult)

        val tooShort = trimToFit(
            state,
            SnippetsContext(durationMs = 9_999L, lengthMs = 10_000L),
        )
        assertNull("PREVIEW must return null when configured length exceeds track duration; actual=$tooShort", tooShort)
    }

    private fun intervalsState(
        selected: Int = 0,
        active: List<Boolean>,
        extras: Int = 0,
    ): SnippetsState = SnippetsState(
        mode = SnippetsMode.INTERVALS,
        selectedPreviewZone = selected,
        activeHomeZones = active,
        extraIntervalsCount = extras,
    )

    private fun assertSeekMatchesNewState(
        effect: SnippetsEffect,
        newState: SnippetsState,
        ctx: SnippetsContext,
        operation: String,
    ) {
        val expectedStart = bounds(newState, ctx).minByOrNull { it.first }?.first
        assertNotNull("$operation should have bounds for the new state; state=$newState, bounds=${bounds(newState, ctx)}", expectedStart)
        assertEquals(
            "$operation must SeekTo the first zone start from bounds(newState); expected=$expectedStart, actual=$effect",
            SnippetsEffect.SeekTo(expectedStart!!),
            effect,
        )
    }
}
