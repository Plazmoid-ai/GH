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
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.slider.Slider
import kotlin.math.max

/**
 * Scans across the stock progress bar at a fixed speed based on the configured play duration.
 * The player's playback position drives elapsed time; intervalId resets the capsule for each cycle.
 */
class SmartSeekMiniIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val capsuleWidthPx = 32f * density
    private val capsuleHeightPx = 14f * density
    private val capsuleRadiusPx = 7f * density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2.5f * density
    }
    private val muteIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val muteSlashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
    }

    private var seekBarView: View? = null
    private var sliderView: View? = null
    private var phaseActive = false
    private var activeDurationMs = 0L
    private var activeIntervalId = Long.MIN_VALUE
    private var activePlayStartPositionMs = 0L
    private var currentPositionProvider: (() -> Long)? = null
    private var playbackIsPlaying = false
    private var isMuteMode = false
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
        intervalId: Long,
        isPlaying: Boolean,
        isSkipping: Boolean,
        isMuteMode: Boolean,
        playStartPositionMs: Long,
        playDurationMs: Long,
        currentPositionProvider: () -> Long,
        accentColor: Int,
        showIndicator: Boolean
    ) {
        val accentChanged = lastAccentColor != accentColor
        if (accentChanged) {
            fillPaint.color = accentColor
            lastAccentColor = accentColor
        }

        val playbackStateChanged = playbackIsPlaying != isPlaying || this.isMuteMode != isMuteMode
        playbackIsPlaying = isPlaying
        this.isMuteMode = isMuteMode
        this.currentPositionProvider = currentPositionProvider

        // Keep an active interval alive while Smart Seek performs its seek. This leaves
        // the capsule at the right edge until the service announces the next interval.
        val phaseShouldRun = smartSeekActive && hasPlayInterval && playDurationMs > 0L &&
            (!isSkipping || phaseActive)
        if (!phaseShouldRun) {
            stopPlayPhase()
            if (visibility != GONE) visibility = GONE
            return
        }

        val intervalChanged = !phaseActive ||
            activeIntervalId != intervalId ||
            activeDurationMs != playDurationMs ||
            activePlayStartPositionMs != playStartPositionMs

        if (intervalChanged) {
            phaseActive = true
            activeIntervalId = intervalId
            activeDurationMs = playDurationMs
            activePlayStartPositionMs = playStartPositionMs
            // The old interval may have been drawn at the right edge; redraw immediately
            // so the new interval appears at its actual start.
            invalidate()
        }

        // Hiding the drawing never discards the interval or its position-based progress.
        if (!showIndicator) {
            if (visibility != GONE) visibility = GONE
            return
        }

        val becameVisible = visibility != VISIBLE
        if (becameVisible) visibility = VISIBLE
        if (becameVisible || accentChanged || playbackStateChanged) invalidate()
    }

    private fun stopPlayPhase() {
        phaseActive = false
        activeDurationMs = 0L
        activeIntervalId = Long.MIN_VALUE
        activePlayStartPositionMs = 0L
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (phaseActive && visibility == VISIBLE && width > 0 && height > 0 &&
            activeDurationMs > 0L
        ) {
            // Use the player's position as the clock: it stops advancing while playback is paused.
            val currentPositionMs =
                currentPositionProvider?.invoke() ?: activePlayStartPositionMs
            val elapsedTimeInPlayMs =
                (currentPositionMs - activePlayStartPositionMs).coerceAtLeast(0L)

            val trackView = when {
                sliderView?.visibility == VISIBLE -> sliderView
                seekBarView?.visibility == VISIBLE -> seekBarView
                else -> null
            }
            if (trackView != null && trackView.width > 0) {
                val sliderInsetPx = if (trackView is Slider) 10f * density else 0f
                val trackOriginX = (trackView.left - left).toFloat()
                val startX = trackOriginX + max(trackView.paddingLeft.toFloat(), sliderInsetPx)
                val endX = trackOriginX + trackView.width -
                    max(trackView.paddingRight.toFloat(), sliderInsetPx)
                // Track width is the full distance available to the capsule's left edge.
                val trackWidth = (endX - startX - capsuleWidthPx).coerceAtLeast(0f)
                val configuredPlayDurationMs = activeDurationMs
                val progress = (elapsedTimeInPlayMs.toFloat() /
                    configuredPlayDurationMs.toFloat()).coerceIn(0f, 1f)
                // Normal mode moves left-to-right; Mute moves right-to-left.
                val distance = trackWidth * if (isMuteMode) (1f - progress) else progress
                val capsuleLeft = startX + distance
                val capsuleTop = (height - capsuleHeightPx) / 2f

                canvas.drawRoundRect(
                    capsuleLeft, capsuleTop,
                    capsuleLeft + capsuleWidthPx, capsuleTop + capsuleHeightPx,
                    capsuleRadiusPx, capsuleRadiusPx, fillPaint
                )
                canvas.drawRoundRect(
                    capsuleLeft, capsuleTop,
                    capsuleLeft + capsuleWidthPx, capsuleTop + capsuleHeightPx,
                    capsuleRadiusPx, capsuleRadiusPx, outlinePaint
                )

                if (isMuteMode) {
                    // Small speaker + slash glyph, drawn directly to avoid emoji-font differences.
                    val iconLeft = capsuleLeft + 4f * density
                    val iconCenterY = capsuleTop + capsuleHeightPx / 2f
                    val speaker = Path().apply {
                        moveTo(iconLeft, iconCenterY - 2.5f * density)
                        lineTo(iconLeft + 2.5f * density, iconCenterY - 2.5f * density)
                        lineTo(iconLeft + 6f * density, iconCenterY - 5f * density)
                        lineTo(iconLeft + 6f * density, iconCenterY + 5f * density)
                        lineTo(iconLeft + 2.5f * density, iconCenterY + 2.5f * density)
                        lineTo(iconLeft, iconCenterY + 2.5f * density)
                        close()
                    }
                    canvas.drawPath(speaker, muteIconPaint)
                    canvas.drawLine(
                        iconLeft + 7f * density, iconCenterY - 4f * density,
                        iconLeft + 12f * density, iconCenterY + 4f * density,
                        muteSlashPaint
                    )
                    canvas.drawLine(
                        iconLeft + 12f * density, iconCenterY - 4f * density,
                        iconLeft + 7f * density, iconCenterY + 4f * density,
                        muteSlashPaint
                    )
                }
            }
        }

        // Never stop the redraw loop while the active Smart Seek capsule is visible.
        // Its position comes from the player, so it naturally stays still during pause.
        if (phaseActive && visibility == VISIBLE) {
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (phaseActive && visibility == VISIBLE) {
            invalidate()
            postInvalidateOnAnimation()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (phaseActive && visibility == VISIBLE) {
            invalidate()
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        // Preserve phase state across temporary detach/reattach; the service owns its lifecycle.
        super.onDetachedFromWindow()
    }
}
