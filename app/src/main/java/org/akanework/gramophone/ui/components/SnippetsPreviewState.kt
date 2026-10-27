/*
 *     Copyright (C) 2026 OpenAI
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager

/**
 * UI-only state for Snippets Preview. It intentionally does not communicate with the player.
 */
object SnippetsPreviewState {
    const val PREFERENCE_KEY = "snippets_preview"
    const val DUMMY_TRACK_DURATION_MS = 180_000L

    var isSnippetsEnabled by mutableStateOf(false)
    var isSnippetsActive by mutableStateOf(false)
    var showSnippetsMenu by mutableStateOf(false)
    var previewDurationSec by mutableStateOf(30)
    var currentPositionMs by mutableStateOf(0L)
    var isPlaying by mutableStateOf(false)
    var selectedAccentName by mutableStateOf("По умолчанию")
    val selectedAccentColor: Color
        get() = when (selectedAccentName) {
            "Прозрачный" -> Color.Transparent
            "Черный" -> Color(0xFF000000)
            "Красный" -> Color(0xFFE53935)
            "Оранжевый" -> Color(0xFFFF9800)
            "Голубой" -> Color(0xFF29B6F6)
            "Зеленый" -> Color(0xFF43A047)
            "Розовый" -> Color(0xFFEC407A)
            "Пурпурный" -> Color(0xFFAB47BC)
            "Синий" -> Color(0xFF1E88E5)
            else -> Color(0xFF9B8AF5)
        }
    val activeZones = mutableStateListOf(true, false, false)

    private var observedPreferences: SharedPreferences? = null

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == PREFERENCE_KEY) {
                isSnippetsEnabled =
                    sharedPreferences?.getBoolean(PREFERENCE_KEY, false) == true
                if (!isSnippetsEnabled) {
                    isSnippetsActive = false
                    showSnippetsMenu = false
                }
            }
        }

    fun initialize(context: Context) {
        val preferences =
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        if (observedPreferences !== preferences) {
            observedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            observedPreferences = preferences
            preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        }
        isSnippetsEnabled = preferences.getBoolean(PREFERENCE_KEY, false)
        if (!isSnippetsEnabled) {
            isSnippetsActive = false
            showSnippetsMenu = false
        }
    }

    fun updateDuration(seconds: Int) {
        previewDurationSec = seconds.coerceIn(
            1,
            (DUMMY_TRACK_DURATION_MS / 1_000L).toInt(),
        )
    }

    fun simulatedTrackDurationMs(): Long = previewDurationSec * 3_000L

    fun advanceSimulatedPosition(deltaMs: Long) {
        if (!isPlaying || deltaMs <= 0L) return
        val duration = simulatedTrackDurationMs().coerceAtLeast(1L)
        currentPositionMs = (currentPositionMs + deltaMs) % duration
    }

    fun activeElapsedMs(): Long {
        val zoneDurationMs = previewDurationSec * 1_000L
        if (zoneDurationMs <= 0L) return 0L
        val zoneIndex = (currentPositionMs / zoneDurationMs)
            .toInt()
            .coerceIn(activeZones.indices)
        val withinZoneMs = currentPositionMs % zoneDurationMs
        val completedActiveZones = activeZones.indices
            .take(zoneIndex)
            .count { activeZones[it] }
        val elapsed = completedActiveZones * zoneDurationMs +
            if (activeZones[zoneIndex]) withinZoneMs else 0L
        val totalActiveDuration = activeZones.count { it } * zoneDurationMs
        return elapsed.coerceIn(0L, totalActiveDuration)
    }

    fun toggleZone(index: Int) {
        if (index !in activeZones.indices) return
        if (activeZones[index]) {
            // Always keep at least one preview zone enabled.
            if (activeZones.count { it } > 1) activeZones[index] = false
        } else {
            activeZones[index] = true
        }
    }
}
