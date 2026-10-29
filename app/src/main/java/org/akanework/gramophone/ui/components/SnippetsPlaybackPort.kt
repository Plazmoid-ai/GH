/*
 *     Copyright (C) 2026 OpenAI
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import kotlinx.coroutines.flow.StateFlow

/**
 * Minimal playback contract used by the isolated Snippets UI/model layer.
 * Implementations decide how playback commands reach the actual player.
 */
interface SnippetsPlaybackPort {
    val durationMs: StateFlow<Long>
    val playWhenReady: StateFlow<Boolean>

    fun positionMs(): Long
    fun seekTo(ms: Long)
    fun play()
    fun pause()
}
