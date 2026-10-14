/*
 *     Copyright (C) 2026 Plazmoid-ai
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.logic

/**
 * Configuration for the initial manual Smart Seek implementation.
 *
 * Known MVP limitation: pausing cancels the current delay. When playback resumes,
 * Smart Seek starts a fresh [playDurationMs] interval instead of continuing the old timer.
 */
data class SmartSeekConfig(
    val playDurationMs: Long = 10_000L,
    val skipDurationMs: Long = 15_000L
)

enum class SmartSeekMode {
    MANUAL
}
