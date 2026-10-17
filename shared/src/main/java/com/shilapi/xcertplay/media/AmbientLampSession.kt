package com.shilapi.xcertplay.media

/**
 * No-op shell of the serialized some cars ambient lamp lifecycle. The hud client and its policy types
 * were removed; the class name and method names are kept for compatibility, but nothing is driven.
 */
internal class AmbientLampSession {

    fun isReady(): Boolean = false

    fun setEnabled(value: Boolean) {}

    fun update(desired: Triple<Int, Int, Int>) {}

    fun recover() {}
}
