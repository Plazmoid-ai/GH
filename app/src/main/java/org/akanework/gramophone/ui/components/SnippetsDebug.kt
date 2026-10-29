/*
 * Temporary Snippets diagnostics.
 * TODO этап 9: удалить.
 */
package org.akanework.gramophone.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.ArrayDeque

/**
 * TODO этап 9: удалить — временный диагностический журнал Snippets.
 * The ring buffer and position-tick counters are synchronized because events may arrive
 * from lifecycle/controller callbacks as well as the main thread.
 */
object SnippetsDebug {
    private const val CAPACITY = 300
    private val startedAtMs = SystemClock.elapsedRealtime()
    private val events = ArrayDeque<String>(CAPACITY)
    private val positionTicks = ArrayDeque<Long>()

    @Synchronized
    fun log(tag: String, text: String) {
        val elapsedMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
        if (events.size >= CAPACITY) events.removeFirst()
        events.addLast("+${elapsedMs}ms [$tag] $text")
    }

    @Synchronized
    fun dump(): String = events.joinToString("\n")

    /** Called only from positionRunnable; does not schedule or alter the polling cycle. */
    @Synchronized
    fun recordPositionTick() {
        val now = SystemClock.elapsedRealtime()
        positionTicks.addLast(now)
        while (positionTicks.isNotEmpty() && now - positionTicks.first() > 1_000L) {
            positionTicks.removeFirst()
        }
    }

    @Synchronized
    fun ticksPerSecond(): Int {
        val now = SystemClock.elapsedRealtime()
        while (positionTicks.isNotEmpty() && now - positionTicks.first() > 1_000L) {
            positionTicks.removeFirst()
        }
        return positionTicks.size
    }

    @Synchronized
    fun positionTickAgeMs(): Long =
        positionTicks.lastOrNull()?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) }
            ?: -1L
}

data class SnippetsDebugStatus(
    val positionMs: Long,
    val currentSnippetIndex: Int,
    val boundsCount: Int,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val runnableRunning: Boolean,
    val tps: Int,
    val positionTickAgeMs: Long,
    val snippetsPlaybackFinished: Boolean,
    val snippetsSeekToStartOnTransition: Boolean,
    val fullBottomSheetHash: Int,
)

/** Small temporary status overlay; long-press copies the recent event journal. */
@androidx.compose.runtime.Composable
fun SnippetsDebugIndicator(
    statusProvider: () -> SnippetsDebugStatus,
    modifier: Modifier = Modifier,
) {
    // TODO этап 9: удалить — диагностический индикатор поверх карты.
    val context = LocalContext.current
    var status by remember { mutableStateOf(statusProvider()) }

    LaunchedEffect(Unit) {
        while (true) {
            status = statusProvider()
            delay(500L)
        }
    }

    Text(
        text = "pos=${status.positionMs} idx=${if (status.boundsCount > 0) status.currentSnippetIndex + 1 else 0}/${status.boundsCount} " +
            "pwr=${status.playWhenReady} playing=${status.isPlaying} run=${status.runnableRunning} " +
            "tps=${status.tps} age=${status.positionTickAgeMs}ms " +
            "fin=${status.snippetsPlaybackFinished} seekStart=${status.snippetsSeekToStartOnTransition} " +
            "hash=${status.fullBottomSheetHash}",
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.52f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("SnippetsDebug", SnippetsDebug.dump())
                        )
                        Toast.makeText(context, "Журнал скопирован", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .padding(horizontal = 5.dp, vertical = 3.dp),
        color = Color.White.copy(alpha = 0.92f),
        fontSize = 9.sp,
        lineHeight = 11.sp,
    )
}
