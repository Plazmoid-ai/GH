/*
 *     Copyright (C) 2026 Plazmoid-ai
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3
 *     of the License, or (at your option) any later version.
 */

package org.akanework.gramophone.ui.fragments.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import org.akanework.gramophone.ui.components.SmartSeekSettingsBottomSheet

/**
 * Smart Seek's summary is a separate settings shortcut; taps on the switch still toggle the feature.
 */
class SmartSeekEnabledPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwitchPreferenceCompat(context, attrs) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.findViewById(android.R.id.summary)?.let { summary ->
            summary.isClickable = true
            summary.isFocusable = true
            summary.setOnClickListener {
                SmartSeekSettingsBottomSheet(
                    context,
                    PreferenceManager.getDefaultSharedPreferences(context)
                ).show()
            }
        }
    }
}
