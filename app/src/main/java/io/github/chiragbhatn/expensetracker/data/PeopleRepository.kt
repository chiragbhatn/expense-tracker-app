package io.github.chiragbhatn.expensetracker.data

import androidx.room.withTransaction
import io.github.chiragbhatn.expensetracker.data.db.AppDatabase
import io.github.chiragbhatn.expensetracker.data.db.PersonEntity
import io.github.chiragbhatn.expensetracker.data.db.ReminderEntity
import io.github.chiragbhatn.expensetracker.data.db.UdhaarEntryEntity
import io.github.chiragbhatn.expensetracker.domain.LedgerSummary
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.Settlement
import io.github.chiragbhatn.expensetracker.domain.SettlementKind
import io.github.chiragbhatn.expensetracker.domain.UdhaarDirection
import io.github.chiragbhatn.expensetracker.domain.cleanName
import io.github.chiragbhatn.expensetracker.domain.nameKey
import io.github.chiragbhatn.expensetracker.domain.newUuid
import java.io.File
import java.time.LocalDate

data class PersonInput(
    val name: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
)

sealed interface SavePersonResult {
    data class Saved(val id: Long) : SavePersonResult
    data class NameTaken(val existingName: String) : SavePersonResult
}

data class SettlementResult(val kind: SettlementKind, val direction: UdhaarDirection, val after: LedgerSummary)

/** People, their ledgers and reminders about them. */
class PeopleRepository(
    private val database: AppDatabase,
    private val clock: AppClock,
    private val files: AppFiles,
) {
    private val people = database.personDao()
    private val entries = database.udhaarDao()
    private val reminders = database.reminderDao()

    /** Returns the id of the person with this name, adding them first if they are new. */
    suspend fun addPerson(name: String): Long {
        val cleaned = cleanName(name)
        require(cleaned.isNotEmpty()) { "A name is required" }
        return database.withTransaction {
            people.findByKey(nameKey(cleaned))?.id ?: people.insert(newPerson(PersonInput(cleaned)))
        }
    }

    /** Adds a person when [id] is null, otherwise updates them. Names are unique, ignoring case. */
    suspend fun save(id: Long?, input: PersonInput): SavePersonResult {
        val cleaned = cleanName(input.name)
        require(cleaned.isNotEmpty()) { "A name is required" }
        return database.withTransaction {
            val clash = people.findByKey(nameKey(cleaned))
            if (clash != null && clash.id != id) return@withTransaction SavePersonResult.NameTaken(clash.name)
            if (id == null) {
                SavePersonResult.Saved(people.insert(newPerson(input.copy(name = cleaned))))
            } else {
                val current = requireNotNull(people.get(id)) { "Person $id no longer exists" }
                people.update(
                    current.copy(
                        name = cleaned,
                        nameKey = nameKey(cleaned),
                        phone = input.phone.trim(),
                        email = input.email.trim(),
                        address = input.address.trim(),
                        notes = input.notes.trim(),
                        tags = joinTags(input.tags),
                        updatedAt = clock.now(),
                    ),
                )
                SavePersonResult.Saved(id)
            }
        }
    }

    private fun newPerson(input: PersonInput): PersonEntity {
        val now = clock.now()
        return PersonEntity(
            name = input.name,
            nameKey = nameKey(input.name),
            phone = input.phone.trim(),
            email = input.email.trim(),
            address = input.address.trim(),
            notes = input.notes.trim(),
            tags = joinTags(input.tags),
            uuid = newUuid(),
            createdAt = now,
            updatedAt = now,
        )
    }

    /** Sets the profile photo to [photo] (already in app storage), or removes it when null. */
    suspend fun setPhoto(id: Long, photo: File?) {
        val current = people.get(id) ?: return
        people.update(current.copy(photoPath = photo?.name, updatedAt = clock.now()))
        current.photoPath?.takeIf { it != photo?.name }?.let { files.photo(it).delete() }
    }

    /**
     * Deletes the person with their ledger and reminders. Expenses that were
     * shared with them stay; the person's share becomes the user's own.
     */
    suspend fun delete(id: Long) {
        val photo = people.get(id)?.photoPath
        database.withTransaction {
            entries.forPerson(id).forEach { entries.delete(it.id) }
            people.delete(id)
        }
        photo?.let { files.photo(it).delete() }
    }

    /** Records a ledger entry other than an expense share. Returns its id. */
    suspend fun addEntry(personId: Long, type: LedgerType, direction: UdhaarDirection, amount: Money, date: LocalDate, note: String): Long {
        require(type != LedgerType.EXPENSE_SHARE) { "Expense shares are recorded with their expense" }
        require(amount.isPositive) { "Amounts must be more than zero" }
        val now = clock.now()
        return entries.insert(
            UdhaarEntryEntity(
                personId = personId,
                direction = type.fixedDirection ?: direction,
                amountPaise = amount.paise,
                dateEpochDay = date.toEpochDay(),
                note = note.trim(),
                expenseId = null,
                type = type,
                uuid = newUuid(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateEntry(id: Long, type: LedgerType, direction: UdhaarDirection, amount: Money, date: LocalDate, note: String) {
        require(type != LedgerType.EXPENSE_SHARE) { "Expense shares are changed with their expense" }
        require(amount.isPositive) { "Amounts must be more than zero" }
        val current = requireNotNull(entries.get(id)) { "Entry $id no longer exists" }
        require(current.type != LedgerType.EXPENSE_SHARE) { "Edit the expense instead" }
        entries.update(
            current.copy(
                type = type,
                direction = type.fixedDirection ?: direction,
                amountPaise = amount.paise,
                dateEpochDay = date.toEpochDay(),
                note = note.trim(),
                updatedAt = clock.now(),
            ),
        )
    }

    /** Deletes an entry recorded on the person's page. Expense shares change with their expense. */
    suspend fun deleteEntry(id: Long) {
        val current = entries.get(id) ?: return
        require(current.type != LedgerType.EXPENSE_SHARE) { "Edit or delete the expense instead" }
        entries.delete(id)
    }

    /**
     * Settles up: when they owe you, they pay you [amount]; when you owe them
     * (or they hold credit), you pay them. Paying more than the balance leaves
     * the extra as credit, never as a negative expense.
     */
    suspend fun settle(personId: Long, amount: Money, date: LocalDate, note: String): SettlementResult {
        require(amount.isPositive) { "A settlement must be more than zero" }
        return database.withTransaction {
            val before = LedgerSummary.of(entries.forPerson(personId).map { it.toDomain(null) })
            val direction = Settlement.directionFor(before)
            val kind = Settlement.kind(before, amount)
            addEntry(personId, LedgerType.SETTLEMENT, direction, amount, date, note)
            SettlementResult(kind, direction, LedgerSummary.of(entries.forPerson(personId).map { it.toDomain(null) }))
        }
    }

    suspend fun addReminder(title: String, note: String, dueDate: LocalDate, personId: Long?): Long {
        val cleaned = cleanName(title)
        require(cleaned.isNotEmpty()) { "A reminder needs a title" }
        val now = clock.now()
        return reminders.insert(
            ReminderEntity(
                title = cleaned,
                note = note.trim(),
                dueEpochDay = dueDate.toEpochDay(),
                personId = personId,
                done = false,
                uuid = newUuid(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun setReminderDone(id: Long, done: Boolean) {
        val current = reminders.getAll().firstOrNull { it.id == id } ?: return
        reminders.update(current.copy(done = done, updatedAt = clock.now()))
    }

    suspend fun deleteReminder(id: Long) = reminders.delete(id)
}
