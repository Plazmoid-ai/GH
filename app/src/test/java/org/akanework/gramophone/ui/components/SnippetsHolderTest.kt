/*
 *     Copyright (C) 2026 OpenAI
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetsHolderTest {
    @Test
    fun dispatchUpdatesStateAndRoutesSeekEffectThroughPort() {
        val port = TestPlaybackPort(duration = 180_000L)
        val engine = StubSnippetsEngine()
        val holder = SnippetsHolder(port, engine) { 10_000L }

        holder.dispatch(SnippetsEvent.TapTab(2))

        assertEquals(2, holder.state.value.selectedPreviewZone)
        assertEquals(listOf(false, false, true), holder.state.value.activeHomeZones)
        assertEquals(listOf("seek:145000"), port.actions)
        assertEquals(145_000L, port.positionMs())
    }

    @Test
    fun multipleEffectsExecuteSeekBeforeTooltip() = runBlocking {
        val actions = mutableListOf<String>()
        val port = TestPlaybackPort(duration = 180_000L, actions = actions)
        val holder = SnippetsHolder(port, StubSnippetsEngine()) { 10_000L }

        holder.dispatch(SnippetsEvent.TapPlus)
        holder.dispatch(SnippetsEvent.TapPlus)
        holder.dispatch(SnippetsEvent.TapPlus)
        assertEquals(1, holder.state.value.extraIntervalsCount)
        actions.clear()

        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            holder.tooltips.collect { actions.add("tooltip:" + it) }
        }
        holder.dispatch(SnippetsEvent.LongPressReturn)

        assertEquals(listOf("seek:25000", "tooltip:Превью: начало"), actions)
        assertEquals(SnippetsMode.PREVIEW, holder.state.value.mode)
        assertEquals(0, holder.state.value.extraIntervalsCount)
        collector.cancel()
    }

    @Test
    fun playingStateIsReadFromPortBeforeDispatch() {
        val port = TestPlaybackPort(duration = 180_000L)
        val holder = SnippetsHolder(port, StubSnippetsEngine()) { 10_000L }
        port.play()

        holder.dispatch(SnippetsEvent.TapPlus)

        assertEquals(SnippetsMode.PREVIEW, holder.state.value.mode)
        assertEquals(listOf(true, false, false), holder.state.value.activeHomeZones)
        assertEquals(listOf("play"), port.actions)
    }

    private class TestPlaybackPort(
        duration: Long,
        val actions: MutableList<String> = mutableListOf(),
    ) : SnippetsPlaybackPort {
        private val mutableDuration = MutableStateFlow(duration)
        private val mutablePlayWhenReady = MutableStateFlow(false)

        override val durationMs: StateFlow<Long> = mutableDuration
        override val playWhenReady: StateFlow<Boolean> = mutablePlayWhenReady

        private var position = 0L

        override fun positionMs(): Long = position

        override fun seekTo(ms: Long) {
            position = ms
            actions.add("seek:" + ms)
        }

        override fun play() {
            mutablePlayWhenReady.value = true
            actions.add("play")
        }

        override fun pause() {
            mutablePlayWhenReady.value = false
            actions.add("pause")
        }
    }
}
