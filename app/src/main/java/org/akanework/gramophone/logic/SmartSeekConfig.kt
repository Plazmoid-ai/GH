/*
 *     Copyright (C) 2026 Plazmoid-ai
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.logic

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.random.Random

/**
 * Persistent user settings for Smart Seek.
 *
 * In percentage mode, both values are percentages of the current track duration.
 * Random mode varies each play and skip interval independently by about +/-25%.
 *
 * Known behavior: pausing resets the current interval. Playback resumes with a fresh interval.
 */
data class SmartSeekConfig(
    val playValue: Int = DEFAULT_PLAY_VALUE,
    val skipValue: Int = DEFAULT_SKIP_VALUE,
    val usePercent: Boolean = false,
    val pattern: SmartSeekPattern = SmartSeekPattern.STRICT
) {
    fun resolveCycle(trackDurationMs: Long): SmartSeekCycle {
        val safeDuration = trackDurationMs.coerceAtLeast(1L)
        val playBase = if (usePercent) {
            safeDuration * playValue.coerceIn(1, 99) / 100L
        } else {
            playValue.coerceIn(1, MAX_SECONDS).toLong() * 1_000L
        }
        val skipBase = if (usePercent) {
            safeDuration * skipValue.coerceIn(1, 99) / 100L
        } else {
            skipValue.coerceIn(1, MAX_SECONDS).toLong() * 1_000L
        }

        return SmartSeekCycle(
            playDurationMs = vary(playBase).coerceAtLeast(MIN_INTERVAL_MS),
            skipDurationMs = vary(skipBase).coerceAtLeast(MIN_INTERVAL_MS)
        )
    }

    private fun vary(value: Long): Long {
        if (pattern == SmartSeekPattern.STRICT) return value
        return (value * Random.nextDouble(RANDOM_MIN_FACTOR, RANDOM_MAX_FACTOR)).toLong()
    }

    fun save(preferences: SharedPreferences) {
        preferences.edit()
            .putInt(PREF_PLAY_VALUE, playValue.coerceIn(1, MAX_SECONDS))
            .putInt(PREF_SKIP_VALUE, skipValue.coerceIn(1, MAX_SECONDS))
            .putBoolean(PREF_USE_PERCENT, usePercent)
            .putString(PREF_PATTERN, pattern.name)
            .apply()
    }

    companion object {
        const val PREF_PLAY_VALUE = "smart_seek_play_value"
        const val PREF_SKIP_VALUE = "smart_seek_skip_value"
        const val PREF_USE_PERCENT = "smart_seek_use_percent"
        const val PREF_PATTERN = "smart_seek_pattern"

        const val DEFAULT_PLAY_VALUE = 10
        const val DEFAULT_SKIP_VALUE = 15
        const val MAX_SECONDS = 600
        const val MIN_INTERVAL_MS = 1_000L

        private const val RANDOM_MIN_FACTOR = 0.75
        private const val RANDOM_MAX_FACTOR = 1.25001

        fun load(preferences: SharedPreferences): SmartSeekConfig {
            val patternName = preferences.getString(PREF_PATTERN, SmartSeekPattern.STRICT.name)
            val pattern = runCatching {
                SmartSeekPattern.valueOf(patternName ?: SmartSeekPattern.STRICT.name)
            }.getOrDefault(SmartSeekPattern.STRICT)
            val usePercent = preferences.getBoolean(PREF_USE_PERCENT, false)
            val maxValue = if (usePercent) 99 else MAX_SECONDS
            return SmartSeekConfig(
                playValue = preferences.getInt(PREF_PLAY_VALUE, DEFAULT_PLAY_VALUE).coerceIn(1, maxValue),
                skipValue = preferences.getInt(PREF_SKIP_VALUE, DEFAULT_SKIP_VALUE).coerceIn(1, maxValue),
                usePercent = usePercent,
                pattern = pattern
            )
        }
    }
}

data class SmartSeekCycle(
    val playDurationMs: Long,
    val skipDurationMs: Long
)

enum class SmartSeekPattern {
    STRICT,
    RANDOM
}

enum class SmartSeekMode {
    MANUAL
}
