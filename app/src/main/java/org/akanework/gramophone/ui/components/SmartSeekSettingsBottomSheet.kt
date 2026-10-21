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
import android.content.res.ColorStateList
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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
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

        // Each setting is one MaterialButton capsule (no separate label or Switch).
        val optionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val capsuleAccent = Color.rgb(0x9b, 0x8a, 0xf5)
        val capsuleTextDark = Color.rgb(0x12, 0x12, 0x12)
        val capsuleTextOff = Color.rgb(0x9e, 0x9e, 0x9e)
        fun makeStateCapsule(
            prefix: String,
            initial: Boolean,
            onChanged: (Boolean) -> Unit
        ): MaterialButton {
            var enabled = initial
            val button = MaterialButton(context).apply {
                isAllCaps = false
                textSize = 12f
                minHeight = dp(44f)
                minimumHeight = dp(44f)
                insetTop = 0
                insetBottom = 0
                cornerRadius = dp(24f)
                strokeWidth = dp(1f)
                setPadding(dp(5f), 0, dp(5f), 0)
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).apply {
                    if (optionRow.childCount == 0) marginEnd = dp(5f) else marginStart = dp(5f)
                }
            }
            fun renderState() {
                button.text = "$prefix: " + if (enabled) "Вкл" else "Выкл"
                button.backgroundTintList = ColorStateList.valueOf(
                    if (enabled) capsuleAccent else Color.TRANSPARENT
                )
                button.strokeColor = ColorStateList.valueOf(
                    if (enabled) capsuleAccent else outlineColor
                )
                button.setTextColor(if (enabled) capsuleTextDark else capsuleTextOff)
                button.contentDescription = button.text
            }
            button.setOnClickListener {
                enabled = !enabled
                renderState()
                onChanged(enabled)
            }
            renderState()
            optionRow.addView(button)
            return button
        }
        val showIndicator = makeStateCapsule(
            "Показывать",
            preferences.getBoolean("smart_seek_show_indicator", true)
        ) { }
        val persistent = makeStateCapsule(
            "Постоянный",
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
        val intervalTypeLabel = TextView(context).apply {
            text = "Тип интервалов"
            textSize = 12f
            setTextColor(capsuleTextOff)
            setPadding(dp(2f), 0, 0, dp(5f))
        }
        form.addView(
            intervalTypeLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2f) }
        )
        form.addView(
            tabStrip,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        fun emptyModeContainer(): LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(92f)
            setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
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

        // Fixed intervals: UI-only controls, values and presets are placeholders for the next step.
        val previewBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = pillBackground(Color.rgb(0x27, 0x27, 0x2d), outlineColor, 8f)
            clipToOutline = true
        }
        previewBar.addView(
            View(context).apply { setBackgroundColor(capsuleAccent) },
            LinearLayout.LayoutParams(0, dp(8f), 3f)
        )
        previewBar.addView(
            View(context).apply { setBackgroundColor(Color.rgb(0x60, 0x60, 0x68)) },
            LinearLayout.LayoutParams(0, dp(8f), 1f)
        )
        fixedContent.addView(
            previewBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8f)
            ).apply { bottomMargin = dp(14f) }
        )

        fun dashedField(value: String): EditText = EditText(context).apply {
            setText(value)
            textSize = 18f
            gravity = Gravity.CENTER
            isSingleLine = true
            setPadding(dp(4f), dp(5f), dp(4f), dp(5f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10f).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(dp(1f), outlineColor, dp(5f).toFloat(), dp(3f).toFloat())
            }
            inputType = android.text.InputType.TYPE_CLASS_DATETIME or
                android.text.InputType.TYPE_DATETIME_VARIATION_TIME
        }
        fun stepButtons(): LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val plus = MaterialButton(context).apply {
                text = "+"
                isAllCaps = false
                minWidth = dp(52f)
                setPadding(0, 0, 0, 0)
                setOnClickListener { /* UI placeholder: interval adjustment comes later. */ }
            }
            val divider = View(context).apply {
                setBackgroundColor(outlineColor)
            }
            val minus = MaterialButton(context).apply {
                text = "−"
                isAllCaps = false
                minWidth = dp(52f)
                setPadding(0, 0, 0, 0)
                setOnClickListener { /* UI placeholder: interval adjustment comes later. */ }
            }
            addView(plus, LinearLayout.LayoutParams(0, dp(40f), 1f))
            addView(divider, LinearLayout.LayoutParams(dp(1f), dp(22f)))
            addView(minus, LinearLayout.LayoutParams(0, dp(40f), 1f))
        }

        val intervalFieldsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val playColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val skipColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        playColumn.addView(
            dashedField("00:30"),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48f)
            )
        )
        playColumn.addView(
            stepButtons(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40f)
            ).apply { topMargin = dp(5f) }
        )
        skipColumn.addView(
            dashedField("00:10"),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48f)
            )
        )
        skipColumn.addView(
            stepButtons(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40f)
            ).apply { topMargin = dp(5f) }
        )
        intervalFieldsRow.addView(
            playColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        intervalFieldsRow.addView(
            TextView(context).apply {
                text = "🔒"
                textSize = 20f
                gravity = Gravity.CENTER
                contentDescription = "Lock"
            },
            LinearLayout.LayoutParams(dp(42f), dp(48f))
        )
        intervalFieldsRow.addView(
            skipColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        fixedContent.addView(
            intervalFieldsRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        fixedContent.addView(
            TextView(context).apply {
                text = "Тап — применить, долгое нажатие — сохранить"
                textSize = 12f
                setTextColor(capsuleTextOff)
                gravity = Gravity.CENTER
                setPadding(0, dp(12f), 0, dp(10f))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        val presetsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        repeat(3) { index ->
            val active = index == 0
            val preset = MaterialButton(context).apply {
                text = listOf("30 / 10", "45 / 15", "60 / 20")[index]
                isAllCaps = false
                textSize = 12f
                cornerRadius = dp(12f)
                strokeWidth = dp(if (active) 2f else 1f)
                strokeColor = ColorStateList.valueOf(if (active) capsuleAccent else outlineColor)
                backgroundTintList = ColorStateList.valueOf(
                    if (active) Color.rgb(0x31, 0x2b, 0x43) else Color.rgb(0x20, 0x20, 0x25)
                )
                setTextColor(if (active) capsuleAccent else capsuleTextOff)
                setOnClickListener { /* UI placeholder: preset behavior comes later. */ }
            }
            presetsRow.addView(
                preset,
                LinearLayout.LayoutParams(0, dp(42f), 1f).apply {
                    if (index > 0) marginStart = dp(5f)
                    if (index < 2) marginEnd = dp(5f)
                }
            )
        }
        fixedContent.addView(
            presetsRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        fun selectedTabBackground(selected: Boolean) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20f).toFloat()
            setColor(if (selected) Color.rgb(0xee, 0xee, 0xf4) else Color.rgb(0x20, 0x20, 0x25))
        }
        fun refreshModeTabs() {
            val simpleSelected = selectedMode == SmartSeekConfig.MODE_SIMPLE
            simpleTab.background = selectedTabBackground(simpleSelected)
            fixedTab.background = selectedTabBackground(!simpleSelected)
            simpleTab.setTextColor(if (simpleSelected) Color.rgb(0x12, 0x12, 0x12) else capsuleTextOff)
            fixedTab.setTextColor(if (!simpleSelected) Color.rgb(0x12, 0x12, 0x12) else capsuleTextOff)
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
                putBoolean("smart_seek_show_indicator", showIndicator.text.toString().endsWith("Вкл"))
                putBoolean("smart_seek_persistent", persistent.text.toString().endsWith("Вкл"))
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
