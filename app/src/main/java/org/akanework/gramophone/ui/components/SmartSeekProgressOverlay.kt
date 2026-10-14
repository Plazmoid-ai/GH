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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors

/**
 * Non-interactive layer over the playback slider. It marks the next seek point and
 * the approximate destination after the configured skip.
 */
class SmartSeekProgressOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var active = false
    private var durationMs = 0L
    private var nextSeekPositionMs = -1L
    private var skipDurationMs = 0L
    private var label = ""

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun update(
        active: Boolean,
        durationMs: Long,
        nextSeekPositionMs: Long,
        skipDurationMs: Long,
        label: String
    ) {
        this.active = active
        this.durationMs = durationMs
        this.nextSeekPositionMs = nextSeekPositionMs
        this.skipDurationMs = skipDurationMs
        this.label = label
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return

        val primary = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorPrimary,
            Color.WHITE
        )
        val secondary = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSecondary,
            primary
        )
        val left = dp(14f)
        val right = width - dp(14f)
        if (right <= left) return

        paint.style = Paint.Style.FILL
        paint.color = primary
        paint.textSize = sp(10f)
        paint.typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.NORMAL
        )
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, width / 2f, dp(12f), paint)

        if (durationMs <= 0L || nextSeekPositionMs < 0L || nextSeekPositionMs >= durationMs) {
            return
        }

        fun xFor(positionMs: Long): Float {
            val boundedPosition = positionMs.coerceIn(0L, durationMs)
            return left + (right - left) * (boundedPosition.toFloat() / durationMs.toFloat())
        }

        val seekX = xFor(nextSeekPositionMs)
        val destinationPosition = (nextSeekPositionMs + skipDurationMs).coerceAtMost(durationMs)
        val destinationX = xFor(destinationPosition)
        val trackY = height / 2f + dp(1f)

        // The highlighted interval between the two markers is the area Smart Seek will jump over.
        paint.color = primary
        paint.alpha = 95
        paint.strokeWidth = dp(5f)
        paint.strokeCap = Paint.Cap.ROUND
        if (destinationX > seekX) {
            canvas.drawLine(seekX, trackY, destinationX, trackY, paint)
        }

        paint.alpha = 255
        paint.strokeWidth = dp(2f)
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(seekX, trackY - dp(6f), seekX, trackY + dp(6f), paint)

        paint.color = secondary
        canvas.drawLine(
            destinationX,
            trackY - dp(6f),
            destinationX,
            trackY + dp(6f),
            paint
        )
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
