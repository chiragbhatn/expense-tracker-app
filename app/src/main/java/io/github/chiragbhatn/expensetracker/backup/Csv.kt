package io.github.chiragbhatn.expensetracker.backup

class CsvException(message: String) : Exception(message)

/** RFC 4180 comma-separated values: quoted fields, doubled quotes, CRLF line ends. */
object Csv {
    /** Byte-order mark, so spreadsheet apps open the file as UTF-8 and show ₹ correctly. */
    const val BOM = "﻿"

    fun write(rows: List<List<String>>): String = buildString {
        rows.forEach { row ->
            append(row.joinToString(",") { quote(it) })
            append("\r\n")
        }
    }

    private fun quote(field: String): String {
        val needsQuotes = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            field.startsWith(" ") || field.endsWith(" ")
        return if (needsQuotes) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }

    /** Rows of fields. Blank lines are skipped; a leading byte-order mark is ignored. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var fieldStarted = false
        var i = if (text.startsWith(BOM)) 1 else 0
        var line = 1

        fun endField() {
            row.add(field.toString())
            field.clear()
            fieldStarted = false
        }

        fun endRow() {
            endField()
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row)
            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> {
                        if (c == '\n') line++
                        field.append(c)
                    }
                }
            } else {
                when (c) {
                    '"' -> if (!fieldStarted && field.isEmpty()) {
                        inQuotes = true
                        fieldStarted = true
                    } else {
                        field.append(c)
                    }
                    ',' -> endField()
                    '\r' -> {
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                        endRow()
                        line++
                    }
                    '\n' -> {
                        endRow()
                        line++
                    }
                    else -> {
                        field.append(c)
                        fieldStarted = true
                    }
                }
            }
            i++
        }
        if (inQuotes) throw CsvException("A quoted field is not closed (around line $line).")
        if (field.isNotEmpty() || row.isNotEmpty() || fieldStarted) endRow()
        return rows
    }

    private val FORMULA_START = setOf('=', '+', '-', '@', '\t', '\r')

    /**
     * Text that a spreadsheet would run as a formula ("=HYPERLINK(...)") is
     * prefixed with an apostrophe, which spreadsheets hide; [readText] removes it.
     */
    fun safeText(value: String): String = if (value.isNotEmpty() && value[0] in FORMULA_START) "'$value" else value

    fun readText(value: String): String =
        if (value.length >= 2 && value[0] == '\'' && value[1] in FORMULA_START) value.substring(1) else value
}
