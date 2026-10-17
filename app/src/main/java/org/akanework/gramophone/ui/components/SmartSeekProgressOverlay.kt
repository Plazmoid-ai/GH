/*
 *     Copyright (C) 2026 Plazmoid-ai
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3
 *     of the License, or (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors

/**
 * Smart Seek progress renderer.
 *
 * During playback it shows the future-only timeline with a fixed cursor.
 * While paused it shows the entire track and lets the user seek to the closest play interval.
 */
class SmartSeekProgressOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var active = false
    private var playWhenReady = false
    private var markerColor = Color.TRANSPARENT
    private var skipColor = Color.TRANSPARENT
    private var displayedTrailColor: Int? = null
    private var trailTargetColor: Int? = null
    private var trailColorAnimator: ValueAnimator? = null

    private var currentPositionMs = 0L
    private var durationMs = 0L
    private var nextSeekPositionMs = -1L
    private var playDurationMs = 15_000L
    private var skipDurationMs = 30_000L

    private var isDragging = false
    private var dragPositionMs: Long? = null

    /** Called when a paused-track drag/tap is released at the snapped play-segment start. */
    var onSeekRequested: ((Long) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = false
        setWillNotDraw(false)
    }

    fun update(
        active: Boolean,
        currentPositionMs: Long,
        durationMs: Long,
        nextSeekPositionMs: Long,
        playDurationMs: Long,
        skipDurationMs: Long,
        playWhenReady: Boolean,
        markerColor: Int,
        skipColor: Int = Color.TRANSPARENT
    ) {
        this.active = active
        this.playWhenReady = playWhenReady
        this.currentPositionMs = currentPositionMs.coerceAtLeast(0L)
        this.durationMs = durationMs.coerceAtLeast(0L)
        this.nextSeekPositionMs = nextSeekPositionMs
        this.playDurationMs = playDurationMs.coerceAtLeast(1L)
        this.skipDurationMs = skipDurationMs.coerceAtLeast(1L)
        this.markerColor = markerColor
        this.skipColor = skipColor

        val desiredTrailColor = if (this.nextSeekPositionMs > this.currentPositionMs) {
            this.markerColor
        } else {
            this.skipColor
        }
        updateTrailColor(desiredTrailColor, active && playWhenReady)

        if (!active || playWhenReady) {
            isDragging = false
            dragPositionMs = null
        }
        postInvalidateOnAnimation()
    }

    /**
     * Smoothly blends the trail color when the current interval changes from play to skip
     * or back. Updates received during the same interval do not restart the animator.
     */
    private fun updateTrailColor(targetColor: Int, animate: Boolean) {
        if (trailTargetColor == null || !animate) {
            trailColorAnimator?.cancel()
            trailColorAnimator = null
            trailTargetColor = targetColor
            displayedTrailColor = targetColor
            return
        }
        if (trailTargetColor == targetColor) return

        val startColor = displayedTrailColor ?: trailTargetColor ?: targetColor
        trailTargetColor = targetColor
        trailColorAnimator?.cancel()
        trailColorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), startColor, targetColor).apply {
            duration = 150L
            addUpdateListener { animator ->
                displayedTrailColor = animator.animatedValue as Int
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active || width <= 0 || height <= 0 || durationMs <= 0L) return

        val barHeight = dp(6f)
        val top = (height - barHeight) / 2f
        val bottom = top + barHeight
        val radius = barHeight / 2f
        val gap = dp(4f)

        if (playWhenReady) {
            drawFutureTimeline(canvas, top, bottom, radius, gap)
        } else {
            drawFullTrack(canvas, top, bottom, radius, gap)
            val selectedPosition = dragPositionMs ?: currentPositionMs
            val cursorX = (selectedPosition.toFloat() / durationMs.toFloat() * width).coerceIn(0f, width.toFloat())
            drawCursor(canvas, cursorX, top, bottom)
        }
    }

    /** Playback mode: the timeline starts at a fixed x-position and represents only the future. */
    private fun drawFutureTimeline(
        canvas: Canvas,
        top: Float,
        bottom: Float,
        radius: Float,
        gap: Float
    ) {
        val narrowView = width.toFloat() < dp(100f)
        // In normal layouts the cursor sits between separate left/right timeline shapes.
        // For very narrow layouts preserve the left-edge cursor and full-width timeline.
        val cursorX = if (narrowView) 0f else dp(50f)
        val cursorWidth = dp(5f)
        val trailEndX = (cursorX - cursorWidth / 2f - gap).coerceAtLeast(0f)
        val timelineStartX = if (narrowView) 0f else cursorX + cursorWidth / 2f + gap
        val remainingMs = durationMs - currentPositionMs
        if (remainingMs <= 0L) {
            drawCursor(canvas, cursorX, top, bottom, leftEdge = narrowView)
            return
        }

        val timelineWidth = (width.toFloat() - timelineStartX).coerceAtLeast(0f)
        if (timelineWidth <= 0f) return
        val pxPerMs = timelineWidth / remainingMs.toFloat()
        val playMs = playDurationMs
        val skipMs = skipDurationMs
        var timelinePositionMs = currentPositionMs

        fun drawPlayUntil(endMs: Long) {
            drawTimelineSegment(
                canvas, timelinePositionMs, endMs, currentPositionMs, pxPerMs,
                timelineStartX, top, bottom, radius, gap, markerColor, 220
            )
            timelinePositionMs = endMs
        }

        fun drawSkipUntil(endMs: Long) {
            drawTimelineSegment(
                canvas, timelinePositionMs, endMs, currentPositionMs, pxPerMs,
                timelineStartX, top, bottom, radius, gap, skipColor, 190
            )
            timelinePositionMs = endMs
        }

        if (nextSeekPositionMs > currentPositionMs) {
            drawPlayUntil(nextSeekPositionMs.coerceAtMost(durationMs))
            if (timelinePositionMs < durationMs) {
                drawSkipUntil((timelinePositionMs + skipMs).coerceAtMost(durationMs))
            }
        } else if (remainingMs < playMs + skipMs) {
            if (remainingMs > playMs) drawPlayUntil(durationMs)
            drawTrail(canvas, trailEndX, top, bottom, narrowView)
            drawCursor(canvas, cursorX, top, bottom, leftEdge = narrowView)
            return
        } else {
            drawPlayUntil((timelinePositionMs + playMs).coerceAtMost(durationMs))
            if (timelinePositionMs < durationMs) {
                drawSkipUntil((timelinePositionMs + skipMs).coerceAtMost(durationMs))
            }
        }

        while (durationMs - timelinePositionMs >= playMs + skipMs) {
            drawPlayUntil((timelinePositionMs + playMs).coerceAtMost(durationMs))
            drawSkipUntil((timelinePositionMs + skipMs).coerceAtMost(durationMs))
        }

        val tailMs = durationMs - timelinePositionMs
        if (tailMs > playMs) drawPlayUntil(durationMs)

        drawTrail(canvas, trailEndX, top, bottom, narrowView)
        drawCursor(canvas, cursorX, top, bottom, leftEdge = narrowView)
    }

    /** Paused mode: draw all play/skip segments from 0 through the track duration. */
    private fun drawFullTrack(
        canvas: Canvas,
        top: Float,
        bottom: Float,
        radius: Float,
        gap: Float
    ) {
        val pxPerMs = width.toFloat() / durationMs.toFloat()
        var timelinePositionMs = 0L

        while (timelinePositionMs < durationMs) {
            val playEndMs = (timelinePositionMs + playDurationMs).coerceAtMost(durationMs)
            drawTimelineSegment(
                canvas, timelinePositionMs, playEndMs, 0L, pxPerMs,
                0f, top, bottom, radius, gap, markerColor, 220
            )
            timelinePositionMs = playEndMs
            if (timelinePositionMs >= durationMs) break

            val skipEndMs = (timelinePositionMs + skipDurationMs).coerceAtMost(durationMs)
            drawTimelineSegment(
                canvas, timelinePositionMs, skipEndMs, 0L, pxPerMs,
                0f, top, bottom, radius, gap, skipColor, 190
            )
            timelinePositionMs = skipEndMs
        }
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
        trailEndX: Float,
        top: Float,
        bottom: Float,
        narrowView: Boolean
    ) {
        if (narrowView || trailEndX <= 0f || currentPositionMs <= 0L) return

        val trailColor = displayedTrailColor
            ?: if (nextSeekPositionMs > currentPositionMs) markerColor else skipColor
        val transparentColor = Color.argb(
            0,
            Color.red(trailColor),
            Color.green(trailColor),
            Color.blue(trailColor)
        )
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = LinearGradient(
            0f, 0f, trailEndX, 0f,
            transparentColor, trailColor, Shader.TileMode.CLAMP
        )
        // Rounded geometry gives the trail a soft right cap; the cursor gap follows it.
        canvas.drawRoundRect(
            RectF(0f, top, trailEndX, bottom),
            (bottom - top) / 2f,
            (bottom - top) / 2f,
            paint
        )
        paint.shader = null
    }

    private fun drawCursor(
        canvas: Canvas,
        cursorX: Float,
        top: Float,
        bottom: Float,
        leftEdge: Boolean = false
    ) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurface
        )
        paint.alpha = 255

        val cursorWidth = dp(5f)
        val cursorExtension = dp(5f)
        val rawLeft = if (leftEdge) 0f else cursorX - cursorWidth / 2f
        val left = rawLeft.coerceIn(0f, (width.toFloat() - cursorWidth).coerceAtLeast(0f))
        val right = left + cursorWidth
        canvas.drawRoundRect(
            RectF(left, top - cursorExtension, right, bottom + cursorExtension),
            cursorWidth / 2f,
            cursorWidth / 2f,
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // In playback mode, deliberately return false so the overlay does not intercept touches.
        if (!active || playWhenReady || durationMs <= 0L || width <= 0) {
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                isDragging = false
                dragPositionMs = null
            }
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                dragPositionMs = positionFromX(event.x)
                parent?.requestDisallowInterceptTouchEvent(true)
                postInvalidateOnAnimation()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                dragPositionMs = positionFromX(event.x)
                postInvalidateOnAnimation()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) return false
                val newPositionMs = positionFromX(event.x)
                val snappedPositionMs = nearestPlaySegmentStart(newPositionMs)
                isDragging = false
                dragPositionMs = snappedPositionMs
                currentPositionMs = snappedPositionMs
                parent?.requestDisallowInterceptTouchEvent(false)
                postInvalidateOnAnimation()
                onSeekRequested?.invoke(snappedPositionMs)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return false
                isDragging = false
                dragPositionMs = null
                parent?.requestDisallowInterceptTouchEvent(false)
                postInvalidateOnAnimation()
                return true
            }

            else -> return isDragging
        }
    }

    private fun positionFromX(x: Float): Long {
        if (width <= 0) return 0L
        return (x.coerceIn(0f, width.toFloat()) / width.toFloat() * durationMs.toFloat())
            .toLong()
            .coerceIn(0L, durationMs)
    }

    /** Finds the closest cycle origin, i.e. the start of a play segment. */
    private fun nearestPlaySegmentStart(positionMs: Long): Long {
        if (durationMs <= 0L) return 0L
        val maxStart = (durationMs - 1L).coerceAtLeast(0L)
        val safePosition = positionMs.coerceIn(0L, maxStart)
        val cycleDurationMs = (playDurationMs + skipDurationMs).coerceAtLeast(1L)
        val previousStart = (safePosition / cycleDurationMs) * cycleDurationMs
        val nextStart = previousStart + cycleDurationMs

        if (nextStart <= maxStart &&
            nextStart - safePosition < safePosition - previousStart
        ) {
            return nextStart
        }
        return previousStart.coerceAtMost(maxStart)
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
