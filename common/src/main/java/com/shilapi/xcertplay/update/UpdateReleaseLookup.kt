package com.shilapi.xcertplay.update

import java.net.URL
import java.net.URLConnection

internal object UpdateReleaseLookup {
    /**
     * XPplay publishes its own releases, so the feed is on. It must stay pointed at this fork:
     * the upstream DiPlay feed would install a different package over this app.
     */
    private const val UPDATE_FEED_ENABLED = true

    fun latest(
        installedVersion: String,
        userAgent: String,
        openConnection: (URL) -> URLConnection = { it.openConnection() },
    ): UpdateRelease? {
        if (!UPDATE_FEED_ENABLED) return null
        // GitHub is routinely blocked in China: try the API, then the CDN copies of version.json.
        var lastFailure: Throwable? = null
        for (url in UpdateMirrors.catalogUrls()) {
            val json = try {
                UpdateClient.fetchText(url, "application/vnd.github+json", userAgent, openConnection)
            } catch (error: Throwable) {
                lastFailure = error
                continue
            }
            val release = UpdateCatalog.parse(json) ?: continue
            return release.takeIf { UpdateVersion.isNewer(it.tagName, installedVersion) }
        }
        lastFailure?.let { throw it }
        return null
    }
}
