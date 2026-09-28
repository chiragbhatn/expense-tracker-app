package io.github.chiragbhatn.expensetracker.data

import androidx.room.withTransaction
import io.github.chiragbhatn.expensetracker.data.db.AppDatabase
import io.github.chiragbhatn.expensetracker.data.db.PersonEntity
import io.github.chiragbhatn.expensetracker.data.db.UdhaarEntryEntity
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.Person
import io.github.chiragbhatn.expensetracker.domain.PersonBalance
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import io.github.chiragbhatn.expensetracker.domain.UdhaarEntry
import io.github.chiragbhatn.expensetracker.domain.balances
import io.github.chiragbhatn.expensetracker.domain.cleanName
import io.github.chiragbhatn.expensetracker.domain.nameKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class UdhaarRepository(private val database: AppDatabase) {
    private val people = database.personDao()
    private val entries = database.udhaarDao()

    fun observePeople(): Flow<List<Person>> = people.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeBalances(): Flow<List<PersonBalance>> =
        combine(people.observeAll(), entries.observeAll()) { allPeople, allEntries ->
            balances(allPeople.map { it.toDomain() }, allEntries.map { it.toDomain() })
        }

    fun observePerson(id: Long): Flow<Person?> = people.observe(id).map { it?.toDomain() }

    fun observeEntries(personId: Long): Flow<List<UdhaarEntry>> =
        entries.observeForPerson(personId).map { rows -> rows.map { it.toDomain() } }

    /** Returns the id of the person with this name, adding them first if they are new. */
    suspend fun addPerson(name: String): Long {
        val cleaned = cleanName(name)
        require(cleaned.isNotEmpty()) { "A name is required" }
        return database.withTransaction {
            people.findByKey(nameKey(cleaned))?.id
                ?: people.insert(PersonEntity(name = cleaned, nameKey = nameKey(cleaned)))
        }
    }

    /** Renames a person; returns false if someone else already has that name. */
    suspend fun renamePerson(id: Long, name: String): Boolean {
        val cleaned = cleanName(name)
        require(cleaned.isNotEmpty()) { "A name is required" }
        return database.withTransaction {
            val clash = people.findByKey(nameKey(cleaned))
            if (clash != null && clash.id != id) {
                false
            } else {
                people.update(PersonEntity(id = id, name = cleaned, nameKey = nameKey(cleaned)))
                true
            }
        }
    }

    /** Deletes the person and their udhaar history. Expenses paid for them stay as the user's expenses. */
    suspend fun deletePerson(id: Long) = database.withTransaction {
        entries.deleteForPerson(id)
        people.delete(id)
    }

    suspend fun addEntry(personId: Long, direction: UdhaarDirection, amount: Money, date: LocalDate, note: String) {
        require(amount.isPositive) { "Udhaar amounts must be positive" }
        entries.insert(
            UdhaarEntryEntity(
                personId = personId,
                direction = direction,
                amountPaise = amount.paise,
                dateEpochDay = date.toEpochDay(),
                note = note.trim(),
                expenseId = null,
            ),
        )
    }

    /** Deletes an entry added on the udhaar screen. Entries created by an expense change with that expense. */
    suspend fun deleteEntry(entry: UdhaarEntry) {
        require(entry.expenseId == null) { "Edit or delete the expense instead" }
        entries.delete(entry.id)
    }
}
