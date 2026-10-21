/*
 *     Copyright (C) 2026 Plazmoid-ai
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3
 *     of the License, or (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.google.android.material.slider.Slider
import kotlin.math.max

/**
 * Timer-driven scanner over the stock progress bar. Animation never uses track position.
 */
class SmartSeekMiniIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val capsuleWidthPx = 48f * density
    private val capsuleHeightPx = 24f * density
    private val capsuleRadiusPx = 12f * density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2.5f * density
    }

    private var seekBarView: View? = null
    private var sliderView: View? = null
    private var phaseActive = false
    private var activeDurationMs = 0L
    private var phaseStartElapsedRealtimeMs = 0L
    private var progress = 0f
    private var valueAnimator: ValueAnimator? = null
    private var lastAccentColor: Int? = null

    init {
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
    }

    fun setSeekableViews(seekBar: View, slider: View) {
        seekBarView = seekBar
        sliderView = slider
    }

    fun updatePlaybackState(
        smartSeekActive: Boolean,
        hasPlayInterval: Boolean,
        isPlaying: Boolean,
        isSkipping: Boolean,
        playDurationMs: Long,
        accentColor: Int,
        showIndicator: Boolean
    ) {
        val accentChanged = lastAccentColor != accentColor
        if (accentChanged) {
            fillPaint.color = accentColor
            lastAccentColor = accentColor
        }
        val phaseShouldRun = smartSeekActive && hasPlayInterval && isPlaying &&
            !isSkipping && playDurationMs > 0L
        if (!phaseShouldRun || (phaseActive && activeDurationMs != playDurationMs)) {
            stopPlayPhase()
            if (!phaseShouldRun) {
                if (visibility != GONE) visibility = GONE
                return
            }
        }

        // The phase clock persists if the drawing is toggled off and back on mid-interval.
        if (!phaseActive) {
            phaseActive = true
            activeDurationMs = playDurationMs
            phaseStartElapsedRealtimeMs = SystemClock.elapsedRealtime()
            progress = 0f
        }
        val elapsedMs = (SystemClock.elapsedRealtime() - phaseStartElapsedRealtimeMs).coerceAtLeast(0L)
        progress = (elapsedMs.toFloat() / activeDurationMs.toFloat()).coerceIn(0f, 1f)

        if (!showIndicator) {
            valueAnimator?.cancel()
            valueAnimator = null
            if (visibility != GONE) visibility = GONE
            return
        }

        val becameVisible = visibility != VISIBLE
        if (becameVisible) visibility = VISIBLE
        if (progress >= 1f) {
            valueAnimator?.cancel()
            valueAnimator = null
            if (becameVisible || accentChanged) invalidate()
            return
        }

        // Only one animator runs per play phase. It resumes from elapsed timer progress
        // if the user temporarily hid the indicator.
        if (valueAnimator?.isRunning != true) {
            val remainingMs = (activeDurationMs - elapsedMs).coerceAtLeast(1L)
            valueAnimator = ValueAnimator.ofFloat(progress, 1f).apply {
                duration = remainingMs
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    if (phaseActive && visibility == VISIBLE) {
                        progress = (animation.animatedValue as Float).coerceIn(0f, 1f)
                        invalidate()
                    }
                }
                start()
            }
        }
        if (becameVisible || accentChanged) invalidate()
    }

    private fun stopPlayPhase() {
        phaseActive = false
        valueAnimator?.cancel()
        valueAnimator = null
        activeDurationMs = 0L
        phaseStartElapsedRealtimeMs = 0L
        progress = 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!phaseActive || visibility != VISIBLE || width <= 0 || height <= 0) return
        val trackView = when {
            sliderView?.visibility == VISIBLE -> sliderView
            seekBarView?.visibility == VISIBLE -> seekBarView
            else -> null
        } ?: return
        if (trackView.width <= 0) return

        val sliderInsetPx = if (trackView is Slider) 10f * density else 0f
        val trackOriginX = (trackView.left - left).toFloat()
        val startX = trackOriginX + max(trackView.paddingLeft.toFloat(), sliderInsetPx)
        val endX = trackOriginX + trackView.width - max(trackView.paddingRight.toFloat(), sliderInsetPx)
        val trackWidth = (endX - startX).coerceAtLeast(0f)
        val availableWidth = (trackWidth - capsuleWidthPx).coerceAtLeast(0f)
        val capsuleLeft = startX + availableWidth * progress
        val capsuleTop = (height - capsuleHeightPx) / 2f

        canvas.drawRoundRect(
            capsuleLeft, capsuleTop, capsuleLeft + capsuleWidthPx, capsuleTop + capsuleHeightPx,
            capsuleRadiusPx, capsuleRadiusPx, fillPaint
        )
        canvas.drawRoundRect(
            capsuleLeft, capsuleTop, capsuleLeft + capsuleWidthPx, capsuleTop + capsuleHeightPx,
            capsuleRadiusPx, capsuleRadiusPx, outlinePaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun onDetachedFromWindow() {
        stopPlayPhase()
        visibility = GONE
        super.onDetachedFromWindow()
    }
}
