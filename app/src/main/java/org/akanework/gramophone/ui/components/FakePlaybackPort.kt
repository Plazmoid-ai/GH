package org.akanework.gramophone.ui.components

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Timer-driven playback simulator, used only by the sandbox. */
class FakePlaybackPort(duration: Long) : SnippetsPlaybackPort {
    private val handler = Handler(Looper.getMainLooper())
    private val durationFlow = MutableStateFlow(duration)
    private val playingFlow = MutableStateFlow(false)
    override val durationMs: StateFlow<Long> = durationFlow
    override val playWhenReady: StateFlow<Boolean> = playingFlow
    private var position = 0L
    private var speed = 1
    private var lastTick = 0L
    private var currentZoneIndex = 0
    private var boundsProvider: () -> List<LongRange> = { emptyList() }
    private val ticker = object : Runnable {
        override fun run() {
            if (!playingFlow.value) return
            val now = SystemClock.elapsedRealtime()
            position += (if (lastTick == 0L) 16L else now - lastTick).coerceAtLeast(0L) * speed
            lastTick = now
            val zones = boundsProvider()
            if (zones.isNotEmpty()) {
                currentZoneIndex = currentZoneIndex.coerceIn(0, zones.lastIndex)
                if (position >= zones[currentZoneIndex].endMsExclusive) {
                    if (currentZoneIndex < zones.lastIndex) {
                        currentZoneIndex++
                        position = zones[currentZoneIndex].first
                    } else {
                        pause()
                        currentZoneIndex = 0
                        position = zones.first().first
                    }
                }
            }
            handler.postDelayed(this, 16L)
        }
    }
    fun setDuration(ms: Long) { durationFlow.value = ms; position = position.coerceAtMost(ms) }
    fun setSpeed(value: Int) { speed = if (value >= 5) 5 else 1 }
    fun setBoundsProvider(provider: () -> List<LongRange>) { boundsProvider = provider }
    override fun positionMs() = position
    override fun seekTo(ms: Long) { position = ms.coerceIn(0L, durationFlow.value); currentZoneIndex = boundsProvider().indexOfFirst { position < it.endMsExclusive }.coerceAtLeast(0); lastTick = SystemClock.elapsedRealtime() }
    override fun play() { if (!playingFlow.value) { playingFlow.value = true; lastTick = 0L; handler.removeCallbacks(ticker); handler.postDelayed(ticker, 16L) } }
    override fun pause() { playingFlow.value = false; lastTick = 0L; handler.removeCallbacks(ticker) }
    fun release() { pause(); handler.removeCallbacksAndMessages(null) }
}
