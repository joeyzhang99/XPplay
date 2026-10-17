package com.shilapi.xcertplay.glance

import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/**
 * What CarPlay is doing, at a glance, for widgets and other screens outside CarPlay: whether a
 * session is connected. Fed by every CarPlay session.
 *
 * The some cars HUD route-guidance and cluster-song display this glance used to show was removed with
 * the rest of the some cars-specific code; the snapshot fields they filled keep their default values.
 */
object CarPlayGlance {
    /** One state of the glance; [maneuverType] is Apple's RouteGuidanceManeuverType, null without a route. */
    data class Snapshot(
        val connected: Boolean = false,
        val maneuverType: Int? = null,
        val drivingSide: Int = 0,
        val distanceMeters: Int = 0,
        val road: String = "",
        val arrivalEpochSeconds: Long? = null,
        val remainingSeconds: Long? = null,
        val remainingMeters: Long? = null,
        val song: String? = null,
        val playing: Boolean = false,
    )

    private var connected = false
    private var last = Snapshot()

    /** Called with each new snapshot, on the thread that changed it. */
    @Volatile var listener: ((Snapshot) -> Unit)? = null

    /** The current snapshot, taken on demand; listeners hear about every change. */
    fun snapshot(): Snapshot {
        val (changed, current) = synchronized(this) { publishLocked() to last }
        changed?.let { listener?.invoke(it) }
        return current
    }

    /**
     * Still called with every CarPlay frame; the some cars HUD route-guidance and cluster-song handling
     * that used to read them was removed, so no frame changes the glance any more.
     */
    fun onFrame(frame: Iap2Frame) {
    }

    fun setConnected(next: Boolean) {
        val changed = synchronized(this) {
            if (connected == next) return
            connected = next
            publishLocked()
        }
        changed?.let { listener?.invoke(it) }
    }

    private fun publishLocked(): Snapshot? {
        val next = Snapshot(connected = connected)
        if (next == last) return null
        last = next
        return next
    }
}
