package io.github.chiragbhatn.expensetracker.ui.udhaar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.chiragbhatn.expensetracker.data.PersonInput
import io.github.chiragbhatn.expensetracker.data.SavePersonResult
import io.github.chiragbhatn.expensetracker.domain.newUuid
import io.github.chiragbhatn.expensetracker.ui.LocalAppContainer
import io.github.chiragbhatn.expensetracker.ui.TestTags
import io.github.chiragbhatn.expensetracker.ui.appData
import io.github.chiragbhatn.expensetracker.ui.components.FormScreen
import io.github.chiragbhatn.expensetracker.ui.components.Hint
import io.github.chiragbhatn.expensetracker.ui.components.InitialAvatar
import io.github.chiragbhatn.expensetracker.ui.components.Loading
import io.github.chiragbhatn.expensetracker.ui.components.TextInput
import io.github.chiragbhatn.expensetracker.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Adds a person ([personId] null) or edits one. [onSaved] receives the person's id. */
@Composable
fun PersonFormScreen(personId: Long?, onDone: () -> Unit, onSaved: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val data = appData()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var loaded by rememberSaveable { mutableStateOf(personId == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(data, personId) {
        if (!loaded && data != null) {
            data.peopleById[personId]?.let { person ->
                name = person.name
                phone = person.phone
                email = person.email
                address = person.address
                notes = person.notes
                tags = person.tags.joinToString(", ")
            }
            loaded = true
        }
    }

    val person = personId?.let { data?.peopleById?.get(it) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && personId != null) {
            scope.launch {
                val file = withContext(Dispatchers.IO) {
                    runCatching {
                        val target = container.files.photo("${newUuid()}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                        target
                    }.getOrNull()
                }
                if (file != null) container.people.setPhoto(personId, file) else toast(context, "Couldn't use that photo")
            }
        }
    }

    FormScreen(title = if (personId == null) "Add person" else "Edit person", onBack = onDone) {
        if (!loaded) {
            Loading()
            return@FormScreen
        }
        if (personId != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InitialAvatar(name, photo = person?.photoPath?.let(container.files::photo), size = 64.dp)
                TextButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(if (person?.photoPath == null) "Add photo" else "Change photo")
                }
                if (person?.photoPath != null) {
                    TextButton(onClick = { scope.launch { container.people.setPhoto(personId, null) } }) { Text("Remove") }
                }
            }
        } else {
            Hint("You can add a photo after saving.")
        }
        TextInput(
            value = name,
            onValueChange = {
                name = it
                nameError = null
            },
            label = "Name",
            capitalization = KeyboardCapitalization.Words,
            errorText = nameError,
            tag = TestTags.NAME_INPUT,
        )
        TextInput(value = phone, onValueChange = { phone = it }, label = "Phone", keyboardType = KeyboardType.Phone)
        TextInput(value = email, onValueChange = { email = it }, label = "Email", keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None)
        TextInput(value = address, onValueChange = { address = it }, label = "Address", singleLine = false)
        TextInput(value = tags, onValueChange = { tags = it }, label = "Tags", supportingText = "Separate with commas, e.g. college, flatmate")
        TextInput(value = notes, onValueChange = { notes = it }, label = "Notes", singleLine = false)
        Button(
            onClick = {
                if (name.isBlank()) {
                    nameError = "Enter a name"
                } else {
                    scope.launch {
                        val input = PersonInput(name, phone, email, address, notes, tags.split(',').map { it.trim() }.filter { it.isNotEmpty() })
                        when (val result = container.people.save(personId, input)) {
                            is SavePersonResult.NameTaken -> nameError = "${result.existingName} is already in your list"
                            is SavePersonResult.Saved -> onSaved(result.id)
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.SAVE),
        ) { Text("Save") }
    }
}
