package io.github.chiragbhatn.expensetracker.domain

import java.util.Locale

private val WHITESPACE = Regex("""\s+""")

/** Trims a merchant or person name and collapses inner whitespace. */
fun cleanName(name: String): String = name.trim().replace(WHITESPACE, " ")

/** Case-insensitive identity of a name: "  swiggy " and "Swiggy" are the same merchant. */
fun nameKey(name: String): String = cleanName(name).lowercase(Locale.ROOT)
