package io.github.chiragbhatn.expensetracker.data

import androidx.room.withTransaction
import io.github.chiragbhatn.expensetracker.data.db.AppDatabase
import io.github.chiragbhatn.expensetracker.data.db.CategoryEntity
import io.github.chiragbhatn.expensetracker.domain.CategoryKind
import io.github.chiragbhatn.expensetracker.domain.DefaultCategories
import io.github.chiragbhatn.expensetracker.domain.cleanName
import io.github.chiragbhatn.expensetracker.domain.nameKey
import io.github.chiragbhatn.expensetracker.domain.newUuid

sealed interface CategoryResult {
    data object Done : CategoryResult
    data class Exists(val name: String) : CategoryResult
    data object Protected : CategoryResult
}

class CategoryRepository(private val database: AppDatabase, private val clock: AppClock) {
    private val categories = database.categoryDao()

    /**
     * The saved spelling of category [name], adding it as a custom category
     * when it is new. A blank name means "Other".
     */
    suspend fun ensure(name: String, kind: CategoryKind): String {
        val cleaned = cleanName(name).ifEmpty { DefaultCategories.OTHER }
        categories.find(nameKey(cleaned), kind.name)?.let { return it.name }
        val now = clock.now()
        categories.insert(CategoryEntity(name = cleaned, nameKey = nameKey(cleaned), kind = kind, isDefault = false, uuid = newUuid(), createdAt = now, updatedAt = now))
        return cleaned
    }

    suspend fun add(name: String, kind: CategoryKind): CategoryResult {
        val cleaned = cleanName(name)
        require(cleaned.isNotEmpty()) { "A name is required" }
        categories.find(nameKey(cleaned), kind.name)?.let { return CategoryResult.Exists(it.name) }
        ensure(cleaned, kind)
        return CategoryResult.Done
    }

    /** Renames a category everywhere it is used. */
    suspend fun rename(id: Long, name: String): CategoryResult {
        val cleaned = cleanName(name)
        require(cleaned.isNotEmpty()) { "A name is required" }
        return database.withTransaction {
            val current = categories.getAll().firstOrNull { it.id == id } ?: return@withTransaction CategoryResult.Done
            if (current.nameKey == nameKey(DefaultCategories.OTHER)) return@withTransaction CategoryResult.Protected
            val clash = categories.find(nameKey(cleaned), current.kind.name)
            if (clash != null && clash.id != id) return@withTransaction CategoryResult.Exists(clash.name)
            val now = clock.now()
            categories.update(current.copy(name = cleaned, nameKey = nameKey(cleaned), updatedAt = now))
            moveUsages(current, cleaned, now)
            CategoryResult.Done
        }
    }

    /** Deletes a category; what used it moves to "Other". "Other" itself cannot be deleted. */
    suspend fun delete(id: Long): CategoryResult = database.withTransaction {
        val current = categories.getAll().firstOrNull { it.id == id } ?: return@withTransaction CategoryResult.Done
        if (current.nameKey == nameKey(DefaultCategories.OTHER)) return@withTransaction CategoryResult.Protected
        categories.delete(id)
        ensure(DefaultCategories.OTHER, current.kind)
        moveUsages(current, DefaultCategories.OTHER, clock.now())
        CategoryResult.Done
    }

    private suspend fun moveUsages(category: CategoryEntity, to: String, now: Long) {
        when (category.kind) {
            CategoryKind.EXPENSE -> {
                database.expenseDao().renameCategory(category.name, to, now)
                database.recurringDao().renameCategory(category.name, to, now)
            }
            CategoryKind.INCOME -> database.incomeDao().renameCategory(category.name, to, now)
        }
    }
}
