package app.whatsweb.web

import java.net.URI

/**
 * WhatsApp Web posts client metrics and crash logs beside the chat connection.
 * Those calls are refused. Messaging, media, and the companion page stay open.
 */
object TelemetryBlock {
    fun blocks(raw: String): Boolean {
        val uri = try {
            URI(raw.trim())
        } catch (_: Exception) {
            return false
        }
        val host = uri.host?.lowercase()?.trim('.') ?: return false
        val path = uri.path.orEmpty()
        return when (host) {
            "dit.whatsapp.net", "crashlogs.whatsapp.net" -> true
            "web.whatsapp.com" -> path == "/ajax/bz" || path.startsWith("/ajax/bz/")
            else -> false
        }
    }
}
