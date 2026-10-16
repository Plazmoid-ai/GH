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
 * Stage 2 draws a static preview of four 15-second play intervals alternating with
 * four 30-second skip intervals. Playback position and real Smart Seek state are
 * intentionally not connected until Stage 3.
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
     * Position and interval values are intentionally ignored until Stage 3.
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

        if (!active || width <= 0 || height <= 0) return

        val barHeight = dp(6f)
        val top = (height - barHeight) / 2f
        val bottom = top + barHeight
        val totalDurationMs = TOTAL_PREVIEW_DURATION_MS.toFloat()
        val gap = dp(1f)
        val gapCount = PREVIEW_CYCLE_COUNT * 2 - 1
        val availableWidth = (width.toFloat() - gap * gapCount).coerceAtLeast(0f)
        val pxPerMs = availableWidth / totalDurationMs
        val radius = barHeight / 2f

        var timelinePositionMs = 0L
        var segmentIndex = 0

        repeat(PREVIEW_CYCLE_COUNT) {
            val playWidth = PLAY_DURATION_MS * pxPerMs
            drawSegment(
                canvas,
                timelinePositionMs * pxPerMs + segmentIndex * gap,
                playWidth,
                top,
                bottom,
                radius,
                markerColor,
                220
            )
            timelinePositionMs += PLAY_DURATION_MS
            segmentIndex++

            val skipWidth = SKIP_DURATION_MS * pxPerMs
            drawSegment(
                canvas,
                timelinePositionMs * pxPerMs + segmentIndex * gap,
                skipWidth,
                top,
                bottom,
                radius,
                Color.GRAY,
                190
            )
            timelinePositionMs += SKIP_DURATION_MS
            segmentIndex++
        }

        // The cursor remains fixed at the left edge; the timeline is still a static preview.
        paint.style = Paint.Style.FILL
        paint.color = markerColor
        paint.alpha = 255
        val cursorWidth = dp(2f)
        canvas.drawRoundRect(
            RectF(0f, top - dp(1f), cursorWidth, bottom + dp(1f)),
            dp(1f),
            dp(1f),
            paint
        )
    }

    private fun drawSegment(
        canvas: Canvas,
        left: Float,
        segmentWidth: Float,
        top: Float,
        bottom: Float,
        radius: Float,
        color: Int,
        alpha: Int
    ) {
        if (segmentWidth <= 0f) return
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = alpha
        canvas.drawRoundRect(
            RectF(left, top, left + segmentWidth, bottom),
            radius,
            radius,
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

    private companion object {
        const val PLAY_DURATION_MS = 15_000L
        const val SKIP_DURATION_MS = 30_000L
        const val PREVIEW_CYCLE_COUNT = 4
        const val TOTAL_PREVIEW_DURATION_MS =
            PREVIEW_CYCLE_COUNT * (PLAY_DURATION_MS + SKIP_DURATION_MS)
    }
}
