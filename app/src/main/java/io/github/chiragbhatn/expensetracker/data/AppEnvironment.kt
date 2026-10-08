package io.github.chiragbhatn.expensetracker.data

import java.io.File
import java.time.LocalDate

/** The current time, replaceable in tests. */
interface AppClock {
    fun now(): Long
    fun today(): LocalDate
}

object SystemClock : AppClock {
    override fun now(): Long = System.currentTimeMillis()
    override fun today(): LocalDate = LocalDate.now()
}

/** Where the app keeps files: receipts, profile photos, safety backups and files being shared. */
class AppFiles(private val filesDir: File, private val cacheDir: File) {
    val attachments: File get() = File(filesDir, "attachments").apply { mkdirs() }
    val photos: File get() = File(filesDir, "photos").apply { mkdirs() }
    val backups: File get() = File(filesDir, "backups").apply { mkdirs() }

    /** Files handed to other apps through the share sheet; cleared regularly. */
    val shared: File get() = File(cacheDir, "shared").apply { mkdirs() }

    fun attachment(name: String) = File(attachments, name)
    fun photo(name: String) = File(photos, name)
}
