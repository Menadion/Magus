package io.github.menadion.magus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// First open: pick a name, then make a family (with a family name) or join one with its code.
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var familyName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun attempt(action: suspend () -> Unit) {
        busy = true
        problem = null
        scope.launch {
            try {
                action()
                onDone()
            } catch (e: Exception) {
                problem = e.message ?: "Something went wrong. Try again."
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier.statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Magus", style = MaterialTheme.typography.headlineLarge)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("What should your family call you?") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = familyName,
            onValueChange = { familyName = it },
            label = { Text("Family name, e.g. Santos") },
            suffix = { Text("Family") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { attempt { Family.create(context, name.trim(), familyName.trim()) } },
            enabled = !busy && name.isNotBlank() && familyName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create family") }

        HorizontalDivider()

        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(6) },
            label = { Text("Family code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedButton(
            onClick = { attempt { Family.join(context, name.trim(), code) } },
            enabled = !busy && name.isNotBlank() && code.length == 6,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Join family") }

        if (busy) Text("Working…")
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
