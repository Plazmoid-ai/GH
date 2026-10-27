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
