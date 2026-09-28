package io.github.chiragbhatn.expensetracker.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
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
import io.github.chiragbhatn.expensetracker.ui.dashboard.DashboardScreen
import io.github.chiragbhatn.expensetracker.ui.expenses.ExpenseEditScreen
import io.github.chiragbhatn.expensetracker.ui.expenses.ExpenseListScreen
import io.github.chiragbhatn.expensetracker.ui.rules.CashbackRulesScreen
import io.github.chiragbhatn.expensetracker.ui.udhaar.PersonDetailScreen
import io.github.chiragbhatn.expensetracker.ui.udhaar.UdhaarScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val EXPENSES = "expenses"
    const val UDHAAR = "udhaar"
    const val RULES = "rules"

    const val EXPENSE_ID_ARG = "expenseId"
    const val EXPENSE = "expense?$EXPENSE_ID_ARG={$EXPENSE_ID_ARG}"
    const val PERSON_ID_ARG = "personId"
    const val PERSON = "person/{$PERSON_ID_ARG}"

    fun expense(id: Long?) = if (id == null) "expense" else "expense?$EXPENSE_ID_ARG=$id"
    fun person(id: Long) = "person/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.DASHBOARD, "Home", Icons.Filled.Home),
    Tab(Routes.EXPENSES, "Expenses", Icons.Filled.Receipt),
    Tab(Routes.UDHAAR, "Udhaar", Icons.Filled.People),
    Tab(Routes.RULES, "Cashback", Icons.Filled.CreditCard),
)

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
    val openExpense: (Long?) -> Unit = { id -> navController.navigate(Routes.expense(id)) }
    val openPerson: (Long) -> Unit = { id -> navController.navigate(Routes.person(id)) }
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
        startDestination = Routes.DASHBOARD,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                bottomBar = bottomBar,
                onAddExpense = { openExpense(null) },
                onOpenExpense = openExpense,
                onOpenUdhaar = { openTab(Routes.UDHAAR) },
                onOpenPerson = openPerson,
            )
        }
        composable(Routes.EXPENSES) {
            ExpenseListScreen(
                bottomBar = bottomBar,
                onAddExpense = { openExpense(null) },
                onOpenExpense = openExpense,
            )
        }
        composable(Routes.UDHAAR) {
            UdhaarScreen(bottomBar = bottomBar, onOpenPerson = openPerson)
        }
        composable(Routes.RULES) {
            CashbackRulesScreen(bottomBar = bottomBar)
        }
        composable(
            route = Routes.EXPENSE,
            arguments = listOf(
                navArgument(Routes.EXPENSE_ID_ARG) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entry ->
            ExpenseEditScreen(onDone = { navController.popFrom(entry) })
        }
        composable(
            route = Routes.PERSON,
            arguments = listOf(navArgument(Routes.PERSON_ID_ARG) { type = NavType.LongType }),
        ) { entry ->
            PersonDetailScreen(
                onBack = { navController.popFrom(entry) },
                onOpenExpense = openExpense,
            )
        }
    }
}

// Ignores repeated back/close taps so a screen can never pop the one below it.
private fun NavHostController.popFrom(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) popBackStack()
}
