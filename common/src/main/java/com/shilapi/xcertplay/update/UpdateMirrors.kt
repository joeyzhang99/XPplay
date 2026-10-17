package com.shilapi.xcertplay.update

/**
 * GitHub 在国内常被阻断，更新需要两条备用路径。
 *
 * - 检查更新：api.github.com 打不开时，改读 jsDelivr 上仓库内的 `version.json`
 *   （与 GitHub releases 同构，解析器共用）。
 * - 下载 APK：assets 直连失败时，依次套用加速前缀重试同一个 GitHub 地址。
 *
 * 前缀都是第三方公益加速服务，先后顺序按"目前相对稳定"排列；某一档失效时只会多花一次
 * 连接超时，不会影响后续档位。
 */
internal object UpdateMirrors {
    /** 仓库内静态清单，jsDelivr 会缓存，国内通常可直连。 */
    internal const val FALLBACK_CATALOG_URL =
        "https://cdn.jsdelivr.net/gh/joeyzhang99/XPplay@main/version.json"

    /** 与 [FALLBACK_CATALOG_URL] 同一份文件的备用 CDN。 */
    internal const val SECONDARY_CATALOG_URL =
        "https://raw.gitmirror.com/joeyzhang99/XPplay/main/version.json"

    /** 拼在原始 GitHub 地址前的加速前缀，按尝试顺序。 */
    internal val DOWNLOAD_PREFIXES = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
    )

    /** 检查更新时依次尝试的地址。 */
    internal fun catalogUrls(): List<String> = listOf(
        UpdateClient.RELEASES_URL,
        FALLBACK_CATALOG_URL,
        SECONDARY_CATALOG_URL,
    )

    /** 一个 GitHub 下载地址的全部可用镜像，原始地址排第一。 */
    internal fun downloadUrls(githubUrl: String): List<String> =
        listOf(githubUrl) + DOWNLOAD_PREFIXES.map { it + githubUrl }

    /** 检查更新时显示给用户的通道名。 */
    internal fun channelLabel(url: String): String = when {
        url.startsWith("https://api.github.com") -> "GitHub"
        url.contains("jsdelivr") -> "jsDelivr"
        url.contains("gitmirror") -> "gitmirror"
        else -> url.substringAfter("://").substringBefore('/')
    }

    /** 展示给用户的镜像站点，用于手动复制。 */
    internal fun mirrorHosts(): List<String> = DOWNLOAD_PREFIXES.map { prefix ->
        prefix.removePrefix("https://").trimEnd('/')
    }
}
