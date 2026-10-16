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
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import org.akanework.gramophone.R

/**
 * Non-interactive visualisation of Smart Seek's current play and skip intervals.
 *
 * The first MVP intentionally uses fixed 15/30-second intervals and full-width tracks.
 * Width normalisation and pause controls are handled in later steps.
 */
class SmartSeekProgressOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val playIcon = ContextCompat.getDrawable(context, R.drawable.outline_volume_up_24)?.mutate()
    private val skipIcon = ContextCompat.getDrawable(context, R.drawable.outline_volume_off_24)?.mutate()

    private var active = false
    private var currentPositionMs = 0L
    private var nextSeekPositionMs = -1L
    private var markerColor = Color.rgb(255, 109, 0)
    private var playProgress = 0f
    private var skipProgress = 0f

    private val secondsUnit = context.getString(R.string.smart_seek_seconds_short)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun update(
        active: Boolean,
        currentPositionMs: Long,
        nextSeekPositionMs: Long,
        markerColor: Int
    ) {
        this.active = active
        this.currentPositionMs = currentPositionMs.coerceAtLeast(0L)
        this.nextSeekPositionMs = nextSeekPositionMs
        this.markerColor = markerColor

        if (nextSeekPositionMs >= 0L) {
            val playStartMs = (nextSeekPositionMs - PLAY_DURATION_MS).coerceAtLeast(0L)
            playProgress = (
                (this.currentPositionMs - playStartMs).toFloat() / PLAY_DURATION_MS
            ).coerceIn(0f, 1f)
            skipProgress = 0f
        } else {
            // The service clears the next target while performing its seek.
            // Show that brief transition as the skip interval being consumed.
            playProgress = 1f
            skipProgress = 1f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return

        val barLeft = dp(72f)
        val barRight = width - dp(8f)
        if (barRight <= barLeft) return

        val playCenterY = height * 0.34f
        val skipCenterY = height * 0.70f
        val trackWidth = barRight - barLeft

        paint.style = Paint.Style.FILL
        paint.color = markerColor
        paint.alpha = 255
        canvas.drawRoundRect(
            RectF(barLeft, playCenterY - dp(4f), barRight, playCenterY + dp(4f)),
            dp(4f),
            dp(4f),
            paint
        )

        paint.color = markerColor
        paint.alpha = 38
        canvas.drawRoundRect(
            RectF(barLeft, skipCenterY - dp(3f), barRight, skipCenterY + dp(3f)),
            dp(3f),
            dp(3f),
            paint
        )

        val playCursorRadius = dp(5.5f)
        val skipCursorRadius = dp(4f)
        val playCursorX = barLeft + playCursorRadius +
            (trackWidth - playCursorRadius * 2f) * playProgress
        val skipCursorX = barLeft + skipCursorRadius +
            (trackWidth - skipCursorRadius * 2f) * skipProgress

        paint.color = Color.BLACK
        paint.alpha = 220
        canvas.drawRoundRect(
            RectF(barLeft, playCenterY - dp(4f), playCursorX, playCenterY + dp(4f)),
            dp(4f),
            dp(4f),
            paint
        )

        paint.color = Color.GRAY
        paint.alpha = 153
        canvas.drawRoundRect(
            RectF(barLeft, skipCenterY - dp(3f), skipCursorX, skipCenterY + dp(3f)),
            dp(3f),
            dp(3f),
            paint
        )

        paint.color = Color.rgb(25, 25, 25)
        paint.alpha = 255
        canvas.drawCircle(playCursorX, playCenterY, playCursorRadius, paint)

        paint.color = Color.GRAY
        paint.alpha = 153
        canvas.drawCircle(skipCursorX, skipCenterY, skipCursorRadius, paint)

        drawLabel(canvas, playIcon, "15$secondsUnit", playCenterY)
        drawLabel(canvas, skipIcon, "+30$secondsUnit", skipCenterY)
    }

    private fun drawLabel(
        canvas: Canvas,
        icon: android.graphics.drawable.Drawable?,
        text: String,
        centerY: Float
    ) {
        paint.style = Paint.Style.FILL
        paint.color = markerColor
        paint.alpha = 255
        paint.textSize = sp(11f)
        paint.typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.NORMAL
        )
        paint.textAlign = Paint.Align.RIGHT

        val textRight = dp(61f)
        val baseline = centerY + dp(4f)
        val textWidth = paint.measureText(text)
        canvas.drawText(text, textRight, baseline, paint)

        val iconSize = dp(14f)
        val iconRight = textRight - textWidth - dp(3f)
        icon?.let {
            it.setTint(markerColor)
            it.setBounds(
                (iconRight - iconSize).toInt(),
                (centerY - iconSize / 2f).toInt(),
                iconRight.toInt(),
                (centerY + iconSize / 2f).toInt()
            )
            it.draw(canvas)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    companion object {
        private const val PLAY_DURATION_MS = 15_000L
    }
}
