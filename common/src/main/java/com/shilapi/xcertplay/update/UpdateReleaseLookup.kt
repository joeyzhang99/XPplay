package com.shilapi.xcertplay.update

import java.net.URL
import java.net.URLConnection

internal object UpdateReleaseLookup {
    /**
     * XPplay is a personal fork. Updates are installed manually from local builds; the upstream
     * DiPlay release feed must never be surfaced because installing it would replace this app
     * with a different package. Set to true only when XPplay has its own release feed.
     */
    private const val UPDATE_FEED_ENABLED = false

    fun latest(
        installedVersion: String,
        userAgent: String,
        openConnection: (URL) -> URLConnection = { it.openConnection() },
    ): UpdateRelease? {
        if (!UPDATE_FEED_ENABLED) return null
        val json = UpdateClient.fetchText(
            UpdateClient.RELEASES_URL,
            "application/vnd.github+json",
            userAgent,
            openConnection,
        )
        val release = UpdateCatalog.parse(json) ?: return null
        return release.takeIf { UpdateVersion.isNewer(it.tagName, installedVersion) }
    }
}
