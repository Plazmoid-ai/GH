/*
 *     Copyright (C) 2026 Plazmoid-ai
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3
 *     of the License, or (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.SmartSeekConfig

/**
 * IntervalPlay settings screen scaffolding. The sheet is locked; only the title handles dismissal.
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

        val outlineColor = MaterialColors.getColor(
            root, com.google.android.material.R.attr.colorOutlineVariant, Color.GRAY
        )
        val surfaceColor = MaterialColors.getColor(
            root, com.google.android.material.R.attr.colorSurfaceVariant, Color.LTGRAY
        )
        val onSurfaceColor = MaterialColors.getColor(
            root, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY
        )
        val primaryColor = MaterialColors.getColor(
            root, androidx.appcompat.R.attr.colorPrimary, Color.rgb(26, 79, 163)
        )
        fun pillBackground(fillColor: Int, strokeColor: Int = outlineColor, radiusDp: Float = 24f) =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(radiusDp).toFloat()
                setColor(fillColor)
                setStroke(dp(1f), strokeColor)
            }

        // Two compact capsule switches in one row.
        val optionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun addCapsuleToggle(labelRes: Int, checked: Boolean, onChanged: (Boolean) -> Unit): MaterialSwitch {
            val toggle = MaterialSwitch(context).apply {
                isChecked = checked
                contentDescription = context.getString(labelRes)
                setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
            }
            val capsule = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(9f), dp(3f), dp(6f), dp(3f))
                background = pillBackground(surfaceColor)
                isClickable = true
                isFocusable = true
                setOnClickListener { toggle.isChecked = !toggle.isChecked }
            }
            capsule.addView(
                TextView(context).apply {
                    setText(labelRes)
                    textSize = 12f
                    maxLines = 2
                    setTextColor(onSurfaceColor)
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            capsule.addView(
                toggle,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            optionRow.addView(
                capsule,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (optionRow.childCount == 0) marginEnd = dp(6f) else marginStart = dp(6f)
                }
            )
            return toggle
        }

        val showIndicator = addCapsuleToggle(
            R.string.smart_seek_show_indicator,
            preferences.getBoolean("smart_seek_show_indicator", true)
        ) { }
        val persistent = addCapsuleToggle(
            R.string.settings_smart_seek_persistent,
            preferences.getBoolean("smart_seek_persistent", false)
        ) { }
        form.addView(
            optionRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2f)
                bottomMargin = dp(10f)
            }
        )

        // A non-scrollable two-segment selector.
        var selectedMode = current.mode
        val tabStrip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
            background = pillBackground(surfaceColor)
        }
        val simpleTab = TextView(context).apply {
            setText(R.string.smart_seek_mode_simple)
            gravity = Gravity.CENTER
            textSize = 14f
            setPadding(dp(8f), dp(10f), dp(8f), dp(10f))
            isClickable = true
            isFocusable = true
        }
        val fixedTab = TextView(context).apply {
            setText(R.string.smart_seek_mode_fixed)
            gravity = Gravity.CENTER
            textSize = 14f
            setPadding(dp(8f), dp(10f), dp(8f), dp(10f))
            isClickable = true
            isFocusable = true
        }
        tabStrip.addView(
            simpleTab,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        tabStrip.addView(
            fixedTab,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        form.addView(
            tabStrip,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        fun emptyModeContainer(): FrameLayout = FrameLayout(context).apply {
            minimumHeight = dp(92f)
            background = pillBackground(surfaceColor, outlineColor, 14f)
        }
        val simpleContent = emptyModeContainer()
        val fixedContent = emptyModeContainer()
        form.addView(
            simpleContent,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8f)
                bottomMargin = dp(4f)
            }
        )
        form.addView(
            fixedContent,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8f)
                bottomMargin = dp(4f)
            }
        )

        fun selectedTabBackground() = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20f).toFloat()
            setColor(primaryColor)
        }
        fun refreshModeTabs() {
            val simpleSelected = selectedMode == SmartSeekConfig.MODE_SIMPLE
            simpleTab.background = if (simpleSelected) selectedTabBackground() else null
            fixedTab.background = if (!simpleSelected) selectedTabBackground() else null
            simpleTab.setTextColor(if (simpleSelected) Color.WHITE else onSurfaceColor)
            fixedTab.setTextColor(if (!simpleSelected) Color.WHITE else onSurfaceColor)
            simpleContent.visibility = if (simpleSelected) View.VISIBLE else View.GONE
            fixedContent.visibility = if (simpleSelected) View.GONE else View.VISIBLE
        }
        simpleTab.setOnClickListener {
            selectedMode = SmartSeekConfig.MODE_SIMPLE
            refreshModeTabs()
        }
        fixedTab.setOnClickListener {
            selectedMode = SmartSeekConfig.MODE_FIXED
            refreshModeTabs()
        }
        refreshModeTabs()

        // Accent selector retained as the current fixed two-row grid.
        val colorValues = context.resources.getStringArray(R.array.smart_seek_marker_color_values)
        val colorEntries = context.resources.getStringArray(R.array.smart_seek_marker_color_entries)
        val accentHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val accentTitle = TextView(context).apply {
            setText(R.string.smart_seek_marker_color)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        }
        val accentName = TextView(context).apply {
            textSize = 14f
            gravity = Gravity.END
            setTextColor(onSurfaceColor)
        }
        accentHeader.addView(
            accentTitle,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        accentHeader.addView(
            accentName,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        form.addView(
            accentHeader,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12f) }
        )

        val accentEnabled = preferences.getBoolean("smart_seek_custom_accent_enabled", true)
        var selectedAccent = if (accentEnabled) {
            preferences.getString("smart_seek_marker_color", "blue") ?: "blue"
        } else {
            "none"
        }
        if (selectedAccent !in colorValues) selectedAccent = "blue"
        fun updateAccentName() {
            val index = colorValues.indexOf(selectedAccent)
            accentName.text = colorEntries.getOrElse(index) { selectedAccent }
        }
        updateAccentName()

        val themeAccentColor = MaterialColors.getColor(
            root, androidx.appcompat.R.attr.colorPrimary, Color.rgb(26, 79, 163)
        )
        val colorSwatches = linkedMapOf<String, FrameLayout>()
        fun swatchFillColor(value: String): Int = when (value) {
            "red" -> Color.RED
            "orange" -> Color.rgb(255, 109, 0)
            "cyan" -> Color.rgb(0, 184, 212)
            "lime" -> Color.rgb(124, 179, 66)
            "pink" -> Color.rgb(245, 0, 87)
            "violet" -> Color.rgb(124, 77, 255)
            "black" -> Color.BLACK
            "none" -> themeAccentColor
            else -> Color.rgb(26, 79, 163)
        }
        fun swatchDrawable(value: String): GradientDrawable = when (value) {
            "dynamic" -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(33, 150, 243), Color.rgb(156, 39, 176), Color.rgb(255, 152, 0))
            ).apply { shape = GradientDrawable.OVAL }
            "transparent" -> GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(2f), Color.WHITE)
            }
            else -> GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(swatchFillColor(value))
            }
        }
        fun updateAccentSelection() {
            val selectedRingColor = MaterialColors.getColor(
                root, androidx.appcompat.R.attr.colorPrimary, Color.WHITE
            )
            colorSwatches.forEach { (value, swatch) ->
                swatch.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(
                        dp(if (value == selectedAccent) 2.5f else 1f),
                        if (value == selectedAccent) selectedRingColor else outlineColor
                    )
                }
                swatch.isSelected = value == selectedAccent
            }
        }
        val orderedColorValues = listOf(
            "none", "dynamic", "transparent", "black", "red", "orange",
            "cyan", "lime", "pink", "violet", "blue"
        )
        val colorGrid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4f), 0, dp(4f))
        }
        val topColorRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val bottomColorRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        colorGrid.addView(
            topColorRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48f))
        )
        colorGrid.addView(
            bottomColorRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48f))
        )
        form.addView(
            colorGrid,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        orderedColorValues.forEachIndexed { index, value ->
            val entryIndex = colorValues.indexOf(value)
            val swatch = FrameLayout(context).apply {
                contentDescription = colorEntries.getOrElse(entryIndex) { value }
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f)).apply {
                    marginEnd = dp(4f)
                }
                addView(
                    View(context).apply { background = swatchDrawable(value) },
                    FrameLayout.LayoutParams(dp(28f), dp(28f), Gravity.CENTER)
                )
                setOnClickListener {
                    selectedAccent = value
                    updateAccentName()
                    updateAccentSelection()
                }
            }
            colorSwatches[value] = swatch
            (if (index < 6) topColorRow else bottomColorRow).addView(swatch)
        }
        updateAccentSelection()

        addLabel(context.getString(R.string.smart_seek_experimental), dp(16f))
        addLabel(context.getString(R.string.smart_seek_boost_buffer), dp(8f))
        val boostBuffer = CheckBox(context).apply {
            setText(R.string.smart_seek_boost_buffer_help)
            isChecked = current.boostBuffer
        }
        form.addView(boostBuffer)

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8f), 0, 0)
        }
        val cancelButton = MaterialButton(context).apply {
            setText(R.string.smart_seek_cancel)
            setOnClickListener { dialog.dismiss() }
        }
        val saveButton = MaterialButton(context).apply { setText(R.string.smart_seek_save) }
        actions.addView(
            cancelButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(16f) }
        )
        actions.addView(
            saveButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(16f) }
        )
        root.addView(
            actions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        dialog.setContentView(root)

        saveButton.setOnClickListener {
            SmartSeekConfig(mode = selectedMode, boostBuffer = boostBuffer.isChecked).save(preferences) {
                putBoolean("smart_seek_show_indicator", showIndicator.isChecked)
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
