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
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.MaterialColors
import org.akanework.gramophone.logic.utils.ColorUtils as PlayerColorUtils

/**
 * Colors resolved from the same Android theme context that colors the native player.
 * When content-based coloring is enabled, that context is wrapped with the current
 * track's album-art palette by FullBottomSheet.
 */
data class SnippetsColors(
    val accent: Color,
    val onAccent: Color,
    val panelBg: Color,
    val tabInactive: Color,
    val onTabInactive: Color,
    val mapTrack: Color,
    val tooltipBg: Color,
    val tooltipText: Color,
)

/**
 * Builds the Snippets palette from the current player theme. Passing FullBottomSheet's
 * wrapped context allows this to follow DynamicColors' album-art-derived palette.
 */
fun snippetsColorsFromPlayerPalette(context: Context): SnippetsColors {
    val colorSurface = MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorSurface,
        -1,
    )
    val colorOnSurfaceVariant = MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorOnSurfaceVariant,
        -1,
    )
    val paletteAccent = Color(
        MaterialColors.getColor(
            context,
            androidx.appcompat.R.attr.colorPrimary,
            -1,
        ),
    )
    val colorOnPrimary = MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorOnPrimary,
        -1,
    )
    val colorSecondaryContainer = MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorSecondaryContainer,
        -1,
    )
    val colorOnSecondaryContainer = MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorOnSecondaryContainer,
        -1,
    )

    // Single future integration point for a user-selected accent.
    val accent = resolveSnippetsAccent(paletteAccent)
    val panelBg = PlayerColorUtils.getColor(
        colorSurface,
        PlayerColorUtils.ColorType.COLOR_BACKGROUND_ELEVATED,
        context,
    )
    val inactiveTrack = PlayerColorUtils.getColor(
        colorSecondaryContainer,
        PlayerColorUtils.ColorType.COLOR_CONTRAST_FAINTED,
        context,
    )

    return SnippetsColors(
        accent = accent,
        onAccent = Color(colorOnPrimary),
        panelBg = Color(panelBg),
        tabInactive = Color(
            MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorSurfaceVariant,
                -1,
            ),
        ),
        onTabInactive = Color(colorOnSurfaceVariant),
        mapTrack = Color(inactiveTrack),
        tooltipBg = Color(colorSecondaryContainer),
        tooltipText = Color(colorOnSecondaryContainer),
    )
}

private fun resolveSnippetsAccent(paletteAccent: Color): Color = paletteAccent
