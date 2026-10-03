package io.github.pesterevnikita.focusgate.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import io.github.pesterevnikita.focusgate.R

/** Visibility belongs only to this field's current UI instance; it is never saved with credentials. */
@Composable internal fun PasswordInput(label: String, value: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    var revealed by remember(label) { mutableStateOf(false) }
    // A submitted/cleared field must not reveal the next password typed into it.
    LaunchedEffect(value.isEmpty(), enabled) { if (value.isEmpty() || !enabled) revealed = false }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, enabled = enabled,
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(enabled = enabled && value.isNotEmpty(), onClick = { revealed = !revealed }) {
                Icon(painterResource(if (revealed) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = if (revealed) "Hide password" else "Show password")
            }
        },
    )
}
