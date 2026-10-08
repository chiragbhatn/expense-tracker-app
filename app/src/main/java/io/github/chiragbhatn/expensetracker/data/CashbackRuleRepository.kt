package io.github.chiragbhatn.expensetracker.data

import androidx.room.withTransaction
import io.github.chiragbhatn.expensetracker.data.db.AppDatabase
import io.github.chiragbhatn.expensetracker.data.db.CashbackRuleEntity
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.cleanName
import io.github.chiragbhatn.expensetracker.domain.nameKey
import io.github.chiragbhatn.expensetracker.domain.newUuid

sealed interface SaveRuleResult {
    data object Saved : SaveRuleResult
    data class DuplicateMerchant(val existingMerchant: String) : SaveRuleResult
}

class CashbackRuleRepository(private val database: AppDatabase, private val clock: AppClock) {
    private val rules = database.cashbackRuleDao()

    /**
     * Adds a rule when [id] is null, otherwise updates it. A merchant has at
     * most one rule for any card and one per specific card, ignoring case.
     */
    suspend fun save(id: Long?, merchant: String, percentage: Percentage, enabled: Boolean, cardId: Long? = null): SaveRuleResult {
        val name = cleanName(merchant)
        require(name.isNotEmpty()) { "A merchant name is required" }
        val now = clock.now()
        return database.withTransaction {
            val existing = rules.find(nameKey(name), cardId)
            if (existing != null && existing.id != id) {
                return@withTransaction SaveRuleResult.DuplicateMerchant(existing.merchant)
            }
            val current = id?.let { ruleId -> rules.getAll().firstOrNull { it.id == ruleId } }
            val rule = CashbackRuleEntity(
                id = id ?: 0,
                merchant = name,
                merchantKey = nameKey(name),
                cashbackPercentageBps = percentage.basisPoints,
                enabled = enabled,
                cardId = cardId,
                uuid = current?.uuid ?: newUuid(),
                createdAt = current?.createdAt ?: now,
                updatedAt = now,
            )
            if (current == null) rules.insert(rule) else rules.update(rule)
            SaveRuleResult.Saved
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = rules.setEnabled(id, enabled, clock.now())

    suspend fun delete(id: Long) = rules.delete(id)
}
