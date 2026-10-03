package io.github.pesterevnikita.focusgate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

/** Show persistent credential status separately from the deliberate set/change action. */
@Composable internal fun PasswordManagement(
    passwordSet: Boolean,
    onSave: suspend (CharArray, CharArray, CharArray) -> String?,
) {
    var editing by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf("") }
    Text(if (passwordSet) "Password set" else "No password set", style = MaterialTheme.typography.titleMedium)
    Text(if (passwordSet) "Your saved password is reused for every new lock. Changing it requires the current password."
        else "Ask a trusted person to set a password before choosing a password release option.")
    if (confirmation.isNotEmpty()) Text(confirmation, color = MaterialTheme.colorScheme.primary)
    OutlinedButton(onClick = { confirmation = ""; editing = true }) {
        Text(if (passwordSet) "Change password" else "Set password")
    }
    if (editing) PasswordChangeDialog(passwordSet, onSave,
        onDismiss = { editing = false },
        onSaved = { editing = false; confirmation = if (passwordSet) "Password changed." else "Password saved." })
}

/** Secrets are transient: never save them across recreation or include them in state/export/logs. */
@Composable private fun PasswordChangeDialog(
    passwordSet: Boolean,
    onSave: suspend (CharArray, CharArray, CharArray) -> String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun clearFields() { current = ""; next = ""; repeat = "" }
    AlertDialog(
        onDismissRequest = { if (!saving) { clearFields(); onDismiss() } },
        title = { Text(if (passwordSet) "Change password" else "Set password") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Use at least six characters. The trusted person should keep the password.")
                if (passwordSet) SecretInput("Current password", current, !saving) { current = it }
                SecretInput("New password", next, !saving) { next = it }
                SecretInput("Repeat new password", repeat, !saving) { repeat = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && next.isNotEmpty() && repeat.isNotEmpty() && (!passwordSet || current.isNotEmpty()), onClick = {
                val currentChars = current.toCharArray()
                val nextChars = next.toCharArray()
                val repeatChars = repeat.toCharArray()
                clearFields()
                saving = true
                error = null
                // Enter finally before the first suspension so leaving the screen cannot strand buffers.
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        val refusal = withContext(Dispatchers.IO) { onSave(currentChars, nextChars, repeatChars) }
                        if (refusal == null) onSaved() else error = refusal
                    } finally {
                        currentChars.fill('\u0000'); nextChars.fill('\u0000'); repeatChars.fill('\u0000')
                        saving = false
                    }
                }
            }) { Text(if (saving) "Saving…" else "Save password") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = { clearFields(); onDismiss() }) { Text("Cancel") } },
    )
}

/** Password keyboard avoids word suggestions and keeps all three fields masked. */
@Composable private fun SecretInput(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, enabled = enabled,
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
}
