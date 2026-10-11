package app.whatsweb

/**
 * Picks the text for the bar above the companion page.
 * WhatsApp's own document title is used when it names a chat.
 */
object Titles {
    private val unreadCount = Regex("""(?i)^\(\d+\)\s+WhatsApp( Web)?$""")

    fun bar(
        pageTitle: String?,
        pane: String,
        linked: Boolean?,
        chats: String,
        link: String,
        conversation: String,
    ): String {
        if (linked == false) return link
        val cleaned = pageTitle?.trim().orEmpty()
        val generic = cleaned.isEmpty() ||
            cleaned.equals("WhatsApp", ignoreCase = true) ||
            cleaned.equals("WhatsApp Web", ignoreCase = true) ||
            unreadCount.matches(cleaned)
        if (!generic) return cleaned
        return if (pane == "chat") conversation else chats
    }
}
