package io.github.chiragbhatn.expensetracker.backup

import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.domain.nameKey

/** How many records of one kind a merge adds, updates or leaves as they are. */
data class TableMerge(val added: Int = 0, val updated: Int = 0, val unchanged: Int = 0) {
    val total: Int get() = added + updated + unchanged
}

data class MergeResult(val data: BackupData, val stats: Map<String, TableMerge>) {
    val added: Int get() = stats.values.sumOf { it.added }
    val updated: Int get() = stats.values.sumOf { it.updated }
}

/**
 * Combines a backup with the data already on this device without creating
 * duplicates. Records are matched by their stable ID; people, categories,
 * cards and cashback rules are also matched by name, since the same person or
 * card may have been added separately on each device. When both sides have a
 * record, the more recently updated version wins. Nothing is deleted, and the
 * device's own settings are kept.
 */
object BackupMerger {
    fun merge(existing: BackupData, incoming: BackupData): MergeResult {
        val stats = linkedMapOf<String, TableMerge>()

        val people = Matcher(existing.people, { it.uuid }, { it.updatedAt }, { nameKey(it.name) })
        incoming.people.forEach { person ->
            people.offer(person, nameKey(person.name)) { winner, loser ->
                // Keep the existing name if the newer one belongs to someone else on this device.
                val name = if (people.nameTakenByOther(nameKey(winner.name), loser.uuid)) loser.name else winner.name
                winner.copy(uuid = loser.uuid, id = loser.id, name = name, createdAt = minPositive(winner.createdAt, loser.createdAt))
            }
        }
        stats[BackupWorkbook.PEOPLE] = people.stats

        val categories = Matcher(existing.categories, { it.uuid }, { it.updatedAt }, { nameKey(it.name) + "|" + it.kind })
        incoming.categories.forEach { category ->
            categories.offer(category, nameKey(category.name) + "|" + category.kind) { winner, loser ->
                winner.copy(uuid = loser.uuid, id = loser.id, isDefault = loser.isDefault || winner.isDefault, createdAt = minPositive(winner.createdAt, loser.createdAt))
            }
        }
        stats[BackupWorkbook.CATEGORIES] = categories.stats

        fun cardKey(card: CardRecord) = if (card.lastFour.isBlank()) null else card.lastFour + "|" + nameKey(card.bank)
        val cards = Matcher(existing.cards, { it.uuid }, { it.updatedAt }, ::cardKey)
        incoming.cards.forEach { card ->
            cards.offer(card, cardKey(card)) { winner, loser -> winner.copy(uuid = loser.uuid, id = loser.id, createdAt = minPositive(winner.createdAt, loser.createdAt)) }
        }
        stats[BackupWorkbook.CARDS] = cards.stats
        fun card(uuid: String?) = uuid?.let { cards.mapped(it) }

        fun ruleKey(rule: CashbackRuleRecord) = nameKey(rule.merchant) + "|" + rule.cardUuid.orEmpty()
        val rules = Matcher(existing.cashbackRules, { it.uuid }, { it.updatedAt }, ::ruleKey)
        incoming.cashbackRules.map { it.copy(cardUuid = card(it.cardUuid)) }.forEach { rule ->
            rules.offer(rule, ruleKey(rule)) { winner, loser -> winner.copy(uuid = loser.uuid, id = loser.id, createdAt = minPositive(winner.createdAt, loser.createdAt)) }
        }
        stats[BackupWorkbook.RULES] = rules.stats

        val recurring = Matcher(existing.recurring, { it.uuid }, { it.updatedAt })
        incoming.recurring.map { it.copy(cardUuid = card(it.cardUuid)) }.forEach { item ->
            recurring.offer(item) { winner, loser -> winner.copy(uuid = loser.uuid, id = loser.id) }
        }
        stats[BackupWorkbook.RECURRING] = recurring.stats

        val expenses = Matcher(existing.expenses, { it.uuid }, { it.updatedAt })
        val incomingWins = mutableSetOf<String>()
        incoming.expenses.map { it.copy(cardUuid = card(it.cardUuid), recurringUuid = it.recurringUuid?.let(recurring::mapped)) }.forEach { expense ->
            expenses.offer(expense) { winner, loser ->
                if (winner === expense) incomingWins += loser.uuid
                winner.copy(uuid = loser.uuid, id = loser.id)
            }
            if (expenses.wasAdded(expense.uuid)) incomingWins += expense.uuid
        }
        stats[BackupWorkbook.EXPENSES] = expenses.stats

        val incomes = Matcher(existing.incomes, { it.uuid }, { it.updatedAt })
        incoming.incomes.forEach { income -> incomes.offer(income) { winner, loser -> winner.copy(uuid = loser.uuid, id = loser.id) } }
        stats[BackupWorkbook.INCOME] = incomes.stats

        // Ledger entries other than expense shares are matched one by one.
        val remappedLedger = incoming.ledger.map { it.copy(personUuid = people.mapped(it.personUuid), expenseUuid = it.expenseUuid?.let(expenses::mapped)) }
        val (incomingShares, incomingOther) = remappedLedger.partition { it.type == LedgerType.EXPENSE_SHARE }
        val (existingShares, existingOther) = existing.ledger.partition { it.type == LedgerType.EXPENSE_SHARE }
        val ledger = Matcher(existingOther, { it.uuid }, { it.updatedAt })
        incomingOther.forEach { entry -> ledger.offer(entry) { winner, loser -> winner.copy(uuid = loser.uuid, id = loser.id) } }

        // Expense shares follow their expense, so its shares always add up as they were saved.
        val existingShareIds = existingShares.associateBy { it.uuid }
        val shares = expenses.result().flatMap { expense ->
            if (expense.uuid in incomingWins) {
                incomingShares.filter { it.expenseUuid == expense.uuid }.map { share ->
                    existingShareIds[share.uuid]?.let { share.copy(id = it.id) } ?: share
                }
            } else {
                existingShares.filter { it.expenseUuid == expense.uuid }
            }
        }
        val shareStats = run {
            val before = existingShares.associateBy { it.uuid }
            val added = shares.count { it.uuid !in before }
            val updated = shares.count { share -> before[share.uuid]?.let { it != share } == true }
            TableMerge(added, updated, shares.size - added - updated)
        }
        val ledgerStats = ledger.statsBy { entry -> sheetFor(entry.type) }
        listOf(BackupWorkbook.LEDGER, BackupWorkbook.PAYMENTS, BackupWorkbook.SETTLEMENTS).forEach { sheet ->
            val base = ledgerStats[sheet] ?: TableMerge()
            stats[sheet] = if (sheet == BackupWorkbook.LEDGER) {
                TableMerge(base.added + shareStats.added, base.updated + shareStats.updated, base.unchanged + shareStats.unchanged)
            } else {
                base
            }
        }

        val cardPayments = Matcher(existing.cardPayments, { it.uuid }, { it.updatedAt })
        incoming.cardPayments.map { it.copy(cardUuid = cards.mapped(it.cardUuid)) }.forEach { payment ->
            cardPayments.offer(payment) { winner, loser -> winner.copy(uuid = loser.uuid, id = loser.id) }
        }
        stats[BackupWorkbook.CARD_PAYMENTS] = cardPayments.stats

        val reminders = Matcher(existing.reminders, { it.uuid }, { it.updatedAt })
        incoming.reminders.map { it.copy(personUuid = it.personUuid?.let(people::mapped)) }.forEach { reminder ->
            reminders.offer(reminder) { winner, loser -> winner.copy(uuid = loser.uuid, id = loser.id) }
        }
        stats[BackupWorkbook.REMINDERS] = reminders.stats

        val attachments = Matcher(existing.attachments, { it.uuid }, { it.updatedAt })
        incoming.attachments
            .map { it.copy(expenseUuid = it.expenseUuid?.let(expenses::mapped), personUuid = it.personUuid?.let(people::mapped)) }
            .forEach { attachment -> attachments.offer(attachment) { winner, loser -> winner.copy(uuid = loser.uuid, id = loser.id) } }
        stats[BackupWorkbook.ATTACHMENTS] = attachments.stats

        val merged = BackupData(
            settings = existing.settings,
            people = people.result(),
            categories = categories.result(),
            cards = cards.result(),
            cashbackRules = rules.result(),
            expenses = expenses.result(),
            incomes = incomes.result(),
            ledger = ledger.result() + shares,
            cardPayments = cardPayments.result(),
            recurring = recurring.result(),
            reminders = reminders.result(),
            attachments = attachments.result(),
        )
        return MergeResult(merged, stats)
    }

