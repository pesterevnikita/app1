package io.github.pesterevnikita.focusgate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

/** Show persistent credential status separately from the deliberate set/change action. */
@Composable internal fun PasswordManagement(
    passwordSet: Boolean,
    onSave: suspend (CharArray, CharArray, CharArray) -> String?,
    onRemove: suspend (CharArray) -> String?,
) {
    var editing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf("") }
    Text(if (passwordSet) "Password set" else "No password set", style = MaterialTheme.typography.titleMedium)
    Text(if (passwordSet) "Your saved password is reused for every new lock. Changing it requires the current password."
        else "Timer only is available. Set a password to use Password only or Password OR timer.")
    if (confirmation.isNotEmpty()) Text(confirmation, color = MaterialTheme.colorScheme.primary)
    OutlinedButton(onClick = { confirmation = ""; editing = true }) {
        Text(if (passwordSet) "Change password" else "Set password")
    }
    if (passwordSet) TextButton(onClick = { confirmation = ""; removing = true }) { Text("Remove password") }
    if (editing) PasswordChangeDialog(passwordSet, onSave,
        onDismiss = { editing = false },
        onSaved = { editing = false; confirmation = if (passwordSet) "Password changed." else "Password saved." })
    if (removing) PasswordRemovalDialog(onRemove,
        onDismiss = { removing = false },
        onRemoved = { removing = false; confirmation = "Password removed. Your blockers are unchanged." })
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
                if (passwordSet) PasswordInput("Current password", current, !saving) { current = it }
                PasswordInput("New password", next, !saving) { next = it }
                PasswordInput("Repeat new password", repeat, !saving) { repeat = it }
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

/** Removal verifies the current credential; it never converts an active password lock to a timer lock. */
@Composable private fun PasswordRemovalDialog(
    onRemove: suspend (CharArray) -> String?,
    onDismiss: () -> Unit,
    onRemoved: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) { current = ""; onDismiss() } },
        title = { Text("Remove password?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter your current password to remove it. Your blockers and usage counters stay unchanged.")
                Text("Password only and Password OR timer will be unavailable until you set a new password. Password OR timer does not automatically become Timer only.")
                Text("You can choose Timer only instead, but those locks cannot be ended early with a password. Setting a new password later will not ask for the old one.")
                PasswordInput("Current password", current, !saving) { current = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && current.isNotEmpty(), onClick = {
                val chars = current.toCharArray()
                current = ""
                saving = true
                error = null
                // Enter cleanup before suspension, including cancellation before the store is called.
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        val refusal = withContext(Dispatchers.IO) { onRemove(chars) }
                        if (refusal == null) onRemoved() else error = refusal
                    } finally {
                        chars.fill('\u0000')
                        saving = false
                    }
                }
            }) { Text(if (saving) "Removing…" else "Remove password") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = { current = ""; onDismiss() }) { Text("Cancel") } },
    )
}
