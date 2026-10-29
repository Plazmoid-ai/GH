/*
 *     Copyright (C) 2026 OpenAI
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the current Snippets state and translates reducer effects through a port.
 * It has no Android or Media3 dependencies.
 */
class SnippetsHolder(
    private val port: SnippetsPlaybackPort,
    private val engine: SnippetsEngine,
    private val lengthMs: () -> Long,
) {
    private val mutableState = MutableStateFlow(SnippetsState().normalized())
    val state: StateFlow<SnippetsState> = mutableState.asStateFlow()

    private val mutableTooltips = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val tooltips: SharedFlow<String> = mutableTooltips.asSharedFlow()

    fun dispatch(event: SnippetsEvent) {
        val context = SnippetsContext(
            durationMs = port.durationMs.value,
            lengthMs = lengthMs(),
            engine = engine,
        )
        val (nextState, effect) = reduce(
            state = mutableState.value,
            event = event,
            ctx = context,
            playing = port.playWhenReady.value,
        )
        mutableState.value = nextState
        execute(effect)
    }

    private fun execute(effect: SnippetsEffect) {
        when (effect) {
            is SnippetsEffect.SeekTo -> port.seekTo(effect.ms)
            is SnippetsEffect.Tooltip -> mutableTooltips.tryEmit(effect.text)
            is SnippetsEffect.Multiple -> effect.effects.forEach(::execute)
            SnippetsEffect.None -> Unit
        }
    }
}
