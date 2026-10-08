package io.github.chiragbhatn.expensetracker.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.chiragbhatn.expensetracker.domain.LedgerType
import io.github.chiragbhatn.expensetracker.ui.backup.BackupScreen
import io.github.chiragbhatn.expensetracker.ui.backup.CsvScreen
import io.github.chiragbhatn.expensetracker.ui.cards.CardDetailScreen
import io.github.chiragbhatn.expensetracker.ui.cards.CardFormScreen
import io.github.chiragbhatn.expensetracker.ui.cards.CardsScreen
import io.github.chiragbhatn.expensetracker.ui.components.AddActions
import io.github.chiragbhatn.expensetracker.ui.expenses.ExpenseFormScreen
import io.github.chiragbhatn.expensetracker.ui.expenses.ExpenseFormViewModel
import io.github.chiragbhatn.expensetracker.ui.expenses.ExpensesScreen
import io.github.chiragbhatn.expensetracker.ui.expenses.IncomeFormScreen
import io.github.chiragbhatn.expensetracker.ui.home.HomeActions
import io.github.chiragbhatn.expensetracker.ui.home.HomeScreen
import io.github.chiragbhatn.expensetracker.ui.recurring.RecurringFormScreen
import io.github.chiragbhatn.expensetracker.ui.recurring.RecurringScreen
import io.github.chiragbhatn.expensetracker.ui.reports.CashbackScreen
import io.github.chiragbhatn.expensetracker.ui.reports.ReportsScreen
import io.github.chiragbhatn.expensetracker.ui.rules.CashbackRulesScreen
import io.github.chiragbhatn.expensetracker.ui.search.SearchActions
import io.github.chiragbhatn.expensetracker.ui.search.SearchScreen
import io.github.chiragbhatn.expensetracker.ui.settings.AboutScreen
import io.github.chiragbhatn.expensetracker.ui.settings.CategoriesScreen
import io.github.chiragbhatn.expensetracker.ui.settings.DataScreen
import io.github.chiragbhatn.expensetracker.ui.settings.RemindersScreen
import io.github.chiragbhatn.expensetracker.ui.settings.SettingsActions
import io.github.chiragbhatn.expensetracker.ui.settings.SettingsScreen
import io.github.chiragbhatn.expensetracker.ui.settings.ShareTemplatesScreen
import io.github.chiragbhatn.expensetracker.ui.udhaar.EntryFormScreen
import io.github.chiragbhatn.expensetracker.ui.udhaar.PersonActions
import io.github.chiragbhatn.expensetracker.ui.udhaar.PersonFormScreen
import io.github.chiragbhatn.expensetracker.ui.udhaar.PersonScreen
import io.github.chiragbhatn.expensetracker.ui.udhaar.SettleScreen
import io.github.chiragbhatn.expensetracker.ui.udhaar.ShareBalanceScreen
import io.github.chiragbhatn.expensetracker.ui.udhaar.UdhaarScreen

object Routes {
    const val HOME = "home"
    const val EXPENSES = "expenses"
    const val UDHAAR = "udhaar"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    const val EXPENSE = "expense?${ExpenseFormViewModel.ARG_EXPENSE_ID}={${ExpenseFormViewModel.ARG_EXPENSE_ID}}" +
        "&${ExpenseFormViewModel.ARG_PERSON_ID}={${ExpenseFormViewModel.ARG_PERSON_ID}}&${ExpenseFormViewModel.ARG_SCAN}={${ExpenseFormViewModel.ARG_SCAN}}"
    const val INCOME = "income?incomeId={incomeId}"
    const val ENTRY = "entry?personId={personId}&type={type}&entryId={entryId}"
    const val PERSON = "person/{personId}"
    const val PERSON_EDIT = "personEdit?personId={personId}"
    const val SHARE_BALANCE = "shareBalance/{personId}"
    const val SETTLE = "settle/{personId}"
    const val CARDS = "cards"
    const val CARD = "card/{cardId}"
    const val CARD_EDIT = "cardEdit?cardId={cardId}"
    const val CASHBACK = "cashback"
    const val RULES = "rules"
    const val RECURRING = "recurring"
    const val RECURRING_EDIT = "recurringEdit?recurringId={recurringId}"
    const val CATEGORIES = "categories"
    const val REMINDERS = "reminders"
    const val SHARE_WORDING = "shareWording"
    const val SEARCH = "search"
    const val BACKUP = "backup"
    const val CSV = "csv"
    const val DATA = "data"
    const val ABOUT = "about"

