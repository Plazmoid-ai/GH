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
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Non-interactive layer over the playback slider. It displays only the configured interval values
 * and marks the next seek point and the approximate destination after the configured skip.
 */
class SmartSeekProgressOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var active = false
    private var persistent = false
    private var durationMs = 0L
    private var nextSeekPositionMs = -1L
    private var skipDurationMs = 0L
    private var playLabel = ""
    private var skipLabel = ""
    private var markerColor = Color.rgb(255, 109, 0)

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
        playLabel: String,
        skipLabel: String,
        markerColor: Int,
        persistent: Boolean
    ) {
        this.active = active
        this.durationMs = durationMs
        this.nextSeekPositionMs = nextSeekPositionMs
        this.skipDurationMs = skipDurationMs
        this.playLabel = playLabel
        this.skipLabel = skipLabel
        this.markerColor = markerColor
        this.persistent = persistent
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return

        drawTechnicalLabels(canvas)

        if (durationMs <= 0L || nextSeekPositionMs < 0L || nextSeekPositionMs >= durationMs) {
            return
        }

        val left = dp(14f)
        val right = width - dp(14f)
        if (right <= left) return

        fun xFor(positionMs: Long): Float {
            val boundedPosition = positionMs.coerceIn(0L, durationMs)
            return left + (right - left) * (boundedPosition.toFloat() / durationMs.toFloat())
        }

        val seekX = xFor(nextSeekPositionMs)
        val destinationPosition = (nextSeekPositionMs + skipDurationMs).coerceAtMost(durationMs)
        val destinationX = xFor(destinationPosition)
        val trackY = height / 2f + dp(1f)

        // The skipped span uses the selected color, with stronger emphasis in persistent mode.
        paint.style = Paint.Style.STROKE
        paint.color = markerColor
        paint.alpha = if (persistent) 105 else 65
        paint.strokeWidth = dp(if (persistent) 7f else 5f)
        paint.strokeCap = Paint.Cap.ROUND
        if (destinationX > seekX) {
            canvas.drawLine(seekX, trackY, destinationX, trackY, paint)
        }

        paint.alpha = 255
        paint.strokeWidth = dp(if (persistent) 3.5f else 2.2f)
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(seekX, trackY - dp(6f), seekX, trackY + dp(6f), paint)
        if (persistent) {
            // A paired tick identifies persistent mode without adding text to the progress bar.
            canvas.drawLine(
                seekX + dp(4f),
                trackY - dp(5f),
                seekX + dp(4f),
                trackY + dp(5f),
                paint
            )
        }

        paint.style = Paint.Style.FILL
        canvas.drawCircle(destinationX, trackY, dp(if (persistent) 4.2f else 3.2f), paint)
    }

    private fun drawTechnicalLabels(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = markerColor
        paint.alpha = 255
        paint.textSize = sp(10f)
        paint.typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.NORMAL
        )
        paint.textAlign = Paint.Align.LEFT

        val playTextWidth = paint.measureText(playLabel)
        val skipTextWidth = paint.measureText(skipLabel)
        val playIconWidth = dp(8f)
        val skipIconWidth = dp(12f)
        val innerGap = dp(3f)
        val groupGap = dp(13f)
        val playGroupWidth = playIconWidth + innerGap + playTextWidth
        val skipGroupWidth = skipIconWidth + innerGap + skipTextWidth
        val totalWidth = playGroupWidth + groupGap + skipGroupWidth
        val startX = (width - totalWidth) / 2f
        val iconY = dp(9.5f)
        val textY = dp(13f)

        drawPlayIcon(canvas, startX + dp(3.5f), iconY)
        canvas.drawText(playLabel, startX + playIconWidth + innerGap, textY, paint)

        val skipStartX = startX + playGroupWidth + groupGap
        drawSkipIcon(canvas, skipStartX + dp(5f), iconY)
        canvas.drawText(skipLabel, skipStartX + skipIconWidth + innerGap, textY, paint)
    }

    private fun drawPlayIcon(canvas: Canvas, centerX: Float, centerY: Float) {
        val path = Path().apply {
            moveTo(centerX - dp(2.5f), centerY - dp(4f))
            lineTo(centerX + dp(3.5f), centerY)
            lineTo(centerX - dp(2.5f), centerY + dp(4f))
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawSkipIcon(canvas: Canvas, centerX: Float, centerY: Float) {
        val first = Path().apply {
            moveTo(centerX - dp(5f), centerY - dp(4f))
            lineTo(centerX - dp(1f), centerY)
            lineTo(centerX - dp(5f), centerY + dp(4f))
            close()
        }
        val second = Path().apply {
            moveTo(centerX - dp(0.5f), centerY - dp(4f))
            lineTo(centerX + dp(3.5f), centerY)
            lineTo(centerX - dp(0.5f), centerY + dp(4f))
            close()
        }
        canvas.drawPath(first, paint)
        canvas.drawPath(second, paint)
        canvas.drawRect(
            centerX + dp(4f),
            centerY - dp(4f),
            centerX + dp(5.5f),
            centerY + dp(4f),
            paint
        )
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
