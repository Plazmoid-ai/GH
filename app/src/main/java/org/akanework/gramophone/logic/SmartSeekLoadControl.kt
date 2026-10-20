@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.akanework.gramophone.logic

import androidx.media3.exoplayer.DefaultLoadControl

/**
 * Optional larger playback buffers for frequent Smart Seek jumps.
 *
 * The player uses this configuration for all playback whenever the preference is enabled.
 */
object SmartSeekLoadControl {
    fun create(): DefaultLoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                60_000,
                180_000,
                1_000,
                2_000
            )
            .setBackBuffer(30_000, true)
            .build()
}
