package com.shilapi.xcertplay.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateMirrorsTest {
    @Test
    fun catalogTriesGithubThenTheAcceleratorsAndKeepsJsdelivrLast() {
        val urls = UpdateMirrors.catalogUrls()
        assertEquals(4, urls.size)
        assertTrue(urls[0], urls[0].startsWith("https://api.github.com/repos/joeyzhang99/XPplay/"))
        // Accelerators proxy the raw file, which has no branch cache; jsDelivr's copy can lag
        // up to twelve hours, so it goes last.
        assertTrue(urls[1], urls[1].contains("raw.githubusercontent.com"))
        assertTrue(urls[2], urls[2].contains("raw.githubusercontent.com"))
        assertTrue(urls[3], urls[3].contains("cdn.jsdelivr.net"))
    }

    @Test
    fun downloadKeepsTheOriginalUrlFirstAndAppendsEachPrefix() {
        val github = "https://github.com/joeyzhang99/XPplay/releases/download/v1.4.0/XPplay-V1.4.0-release.apk"
        val urls = UpdateMirrors.downloadUrls(github)
        assertEquals(UpdateMirrors.DOWNLOAD_PREFIXES.size + 1, urls.size)
        assertEquals(github, urls[0])
        UpdateMirrors.DOWNLOAD_PREFIXES.forEachIndexed { index, prefix ->
            assertEquals(prefix + github, urls[index + 1])
        }
    }

    @Test
    fun mirrorHostsAreWhatThePageShows() {
        val hosts = UpdateMirrors.mirrorHosts()
        assertEquals(UpdateMirrors.DOWNLOAD_PREFIXES.size, hosts.size)
        hosts.forEach { host ->
            assertTrue(host, !host.startsWith("https://") && !host.endsWith("/"))
            assertTrue(host, host.contains('.'))
        }
    }
}
