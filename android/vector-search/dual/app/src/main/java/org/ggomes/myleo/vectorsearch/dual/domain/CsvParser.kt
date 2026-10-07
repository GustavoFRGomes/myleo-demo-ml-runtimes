package org.ggomes.myleo.vectorsearch.dual.domain

/**
 * Minimal RFC-4180 CSV reader: quoted fields, embedded commas/newlines and
 * `""` escapes. Pure functions — unit-tested with inline strings.
 */
object CsvParser {
    /**
     * Parses all rows. Drops the single empty row a trailing newline implies.
     * Iterates chars so `\r\n` never fuses into one field break.
     */
    fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val current = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i += 2
                    } else {
                        inQuotes = false
                        i++
                    }
                } else {
                    field.append(c)
                    i++
                }
            } else if (c == '"') {
                inQuotes = true
                i++
            } else if (c == ',') {
                current.add(field.toString())
                field.clear()
                i++
            } else if (c == '\n') {
                current.add(field.toString())
                field.clear()
                rows.add(current.toList())
                current.clear()
                i++
            } else if (c == '\r') {
                i++ // paired with \n, handled above
            } else {
                field.append(c)
                i++
            }
        }
        // Lenient close: an unterminated quote still yields its field.
        current.add(field.toString())
        rows.add(current.toList())
        return rows.filter { !(it.size == 1 && it[0].isEmpty()) }
    }

    /**
     * Splits [text] into `(header, up to maxRows data rows)`.
     * Throws [IllegalArgumentException] on empty input.
     */
    fun loadRows(text: String, maxRows: Int = Int.MAX_VALUE): Pair<List<String>, List<List<String>>> {
        val rows = parseRows(text)
        require(rows.isNotEmpty()) { "empty CSV input" }
        val header = rows.first()
        val data = rows.drop(1).take(maxRows)
        return header to data
    }
}
