package com.shilapi.xcertplay.media

import android.content.Context
import java.util.concurrent.CompletableFuture

/**
 * No-op shell of the some cars music-reactive ambient lamp controller. The hud package that drove the
 * lamps was removed; every entry point keeps its signature so surviving callers compile unchanged,
 * but no lamp I/O happens and the feature reports itself as unsupported.
 */
object AmbientMusicController {

    fun openSink(context: Context?): Long = 0L

    internal fun openRenderer(sink: Long, envelope: AmbientMusicEnvelope, playback: () -> Pair<Int, Boolean>): Long = 0L

    internal fun wantsPcm(token: Long): Boolean = false

    internal fun closeRenderer(token: Long) {}

    fun closeSink(token: Long) {}

    /** Binds phone playback callbacks to the current CarPlay media owner. */
    fun claimPlaybackOwner(owner: Any) {}

    /** Already decoded artwork is sampled on the existing artwork worker, never on PCM writes. */
    fun albumColorChanged(owner: Any, color: Int?) {}

    /** Stale callbacks from a replaced media owner are ignored. */
    fun phonePlaybackChanged(owner: Any, playing: Boolean): Boolean = false

    fun releasePlaybackOwner(owner: Any): Boolean = false

    fun checkSupport(context: Context): CompletableFuture<Boolean> = CompletableFuture.completedFuture(false)

    fun settingsChanged(context: Context) {}
}
