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
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Canvas-based Smart Seek progress area.
 *
 * The fixed cursor anchors the future-only timeline. Audible intervals use the configured
 * accent color and skipped intervals use the neutral Smart Seek skip color.
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
    private var markerColor = Color.TRANSPARENT
    private var skipColor = Color.TRANSPARENT

    private var resumeButtonVisible = false
    private var resumeTouchActive = false

    /**
     * Invoked when the user taps the centered play button while Smart Seek is paused.
     */
    var onResumeRequested: (() -> Unit)? = null

    private var currentPositionMs = 0L
    private var durationMs = 0L
    private var nextSeekPositionMs = -1L
    private var playDurationMs = 15_000L
    private var skipDurationMs = 30_000L

    init {
        isClickable = true
        isFocusable = false
        setWillNotDraw(false)
    }

    /**
     * Updates the live timeline. The next seek position marks the end of the current
     * audible interval; after it, the timeline alternates play and skip intervals.
     */
    @Suppress("UNUSED_PARAMETER")
    fun update(
        active: Boolean,
        currentPositionMs: Long,
        durationMs: Long,
        nextSeekPositionMs: Long,
        playDurationMs: Long,
        skipDurationMs: Long,
        seeking: Boolean,
        markerColor: Int,
        skipColor: Int = Color.TRANSPARENT
    ) {
        this.active = active
        this.currentPositionMs = currentPositionMs.coerceAtLeast(0L)
        this.durationMs = durationMs.coerceAtLeast(0L)
        this.nextSeekPositionMs = nextSeekPositionMs
        this.playDurationMs = playDurationMs.coerceAtLeast(1L)
        this.skipDurationMs = skipDurationMs.coerceAtLeast(1L)
        this.markerColor = markerColor
        this.skipColor = skipColor
        invalidate()
    }

    /**
     * Keeps the View available for the play button while paused. In that state, only the
     * icon is drawn; the custom timeline itself is hidden.
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

        if (!active || width <= 0 || height <= 0 || durationMs <= currentPositionMs) return

        val barHeight = dp(6f)
        val top = (height - barHeight) / 2f
        val bottom = top + barHeight
        val remainingMs = durationMs - currentPositionMs
        val narrowView = width.toFloat() < dp(100f)
        val cursorX = if (narrowView) 0f else dp(50f)
        val timelineWidth = (width.toFloat() - cursorX).coerceAtLeast(0f)
        if (timelineWidth <= 0f) return

        val pxPerMs = timelineWidth / remainingMs.toFloat()
        val radius = barHeight / 2f
        val gap = dp(1f)
        val playMs = playDurationMs
        val skipMs = skipDurationMs

        var timelinePositionMs = currentPositionMs

        fun drawPlayUntil(endMs: Long) {
            drawTimelineSegment(
                canvas, timelinePositionMs, endMs, currentPositionMs, pxPerMs,
                cursorX, top, bottom, radius, gap, markerColor, 220
            )
            timelinePositionMs = endMs
        }

        fun drawSkipUntil(endMs: Long) {
            drawTimelineSegment(
                canvas, timelinePositionMs, endMs, currentPositionMs, pxPerMs,
                cursorX, top, bottom, radius, gap, skipColor, 190
            )
            timelinePositionMs = endMs
        }

        /*
         * If the service has announced the next seek boundary, use the actual remaining
         * audible interval instead of restarting a full play interval at the current cursor.
         */
        if (nextSeekPositionMs > currentPositionMs) {
            drawPlayUntil(nextSeekPositionMs.coerceAtMost(durationMs))
            if (timelinePositionMs < durationMs) {
                drawSkipUntil((timelinePositionMs + skipMs).coerceAtMost(durationMs))
            }
        } else if (remainingMs < playMs + skipMs) {
            // The final tail is audible only when it is longer than one play interval.
            if (remainingMs > playMs) drawPlayUntil(durationMs)
            drawTrail(canvas, cursorX, top, bottom, narrowView)
            drawCursor(canvas, cursorX, top, bottom)
            return
        } else {
            drawPlayUntil((timelinePositionMs + playMs).coerceAtMost(durationMs))
            if (timelinePositionMs < durationMs) {
                drawSkipUntil((timelinePositionMs + skipMs).coerceAtMost(durationMs))
            }
        }

        // Fill the future timeline with complete play/skip cycles.
        while (durationMs - timelinePositionMs >= playMs + skipMs) {
            drawPlayUntil((timelinePositionMs + playMs).coerceAtMost(durationMs))
            drawSkipUntil((timelinePositionMs + skipMs).coerceAtMost(durationMs))
        }

        // A short final tail is omitted; a longer one remains an audible-colored segment.
        val tailMs = durationMs - timelinePositionMs
        if (tailMs > playMs) {
            drawPlayUntil(durationMs)
        }

        drawTrail(canvas, cursorX, top, bottom, narrowView)
        drawCursor(canvas, cursorX, top, bottom)
    }

    private fun drawTimelineSegment(
        canvas: Canvas,
        startMs: Long,
        endMs: Long,
        currentPositionMs: Long,
        pxPerMs: Float,
        timelineStartX: Float,
        top: Float,
        bottom: Float,
        radius: Float,
        gap: Float,
        color: Int,
        alpha: Int
    ) {
        if (endMs <= startMs) return
        val rawLeft = timelineStartX + (startMs - currentPositionMs).toFloat() * pxPerMs
        val rawRight = timelineStartX + (endMs - currentPositionMs).toFloat() * pxPerMs
        val left = if (startMs == currentPositionMs) rawLeft else rawLeft + gap / 2f
        val right = if (endMs >= durationMs) rawRight else rawRight - gap / 2f
        drawSegment(canvas, left, (right - left).coerceAtLeast(0f), top, bottom, radius, color, alpha)
    }

    private fun drawTrail(
        canvas: Canvas,
        cursorX: Float,
        top: Float,
        bottom: Float,
        narrowView: Boolean
    ) {
        // A narrow View uses a left-edge cursor and deliberately has no trail.
        if (narrowView || cursorX <= 0f || currentPositionMs <= 0L) return

        val trailColor = if (nextSeekPositionMs > currentPositionMs) markerColor else skipColor
        val transparentColor = Color.argb(0, Color.red(trailColor), Color.green(trailColor), Color.blue(trailColor))
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = LinearGradient(
            0f, 0f, cursorX, 0f,
            transparentColor, trailColor, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, top, cursorX, bottom, paint)
        paint.shader = null
    }

    private fun drawCursor(canvas: Canvas, cursorX: Float, top: Float, bottom: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = markerColor
        paint.alpha = 255
        val cursorWidth = dp(3f)
        canvas.drawRoundRect(
            RectF(cursorX, top - dp(1f), cursorX + cursorWidth, bottom + dp(1f)),
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
        paint.shader = null
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
        paint.shader = null
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
