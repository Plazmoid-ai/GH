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
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Canvas-based Smart Seek progress area.
 *
 * This file is intentionally rebuilt from scratch. This first replacement uses one
 * future-only track as a temporary baseline; the segmented timeline is added in Stage 2.
 * The update() signature stays compatible with FullBottomSheet until real timeline data
 * is connected in Stage 3.
 */
class SmartSeekProgressOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val resumeButtonBounds = RectF()
    private val resumeIconPath = Path()

    private var active = false
    private var markerColor = Color.rgb(255, 109, 0)

    private var resumeButtonVisible = false
    private var resumeTouchActive = false

    /**
     * Invoked when the user taps the centered play button while Smart Seek is paused.
     * The callback is wired to the controller in Stage 4.
     */
    var onResumeRequested: (() -> Unit)? = null

    init {
        isClickable = true
        isFocusable = false
        setWillNotDraw(false)
    }

    /**
     * Retains the current call-site contract so this file can be replaced independently.
     * Position and interval values are intentionally ignored for drawing in Stage 1.
     */
    @Suppress("UNUSED_PARAMETER")
    fun update(
        active: Boolean,
        currentPositionMs: Long,
        nextSeekPositionMs: Long,
        seeking: Boolean,
        markerColor: Int
    ) {
        this.active = active
        this.markerColor = markerColor
        invalidate()
    }

    /**
     * Stage 4 will use this to keep the View available for the play button while paused.
     * In that state, only the icon is drawn; the custom timeline itself is hidden.
     */
    fun setResumeButtonVisible(visible: Boolean) {
        if (resumeButtonVisible == visible) return
        resumeButtonVisible = visible
        resumeTouchActive = false
        updateResumeButtonBounds()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateResumeButtonBounds()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (resumeButtonVisible) {
            drawResumeButton(canvas)
            return
        }

        if (!active) return

        // Stage 1 baseline: one track only. Stage 2 replaces its fill with fixed segments.
        val barHeight = dp(6f)
        val top = (height - barHeight) / 2f
        val bar = RectF(0f, top, width.toFloat(), top + barHeight)

        paint.style = Paint.Style.FILL
        paint.color = markerColor
        paint.alpha = 153
        canvas.drawRoundRect(bar, barHeight / 2f, barHeight / 2f, paint)

        // The fixed cursor marks the left edge; it never advances across the track.
        paint.alpha = 255
        val cursorWidth = dp(2f)
        canvas.drawRoundRect(
            RectF(0f, top - dp(1f), cursorWidth, top + barHeight + dp(1f)),
            dp(1f),
            dp(1f),
            paint
        )
    }

    private fun drawResumeButton(canvas: Canvas) {
        updateResumeButtonBounds()

        val centerX = width / 2f
        val centerY = height / 2f
        val halfHeight = dp(9f)

        resumeIconPath.reset()
        resumeIconPath.moveTo(centerX - dp(5f), centerY - halfHeight)
        resumeIconPath.lineTo(centerX - dp(5f), centerY + halfHeight)
        resumeIconPath.lineTo(centerX + dp(9f), centerY)
        resumeIconPath.close()

        paint.style = Paint.Style.FILL
        paint.color = markerColor
        paint.alpha = 255
        canvas.drawPath(resumeIconPath, paint)
    }

    private fun updateResumeButtonBounds() {
        val touchSize = dp(48f)
        val left = (width - touchSize) / 2f
        val top = (height - touchSize) / 2f
        resumeButtonBounds.set(left, top, left + touchSize, top + touchSize)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Only the centered resume-button target is handled. All other DOWN events
                // return false so the visible standard SeekBar/Slider underneath can receive them.
                resumeTouchActive = resumeButtonVisible &&
                    resumeButtonBounds.contains(event.x, event.y)
                return resumeTouchActive
            }

            MotionEvent.ACTION_MOVE -> {
                return resumeTouchActive
            }

            MotionEvent.ACTION_UP -> {
                if (!resumeTouchActive) return false

                val shouldResume = resumeButtonVisible &&
                    resumeButtonBounds.contains(event.x, event.y)
                resumeTouchActive = false

                if (shouldResume) performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                val wasHandlingTouch = resumeTouchActive
                resumeTouchActive = false
                return wasHandlingTouch
            }

            else -> return resumeTouchActive
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (resumeButtonVisible) onResumeRequested?.invoke()
        return true
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
