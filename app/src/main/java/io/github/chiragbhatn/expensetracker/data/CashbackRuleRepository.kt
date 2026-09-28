package io.github.chiragbhatn.expensetracker.data

import androidx.room.withTransaction
import io.github.chiragbhatn.expensetracker.data.db.AppDatabase
import io.github.chiragbhatn.expensetracker.data.db.CashbackRuleEntity
import io.github.chiragbhatn.expensetracker.domain.CashbackRule
import io.github.chiragbhatn.expensetracker.domain.Percentage
import io.github.chiragbhatn.expensetracker.domain.cleanName
import io.github.chiragbhatn.expensetracker.domain.nameKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface SaveRuleResult {
    data object Saved : SaveRuleResult
    data class DuplicateMerchant(val existingMerchant: String) : SaveRuleResult
}

class CashbackRuleRepository(private val database: AppDatabase) {
    private val rules = database.cashbackRuleDao()

    fun observeAll(): Flow<List<CashbackRule>> = rules.observeAll().map { list -> list.map { it.toDomain() } }

    /** Adds a rule when [id] is null, otherwise updates it. One rule per merchant, ignoring case. */
    suspend fun save(id: Long?, merchant: String, percentage: Percentage, enabled: Boolean): SaveRuleResult {
        val name = cleanName(merchant)
        require(name.isNotEmpty()) { "A merchant name is required" }
        return database.withTransaction {
            val existing = rules.findByKey(nameKey(name))
            if (existing != null && existing.id != id) {
                return@withTransaction SaveRuleResult.DuplicateMerchant(existing.merchant)
            }
            val rule = CashbackRuleEntity(
                id = id ?: 0,
                merchant = name,
                merchantKey = nameKey(name),
                cashbackPercentageBps = percentage.basisPoints,
                enabled = enabled,
            )
            if (id == null) rules.insert(rule) else rules.update(rule)
            SaveRuleResult.Saved
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = rules.setEnabled(id, enabled)

    suspend fun delete(id: Long) = rules.delete(id)
}
