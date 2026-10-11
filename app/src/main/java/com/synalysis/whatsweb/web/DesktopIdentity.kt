package com.synalysis.whatsweb.web

/**
 * Presents the system WebView as a current desktop Chrome.
 * WhatsApp Web refuses mobile user agents and outdated Chrome versions.
 */
object DesktopIdentity {
    const val FALLBACK_VERSION = "131.0.0.0"

    private val fourPart = Regex("""\d+\.\d+\.\d+\.\d+""")

    fun chromeVersion(webViewVersionName: String?): String {
        val token = webViewVersionName
            ?.trim()
            ?.substringBefore(' ')
            ?.substringBefore('(')
            ?.trim()
            .orEmpty()
        return if (fourPart.matches(token)) token else FALLBACK_VERSION
    }

    fun userAgent(chromeVersion: String): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"
    }
}
