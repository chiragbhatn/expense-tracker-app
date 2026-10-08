package io.github.chiragbhatn.expensetracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.chiragbhatn.expensetracker.ui.TestTags

/** What the "+" button can add. */
data class AddActions(
    val expense: () -> Unit,
    val income: () -> Unit,
    val udhaar: () -> Unit,
    val payment: () -> Unit,
)

/** The "+" button: Expense, Income, Udhaar or Payment. */
@Composable
fun AddMenuFab(actions: AddActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        FloatingActionButton(onClick = { open = true }, modifier = Modifier.testTag(TestTags.ADD_MENU)) {
            Icon(Icons.Filled.Add, contentDescription = "Add")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Expense") },
                leadingIcon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                onClick = {
                    open = false
                    actions.expense()
                },
                modifier = Modifier.testTag(TestTags.ADD_EXPENSE),
            )
            DropdownMenuItem(
                text = { Text("Income") },
                leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null) },
                onClick = {
                    open = false
                    actions.income()
                },
                modifier = Modifier.testTag(TestTags.ADD_INCOME),
            )
            DropdownMenuItem(
                text = { Text("Udhaar") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallMade, contentDescription = null) },
                onClick = {
                    open = false
                    actions.udhaar()
                },
                modifier = Modifier.testTag(TestTags.ADD_UDHAAR),
            )
            DropdownMenuItem(
                text = { Text("Payment") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallReceived, contentDescription = null) },
                onClick = {
                    open = false
                    actions.payment()
                },
                modifier = Modifier.testTag(TestTags.ADD_PAYMENT),
            )
        }
    }
}