    private fun sheetFor(type: LedgerType) = when (type) {
        LedgerType.PAYMENT_RECEIVED, LedgerType.PAYMENT_MADE -> BackupWorkbook.PAYMENTS
        LedgerType.SETTLEMENT -> BackupWorkbook.SETTLEMENTS
        else -> BackupWorkbook.LEDGER
    }

    private fun minPositive(a: Long, b: Long) = listOf(a, b).filter { it > 0 }.minOrNull() ?: 0

    /**
     * Collects one table's merged records. Incoming records are matched to
     * existing ones by uuid, then by [naturalKey]; [mapped] translates an
     * incoming uuid to the uuid the record ends up with.
     */
    private class Matcher<T : Any>(
        existing: List<T>,
        private val uuidOf: (T) -> String,
        private val updatedAtOf: (T) -> Long,
        private val naturalKey: ((T) -> String?)? = null,
    ) {
        private val records = LinkedHashMap<String, T>().apply { existing.forEach { put(uuidOf(it), it) } }
        private val original = records.toMap()
        private val byKey = HashMap<String, String>().apply {
            if (naturalKey != null) existing.forEach { record -> naturalKey.invoke(record)?.let { put(it, uuidOf(record)) } }
        }
        private val uuidMap = HashMap<String, String>()
        private val added = mutableSetOf<String>()
        private val updated = mutableSetOf<String>()
        private val seen = mutableSetOf<String>()

        fun offer(record: T, key: String? = null, combine: (winner: T, loser: T) -> T) {
            val uuid = uuidOf(record)
            val matchUuid = when {
                uuid in records -> uuid
                key != null -> byKey[key]
                else -> null
            }
            if (matchUuid == null) {
                records[uuid] = record
                key?.let { byKey[it] = uuid }
                uuidMap[uuid] = uuid
                added += uuid
                return
            }
            uuidMap[uuid] = matchUuid
            val current = records.getValue(matchUuid)
            seen += matchUuid
            if (updatedAtOf(record) > updatedAtOf(current)) {
                val combined = combine(record, current)
                if (combined != current) {
                    records[matchUuid] = combined
                    if (matchUuid in original) updated += matchUuid
                }
            } else {
                combine(current, record)
            }
        }

        fun mapped(uuid: String): String = uuidMap[uuid] ?: uuid

        fun wasAdded(uuid: String) = uuid in added

        fun nameTakenByOther(key: String, uuid: String): Boolean = byKey[key]?.let { it != uuid } == true

        fun result(): List<T> = records.values.toList()

        val stats: TableMerge get() = TableMerge(added.size, updated.size, seen.count { it !in updated && it in original })

        fun statsBy(group: (T) -> String): Map<String, TableMerge> =
            records.values.groupBy(group).mapValues { (_, items) ->
                val ids = items.map(uuidOf)
                TableMerge(ids.count { it in added }, ids.count { it in updated }, ids.count { it in seen && it !in updated && it in original })
            }
    }
}
