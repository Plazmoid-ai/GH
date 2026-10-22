/*
 *     Copyright (C) 2026 Plazmoid-ai
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3
 *     of the License, or (at your option) any later version.
 */

package org.akanework.gramophone.logic

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persistent user settings for IntervalPlay.
 * The screen scaffolding currently stores the selected tab and experimental buffer option.
 */
data class SmartSeekConfig(
    val mode: String = MODE_FIXED,
    val boostBuffer: Boolean = false,
    val wheelSwipeEnabled: Boolean = true
) {
    fun save(
        preferences: SharedPreferences,
        additionalChanges: SharedPreferences.Editor.() -> Unit = {}
    ) {
        preferences.edit {
            putString(PREF_MODE, mode.takeIf { it == MODE_SIMPLE } ?: MODE_FIXED)
            putBoolean(PREF_BOOST_BUFFER, boostBuffer)
            putBoolean(PREF_WHEEL_SWIPE_ENABLED, wheelSwipeEnabled)
            additionalChanges()
            putLong(PREF_CONFIG_REVISION, System.nanoTime())
        }
    }

    companion object {
        const val MODE_SIMPLE = "simple"
        const val MODE_FIXED = "fixed"

        const val PREF_MODE = "smart_seek_mode"
        const val PREF_BOOST_BUFFER = "smart_seek_boost_buffer"
        const val PREF_WHEEL_SWIPE_ENABLED = "smart_seek_wheel_swipe_enabled"
        const val PREF_CONFIG_REVISION = "smart_seek_config_revision"

        fun load(preferences: SharedPreferences): SmartSeekConfig {
            val storedMode = preferences.getString(PREF_MODE, MODE_FIXED)
            val mode = if (storedMode == MODE_SIMPLE) MODE_SIMPLE else MODE_FIXED
            return SmartSeekConfig(
                mode = mode,
                boostBuffer = preferences.getBoolean(PREF_BOOST_BUFFER, false),
                wheelSwipeEnabled = preferences.getBoolean(PREF_WHEEL_SWIPE_ENABLED, true)
            )
        }
    }
}

enum class SmartSeekMode {
    MANUAL
}
