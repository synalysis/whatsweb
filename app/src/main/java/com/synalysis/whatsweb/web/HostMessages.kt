package com.synalysis.whatsweb.web

internal object HostMessages {
    private val PHONE = Regex("^[0-9]{8,15}$")

    sealed interface Message {
        data class Pane(val pane: String) : Message
        data class Cover(val open: Boolean) : Message
        data class Session(val linked: Boolean) : Message
        data class Self(val phone: String) : Message
        data class Chunk(
            val id: String,
            val index: Int,
            val total: Int,
            val mime: String,
            val part: String,
        ) : Message
        data object Fail : Message
    }

    fun parse(raw: String): Message? {
        val fields = FlatJson.fields(raw) ?: return null
        return when (fields["type"]) {
            "pane" -> fields["pane"]?.let { Message.Pane(it) }
            "cover" -> when (fields["open"]) {
                "true" -> Message.Cover(true)
                "false" -> Message.Cover(false)
                else -> null
            }
            "session" -> when (fields["linked"]) {
                "true" -> Message.Session(true)
                "false" -> Message.Session(false)
                else -> null
            }
            "self" -> {
                val phone = fields["phone"] ?: return null
                when {
                    phone.isEmpty() -> Message.Self("")
                    PHONE.matches(phone) -> Message.Self(phone)
                    else -> null
                }
            }
            "fail" -> Message.Fail
            "chunk" -> {
                val id = fields["id"] ?: return null
                val index = fields["index"]?.toIntOrNull() ?: return null
                val total = fields["total"]?.toIntOrNull() ?: return null
                val mime = fields["mime"] ?: return null
                val part = fields["part"] ?: return null
                Message.Chunk(id, index, total, mime, part)
            }
            else -> null
        }
    }
}

internal object FlatJson {
    fun fields(raw: String): Map<String, String>? {
        return Parser(raw.trim()).parseObject()
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun parseObject(): Map<String, String>? {
            if (!consume('{')) return null
            val values = linkedMapOf<String, String>()
            skip()
            if (consume('}')) return values
            while (index < text.length) {
                val key = parseString() ?: return null
                skip()
                if (!consume(':')) return null
                skip()
                val value = parseValue() ?: return null
                if (key in values) return null
                values[key] = value
                skip()
                when {
                    consume(',') -> skip()
                    consume('}') -> return if (index == text.length) values else null
                    else -> return null
                }
            }
            return null
        }

        private fun parseValue(): String? {
            skip()
            return when (peek()) {
                '"' -> parseString()
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber()
                else -> null
            }
        }

        private fun literal(value: String): String? {
            if (!text.startsWith(value, index)) return null
            index += value.length
            return value
        }

        private fun parseNumber(): String? {
            val start = index
            if (peek() == '-') index += 1
            if (index >= text.length || !text[index].isDigit()) return null
            while (index < text.length && text[index].isDigit()) index += 1
            return text.substring(start, index)
        }

        private fun parseString(): String? {
            if (!consume('"')) return null
            val out = StringBuilder()
            while (index < text.length) {
                val char = text[index]
                index += 1
                when {
                    char == '"' -> return out.toString()
                    char == '\\' -> {
                        if (index >= text.length) return null
                        when (val escaped = text[index]) {
                            '"', '\\', '/' -> out.append(escaped)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (index + 4 >= text.length) return null
                                val hex = text.substring(index + 1, index + 5)
                                val code = hex.toIntOrNull(16) ?: return null
                                out.append(code.toChar())
                                index += 4
                            }
                            else -> return null
                        }
                        index += 1
                    }
                    char < ' ' -> return null
                    else -> out.append(char)
                }
            }
            return null
        }

        private fun consume(expected: Char): Boolean {
            if (peek() != expected) return false
            index += 1
            return true
        }

        private fun skip() {
            while (index < text.length && text[index].isWhitespace()) index += 1
        }

        private fun peek(): Char? = text.getOrNull(index)
    }
}
