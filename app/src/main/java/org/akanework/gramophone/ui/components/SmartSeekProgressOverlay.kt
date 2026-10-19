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
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import org.akanework.gramophone.logic.utils.CalculationUtils

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
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = sp(12f)
    }
    private val hudCounterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = sp(17f)
    }

    private var active = false
    private var playWhenReady = false
    private var markerColor = Color.TRANSPARENT
    private var skipColor = Color.TRANSPARENT
    private var displayedTrailColor: Int? = null
    private var trailTargetColor: Int? = null
    private var trailColorAnimator: ValueAnimator? = null

    private var currentPositionMs = 0L
    private var durationMs = 0L
    private var timelineRightEdgeX = Float.NaN
    private var cursorPositionXOverride = Float.NaN
    private var nextSeekPositionMs = -1L
    private var playDurationMs = 15_000L
    private var skipDurationMs = 30_000L
    private var hudPlayLabel = "15"
    private var hudSkipLabel = "30"
    private var hudListenedMs = 0L
    private var hudRemainingPeriods = 0
    private var hudAccentColor = Color.rgb(124, 77, 255)
    private var hudOnSurfaceColor = Color.rgb(51, 51, 51)
    private var hudCustomAccentEnabled = true
    private var hudMonochromeAccent = false

    private var isDragging = false
    private var showDraggingHud = false
    private var dragPositionMs: Long? = null
    private var dragStartX = 0f
    private var dragStartPositionMs = 0L
    private var gesturePending = false
    private var dragHoldRunnable: Runnable? = null

    /** Called when a progress-bar gesture commits a seek position. */
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
        skipColor: Int = Color.TRANSPARENT,
        hudPlayLabel: String = "15",
        hudSkipLabel: String = "30",
        hudListenedMs: Long = 0L,
        hudRemainingPeriods: Int = 0,
        hudAccentColor: Int = Color.rgb(124, 77, 255),
        hudOnSurfaceColor: Int = Color.rgb(51, 51, 51),
        hudCustomAccentEnabled: Boolean = true,
        hudMonochromeAccent: Boolean = false
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
        this.hudPlayLabel = hudPlayLabel
        this.hudSkipLabel = hudSkipLabel
        this.hudListenedMs = hudListenedMs.coerceAtLeast(0L)
        this.hudRemainingPeriods = hudRemainingPeriods.coerceAtLeast(0)
        this.hudAccentColor = hudAccentColor
        this.hudOnSurfaceColor = hudOnSurfaceColor
        this.hudCustomAccentEnabled = hudCustomAccentEnabled
        this.hudMonochromeAccent = hudMonochromeAccent

        val desiredTrailColor = if (this.nextSeekPositionMs > this.currentPositionMs) {
            this.markerColor
        } else {
            this.skipColor
        }
        updateTrailColor(desiredTrailColor, active && playWhenReady && !this.hudMonochromeAccent)

        if (!active) {
            isDragging = false
            showDraggingHud = false
            dragPositionMs = null
            dragHoldRunnable?.let { removeCallbacks(it) }
            dragHoldRunnable = null
        }
        postInvalidateOnAnimation()
    }

    /** Sets the right edge shared by the future segments and the static interval label. */
    fun setTimelineRightEdgeX(edgeX: Float) {
        val resolvedEdgeX = if (width > 0 && edgeX.isFinite()) {
            edgeX.coerceIn(0f, width.toFloat())
        } else {
            Float.NaN
        }
        if (timelineRightEdgeX != resolvedEdgeX) {
            timelineRightEdgeX = resolvedEdgeX
            postInvalidateOnAnimation()
        }
    }

    private fun resolvedTimelineRightEdgeX(): Float =
        if (timelineRightEdgeX.isFinite() && timelineRightEdgeX > 0f) {
            timelineRightEdgeX.coerceAtMost(width.toFloat())
        } else {
            width.toFloat()
        }

    /** Pins the visual cursor to a caller-provided x coordinate in this view's local space. */
    fun setCursorX(positionX: Float) {
        val resolvedX = if (width > 0 && positionX.isFinite()) {
            positionX.coerceIn(0f, width.toFloat())
        } else {
            Float.NaN
        }
        if (cursorPositionXOverride != resolvedX) {
            cursorPositionXOverride = resolvedX
            postInvalidateOnAnimation()
        }
    }

    private fun resolvedCursorX(): Float =
        if (cursorPositionXOverride.isFinite() && width > 0) {
            cursorPositionXOverride.coerceIn(0f, width.toFloat())
        } else {
            50f * resources.displayMetrics.density
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
        val barTop = (height - barHeight) / 2f
        val barBottom = barTop + barHeight
        val radius = barHeight / 2f
        val gap = dp(4f)

        val selectedPositionMs = dragPositionMs ?: currentPositionMs
        // Keep the cursor aligned with the right edge of the elapsed-time label.
        val cursorX = resolvedCursorX()
        val timelineEndX = resolvedTimelineRightEdgeX()

        if (isDragging) {
            drawFutureTimeline(canvas, barTop, barBottom, radius, gap, cursorX, selectedPositionMs, timelineEndX)
            drawCursor(canvas, cursorX, barTop, barBottom)
        } else if (playWhenReady) {
            drawFutureTimeline(canvas, barTop, barBottom, radius, gap, cursorX, currentPositionMs, timelineEndX)
            drawCursor(canvas, cursorX, barTop, barBottom)
        } else {
            drawFullTrack(canvas, barTop, barBottom, radius, gap, timelineEndX)
            drawCursor(canvas, cursorX, barTop, barBottom)
        }
        if (isDragging && showDraggingHud) {
            drawDraggingHud(canvas, selectedPositionMs, barTop)
        } else {
            drawHud(canvas, cursorX, barTop, barBottom, timelineEndX)
        }
    }

    /** Draws listened play-time and the remaining interval count, plus a right-aligned static label. */
    private fun drawHud(
        canvas: Canvas,
        cursorX: Float,
        barTop: Float,
        barBottom: Float,
        timelineRightEdgeX: Float
    ) {
        val gray = if (hudMonochromeAccent) hudAccentColor else Color.rgb(158, 158, 158)
        val margin = dp(4f)
        val centerY = (barTop + barBottom) / 2f
        val topBaseline = centerY - dp(17f)
        val bottomBaseline = centerY + dp(32f)
        val spacing = dp(6f)

        hudTextPaint.textSize = sp(12f)
        val timer = CalculationUtils.convertDurationToTimeStamp(hudListenedMs)
        val topParts = listOf(
            Triple(timer, if (hudMonochromeAccent) hudAccentColor else hudOnSurfaceColor, 255),
            Triple(hudRemainingPeriods.toString(), hudAccentColor, 255)
        )
        drawHudLine(canvas, cursorX, topBaseline, hudTextPaint, topParts, spacing, margin)

        hudCounterPaint.textSize = sp(12f)
        val bottomParts = listOf(
            Triple("[", gray, 255),
            Triple(hudSkipLabel, gray, 255),
            Triple(":", gray, 255),
            Triple(hudPlayLabel, hudAccentColor, 255),
            Triple("]", gray, 255)
        )
        drawHudLine(
            canvas, cursorX, bottomBaseline, hudCounterPaint, bottomParts,
            0f, margin, timelineRightEdgeX
        )
    }

    /**
     * Compact capsule above the bar. The time is accumulated play-interval duration,
     * not the raw position within the track.
     */
    private fun drawDraggingHud(
        canvas: Canvas,
        positionMs: Long,
        barTop: Float
    ) {
        val centerX = width / 2f
        // Keep the capsule inside the slider frame instead of letting the title row clip it.
        val cardCenterY = barTop - dp(18f)
        val cycleMs = (playDurationMs + skipDurationMs).coerceAtLeast(1L)
        val totalPeriods = if (durationMs > 0L) durationMs / cycleMs else 0L
        val passedPeriods = (positionMs.coerceAtLeast(0L) / cycleMs)
            .coerceAtMost(totalPeriods)
        val remainingPeriods = (totalPeriods - passedPeriods).coerceAtLeast(0L)
        val timeText = CalculationUtils.convertDurationToTimeStamp(
            calculateListenedPlayTime(positionMs)
        )
        val hudText = "[$timeText]  $remainingPeriods"

        hudTextPaint.textSize = sp(16f)
        val textWidth = hudTextPaint.measureText(hudText)
        val cardWidth = (textWidth + dp(32f))
            .coerceAtMost((width.toFloat() - dp(16f)).coerceAtLeast(0f))
        val card = RectF(
            centerX - cardWidth / 2f,
            cardCenterY - dp(16f),
            centerX + cardWidth / 2f,
            cardCenterY + dp(16f)
        )

        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.rgb(26, 26, 26)
        paint.alpha = 255
        canvas.drawRoundRect(card, dp(20f), dp(20f), paint)

        hudTextPaint.color = Color.WHITE
        hudTextPaint.alpha = 255
        hudTextPaint.textAlign = Paint.Align.CENTER
        val textBaseline = card.centerY() -
            (hudTextPaint.ascent() + hudTextPaint.descent()) / 2f
        canvas.drawText(hudText, centerX, textBaseline, hudTextPaint)
        hudTextPaint.textAlign = Paint.Align.LEFT
        hudTextPaint.textSize = sp(12f)
        hudTextPaint.alpha = 255
    }

    private fun calculateListenedPlayTime(positionMs: Long): Long {
        val playMs = playDurationMs.coerceAtLeast(1L)
        val cycleMs = (playMs + skipDurationMs.coerceAtLeast(1L)).coerceAtLeast(1L)
        val safePositionMs = positionMs.coerceAtLeast(0L)
            .let { if (durationMs > 0L) it.coerceAtMost(durationMs) else it }
        val fullPlayCycles = safePositionMs / cycleMs
        val positionWithinCycleMs = safePositionMs % cycleMs
        return fullPlayCycles * playMs + minOf(positionWithinCycleMs, playMs)
    }

    private fun drawHudLine(
        canvas: Canvas,
        cursorX: Float,
        baseline: Float,
        textPaint: Paint,
        parts: List<Triple<String, Int, Int>>,
        spacing: Float,
        margin: Float,
        rightEdgeX: Float? = null
    ) {
        val textWidth = parts.sumOf { textPaint.measureText(it.first).toDouble() }.toFloat()
        val lineWidth = textWidth + spacing * (parts.size - 1)
        val maxStartX = (width.toFloat() - margin - lineWidth).coerceAtLeast(margin)
        val xStart = if (rightEdgeX != null) {
            (rightEdgeX - lineWidth).coerceIn(margin, maxStartX)
        } else {
            (cursorX - lineWidth / 2f).coerceIn(margin, maxStartX)
        }
        var x = xStart
        parts.forEachIndexed { index, (text, color, alpha) ->
            textPaint.color = color
            textPaint.alpha = alpha
            canvas.drawText(text, x, baseline, textPaint)
            x += textPaint.measureText(text)
            if (index != parts.lastIndex) x += spacing
        }
        textPaint.alpha = 255
    }

    /** Playback mode: the timeline starts at a fixed x-position and represents only the future. */
    private fun drawFutureTimeline(
        canvas: Canvas,
        top: Float,
        bottom: Float,
        radius: Float,
        gap: Float,
        cursorX: Float,
        timelinePositionMs: Long,
        timelineRightEdgeX: Float
    ) {
        // Playback cursor stays fixed; timeline segments extend to the technical-info boundary.
        val cursorWidth = dp(5f)
        val cursorLeftX = cursorX - cursorWidth / 2f
        val cursorRightX = cursorX + cursorWidth / 2f
        val trailEndX = (cursorLeftX - gap).coerceAtLeast(0f)
        val timelineStartX = cursorRightX + gap
        val remainingMs = durationMs - timelinePositionMs
        if (remainingMs <= 0L) {
            drawCursor(canvas, cursorX, top, bottom)
            return
        }

        val timelineWidth = (timelineRightEdgeX - timelineStartX).coerceAtLeast(0f)
        if (timelineWidth <= 0f) return
        val pxPerMs = timelineWidth / remainingMs.toFloat()
        val playMs = playDurationMs
        val skipMs = skipDurationMs
        var currentTimelineMs = timelinePositionMs

        var visualX = timelineStartX
        val minSegmentWidth = dp(MIN_SEGMENT_WIDTH_DP)

        fun drawFutureSegmentUntil(endMs: Long, color: Int, alpha: Int) {
            if (endMs <= currentTimelineMs) return
            val isFirst = currentTimelineMs == timelinePositionMs
            val trimsLeft = if (isFirst) 0f else gap / 2f
            val trimsRight = if (endMs >= durationMs) 0f else gap / 2f
            val calculatedWidth =
                (endMs - currentTimelineMs).toFloat() * pxPerMs - trimsLeft - trimsRight
            val availableWidth = (timelineRightEdgeX - visualX).coerceAtLeast(0f)
            val segmentWidth = if (endMs >= durationMs) {
                availableWidth
            } else {
                calculatedWidth.coerceAtLeast(minSegmentWidth).coerceAtMost(availableWidth)
            }
            if (segmentWidth > 0f && (!hudMonochromeAccent || color != skipColor)) {
                drawSegment(canvas, visualX, segmentWidth, top, bottom, radius, color, alpha)
            }
            currentTimelineMs = endMs
            // Keep the final segment's right edge exactly aligned to the technical-info boundary.
            if (endMs < durationMs) visualX += segmentWidth + gap
            else visualX = timelineRightEdgeX
        }

        fun drawPlayUntil(endMs: Long) =
            drawFutureSegmentUntil(endMs, markerColor, if (hudMonochromeAccent) 255 else 220)

        fun drawSkipUntil(endMs: Long) =
            drawFutureSegmentUntil(endMs, skipColor, 190)

        if (nextSeekPositionMs > timelinePositionMs) {
            drawPlayUntil(nextSeekPositionMs.coerceAtMost(durationMs))
            if (currentTimelineMs < durationMs) {
                drawSkipUntil((currentTimelineMs + skipMs).coerceAtMost(durationMs))
            }
        } else if (remainingMs < playMs + skipMs) {
            // Render a final partial cycle instead of leaving a short tail blank.
            drawPlayUntil((currentTimelineMs + playMs).coerceAtMost(durationMs))
            if (currentTimelineMs < durationMs) drawSkipUntil(durationMs)
            drawPastTimeline(canvas, trailEndX, top, bottom, radius, gap, pxPerMs, timelinePositionMs)
            drawCursor(canvas, cursorX, top, bottom)
            return
        } else {
            drawPlayUntil((currentTimelineMs + playMs).coerceAtMost(durationMs))
            if (currentTimelineMs < durationMs) {
                drawSkipUntil((currentTimelineMs + skipMs).coerceAtMost(durationMs))
            }
        }

        while (durationMs - currentTimelineMs >= playMs + skipMs) {
            // Once the visual timeline has crossed the viewport, more segments are clipped
            // and need not be allocated/drawn on every animation frame.
            if (visualX >= timelineRightEdgeX) break
            drawPlayUntil((currentTimelineMs + playMs).coerceAtMost(durationMs))
            if (visualX >= timelineRightEdgeX) break
            drawSkipUntil((currentTimelineMs + skipMs).coerceAtMost(durationMs))
        }

        // Draw the remaining partial cycle too; omitting a short tail left a visible empty gap.
        if (visualX < timelineRightEdgeX && currentTimelineMs < durationMs) {
            drawPlayUntil((currentTimelineMs + playMs).coerceAtMost(durationMs))
            if (visualX < timelineRightEdgeX && currentTimelineMs < durationMs) {
                drawSkipUntil((currentTimelineMs + skipMs).coerceAtMost(durationMs))
            }
        }

        drawPastTimeline(canvas, trailEndX, top, bottom, radius, gap, pxPerMs, timelinePositionMs)
        drawCursor(canvas, cursorX, top, bottom)
    }

    /** Paused mode: draw all play/skip segments from 0 through the track duration. */
    private fun drawFullTrack(
        canvas: Canvas,
        top: Float,
        bottom: Float,
        radius: Float,
        gap: Float,
        timelineRightEdgeX: Float
    ) {
        val pxPerMs = timelineRightEdgeX / durationMs.toFloat()
        var timelinePositionMs = 0L

        while (timelinePositionMs < durationMs) {
            val playEndMs = (timelinePositionMs + playDurationMs).coerceAtMost(durationMs)
            drawTimelineSegment(
                canvas, timelinePositionMs, playEndMs, 0L, pxPerMs,
                0f, top, bottom, radius, gap, markerColor, if (hudMonochromeAccent) 255 else 220
            )
            timelinePositionMs = playEndMs
            if (timelinePositionMs >= durationMs) break

            val skipEndMs = (timelinePositionMs + skipDurationMs).coerceAtMost(durationMs)
            if (!hudMonochromeAccent) {
                drawTimelineSegment(
                    canvas, timelinePositionMs, skipEndMs, 0L, pxPerMs,
                    0f, top, bottom, radius, gap, skipColor, 190
                )
            }
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

    /** Draw recent elapsed play/skip intervals as solid segments behind the fixed cursor. */
    private fun drawPastTimeline(
        canvas: Canvas,
        trailEndX: Float,
        top: Float,
        bottom: Float,
        radius: Float,
        gap: Float,
        pxPerMs: Float,
        timelinePositionMs: Long
    ) {
        if (trailEndX <= 0f || timelinePositionMs <= 0L || pxPerMs <= 0f) return

        data class PastSegment(val startMs: Long, val endMs: Long, val color: Int, val alpha: Int)
        val playMs = playDurationMs.coerceAtLeast(1L)
        val skipMs = skipDurationMs.coerceAtLeast(1L)
        val cycleMs = (playMs + skipMs).coerceAtLeast(1L)
        // Keep a small, bounded history. Its geometry uses the same px/ms scale as
        // the future timeline; older segments are clipped naturally by the Canvas.
        val maxSegments = 10
        val segmentsNewestFirst = mutableListOf<PastSegment>()
        var cursorMs = timelinePositionMs.coerceAtMost(durationMs)

        while (cursorMs > 0L && segmentsNewestFirst.size < maxSegments) {
            val cycleIndex = (cursorMs - 1L) / cycleMs
            val cycleStart = cycleIndex * cycleMs
            val playEnd = (cycleStart + playMs).coerceAtMost(durationMs)
            val isSkipSegment = cursorMs > playEnd
            val segment = if (isSkipSegment) {
                val start = (cycleStart + playMs).coerceAtMost(cursorMs)
                PastSegment(start, cursorMs, skipColor, 190)
            } else {
                PastSegment(cycleStart, cursorMs.coerceAtMost(playEnd), markerColor, if (hudMonochromeAccent) 255 else 220)
            }
            if (segment.endMs <= segment.startMs) break
            if (!hudMonochromeAccent || !isSkipSegment) {
                segmentsNewestFirst += segment
            }
            cursorMs = segment.startMs
        }

        val segments = segmentsNewestFirst.asReversed()
        if (segments.isEmpty()) return

        segments.forEachIndexed { index, segment ->
            // Anchor the newest segment to the end of the trail. A long segment may
            // begin left of x=0; do not compress it to fit the 50dp trail.
            val left = trailEndX - (timelinePositionMs - segment.startMs).toFloat() * pxPerMs
            val right = trailEndX - (timelinePositionMs - segment.endMs).toFloat() * pxPerMs
            val segmentWidth = right - left
            if (segmentWidth <= 0f) return@forEachIndexed

            val gapAtBoundary = minOf(gap / 2f, segmentWidth * 0.15f)
            val trimmedLeft = if (index == 0) left else left + gapAtBoundary
            val trimmedRight = if (index == segments.lastIndex) right else right - gapAtBoundary
            val trimmedSegmentWidth = (trimmedRight - trimmedLeft).coerceAtLeast(0f)
            drawSegment(
                canvas, trimmedLeft, trimmedSegmentWidth, top, bottom, radius,
                segment.color, if (hudMonochromeAccent) 255 else segment.alpha
            )
        }
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
        paint.color = if (hudMonochromeAccent) {
            markerColor
        } else {
            MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOnSurface
            )
        }
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

    /** Jump to the start of the adjacent play interval without entering drag-HUD mode. */
    fun seekAdjacentPlaySegment(direction: Int): Boolean {
        if (!active || durationMs <= 0L || direction == 0) return false
        val cycleMs = (playDurationMs + skipDurationMs).coerceAtLeast(1L)
        val position = currentPositionMs.coerceIn(0L, durationMs)
        val cycleIndex = position / cycleMs
        val target = if (direction > 0) {
            (cycleIndex + 1L) * cycleMs
        } else {
            val startOfCurrentCycle = cycleIndex * cycleMs
            if (position == startOfCurrentCycle) {
                (cycleIndex - 1L) * cycleMs
            } else {
                startOfCurrentCycle
            }
        }
        if (target < 0L || target >= durationMs) return false
        currentPositionMs = target
        postInvalidateOnAnimation()
        onSeekRequested?.invoke(target)
        return true
    }

    /** Start/update/finish a long gesture originating on the Play/Pause button. */
    fun beginGestureDrag(positionMs: Long) {
        if (!active || durationMs <= 0L) return
        isDragging = true
        showDraggingHud = true
        dragPositionMs = positionMs.coerceIn(0L, durationMs)
        parent?.requestDisallowInterceptTouchEvent(true)
        postInvalidateOnAnimation()
    }

    fun updateGestureDrag(positionMs: Long) {
        if (!isDragging) return
        dragPositionMs = positionMs.coerceIn(0L, durationMs)
        postInvalidateOnAnimation()
    }

    fun finishGestureDrag(commit: Boolean) {
        if (!isDragging) return
        val target = dragPositionMs?.coerceIn(0L, durationMs) ?: currentPositionMs
        isDragging = false
        showDraggingHud = false
        dragPositionMs = null
        parent?.requestDisallowInterceptTouchEvent(false)
        if (commit) {
            currentPositionMs = target
            onSeekRequested?.invoke(target)
        }
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!active || durationMs <= 0L || width <= 0) {
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                dragHoldRunnable?.let { removeCallbacks(it) }
                dragHoldRunnable = null
                gesturePending = false
                isDragging = false
                showDraggingHud = false
                dragPositionMs = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (kotlin.math.abs(event.y - height / 2f) > dp(24f)) return false
                dragStartX = event.x
                dragStartPositionMs = positionFromX(event.x)
                dragPositionMs = dragStartPositionMs
                gesturePending = true
                isDragging = false
                showDraggingHud = false
                dragHoldRunnable?.let { removeCallbacks(it) }
                dragHoldRunnable = Runnable {
                    if (gesturePending && active) {
                        gesturePending = false
                        isDragging = true
                        showDraggingHud = true
                        dragPositionMs = dragStartPositionMs
                        parent?.requestDisallowInterceptTouchEvent(true)
                        postInvalidateOnAnimation()
                    }
                }.also { postDelayed(it, 500L) }
                parent?.requestDisallowInterceptTouchEvent(true)
                postInvalidateOnAnimation()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - dragStartX
                if (gesturePending && kotlin.math.abs(deltaX) > dp(60f)) {
                    gesturePending = false
                    isDragging = true
                    showDraggingHud = true
                    dragPositionMs = dragStartPositionMs
                }
                if (!isDragging) return gesturePending
                dragPositionMs = positionFromX(event.x)
                postInvalidateOnAnimation()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val deltaX = event.x - dragStartX
                dragHoldRunnable?.let { removeCallbacks(it) }
                dragHoldRunnable = null
                val wasDragging = isDragging
                val wasPending = gesturePending
                gesturePending = false

                if (wasDragging) {
                    val target = positionFromX(event.x)
                    isDragging = false
                    showDraggingHud = false
                    dragPositionMs = null
                    currentPositionMs = target
                    parent?.requestDisallowInterceptTouchEvent(false)
                    postInvalidateOnAnimation()
                    onSeekRequested?.invoke(target)
                    return true
                }

                isDragging = false
                showDraggingHud = false
                dragPositionMs = null
                parent?.requestDisallowInterceptTouchEvent(false)
                when {
                    wasPending && kotlin.math.abs(deltaX) >= dp(20f) &&
                        kotlin.math.abs(deltaX) <= dp(60f) -> {
                        seekAdjacentPlaySegment(if (deltaX > 0f) 1 else -1)
                    }
                    wasPending && kotlin.math.abs(deltaX) > dp(60f) -> {
                        val target = positionFromX(event.x)
                        currentPositionMs = target
                        onSeekRequested?.invoke(target)
                    }
                    wasPending -> {
                        val target = nearestPlaySegmentStart(positionFromX(event.x))
                        currentPositionMs = target
                        onSeekRequested?.invoke(target)
                    }
                }
                postInvalidateOnAnimation()
                return wasPending
            }

            MotionEvent.ACTION_CANCEL -> {
                dragHoldRunnable?.let { removeCallbacks(it) }
                dragHoldRunnable = null
                gesturePending = false
                isDragging = false
                showDraggingHud = false
                dragPositionMs = null
                parent?.requestDisallowInterceptTouchEvent(false)
                postInvalidateOnAnimation()
                return true
            }

            else -> return gesturePending || isDragging
        }
    }

    private fun nearestPlaySegmentStart(positionMs: Long): Long {
        val position = positionMs.coerceIn(0L, durationMs)
        val cycleMs = (playDurationMs + skipDurationMs).coerceAtLeast(1L)
        val cycleStart = (position / cycleMs) * cycleMs
        val nextStart = (cycleStart + cycleMs).coerceAtMost(durationMs)
        return if (position - cycleStart <= nextStart - position) cycleStart else nextStart
    }

    private fun positionFromX(x: Float): Long {
        if (width <= 0) return 0L
        return (x.coerceIn(0f, width.toFloat()) / width.toFloat() * durationMs.toFloat())
            .toLong()
            .coerceIn(0L, durationMs)
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    private fun sp(value: Float): Float =
        value * resources.displayMetrics.scaledDensity

    private companion object {
        const val MIN_SEGMENT_WIDTH_DP = 6f
    }
}
