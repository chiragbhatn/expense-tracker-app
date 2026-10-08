package io.github.chiragbhatn.expensetracker.data

import androidx.room.withTransaction
import io.github.chiragbhatn.expensetracker.data.db.AppDatabase
import io.github.chiragbhatn.expensetracker.data.db.CardPaymentEntity
import io.github.chiragbhatn.expensetracker.data.db.CreditCardEntity
import io.github.chiragbhatn.expensetracker.domain.Money
import io.github.chiragbhatn.expensetracker.domain.cleanName
import io.github.chiragbhatn.expensetracker.domain.newUuid
import java.time.LocalDate

data class CardInput(
    val name: String,
    val bank: String,
    val lastFour: String,
    val creditLimit: Money,
    val statementDay: Int,
    val dueDay: Int,
    val notes: String = "",
    val active: Boolean = true,
    val reminderDaysBefore: Int? = null,
)

class CardRepository(private val database: AppDatabase, private val clock: AppClock) {
    private val cards = database.cardDao()

    suspend fun save(id: Long?, input: CardInput): Long {
        val name = cleanName(input.name)
        require(name.isNotEmpty()) { "A card name is required" }
        require(input.lastFour.isEmpty() || Regex("""\d{4}""").matches(input.lastFour)) { "Last four digits must be 4 digits" }
        require(input.statementDay in 1..31 && input.dueDay in 1..31) { "Days must be from 1 to 31" }
        require(!input.creditLimit.isNegative) { "The credit limit cannot be negative" }
        val now = clock.now()
        return database.withTransaction {
            val current = id?.let { cards.get(it) }
            val entity = CreditCardEntity(
                id = id ?: 0,
                name = name,
                bank = cleanName(input.bank),
                lastFour = input.lastFour,
                creditLimitPaise = input.creditLimit.paise,
                statementDay = input.statementDay,
                dueDay = input.dueDay,
                notes = input.notes.trim(),
                active = input.active,
                reminderDaysBefore = input.reminderDaysBefore,
                uuid = current?.uuid ?: newUuid(),
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
            )
            if (current == null) cards.insert(entity) else entity.id.also { cards.update(entity) }
        }
    }

    /**
     * Deletes the card and its bill payments. Its expenses stay, without the
     * card link; rules that were only for this card are removed.
     */
    suspend fun delete(id: Long) = database.withTransaction {
        database.expenseDao().clearCard(id)
        database.cashbackRuleDao().deleteForCard(id)
        cards.delete(id)
    }

    /** Records paying the card's bill. */
    suspend fun addPayment(cardId: Long, amount: Money, date: LocalDate, note: String): Long {
        require(amount.isPositive) { "A payment must be more than zero" }
        val now = clock.now()
        return cards.insertPayment(
            CardPaymentEntity(
                cardId = cardId,
                amountPaise = amount.paise,
                dateEpochDay = date.toEpochDay(),
                note = note.trim(),
                uuid = newUuid(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun deletePayment(id: Long) = cards.deletePayment(id)
}
