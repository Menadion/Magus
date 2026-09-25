package io.github.menadion.magus

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// First open: a name, then one of two choice cards, start a family or join one with its code.
// Spec: HANDOFF.md section 8.
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(Family.savedName(context) ?: "") }
    var phone by remember { mutableStateOf(Phone.digits(Family.savedPhone(context))) }
    var familyName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var joining by remember { mutableStateOf(false) } // which choice card is open
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
                problem = e.message ?: context.getString(R.string.something_wrong_try_again)
            } finally {
                busy = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 40.dp, end = 24.dp, bottom = 28.dp),
        ) {
            Box(
                modifier = Modifier.size(56.dp).background(colors.primaryContainer, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = colors.primary, modifier = Modifier.size(30.dp))
            }
            Text(stringResource(R.string.welcome), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 16.dp))
            Text(
                stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = MaterialTheme.typography.bodyLarge.fontWeight),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.your_name)) },
                supportingText = { Text(stringResource(R.string.shown_above_dot)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            )

            // Optional. "+63" is fixed; the person types the ten digits after it (see Phone).
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = Phone.clean(it) },
                label = { Text(stringResource(R.string.phone_number)) },
                prefix = { Text(Phone.PREFIX + " ") },
                supportingText = { Text(stringResource(R.string.phone_optional)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            Text(stringResource(R.string.your_family), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp))

            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceCard(
                    title = stringResource(R.string.start_family),
                    subtitle = stringResource(R.string.start_family_subtitle),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    selected = !joining,
                    onSelect = { joining = false },
                ) {
                    OutlinedTextField(
                        value = familyName,
                        onValueChange = { familyName = it },
                        label = { Text(stringResource(R.string.family_name)) },
                        prefix = if (booleanResource(R.bool.family_word_first)) { { Text(stringResource(R.string.family) + " ") } } else null,
                        suffix = if (booleanResource(R.bool.family_word_first)) null else { { Text(stringResource(R.string.family)) } },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { attempt { Family.create(context, name.trim(), familyName.trim(), Phone.store(phone)) } },
                        enabled = !busy && name.isNotBlank() && familyName.isNotBlank(),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(R.string.create_family), style = MaterialTheme.typography.titleSmall) }
                }
                ChoiceCard(
                    title = stringResource(R.string.join_family),
                    subtitle = stringResource(R.string.join_family_subtitle),
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    selected = joining,
                    onSelect = { joining = true },
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase().take(6) },
                        label = { Text(stringResource(R.string.family_code)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { attempt { Family.join(context, name.trim(), code, Phone.store(phone)) } },
                        enabled = !busy && name.isNotBlank() && code.length == 6,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(R.string.join_family_button), style = MaterialTheme.typography.titleSmall) }
                }
            }

            if (busy) Text(stringResource(R.string.working), modifier = Modifier.padding(top = 16.dp), color = colors.onSurfaceVariant)
            problem?.let { Text(it, color = colors.error, modifier = Modifier.padding(top = 16.dp)) }
        }
    }
}

// A card that works like a radio button and opens to show its fields when picked.
@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onSelect: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onSelect,
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLowest,
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.5.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides if (selected) colors.primary else colors.onSurfaceVariant,
                    ) { icon() }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight),
                        color = colors.onSurfaceVariant,
                    )
                }
                RadioButton(selected = selected, onClick = null)
            }
            AnimatedVisibility(visible = selected) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
            }
        }
    }
}
