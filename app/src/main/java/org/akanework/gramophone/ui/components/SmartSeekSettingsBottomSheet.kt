/*
 *     Copyright (C) 2026 Plazmoid-ai
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.SmartSeekConfig
import org.akanework.gramophone.logic.SmartSeekInputMode

/**
 * Smart Seek settings. The sheet itself is locked; only the title handles downward-dismiss gestures.
 */
class SmartSeekSettingsBottomSheet(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val onSaved: (() -> Unit)? = null
) {
    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun show(): BottomSheetDialog {
        val current = SmartSeekConfig.load(preferences)
        val dialog = BottomSheetDialog(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(8f), dp(16f), dp(12f))
        }

        val title = TextView(context).apply {
            setText(R.string.smart_seek_settings_title)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(8f), dp(4f), dp(14f))
            contentDescription = context.getString(R.string.smart_seek_settings_title)
        }
        root.addView(title)

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(2f), 0, dp(2f), dp(8f))
        }
        val form = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(form)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.60f).toInt()
            )
        )

        fun addLabel(text: CharSequence, topMargin: Int = dp(12f)) {
            form.addView(
                TextView(context).apply {
                    this.text = text
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { this.topMargin = topMargin }
            )
        }

        form.addView(
            TextView(context).apply {
                setText(R.string.smart_seek_settings_intro)
                textSize = 14f
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8f) }
        )

        val persistent = CheckBox(context).apply {
            setText(R.string.settings_smart_seek_persistent)
            isChecked = preferences.getBoolean("smart_seek_persistent", false)
        }
        form.addView(persistent)

        addLabel(context.getString(R.string.smart_seek_input_mode))
        val modeGroup = RadioGroup(context).apply { orientation = LinearLayout.VERTICAL }

        fun modeRadio(mode: SmartSeekInputMode, labelRes: Int) =
            RadioButton(context).apply {
                id = View.generateViewId()
                tag = mode
                setText(labelRes)
            }

        val absoluteRadio = modeRadio(SmartSeekInputMode.ABSOLUTE, R.string.smart_seek_mode_absolute)
        val percentRadio = modeRadio(SmartSeekInputMode.PERCENT, R.string.smart_seek_mode_percent)
        val cycleRadio = modeRadio(SmartSeekInputMode.CYCLE, R.string.smart_seek_mode_cycle)
        modeGroup.addView(absoluteRadio)
        modeGroup.addView(percentRadio)
        modeGroup.addView(cycleRadio)
        when (current.inputMode) {
            SmartSeekInputMode.ABSOLUTE -> absoluteRadio.isChecked = true
            SmartSeekInputMode.PERCENT -> percentRadio.isChecked = true
            SmartSeekInputMode.CYCLE -> cycleRadio.isChecked = true
        }
        form.addView(modeGroup)

        val playLabel = TextView(context)
        val playInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setSelectAllOnFocus(true)
        }
        val skipLabel = TextView(context)
        val skipInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setSelectAllOnFocus(true)
        }
        form.addView(playLabel)
        form.addView(playInput)
        form.addView(skipLabel)
        form.addView(skipInput)

        val modeHelp = TextView(context).apply {
            textSize = 13f
            setPadding(0, dp(4f), 0, dp(4f))
        }
        form.addView(
            modeHelp,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4f) }
        )

        fun currentMode(): SmartSeekInputMode =
            when (modeGroup.checkedRadioButtonId) {
                absoluteRadio.id -> SmartSeekInputMode.ABSOLUTE
                percentRadio.id -> SmartSeekInputMode.PERCENT
                else -> SmartSeekInputMode.CYCLE
            }

        fun updateInputLabels(mode: SmartSeekInputMode) {
            when (mode) {
                SmartSeekInputMode.ABSOLUTE -> {
                    playLabel.setText(R.string.smart_seek_play_interval)
                    skipLabel.setText(R.string.smart_seek_skip_amount)
                    modeHelp.setText(R.string.smart_seek_absolute_help)
                }
                SmartSeekInputMode.PERCENT -> {
                    playLabel.setText(R.string.smart_seek_play_interval_percent)
                    skipLabel.setText(R.string.smart_seek_skip_amount_percent)
                    modeHelp.setText(R.string.smart_seek_percent_help)
                }
                SmartSeekInputMode.CYCLE -> {
                    playLabel.setText(R.string.smart_seek_cycle_duration)
                    skipLabel.setText(R.string.smart_seek_sound_ratio)
                    modeHelp.setText(R.string.smart_seek_cycle_help)
                }
            }
        }

        fun loadModeValues(mode: SmartSeekInputMode) {
            val values = when (mode) {
                SmartSeekInputMode.ABSOLUTE -> current.absolutePlaySeconds to current.absoluteSkipSeconds
                SmartSeekInputMode.PERCENT -> current.playPercent to current.skipPercent
                SmartSeekInputMode.CYCLE -> (current.cycleDurationMs / 1_000L).toInt() to current.soundRatioPercent
            }
            playInput.setText(values.first.toString())
            skipInput.setText(values.second.toString())
        }

        updateInputLabels(current.inputMode)
        loadModeValues(current.inputMode)
        modeGroup.setOnCheckedChangeListener { _, _ ->
            val mode = currentMode()
            updateInputLabels(mode)
            loadModeValues(mode)
        }

        addLabel(context.getString(R.string.smart_seek_marker_color))
        val colorSpinner = Spinner(context)
        colorSpinner.adapter = ArrayAdapter.createFromResource(
            context,
            R.array.smart_seek_marker_color_entries,
            android.R.layout.simple_spinner_item
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val colorValues = context.resources.getStringArray(R.array.smart_seek_marker_color_values)
        val accentEnabled = preferences.getBoolean("smart_seek_custom_accent_enabled", true)
        val selectedColor = if (accentEnabled) {
            preferences.getString("smart_seek_marker_color", "blue") ?: "blue"
        } else {
            "none"
        }
        colorSpinner.setSelection(colorValues.indexOf(selectedColor).coerceAtLeast(0))
        form.addView(colorSpinner)

        addLabel(context.getString(R.string.smart_seek_boost_buffer))
        val boostBuffer = CheckBox(context).apply {
            setText(R.string.smart_seek_boost_buffer_help)
            isChecked = current.boostBuffer
        }
        form.addView(boostBuffer)

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, 0)
        }
        val cancelButton = MaterialButton(context).apply {
            setText(R.string.smart_seek_cancel)
            setOnClickListener { dialog.dismiss() }
        }
        val saveButton = MaterialButton(context).apply { setText(R.string.smart_seek_save) }
        actions.addView(cancelButton)
        actions.addView(saveButton)
        root.addView(
            actions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        dialog.setContentView(root)

        saveButton.setOnClickListener {
            val mode = currentMode()
            val playMax = if (mode == SmartSeekInputMode.PERCENT) 99 else 600
            val skipMax = if (mode == SmartSeekInputMode.ABSOLUTE) 600 else 99
            val play = playInput.text.toString().trim().toIntOrNull()
            val skip = skipInput.text.toString().trim().toIntOrNull()
            if (play == null || skip == null || play !in 1..playMax || skip !in 1..skipMax) {
                Toast.makeText(
                    context,
                    context.getString(R.string.smart_seek_invalid_value, minOf(playMax, skipMax)),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val updated = when (mode) {
                SmartSeekInputMode.ABSOLUTE -> current.copy(
                    playValue = play,
                    skipValue = skip,
                    usePercent = false,
                    inputMode = mode,
                    absolutePlaySeconds = play,
                    absoluteSkipSeconds = skip
                )
                SmartSeekInputMode.PERCENT -> current.copy(
                    playValue = play,
                    skipValue = skip,
                    usePercent = true,
                    inputMode = mode,
                    playPercent = play,
                    skipPercent = skip
                )
                SmartSeekInputMode.CYCLE -> current.copy(
                    playValue = (play * skip / 100).coerceAtLeast(1),
                    skipValue = (play - play * skip / 100).coerceAtLeast(1),
                    usePercent = false,
                    inputMode = mode,
                    cycleDurationMs = play.toLong() * 1_000L,
                    soundRatioPercent = skip
                )
            }
            val selectedAccent = colorValues[colorSpinner.selectedItemPosition]
            updated.copy(boostBuffer = boostBuffer.isChecked).save(preferences) {
                putBoolean("smart_seek_persistent", persistent.isChecked)
                putBoolean("smart_seek_custom_accent_enabled", selectedAccent != "none")
                putString("smart_seek_marker_color", if (selectedAccent == "none") "blue" else selectedAccent)
            }
            dialog.dismiss()
            onSaved?.invoke()
        }

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val dismissDistance = maxOf(dp(56f), touchSlop * 2)
        var startY = 0f
        var maxDownDistance = 0f
        title.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    maxDownDistance = 0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    maxDownDistance = maxOf(maxDownDistance, event.rawY - startY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val distance = event.rawY - startY
                    if (maxOf(distance, maxDownDistance) >= dismissDistance) dialog.dismiss()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }

        dialog.setOnShowListener {
            dialog.behavior.isDraggable = false
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.show()
        return dialog
    }
}
