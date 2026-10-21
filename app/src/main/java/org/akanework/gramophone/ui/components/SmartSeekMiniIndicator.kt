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
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.slider.Slider
import kotlin.math.max

/**
 * Small, non-interactive capsule indicating progress through the current Smart Seek play interval.
 *
 * The stock SeekBar/Slider remains responsible for all input and progress rendering.
 */
class SmartSeekMiniIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val capsuleWidthPx = 16f * density
    private val capsuleHeightPx = 8f * density
    private val capsuleRadiusPx = 4f * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var seekBarView: View? = null
    private var sliderView: View? = null
    private var indicatorVisible = false
    private var progress = 0f

    init {
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
    }

    fun setSeekableViews(seekBar: View, slider: View) {
        seekBarView = seekBar
        sliderView = slider
    }

    /**
     * Updates the capsule from Smart Seek's service state.
     * Explicit invalidation is only requested during an active play interval.
     */
    fun updatePlaybackState(
        smartSeekActive: Boolean,
        isPlaying: Boolean,
        isSkipping: Boolean,
        currentPositionMs: Long,
        intervalEndPositionMs: Long,
        playDurationMs: Long,
        accentColor: Int
    ) {
        val intervalStartPositionMs = intervalEndPositionMs - playDurationMs
        val shouldShow = smartSeekActive &&
            isPlaying &&
            !isSkipping &&
            playDurationMs > 0L &&
            intervalEndPositionMs > 0L &&
            currentPositionMs >= intervalStartPositionMs &&
            currentPositionMs < intervalEndPositionMs

        if (!shouldShow) {
            indicatorVisible = false
            if (visibility != GONE) visibility = GONE
            return
        }

        indicatorVisible = true
        progress = ((currentPositionMs - intervalStartPositionMs).toFloat() /
            playDurationMs.toFloat()).coerceIn(0f, 1f)
        paint.color = accentColor
        if (visibility != VISIBLE) visibility = VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!indicatorVisible || width <= 0 || height <= 0) return

        val trackView = when {
            sliderView?.visibility == VISIBLE -> sliderView
            seekBarView?.visibility == VISIBLE -> seekBarView
            else -> null
        } ?: return
        if (trackView.width <= 0) return

        // Material Slider draws its track inset by half the 20dp thumb width. SeekBar
        // already exposes its drawable insets through paddingLeft/paddingRight.
        val sliderInsetPx = if (trackView is Slider) 10f * density else 0f
        val leftInsetPx = max(trackView.paddingLeft.toFloat(), sliderInsetPx)
        val rightInsetPx = max(trackView.paddingRight.toFloat(), sliderInsetPx)
        val trackOriginX = (trackView.left - left).toFloat()
        val trackStartX = trackOriginX + leftInsetPx
        val trackEndX = trackOriginX + trackView.width - rightInsetPx
        val availableWidth = (trackEndX - trackStartX - capsuleWidthPx).coerceAtLeast(0f)

        val capsuleLeft = trackStartX + availableWidth * progress
        val capsuleTop = (height - capsuleHeightPx) / 2f
        canvas.drawRoundRect(
            capsuleLeft,
            capsuleTop,
            capsuleLeft + capsuleWidthPx,
            capsuleTop + capsuleHeightPx,
            capsuleRadiusPx,
            capsuleRadiusPx,
            paint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