    fun expense(id: Long? = null, personId: Long? = null, scan: Boolean = false) =
        "expense?${ExpenseFormViewModel.ARG_EXPENSE_ID}=${id ?: -1}&${ExpenseFormViewModel.ARG_PERSON_ID}=${personId ?: -1}&${ExpenseFormViewModel.ARG_SCAN}=$scan"
    fun income(id: Long? = null) = "income?incomeId=${id ?: -1}"
    fun entry(personId: Long?, type: LedgerType, entryId: Long? = null) = "entry?personId=${personId ?: -1}&type=${type.name}&entryId=${entryId ?: -1}"
    fun person(id: Long) = "person/$id"
    fun personEdit(id: Long? = null) = "personEdit?personId=${id ?: -1}"
    fun shareBalance(personId: Long) = "shareBalance/$personId"
    fun settle(personId: Long) = "settle/$personId"
    fun card(id: Long) = "card/$id"
    fun cardEdit(id: Long? = null) = "cardEdit?cardId=${id ?: -1}"
    fun recurringEdit(id: Long? = null) = "recurringEdit?recurringId=${id ?: -1}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.EXPENSES, "Expenses", Icons.Filled.Receipt),
    Tab(Routes.UDHAAR, "Udhaar", Icons.Filled.People),
    Tab(Routes.REPORTS, "Reports", Icons.Filled.BarChart),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

private fun optionalLong(name: String) = navArgument(name) {
    type = NavType.LongType
    defaultValue = -1L
}

private fun NavBackStackEntry.long(name: String): Long? = arguments?.getLong(name)?.takeIf { it > 0 }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ExpenseTrackerNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val openTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val go: (String) -> Unit = { route -> navController.navigate(route) }
    val addActions = AddActions(
        expense = { go(Routes.expense()) },
        income = { go(Routes.income()) },
        udhaar = { go(Routes.entry(null, LedgerType.UDHAAR_GIVEN)) },
        payment = { go(Routes.entry(null, LedgerType.PAYMENT_RECEIVED)) },
    )
    val bottomBar: @Composable () -> Unit = {
        NavigationBar {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = currentRoute == tab.route,
                    onClick = { openTab(tab.route) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(tab.label) },
                    modifier = Modifier.testTag(TestTags.navTab(tab.route)),
                )
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                bottomBar = bottomBar,
                actions = HomeActions(
                    add = addActions,
                    addPerson = { go(Routes.personEdit()) },
                    openExpense = { go(Routes.expense(it)) },
                    openExpenses = { openTab(Routes.EXPENSES) },
                    openUdhaar = { openTab(Routes.UDHAAR) },
                    openCards = { go(Routes.CARDS) },
                    openCard = { go(Routes.card(it)) },
                    openRecurring = { go(Routes.RECURRING) },
                    openCashback = { go(Routes.CASHBACK) },
                    openReports = { openTab(Routes.REPORTS) },
                    search = { go(Routes.SEARCH) },
                ),
            )
        }
        composable(Routes.EXPENSES) {
            ExpensesScreen(
                bottomBar = bottomBar,
                addActions = addActions,
                onOpenExpense = { go(Routes.expense(it)) },
                onOpenIncome = { go(Routes.income(it)) },
                onSearch = { go(Routes.SEARCH) },
            )
        }
        composable(Routes.UDHAAR) {
            UdhaarScreen(
                bottomBar = bottomBar,
                addActions = addActions,
                onAddPerson = { go(Routes.personEdit()) },
                onOpenPerson = { go(Routes.person(it)) },
                onSearch = { go(Routes.SEARCH) },
            )
        }
        composable(Routes.REPORTS) {
            ReportsScreen(
                bottomBar = bottomBar,
                onOpenCashback = { go(Routes.CASHBACK) },
                onOpenPerson = { go(Routes.person(it)) },
                onOpenCards = { go(Routes.CARDS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                bottomBar = bottomBar,
                actions = SettingsActions(
                    cards = { go(Routes.CARDS) },
                    rules = { go(Routes.RULES) },
                    categories = { go(Routes.CATEGORIES) },
                    recurring = { go(Routes.RECURRING) },
                    reminders = { go(Routes.REMINDERS) },
                    shareWording = { go(Routes.SHARE_WORDING) },
                    backup = { go(Routes.BACKUP) },
                    csv = { go(Routes.CSV) },
                    data = { go(Routes.DATA) },
                    about = { go(Routes.ABOUT) },
                ),
            )
        }
        composable(
            Routes.EXPENSE,
            arguments = listOf(
                optionalLong(ExpenseFormViewModel.ARG_EXPENSE_ID),
                optionalLong(ExpenseFormViewModel.ARG_PERSON_ID),
                navArgument(ExpenseFormViewModel.ARG_SCAN) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            ExpenseFormScreen(onDone = { navController.popFrom(entry) }, startScan = entry.arguments?.getBoolean(ExpenseFormViewModel.ARG_SCAN) == true)
        }
        composable(Routes.INCOME, arguments = listOf(optionalLong("incomeId"))) { entry ->
            IncomeFormScreen(incomeId = entry.long("incomeId"), onDone = { navController.popFrom(entry) })
        }
        composable(
            Routes.ENTRY,
            arguments = listOf(
                optionalLong("personId"),
                optionalLong("entryId"),
                navArgument("type") {
                    type = NavType.StringType
                    defaultValue = LedgerType.UDHAAR_GIVEN.name
                },
            ),
        ) { entry ->
            val type = LedgerType.entries.firstOrNull { it.name == entry.arguments?.getString("type") } ?: LedgerType.UDHAAR_GIVEN
            EntryFormScreen(
                personId = entry.long("personId"),
                initialType = type,
                entryId = entry.long("entryId"),
                onDone = { navController.popFrom(entry) },
            )
        }
        composable(Routes.PERSON, arguments = listOf(navArgument("personId") { type = NavType.LongType })) { entry ->
            val personId = entry.arguments?.getLong("personId") ?: -1L
            PersonScreen(
                personId = personId,
                actions = PersonActions(
                    back = { navController.popFrom(entry) },
                    edit = { go(Routes.personEdit(it)) },
                    addExpense = { go(Routes.expense(personId = it)) },
                    addEntry = { id, type -> go(Routes.entry(id, type)) },
                    openEntry = { id, entryId -> go(Routes.entry(id, LedgerType.UDHAAR_GIVEN, entryId)) },
                    openExpense = { go(Routes.expense(it)) },
                    shareBalance = { go(Routes.shareBalance(it)) },
                    settle = { go(Routes.settle(it)) },
                ),
            )
        }
        composable(Routes.SHARE_BALANCE, arguments = listOf(navArgument("personId") { type = NavType.LongType })) { entry ->
            ShareBalanceScreen(
                personId = entry.arguments?.getLong("personId") ?: -1L,
                onBack = { navController.popFrom(entry) },
                onEditWording = { go(Routes.SHARE_WORDING) },
            )
        }
        composable(Routes.SETTLE, arguments = listOf(navArgument("personId") { type = NavType.LongType })) { entry ->
            SettleScreen(personId = entry.arguments?.getLong("personId") ?: -1L, onDone = { navController.popFrom(entry) })
        }
        composable(Routes.PERSON_EDIT, arguments = listOf(optionalLong("personId"))) { entry ->
            val personId = entry.long("personId")
            PersonFormScreen(
                personId = personId,
                onDone = { navController.popFrom(entry) },
                onSaved = { id ->
                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                        if (personId == null) {
                            navController.navigate(Routes.person(id)) { popUpTo(Routes.PERSON_EDIT) { inclusive = true } }
                        } else {
                            navController.popBackStack()
                        }
                    }
                },
            )
        }
        composable(Routes.CARDS) { entry ->
            CardsScreen(onBack = { navController.popFrom(entry) }, onOpenCard = { go(Routes.card(it)) }, onAddCard = { go(Routes.cardEdit()) })
        }
        composable(Routes.CARD, arguments = listOf(navArgument("cardId") { type = NavType.LongType })) { entry ->
            CardDetailScreen(
                cardId = entry.arguments?.getLong("cardId") ?: -1L,
                onBack = { navController.popFrom(entry) },
                onEdit = { go(Routes.cardEdit(it)) },
                onOpenExpense = { go(Routes.expense(it)) },
            )
        }
        composable(Routes.CARD_EDIT, arguments = listOf(optionalLong("cardId"))) { entry ->
            CardFormScreen(cardId = entry.long("cardId"), onDone = { navController.popFrom(entry) })
        }
        composable(Routes.CASHBACK) { entry -> CashbackScreen(onBack = { navController.popFrom(entry) }, onOpenRules = { go(Routes.RULES) }) }
        composable(Routes.RULES) { entry -> CashbackRulesScreen(onBack = { navController.popFrom(entry) }) }
        composable(Routes.RECURRING) { entry ->
            RecurringScreen(onBack = { navController.popFrom(entry) }, onOpen = { go(Routes.recurringEdit(it)) })
        }
        composable(Routes.RECURRING_EDIT, arguments = listOf(optionalLong("recurringId"))) { entry ->
            RecurringFormScreen(recurringId = entry.long("recurringId"), onDone = { navController.popFrom(entry) })
        }
        composable(Routes.CATEGORIES) { entry -> CategoriesScreen(onBack = { navController.popFrom(entry) }) }
        composable(Routes.REMINDERS) { entry -> RemindersScreen(onBack = { navController.popFrom(entry) }) }
        composable(Routes.SHARE_WORDING) { entry -> ShareTemplatesScreen(onBack = { navController.popFrom(entry) }) }
        composable(Routes.SEARCH) { entry ->
            SearchScreen(
                SearchActions(
                    back = { navController.popFrom(entry) },
                    openPerson = { go(Routes.person(it)) },
                    openExpense = { go(Routes.expense(it)) },
                    openIncome = { go(Routes.income(it)) },
                    openCard = { go(Routes.card(it)) },
                    openEntry = { personId, entryId -> go(Routes.entry(personId, LedgerType.UDHAAR_GIVEN, entryId)) },
                ),
            )
        }
        composable(Routes.BACKUP) { entry -> BackupScreen(onBack = { navController.popFrom(entry) }) }
        composable(Routes.CSV) { entry -> CsvScreen(onBack = { navController.popFrom(entry) }) }
        composable(Routes.DATA) { entry -> DataScreen(onBack = { navController.popFrom(entry) }, onOpenExpense = { go(Routes.expense(it)) }) }
        composable(Routes.ABOUT) { entry -> AboutScreen(onBack = { navController.popFrom(entry) }) }
    }
}

// Ignores repeated back/close taps so a screen can never pop the one below it.
private fun NavHostController.popFrom(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) popBackStack()
}
