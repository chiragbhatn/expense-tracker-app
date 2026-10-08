package io.github.chiragbhatn.expensetracker.domain

// Stored by name, so constants must not be renamed.
enum class CategoryKind { EXPENSE, INCOME }

data class Category(
    val id: Long,
    val uuid: String,
    val name: String,
    val kind: CategoryKind,
    val isDefault: Boolean,
)

object DefaultCategories {
    /** Used when a category is missing or deleted. */
    const val OTHER = "Other"

    val expense = listOf(
        "Food", "Groceries", "Shopping", "Transport", "Fuel", "Bills", "Entertainment",
        "Health", "Travel", "Education", "Rent", "Subscription", OTHER,
    )

    val income = listOf("Salary", "Business", "Freelance", "Interest", "Gift", "Refund", OTHER)

    fun forKind(kind: CategoryKind) = if (kind == CategoryKind.EXPENSE) expense else income
}
