package com.maldawr.mediagrabber

import java.net.URI

object DirectMediaPolicy {
    private val blockedHosts = setOf(
        "youtube.com",
        "youtu.be",
        "googlevideo.com",
        "youtube-nocookie.com"
    )

    fun validationError(rawUrl: String): String? {
        val value = rawUrl.trim()
        if (value.isBlank()) return "empty"

        val uri = runCatching { URI(value) }.getOrNull() ?: return "invalid"
        if (!uri.scheme.equals("https", ignoreCase = true)) return "https_required"

        val host = uri.host?.lowercase()?.trimEnd('.') ?: return "invalid"
        if (blockedHosts.any { blocked -> host == blocked || host.endsWith(".$blocked") }) {
            return "blocked_source"
        }

        return null
    }
}
