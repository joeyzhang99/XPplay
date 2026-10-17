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
    /** 仓库内的静态清单，实时内容，没有分支缓存。 */
    internal const val RAW_CATALOG_URL =
        "https://raw.githubusercontent.com/joeyzhang99/XPplay/main/version.json"

    /**
     * jsDelivr 的副本。分支引用（@main）有 12 小时缓存，刚发版时可能还是旧版本，所以它排在
     * 加速通道之后，只作最后的兜底。
     */
    internal const val FALLBACK_CATALOG_URL =
        "https://cdn.jsdelivr.net/gh/joeyzhang99/XPplay@main/version.json"

    /**
     * 拼在原始 GitHub 地址前的加速前缀，按尝试顺序。
     *
     * 2026-10-17 在上海移动网络实测：ghfast.top 全部超时（连接挂住不返回），ghproxy.net 稳定
     * 1–2 秒，gh-proxy.com 正常但波动大（0.5–9 秒）。失效的那条已移除——留着它每次让用户白等
     * 一个超时。
     */
    internal val DOWNLOAD_PREFIXES = listOf(
        "https://ghproxy.net/",
        "https://gh-proxy.com/",
    )

    /**
     * 检查更新时依次尝试的地址。
     *
     * GitHub 的 API 与 raw 在国内都不通，但加速前缀能代理 raw：它转发的是实时文件，没有分支
     * 缓存，所以比 jsDelivr 的新鲜度可靠。jsDelivr 留在最后兜底。
     */
    internal fun catalogUrls(): List<String> = listOf(
        UpdateClient.RELEASES_URL,
        DOWNLOAD_PREFIXES[0] + RAW_CATALOG_URL,
        DOWNLOAD_PREFIXES[1] + RAW_CATALOG_URL,
        FALLBACK_CATALOG_URL,
    )

    /** 一个 GitHub 下载地址的全部可用镜像，原始地址排第一。 */
    internal fun downloadUrls(githubUrl: String): List<String> =
        listOf(githubUrl) + DOWNLOAD_PREFIXES.map { it + githubUrl }

    /** 检查更新时显示给用户的通道名。 */
    internal fun channelLabel(url: String): String = when {
        url.startsWith("https://api.github.com") -> "GitHub"
        url.contains("jsdelivr") -> "jsDelivr"
        DOWNLOAD_PREFIXES.any { url.startsWith(it) } ->
            DOWNLOAD_PREFIXES.first { url.startsWith(it) }.substringAfter("://").trimEnd('/')
        else -> url.substringAfter("://").substringBefore('/')
    }

    /** 展示给用户的镜像站点，用于手动复制。 */
    internal fun mirrorHosts(): List<String> = DOWNLOAD_PREFIXES.map { prefix ->
        prefix.removePrefix("https://").trimEnd('/')
    }
}
