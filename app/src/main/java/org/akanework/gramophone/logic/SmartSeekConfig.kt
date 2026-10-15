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
    val pattern: SmartSeekPattern = SmartSeekPattern.STRICT,
    val inputMode: SmartSeekInputMode = SmartSeekInputMode.CYCLE,
    val cycleDurationMs: Long = DEFAULT_CYCLE_DURATION_MS,
    val soundRatioPercent: Int = DEFAULT_SOUND_RATIO_PERCENT,
    val absolutePlaySeconds: Int = DEFAULT_ABSOLUTE_PLAY_SECONDS,
    val absoluteSkipSeconds: Int = DEFAULT_ABSOLUTE_SKIP_SECONDS,
    val playPercent: Int = DEFAULT_PLAY_PERCENT,
    val skipPercent: Int = DEFAULT_SKIP_PERCENT
) {
    fun resolveCycle(trackDurationMs: Long): SmartSeekCycle {
        if (inputMode == SmartSeekInputMode.CYCLE) {
            val cycleDuration = cycleDurationMs.coerceIn(MIN_INTERVAL_MS, MAX_CYCLE_DURATION_MS)
            val playDuration = cycleDuration * soundRatioPercent.coerceIn(1, 99) / 100L
            return SmartSeekCycle(
                playDurationMs = playDuration.coerceAtLeast(1L),
                skipDurationMs = (cycleDuration - playDuration).coerceAtLeast(1L)
            )
        }
        val safeDuration = trackDurationMs.coerceAtLeast(1L)
        val playBase = when (inputMode) {
            SmartSeekInputMode.ABSOLUTE -> absolutePlaySeconds.coerceIn(1, MAX_SECONDS).toLong() * 1_000L
            SmartSeekInputMode.PERCENT -> safeDuration * playPercent.coerceIn(1, 99) / 100L
            SmartSeekInputMode.CYCLE -> error("Cycle mode was handled above")
        }
        val skipBase = when (inputMode) {
            SmartSeekInputMode.ABSOLUTE -> absoluteSkipSeconds.coerceIn(1, MAX_SECONDS).toLong() * 1_000L
            SmartSeekInputMode.PERCENT -> safeDuration * skipPercent.coerceIn(1, 99) / 100L
            SmartSeekInputMode.CYCLE -> error("Cycle mode was handled above")
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
            .putInt(PREF_PLAY_VALUE, playValue.coerceIn(1, if (inputMode == SmartSeekInputMode.PERCENT) 99 else MAX_SECONDS))
            .putInt(PREF_SKIP_VALUE, skipValue.coerceIn(1, if (inputMode == SmartSeekInputMode.PERCENT) 99 else MAX_SECONDS))
            .putBoolean(PREF_USE_PERCENT, inputMode == SmartSeekInputMode.PERCENT)
            .putString(PREF_PATTERN, pattern.name)
            .putString(PREF_INPUT_MODE, inputMode.name)
            .putLong(PREF_CYCLE_DURATION_MS, cycleDurationMs.coerceIn(MIN_INTERVAL_MS, MAX_CYCLE_DURATION_MS))
            .putInt(PREF_SOUND_RATIO_PERCENT, soundRatioPercent.coerceIn(1, 99))
            .putInt(PREF_ABSOLUTE_PLAY_SECONDS, absolutePlaySeconds.coerceIn(1, MAX_SECONDS))
            .putInt(PREF_ABSOLUTE_SKIP_SECONDS, absoluteSkipSeconds.coerceIn(1, MAX_SECONDS))
            .putInt(PREF_PLAY_PERCENT, playPercent.coerceIn(1, 99))
            .putInt(PREF_SKIP_PERCENT, skipPercent.coerceIn(1, 99))
            .apply()
    }

    companion object {
        const val PREF_PLAY_VALUE = "smart_seek_play_value"
        const val PREF_SKIP_VALUE = "smart_seek_skip_value"
        const val PREF_USE_PERCENT = "smart_seek_use_percent"
        const val PREF_PATTERN = "smart_seek_pattern"
        const val PREF_INPUT_MODE = "smart_seek_input_mode"
        const val PREF_CYCLE_DURATION_MS = "smart_seek_cycle_duration_ms"
        const val PREF_SOUND_RATIO_PERCENT = "smart_seek_sound_ratio_percent"
        const val PREF_ABSOLUTE_PLAY_SECONDS = "smart_seek_absolute_play_seconds"
        const val PREF_ABSOLUTE_SKIP_SECONDS = "smart_seek_absolute_skip_seconds"
        const val PREF_PLAY_PERCENT = "smart_seek_play_percent"
        const val PREF_SKIP_PERCENT = "smart_seek_skip_percent"

        const val DEFAULT_ABSOLUTE_PLAY_SECONDS = 20
        const val DEFAULT_ABSOLUTE_SKIP_SECONDS = 10
        const val DEFAULT_PLAY_PERCENT = 60
        const val DEFAULT_SKIP_PERCENT = 40
        const val DEFAULT_CYCLE_DURATION_MS = 30_000L
        const val DEFAULT_SOUND_RATIO_PERCENT = 60
        const val DEFAULT_CYCLE_PLAY_DURATION_MS = DEFAULT_CYCLE_DURATION_MS * DEFAULT_SOUND_RATIO_PERCENT / 100
        const val DEFAULT_CYCLE_SKIP_DURATION_MS = DEFAULT_CYCLE_DURATION_MS - DEFAULT_CYCLE_PLAY_DURATION_MS
        const val MAX_CYCLE_DURATION_MS = 600_000L

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
            val inputModeName = preferences.getString(PREF_INPUT_MODE, SmartSeekInputMode.CYCLE.name)
            val inputMode = runCatching {
                SmartSeekInputMode.valueOf(inputModeName ?: SmartSeekInputMode.CYCLE.name)
            }.getOrDefault(SmartSeekInputMode.CYCLE)
            val cycleDurationMs = preferences.getLong(PREF_CYCLE_DURATION_MS, DEFAULT_CYCLE_DURATION_MS)
                .coerceIn(MIN_INTERVAL_MS, MAX_CYCLE_DURATION_MS)
            val soundRatioPercent = preferences.getInt(PREF_SOUND_RATIO_PERCENT, DEFAULT_SOUND_RATIO_PERCENT)
                .coerceIn(1, 99)
            val absolutePlaySeconds = preferences.getInt(PREF_ABSOLUTE_PLAY_SECONDS, DEFAULT_ABSOLUTE_PLAY_SECONDS)
                .coerceIn(1, MAX_SECONDS)
            val absoluteSkipSeconds = preferences.getInt(PREF_ABSOLUTE_SKIP_SECONDS, DEFAULT_ABSOLUTE_SKIP_SECONDS)
                .coerceIn(1, MAX_SECONDS)
            val playPercent = preferences.getInt(PREF_PLAY_PERCENT, DEFAULT_PLAY_PERCENT).coerceIn(1, 99)
            val skipPercent = preferences.getInt(PREF_SKIP_PERCENT, DEFAULT_SKIP_PERCENT).coerceIn(1, 99)
            val cyclePlaySeconds = (cycleDurationMs * soundRatioPercent / 100L / 1_000L).toInt().coerceAtLeast(1)
            val cycleSkipSeconds = ((cycleDurationMs - cycleDurationMs * soundRatioPercent / 100L) / 1_000L).toInt().coerceAtLeast(1)
            val playValue = when (inputMode) {
                SmartSeekInputMode.ABSOLUTE -> absolutePlaySeconds
                SmartSeekInputMode.PERCENT -> playPercent
                SmartSeekInputMode.CYCLE -> cyclePlaySeconds
            }
            val skipValue = when (inputMode) {
                SmartSeekInputMode.ABSOLUTE -> absoluteSkipSeconds
                SmartSeekInputMode.PERCENT -> skipPercent
                SmartSeekInputMode.CYCLE -> cycleSkipSeconds
            }
            return SmartSeekConfig(
                playValue = playValue,
                skipValue = skipValue,
                usePercent = inputMode == SmartSeekInputMode.PERCENT,
                pattern = pattern,
                inputMode = inputMode,
                cycleDurationMs = cycleDurationMs,
                soundRatioPercent = soundRatioPercent,
                absolutePlaySeconds = absolutePlaySeconds,
                absoluteSkipSeconds = absoluteSkipSeconds,
                playPercent = playPercent,
                skipPercent = skipPercent
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

enum class SmartSeekInputMode {
    ABSOLUTE,
    PERCENT,
    CYCLE
}

enum class SmartSeekMode {
    MANUAL
}
