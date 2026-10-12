package com.synalysis.whatsweb.prefs

data class CompanionAccount(
    val id: String,
    val name: String,
    val phone: String = "",
)

/**
 * The list of WhatsApp accounts stored beside the web session, so switching
 * profiles does not forget the names.
 */
object LinkRecords {
    const val MAIN = "main"
    const val MAX = 8

    private val idPattern = Regex("^[a-z][a-z0-9]{0,31}$")

    fun isId(id: String): Boolean = idPattern.matches(id)

    fun cleanName(raw: String): String? {
        val name = raw.trim().replace(Regex("\\s+"), " ").take(40)
        return name.ifBlank { null }
    }

    fun cleanPhone(raw: String): String? {
        val digits = raw.trim()
        if (!digits.matches(Regex("^[0-9]{8,15}$"))) return null
        // WhatsApp still stores Mexican mobiles with the retired prefix: 52, then 1, then 10 digits.
        if (digits.length == 13 && digits.startsWith("521")) {
            return "52" + digits.substring(3)
        }
        return digits
    }

    fun decode(raw: String?): List<CompanionAccount> {
        if (raw.isNullOrBlank()) return listOf(CompanionAccount(MAIN, ""))
        val accounts = raw.lineSequence().mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size < 2) return@mapNotNull null
            val id = fields[0]
            if (!isId(id)) return@mapNotNull null
            val name = cleanName(fields[1]).orEmpty()
            val phone = fields.getOrNull(2)?.let(::cleanPhone).orEmpty()
            CompanionAccount(id, name, phone)
        }.distinctBy { it.id }.toList()
        return accounts.ifEmpty { listOf(CompanionAccount(MAIN, "")) }
    }

    fun encode(accounts: List<CompanionAccount>): String {
        return accounts.joinToString("\n") { account ->
            "${account.id}\t${account.name}\t${account.phone}"
        }
    }
}
