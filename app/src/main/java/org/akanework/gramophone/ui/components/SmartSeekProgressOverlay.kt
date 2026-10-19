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
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
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
    private var nextSeekPositionMs = -1L
    private var playDurationMs = 15_000L
    private var skipDurationMs = 30_000L
    private var hudPlayLabel = "15"
    private var hudSkipLabel = "30"
    private var hudRemainingMs = 15_000L
    private var hudPassedPeriods = 0
    private var hudRemainingPeriods = 0
    private var hudAccentColor = Color.rgb(124, 77, 255)
    private var hudPassedColor = Color.rgb(166, 139, 255)
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
        hudRemainingMs: Long = 15_000L,
        hudPassedPeriods: Int = 0,
        hudRemainingPeriods: Int = 0,
        hudAccentColor: Int = Color.rgb(124, 77, 255),
        hudPassedColor: Int = Color.rgb(166, 139, 255),
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
        this.hudRemainingMs = hudRemainingMs.coerceAtLeast(0L)
        this.hudPassedPeriods = hudPassedPeriods.coerceAtLeast(0)
        this.hudRemainingPeriods = hudRemainingPeriods.coerceAtLeast(0)
        this.hudAccentColor = hudAccentColor
        this.hudPassedColor = hudPassedColor
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

        val narrowView = width.toFloat() < dp(100f)
        val selectedPositionMs = dragPositionMs ?: currentPositionMs
        val rawCursorX = if (isDragging) {
            (selectedPositionMs.toFloat() / durationMs.toFloat() * width).coerceIn(0f, width.toFloat())
        } else if (playWhenReady) {
            if (narrowView) 0f else dp(50f)
        } else {
            (currentPositionMs.toFloat() / durationMs.toFloat() * width).coerceIn(0f, width.toFloat())
        }
        // Match the visual center of the 5dp cursor even at the track's extreme edges.
        val halfCursorWidth = dp(2.5f)
        val cursorX = rawCursorX.coerceIn(
            halfCursorWidth,
            (width.toFloat() - halfCursorWidth).coerceAtLeast(halfCursorWidth)
        )

        if (isDragging) {
            // While scrubbing, show the full segmented timeline and move the cursor with the finger.
            drawFullTrack(canvas, top, bottom, radius, gap)
            drawCursor(canvas, cursorX, top, bottom)
        } else if (playWhenReady) {
            drawFutureTimeline(canvas, top, bottom, radius, gap)
            drawHorizon(canvas)
            drawCursor(canvas, cursorX, top, bottom, leftEdge = narrowView)
        } else {
            drawFullTrack(canvas, top, bottom, radius, gap)
            drawCursor(canvas, cursorX, top, bottom)
        }
        if (isDragging && showDraggingHud) {
            drawDraggingHud(canvas, cursorX, selectedPositionMs, top, bottom)
        } else {
            drawHud(canvas, cursorX, top, bottom)
        }
    }

    /**
     * Draws the two-line HUD centered around the cursor. Every fragment has its own color:
     * gray delimiters, a subdued left label, a readable timer, and a bright right accent.
     */
    private fun drawHud(canvas: Canvas, cursorX: Float, barTop: Float, barBottom: Float) {
        val gray = if (hudMonochromeAccent) hudAccentColor else Color.rgb(158, 158, 158)
        val margin = dp(4f)
        val centerY = (barTop + barBottom) / 2f
        val topBaseline = centerY - dp(17f)
        val bottomBaseline = centerY + dp(32f)
        val spacing = dp(2.5f)

        hudTextPaint.textSize = sp(12f)
        val timer = CalculationUtils.convertDurationToTimeStamp(hudRemainingMs)
        // Keep the left counter softer than the right counter without losing contrast.
        val subduedAlpha = if (hudMonochromeAccent) 255 else 220
        val topParts = listOf(
            Triple("<", gray, 255),
            Triple(hudPassedPeriods.toString(), hudPassedColor, subduedAlpha),
            Triple("[", gray, 255),
            Triple(timer, if (hudMonochromeAccent) hudAccentColor else hudOnSurfaceColor, 255),
            Triple("]", gray, 255),
            Triple(hudRemainingPeriods.toString(), hudAccentColor, 255),
            Triple("<", gray, 255)
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
        drawHudLine(canvas, cursorX, bottomBaseline, hudCounterPaint, bottomParts, 0f, margin)
    }

    /**
     * Elevated one-line HUD shown only for a long drag or a long press on the cursor.
     * The card sits above the finger and points down toward the active cursor.
     */
    private fun drawDraggingHud(
        canvas: Canvas,
        cursorX: Float,
        positionMs: Long,
        barTop: Float,
        barBottom: Float
    ) {
        val margin = dp(4f)
        val spacing = dp(3f)
        val textSize = sp(16f)
        hudTextPaint.textSize = textSize
        hudTextPaint.alpha = 255

        val cycleMs = (playDurationMs + skipDurationMs).coerceAtLeast(1L)
        val totalPeriods = if (durationMs > 0L) durationMs / cycleMs else 0L
        val passedPeriods = (positionMs.coerceAtLeast(0L) / cycleMs)
            .coerceAtMost(totalPeriods)
        val remainingPeriods = (totalPeriods - passedPeriods).coerceAtLeast(0L)
        val timeText = CalculationUtils.convertDurationToTimeStamp(
            positionMs.coerceIn(0L, durationMs)
        )
        val neutralColor = Color.WHITE
        val passedColor = if (hudMonochromeAccent) Color.WHITE else hudPassedColor
        val futureColor = if (hudMonochromeAccent) Color.WHITE else hudAccentColor
        val parts = listOf(
            Triple("< ", neutralColor, 255),
            Triple("[$passedPeriods]", passedColor, 255),
            Triple("  $timeText  ", neutralColor, 255),
            Triple("[$remainingPeriods]", futureColor, 255),
            Triple(" <", neutralColor, 255)
        )

        val textWidth = parts.sumOf { hudTextPaint.measureText(it.first).toDouble() }.toFloat()
        val lineWidth = textWidth + spacing * (parts.size - 1)
        val paddingX = dp(9f)
        val paddingY = dp(6f)
        val rectWidth = lineWidth + paddingX * 2f
        val left = (cursorX - rectWidth / 2f).coerceIn(
            margin,
            (width.toFloat() - margin - rectWidth).coerceAtLeast(margin)
        )
        val baseline = (barTop + barBottom) / 2f - dp(60f)
        val fontMetrics = hudTextPaint.fontMetrics
        val card = RectF(
            left,
            (baseline + fontMetrics.ascent - paddingY).coerceAtLeast(0f),
            left + rectWidth,
            baseline + fontMetrics.descent + paddingY
        )

        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.alpha = 255
        paint.color = if (hudMonochromeAccent) Color.BLACK else Color.rgb(26, 26, 26)
        val pointer = Path().apply {
            moveTo((cursorX - dp(10f)).coerceIn(left + dp(8f), left + rectWidth - dp(8f)), card.bottom - dp(1f))
            lineTo(cursorX, (barTop - dp(8f)).coerceAtLeast(card.bottom))
            lineTo((cursorX + dp(10f)).coerceIn(left + dp(8f), left + rectWidth - dp(8f)), card.bottom - dp(1f))
            close()
        }
        canvas.drawPath(pointer, paint)
        canvas.drawRoundRect(card, dp(10f), dp(10f), paint)

        var x = left + paddingX
        parts.forEachIndexed { index, (text, color, alpha) ->
            hudTextPaint.color = color
            hudTextPaint.alpha = 255
            canvas.drawText(text, x, baseline, hudTextPaint)
            x += hudTextPaint.measureText(text)
            if (index != parts.lastIndex) x += spacing
        }
        hudTextPaint.alpha = 255
        hudTextPaint.textSize = sp(12f)
    }

    private fun drawHudLine(
        canvas: Canvas,
        cursorX: Float,
        baseline: Float,
        textPaint: Paint,
        parts: List<Triple<String, Int, Int>>,
        spacing: Float,
        margin: Float
    ) {
        val textWidth = parts.sumOf { textPaint.measureText(it.first).toDouble() }.toFloat()
        val lineWidth = textWidth + spacing * (parts.size - 1)
        val xStart = (cursorX - lineWidth / 2f).coerceIn(
            margin,
            (width.toFloat() - margin - lineWidth).coerceAtLeast(margin)
        )
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
        gap: Float
    ) {
        val narrowView = width.toFloat() < dp(100f)
        // The cursor center is fixed at 50dp. Its left and right gaps are identical:
        // 4dp from the rounded trail cap to the cursor, and 4dp from cursor to timeline.
        // For very narrow layouts preserve the left-edge cursor and full-width timeline.
        val cursorX = if (narrowView) 0f else dp(50f)
        val cursorWidth = dp(5f)
        val cursorLeftX = cursorX - cursorWidth / 2f
        val cursorRightX = cursorX + cursorWidth / 2f
        val trailEndX = (cursorLeftX - gap).coerceAtLeast(0f)
        val timelineStartX = if (narrowView) 0f else cursorRightX + gap
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

        var visualX = timelineStartX
        val minSegmentWidth = dp(MIN_SEGMENT_WIDTH_DP)

        fun drawFutureSegmentUntil(endMs: Long, color: Int, alpha: Int) {
            if (endMs <= timelinePositionMs) return
            val isFirst = timelinePositionMs == currentPositionMs
            val trimsLeft = if (isFirst) 0f else gap / 2f
            val trimsRight = if (endMs >= durationMs) 0f else gap / 2f
            val calculatedWidth =
                (endMs - timelinePositionMs).toFloat() * pxPerMs - trimsLeft - trimsRight
            val segmentWidth = calculatedWidth.coerceAtLeast(minSegmentWidth)
            if (!hudMonochromeAccent || color != skipColor) {
                drawSegment(canvas, visualX, segmentWidth, top, bottom, radius, color, alpha)
            }
            timelinePositionMs = endMs
            // Each drawn block advances the visual timeline; minimum-width blocks do not overlap.
            if (endMs < durationMs) visualX += segmentWidth + gap
        }

        fun drawPlayUntil(endMs: Long) =
            drawFutureSegmentUntil(endMs, markerColor, if (hudMonochromeAccent) 255 else 220)

        fun drawSkipUntil(endMs: Long) =
            drawFutureSegmentUntil(endMs, skipColor, 190)

        if (nextSeekPositionMs > currentPositionMs) {
            drawPlayUntil(nextSeekPositionMs.coerceAtMost(durationMs))
            if (timelinePositionMs < durationMs) {
                drawSkipUntil((timelinePositionMs + skipMs).coerceAtMost(durationMs))
            }
        } else if (remainingMs < playMs + skipMs) {
            if (remainingMs > playMs) drawPlayUntil(durationMs)
            drawPastTimeline(canvas, trailEndX, top, bottom, radius, gap, pxPerMs, narrowView)
            drawCursor(canvas, cursorX, top, bottom, leftEdge = narrowView)
            return
        } else {
            drawPlayUntil((timelinePositionMs + playMs).coerceAtMost(durationMs))
            if (timelinePositionMs < durationMs) {
                drawSkipUntil((timelinePositionMs + skipMs).coerceAtMost(durationMs))
            }
        }

        while (durationMs - timelinePositionMs >= playMs + skipMs) {
            // Once the visual timeline has crossed the viewport, more segments are clipped
            // and need not be allocated/drawn on every animation frame.
            if (visualX >= width.toFloat()) break
            drawPlayUntil((timelinePositionMs + playMs).coerceAtMost(durationMs))
            if (visualX >= width.toFloat()) break
            drawSkipUntil((timelinePositionMs + skipMs).coerceAtMost(durationMs))
        }

        val tailMs = durationMs - timelinePositionMs
        if (visualX < width.toFloat() && tailMs > playMs) drawPlayUntil(durationMs)

        drawPastTimeline(canvas, trailEndX, top, bottom, radius, gap, pxPerMs, narrowView)
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

    /**
     * Softly dissolves the far-right timeline into the player's surface color.
     * The background overlay fades from transparent to opaque over the final 24dp,
     * causing the timeline underneath to fade from fully visible to fully hidden.
     */
    private fun drawHorizon(canvas: Canvas) {
        if (width <= 0) return
        val horizonWidth = dp(HORIZON_WIDTH_DP).coerceAtMost(width.toFloat())
        if (horizonWidth <= 0f) return

        val startX = width.toFloat() - horizonWidth
        if (hudMonochromeAccent) {
            val backgroundColor = if (markerColor == Color.WHITE) Color.BLACK else Color.WHITE
            paint.style = Paint.Style.FILL
            paint.alpha = 255
            paint.shader = null
            paint.color = backgroundColor
            canvas.drawRect(startX, 0f, width.toFloat(), height.toFloat(), paint)
            return
        }
        val backgroundColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurface
        )
        val transparentBackgroundColor = Color.argb(
            0,
            Color.red(backgroundColor),
            Color.green(backgroundColor),
            Color.blue(backgroundColor)
        )
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = LinearGradient(
            startX, 0f, width.toFloat(), 0f,
            transparentBackgroundColor, backgroundColor, Shader.TileMode.CLAMP
        )
        canvas.drawRect(startX, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    /**
     * Draw recent elapsed play/skip intervals as separate segments. Each segment has
     * its own left-to-right fade; no continuous gradient is painted behind the cursor.
     */
    private fun drawPastTimeline(
        canvas: Canvas,
        trailEndX: Float,
        top: Float,
        bottom: Float,
        radius: Float,
        gap: Float,
        pxPerMs: Float,
        narrowView: Boolean
    ) {
        if (narrowView || trailEndX <= 0f || currentPositionMs <= 0L || pxPerMs <= 0f) return

        data class PastSegment(val startMs: Long, val endMs: Long, val color: Int, val alpha: Int)
        val playMs = playDurationMs.coerceAtLeast(1L)
        val skipMs = skipDurationMs.coerceAtLeast(1L)
        val cycleMs = (playMs + skipMs).coerceAtLeast(1L)
        // Keep a small, bounded history. Its geometry uses the same px/ms scale as
        // the future timeline; older segments are clipped naturally by the Canvas.
        val maxSegments = 10
        val segmentsNewestFirst = mutableListOf<PastSegment>()
        var cursorMs = currentPositionMs.coerceAtMost(durationMs)

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
            val left = trailEndX - (currentPositionMs - segment.startMs).toFloat() * pxPerMs
            val right = trailEndX - (currentPositionMs - segment.endMs).toFloat() * pxPerMs
            val segmentWidth = right - left
            if (segmentWidth <= 0f) return@forEachIndexed

            val gapAtBoundary = minOf(gap / 2f, segmentWidth * 0.15f)
            val trimmedLeft = if (index == 0) left else left + gapAtBoundary
            val trimmedRight = if (index == segments.lastIndex) right else right - gapAtBoundary
            val trimmedSegmentWidth = (trimmedRight - trimmedLeft).coerceAtLeast(0f)
            if (hudMonochromeAccent) {
                drawSegment(canvas, trimmedLeft, trimmedSegmentWidth, top, bottom, radius, segment.color, 255)
            } else {
                drawFadedSegment(
                    canvas,
                    trimmedLeft,
                    trimmedSegmentWidth,
                    top, bottom, radius, segment.color, segment.alpha
                )
            }
        }
    }

    private fun drawFadedSegment(
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
        val transparentColor = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
        val opaqueColor = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        val fadeWidth = dp(EDGE_FADE_WIDTH_DP).coerceAtMost(segmentWidth)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = LinearGradient(
            left, 0f, left + fadeWidth, 0f,
            transparentColor, opaqueColor, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            RectF(left, top, left + segmentWidth, bottom),
            radius,
            radius,
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
        const val EDGE_FADE_WIDTH_DP = 24f
        const val HORIZON_WIDTH_DP = 24f
    }
}
