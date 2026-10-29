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
import androidx.compose.ui.graphics.Color
import androidx.preference.PreferenceManager

/**
 * State shared by the Snippets Preview controls and the real player controller.
 * Playback itself remains owned by Gramophone's MediaSession/MediaBrowser.
 */
object SnippetsPreviewState {
    const val PREFERENCE_KEY = "snippets_preview"
    const val MAX_PREVIEW_DURATION_SEC = 600

    var isSnippetsEnabled by mutableStateOf(false)
    var isSnippetsActive by mutableStateOf(false)
    var showSnippetsMenu by mutableStateOf(false)
    var previewDurationSec by mutableStateOf(30)
    var trackDurationMs by mutableStateOf(0L)
    var currentPositionMs by mutableStateOf(0L)
    var currentSnippetIndex by mutableStateOf(0)
    var snippetBounds by mutableStateOf<List<Pair<Long, Long>>>(emptyList())
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
    val activeZones = mutableStateListOf(true, true, true)

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
        previewDurationSec = seconds.coerceIn(1, MAX_PREVIEW_DURATION_SEC)
    }

    /**
     * Returns candidate windows for the three macro-zones.
     * Short previews are centered in the middle micro-zone of each macro-zone,
     * medium previews in the macro-zone, and longer previews on macro-zone seams.
     */
    fun calculateSnippetBounds(
        trackDurationMs: Long,
        previewDurationMs: Long,
    ): List<Pair<Long, Long>> {
        if (trackDurationMs <= 0L || previewDurationMs <= 0L) return emptyList()
        if (previewDurationMs >= trackDurationMs) return listOf(0L to trackDurationMs)

        val macroZoneMs = trackDurationMs / 3L
        val microZoneMs = macroZoneMs / 3L
        val latestStart = (trackDurationMs - previewDurationMs).coerceAtLeast(0L)

        return (0..2).map { index ->
            val macroStart = macroZoneMs * index
            val center = when {
                previewDurationMs <= microZoneMs ->
                    macroStart + microZoneMs + microZoneMs / 2L
                previewDurationMs <= macroZoneMs ->
                    macroStart + macroZoneMs / 2L
                previewDurationMs < macroZoneMs * 2L ->
                    macroZoneMs * (index + 1L).coerceAtMost(2L)
                else -> trackDurationMs / 2L
            }
            val start = (center - previewDurationMs / 2L).coerceIn(0L, latestStart)
            start to (start + previewDurationMs).coerceAtMost(trackDurationMs)
        }
    }

    /**
     * Applies the selected zones and disables trailing active zones until the
     * combined preview duration fits the track. If one preview cannot fit,
     * the complete track is returned as a single window.
     */
    fun resolveActiveSnippetBounds(
        trackDurationMs: Long,
        previewDurationMs: Long,
    ): List<Pair<Long, Long>> {
        if (trackDurationMs <= 0L || previewDurationMs <= 0L) return emptyList()

        if (previewDurationMs >= trackDurationMs) {
            val keepIndex = activeZones.indexOfFirst { it }.takeIf { it >= 0 } ?: 0
            activeZones.indices.forEach { activeZones[it] = it == keepIndex }
            return listOf(0L to trackDurationMs)
        }

        var selectedIndices = activeZones.indices.filter { activeZones[it] }
        if (selectedIndices.isEmpty()) {
            activeZones[0] = true
            selectedIndices = listOf(0)
        }

        while (
            selectedIndices.size > 1 &&
            previewDurationMs * selectedIndices.size.toLong() > trackDurationMs
        ) {
            activeZones[selectedIndices.last()] = false
            selectedIndices = selectedIndices.dropLast(1)
        }

        val macroZoneMs = trackDurationMs / 3L
        if (previewDurationMs > macroZoneMs && previewDurationMs < macroZoneMs * 2L) {
            // There are only two macro seams available in this size range. When two
            // zones remain, place them at 1/3 and 2/3 regardless of which toggles were chosen.
            val centers = if (selectedIndices.size > 1) {
                listOf(macroZoneMs, macroZoneMs * 2L)
            } else {
                listOf(trackDurationMs * 2L / 3L)
            }
            val latestStart = (trackDurationMs - previewDurationMs).coerceAtLeast(0L)
            return selectedIndices.mapIndexed { index, _ ->
                val center = centers[index.coerceAtMost(centers.lastIndex)]
                val start = (center - previewDurationMs / 2L).coerceIn(0L, latestStart)
                start to (start + previewDurationMs).coerceAtMost(trackDurationMs)
            }.sortedBy { it.first }
        }

        val candidates = calculateSnippetBounds(trackDurationMs, previewDurationMs)
        return selectedIndices.map { candidates[it] }.sortedBy { it.first }
    }

    fun totalSnippetDurationMs(): Long =
        snippetBounds.sumOf { (start, end) -> (end - start).coerceAtLeast(0L) }

    /** Position within the currently playing snippet, not including gaps or earlier snippets. */
    fun activeElapsedMs(): Long {
        if (snippetBounds.isEmpty()) return 0L
        val index = currentSnippetIndex.coerceIn(0, snippetBounds.lastIndex)
        val (start, end) = snippetBounds[index]
        return (currentPositionMs - start)
            .coerceIn(0L, (end - start).coerceAtLeast(0L))
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
