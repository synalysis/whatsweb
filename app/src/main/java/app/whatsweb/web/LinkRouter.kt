package app.whatsweb.web

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Decides what a navigation inside the companion view should do.
 * Phone-sized browsers get bounced to the WhatsApp marketing site; that bounce is recovered.
 * wa.me and invite links are rewritten into the web companion.
 */
object LinkRouter {
    const val HOME = "https://web.whatsapp.com/"
    private const val COMPANION_HOST = "web.whatsapp.com"

    sealed interface Route {
        data object Stay : Route
        data class Translate(val url: String) : Route
        data class External(val url: String) : Route
        data object Recover : Route
        data object Drop : Route
    }

    fun route(raw: String): Route {
        val uri = try {
            URI(raw.trim())
        } catch (_: Exception) {
            return Route.Drop
        }
        val scheme = uri.scheme?.lowercase() ?: return Route.Drop
        if (scheme == "about" || scheme == "blob" || scheme == "data") return Route.Stay
        if (scheme != "http" && scheme != "https") return Route.Drop

        val host = uri.host?.lowercase()?.trim('.') ?: return Route.Drop
        if (host == COMPANION_HOST) {
            return if (scheme == "https") Route.Stay else Route.Translate(toHttps(uri))
        }
        if (host == "whatsapp.com" || host == "www.whatsapp.com") return Route.Recover

        translate(host, uri)?.let { return Route.Translate(it) }
        return Route.External(raw.trim())
    }

    fun isCompanionPage(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val host = try {
            URI(raw).host
        } catch (_: Exception) {
            return false
        }
        return host.equals(COMPANION_HOST, ignoreCase = true)
    }

    private fun translate(host: String, uri: URI): String? {
        val params = queryParams(uri)
        return when (host) {
            "wa.me" -> sendUrl(phoneFrom(uri.path), params["text"])
            "api.whatsapp.com" -> sendUrl(params["phone"] ?: phoneFrom(uri.path), params["text"])
            "chat.whatsapp.com" -> inviteUrl(uri.path)
            else -> null
        }
    }

    private fun sendUrl(phoneSource: String?, text: String?): String? {
        val digits = phoneSource?.filter { it.isDigit() }.orEmpty()
        if (digits.length !in 4..15) return null
        val textPart = text?.takeIf { it.isNotEmpty() }?.let { "&text=${encode(it)}" }.orEmpty()
        return "https://web.whatsapp.com/send?phone=$digits$textPart"
    }

    private fun inviteUrl(path: String?): String? {
        val code = path?.trim('/')?.substringBefore('/')?.substringBefore('?').orEmpty()
        if (!code.matches(Regex("""[A-Za-z0-9]{10,32}"""))) return null
        return "https://web.whatsapp.com/accept?code=${encode(code)}"
    }

    private fun phoneFrom(path: String?): String? {
        return path?.trim('/')?.substringBefore('/')?.substringBefore('?')
    }

    private fun toHttps(uri: URI): String {
        val specific = uri.rawSchemeSpecificPart ?: return HOME
        return "https:$specific"
    }

    private fun queryParams(uri: URI): Map<String, String> {
        val raw = uri.rawQuery ?: return emptyMap()
        val values = linkedMapOf<String, String>()
        for (part in raw.split('&')) {
            if (part.isEmpty()) continue
            val index = part.indexOf('=')
            val key = decode(if (index < 0) part else part.substring(0, index))
            val value = if (index < 0) "" else decode(part.substring(index + 1))
            values.putIfAbsent(key, value)
        }
        return values
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
