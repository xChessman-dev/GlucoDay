package dev.chessman.glucoday.domain

/**
 * Android's org.json parser accepts several non-JSON forms and repeated object keys.
 * Validate the grammar before decoding so ambiguous backup fields cannot be silently replaced.
 */
internal object StrictJson {
    fun validate(value: String) = Parser(value).validate()

    private class Parser(private val text: String) {
        private var position = 0

        fun validate() {
            value(0)
            whitespace()
            require(position == text.length) { "Лишние данные после резервной копии" }
        }

        private fun value(depth: Int) {
            require(depth <= 16) { "Слишком глубокая структура резервной копии" }
            whitespace()
            when (peek()) {
                '{' -> objectValue(depth + 1)
                '[' -> arrayValue(depth + 1)
                '"' -> stringValue()
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                '-', in '0'..'9' -> number()
                else -> invalid()
            }
        }

        private fun objectValue(depth: Int) {
            expect('{')
            whitespace()
            if (consume('}')) return
            val keys = HashSet<String>()
            while (true) {
                whitespace()
                val key = stringValue()
                require(keys.add(key)) { "Повторяющееся поле в резервной копии: $key" }
                whitespace()
                expect(':')
                value(depth)
                whitespace()
                if (consume('}')) return
                expect(',')
            }
        }

        private fun arrayValue(depth: Int) {
            expect('[')
            whitespace()
            if (consume(']')) return
            while (true) {
                value(depth)
                whitespace()
                if (consume(']')) return
                expect(',')
            }
        }

        private fun stringValue(): String {
            expect('"')
            val result = StringBuilder()
            while (position < text.length) {
                val char = text[position++]
                when {
                    char == '"' -> return result.toString()
                    char == '\\' -> {
                        require(position < text.length) { "Незавершённая строка JSON" }
                        when (val escaped = text[position++]) {
                            '"', '\\', '/' -> result.append(escaped)
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000C')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> {
                                require(position + 4 <= text.length) { "Некорректная escape-последовательность JSON" }
                                val hex = text.substring(position, position + 4)
                                require(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                                    "Некорректная escape-последовательность JSON"
                                }
                                result.append(hex.toInt(16).toChar())
                                position += 4
                            }
                            else -> invalid()
                        }
                    }
                    char.code < 32 -> invalid()
                    else -> result.append(char)
                }
            }
            invalid()
        }

        private fun number() {
            consume('-')
            if (!consume('0')) {
                require(peek() in '1'..'9') { "Некорректное число JSON" }
                while (peek() in '0'..'9') position++
            }
            if (consume('.')) {
                require(peek() in '0'..'9') { "Некорректное число JSON" }
                while (peek() in '0'..'9') position++
            }
            if (peek() == 'e' || peek() == 'E') {
                position++
                if (peek() == '+' || peek() == '-') position++
                require(peek() in '0'..'9') { "Некорректное число JSON" }
                while (peek() in '0'..'9') position++
            }
        }

        private fun literal(expected: String) {
            require(text.startsWith(expected, position)) { "Некорректное значение JSON" }
            position += expected.length
        }
        private fun whitespace() { while (peek() in listOf(' ', '\t', '\r', '\n')) position++ }
        private fun peek(): Char = text.getOrNull(position) ?: '\u0000'
        private fun consume(char: Char): Boolean = (peek() == char).also { if (it) position++ }
        private fun expect(char: Char) { require(consume(char)) { "Повреждённая структура резервной копии" } }
        private fun invalid(): Nothing = throw IllegalArgumentException("Некорректный JSON в резервной копии")
    }
}
