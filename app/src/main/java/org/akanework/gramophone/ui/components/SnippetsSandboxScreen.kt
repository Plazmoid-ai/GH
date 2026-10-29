package org.akanework.gramophone.ui.components

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

/** TODO очистка: удалить пункт меню после завершения разработки. */
const val SANDBOX_ENABLED = true

private data class SandboxPalette(val title: String, val colors: SnippetsColors)
private fun palettes() = listOf(
    SandboxPalette("1 · Фиолетовый", SnippetsColors(androidx.compose.ui.graphics.Color(0xFF7654E8), androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color(0xFF302B43), androidx.compose.ui.graphics.Color(0xFFDAD5E8), androidx.compose.ui.graphics.Color(0xFF252231), androidx.compose.ui.graphics.Color(0xFFE4E0ED), androidx.compose.ui.graphics.Color(0xFF302B43), androidx.compose.ui.graphics.Color.White)),
    SandboxPalette("2 · Оранжево-коричневый", SnippetsColors(androidx.compose.ui.graphics.Color(0xFFC56A32), androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color(0xFF49352C), androidx.compose.ui.graphics.Color(0xFFE9D8CC), androidx.compose.ui.graphics.Color(0xFF39271F), androidx.compose.ui.graphics.Color(0xFFEDE2D9), androidx.compose.ui.graphics.Color(0xFF49352C), androidx.compose.ui.graphics.Color.White)),
    SandboxPalette("3 · Зелёный", SnippetsColors(androidx.compose.ui.graphics.Color(0xFF25865B), androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color(0xFF243A31), androidx.compose.ui.graphics.Color(0xFFD3E6DA), androidx.compose.ui.graphics.Color(0xFF20392D), androidx.compose.ui.graphics.Color(0xFFE0EAE3), androidx.compose.ui.graphics.Color(0xFF243A31), androidx.compose.ui.graphics.Color.White)),
    SandboxPalette("4 · Серый", SnippetsColors(androidx.compose.ui.graphics.Color(0xFF6D7482), androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color(0xFF30343B), androidx.compose.ui.graphics.Color(0xFFDADDE2), androidx.compose.ui.graphics.Color(0xFF292D33), androidx.compose.ui.graphics.Color(0xFFE6E8EB), androidx.compose.ui.graphics.Color(0xFF30343B), androidx.compose.ui.graphics.Color.White)),
)

fun showSnippetsSandbox(context: Context) {
    if (!SANDBOX_ENABLED) return
    val view = ComposeView(context).apply { setContent { MaterialTheme { SnippetsSandboxScreen() } } }
    AlertDialog.Builder(context).setTitle("Песочница интервалов").setView(view).setPositiveButton("Закрыть", null).show()
}

@Composable
fun SnippetsSandboxScreen() {
    var trackSec by remember { mutableIntStateOf(180) }
    var intervalSec by remember { mutableIntStateOf(10) }
    var speed by remember { mutableIntStateOf(1) }
    var palette by remember { mutableIntStateOf(0) }
    var framePos by remember { mutableLongStateOf(0L) }
    val palettes = remember { palettes() }
    val port = remember { FakePlaybackPort(180_000L) }
    val holder = remember(port) { SnippetsHolder(port, StubSnippetsEngine()) { intervalSec * 1000L } }
    val state by holder.state.collectAsState()
    val engine = remember { StubSnippetsEngine() }
    val ctx = SnippetsContext(trackSec * 1000L, intervalSec * 1000L, engine)
    val bounds = remember(state, trackSec, intervalSec) { engine.bounds(state, ctx) }
    port.setBoundsProvider { engine.bounds(holder.state.value, SnippetsContext(port.durationMs.value, intervalSec * 1000L, engine)) }
    LaunchedEffect(port) { while (true) { framePos = port.positionMs(); delay(16L) } }
    DisposableEffect(port) { onDispose { port.release() } }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Параметры песочницы", style = MaterialTheme.typography.titleMedium)
        Parameter("Длина трека", String.format(Locale.ROOT, "%d:%02d", trackSec / 60, trackSec % 60),
            { trackSec = (trackSec - 30).coerceAtLeast(60); port.setDuration(trackSec * 1000L) },
            { trackSec = (trackSec + 30).coerceAtMost(600); port.setDuration(trackSec * 1000L) })
        Parameter("Длина интервала", "$intervalSec с",
            { intervalSec = (intervalSec - 5).coerceAtLeast(5) }, { intervalSec = (intervalSec + 5).coerceAtMost(300) })
        Parameter("Скорость", "×$speed", { speed = 1; port.setSpeed(1) }, { speed = 5; port.setSpeed(5) })
        Parameter("Палитра", palettes[palette].title, { palette = (palette - 1).mod(4) }, { palette = (palette + 1).mod(4) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = { port.pause(); trackSec = 180; intervalSec = 10; speed = 1; palette = 0; port.setDuration(180_000L); port.setSpeed(1); port.seekTo(0L) }) { Text("Сброс") }
        }
        Text("Карта трека", style = MaterialTheme.typography.titleMedium)
        SnippetsTrackMap(bounds, palettes[palette].colors, trackSec * 1000L, framePos, port.playWhenReady.value, positionProvider = { port.positionMs() })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { if (port.playWhenReady.value) port.pause() else port.play() }) { Text(if (port.playWhenReady.value) "⏸ Пауза" else "▶ Воспроизвести") }
            Text("${time(port.positionMs())} / ${time(trackSec * 1000L)}")
        }
        Text("панель: этап B", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Parameter(title: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) { Text(title); Text(value) }
        Button(onClick = minus) { Text("−") }
        Button(onClick = plus) { Text("+") }
    }
}
private fun time(ms: Long) = String.format(Locale.ROOT, "%d:%02d", ms.coerceAtLeast(0L) / 60_000L, (ms.coerceAtLeast(0L) / 1_000L) % 60L)
