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
    var showSnippetsMenu by mutableStateOf(false)
    var previewDurationSec by mutableStateOf(30)
    var snippetStartMs by mutableStateOf(75_000L)
    var snippetEndMs by mutableStateOf(105_000L)

    private var observedPreferences: SharedPreferences? = null

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == PREFERENCE_KEY) {
                isSnippetsEnabled =
                    sharedPreferences?.getBoolean(PREFERENCE_KEY, false) == true
                if (!isSnippetsEnabled) {
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
            showSnippetsMenu = false
        }
    }

    fun updateDuration(seconds: Int) {
        val safeSeconds = seconds.coerceIn(1, (DUMMY_TRACK_DURATION_MS / 1_000L).toInt())
        previewDurationSec = safeSeconds
        val lengthMs = safeSeconds * 1_000L
        snippetStartMs = ((DUMMY_TRACK_DURATION_MS / 2L) - lengthMs / 2L)
            .coerceIn(0L, DUMMY_TRACK_DURATION_MS - lengthMs)
        snippetEndMs = snippetStartMs + lengthMs
    }

    fun moveSnippetTo(centerMs: Long) {
        val lengthMs = previewDurationSec * 1_000L
        snippetStartMs = (centerMs - lengthMs / 2L)
            .coerceIn(0L, DUMMY_TRACK_DURATION_MS - lengthMs)
        snippetEndMs = snippetStartMs + lengthMs
    }

    fun moveSnippetStart(startMs: Long) {
        val lengthMs = previewDurationSec * 1_000L
        snippetStartMs = startMs.coerceIn(0L, DUMMY_TRACK_DURATION_MS - lengthMs)
        snippetEndMs = snippetStartMs + lengthMs
    }
}
