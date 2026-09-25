package io.github.menadion.magus

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The family page, opened by tapping the family name on the map. Laid out like Messenger's group
// page, which the family already knows: the name and code, the members, you, and Leave at the bottom.
@Composable
fun FamilyPage(
    code: String,
    canRename: Boolean,
    people: List<Person>,
    now: Long,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    onLeft: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var familyName by remember { mutableStateOf(Family.savedFamilyName(context) ?: "") }
    var myName by remember { mutableStateOf(Family.savedName(context) ?: "") }
    var editFamilyName by remember { mutableStateOf(false) }
    var editMyName by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val me = people.firstOrNull { it.isYou }
    val myPhoto = me?.member?.photo

    fun attempt(action: suspend () -> Unit) {
        busy = true
        problem = null
        scope.launch {
            try {
                action()
            } catch (e: Exception) {
                problem = e.message ?: "Something went wrong. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    // Android's own picker: no gallery permission needed. The photo is shrunk before it is sent.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            attempt {
                val bytes = withContext(Dispatchers.IO) { Photos.shrink(context, uri) }
                Family.setPhoto(context, bytes)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.height(64.dp).padding(start = 4.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to map")
                }
                Text("Family", style = MaterialTheme.typography.headlineMedium)
            }

            // The name and the code, centred like a group's header.
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$familyName Family".trim(),
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                    )
                    if (canRename) {
                        IconButton(onClick = { editFamilyName = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Change family name", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                // Tap the code to copy it, for pasting into a chat.
                Text(
                    code,
                    style = MaterialTheme.typography.displayMedium,
                    color = if (copied) colors.primary else colors.onSurface,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Mogar family code", code))
                            copied = true
                        }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                )
                LaunchedEffect(copied) {
                    if (copied) {
                        delay(1500)
                        copied = false
                    }
                }
                Text(
                    if (copied) "Copied" else "Tap the code to copy it, then send it to family so they can join.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight),
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Group("Members") {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (person in people) PersonRow(person, now, onPick = { onPick(person.uid) })
                    }
                }

                Group("You") {
                    Row(
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Avatar(Markers.State.YOU, me?.letter ?: myName.take(1).uppercase(), 56.dp, photo = myPhoto)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(myName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Shown above your dot on the map",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight),
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.padding(start = 20.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(onClick = { editMyName = true }, enabled = !busy) { Text("Change name") }
                        FilledTonalButton(
                            onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !busy,
                        ) { Text("Change picture") }
                    }
                    if (myPhoto != null) {
                        TextButton(
                            onClick = { attempt { Family.setPhoto(context, null) } },
                            enabled = !busy,
                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                        ) { Text("Remove picture", color = colors.error) }
                    }
                }

                Group("Leave") {
                    NavRow(
                        title = "Leave family",
                        subtitle = "Your family stops seeing you",
                        icon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = colors.error) },
                        iconBackground = colors.surfaceContainerHigh,
                        titleColor = colors.error,
                        onClick = { confirmLeave = true },
                    )
                }
                problem?.let { Text(it, color = colors.error, modifier = Modifier.padding(horizontal = 8.dp)) }
            }
        }
    }

    if (editFamilyName) {
        NameDialog(
            title = "Family name",
            initial = familyName,
            suffix = "Family",
            busy = busy,
            onDismiss = { editFamilyName = false },
            onSave = { newName ->
                attempt {
                    Family.renameFamily(context, newName)
                    familyName = newName
                    editFamilyName = false
                }
            },
        )
    }

    if (editMyName) {
        NameDialog(
            title = "Your name",
            initial = myName,
            busy = busy,
            onDismiss = { editMyName = false },
            onSave = { newName ->
                attempt {
                    Family.rename(context, newName)
                    myName = newName
                    editMyName = false
                }
            },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmLeave = false },
            containerColor = MogarColors.Dialog,
            shape = MaterialTheme.shapes.extraLarge,
            icon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = colors.error) },
            title = { Text("Leave ${Family.familyLabel(context)}?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center) },
            text = {
                Text(
                    "Your family will stop seeing you. You can join again with the code $code.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        attempt {
                            ShareService.stop(context)
                            Family.leave(context)
                            confirmLeave = false
                            onLeft()
                        }
                    },
                ) { Text(if (busy) "Leaving…" else "Leave", color = colors.error) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmLeave = false }) { Text("Cancel") } },
        )
    }
}

// One box for changing a name: the field, Cancel, Save.
@Composable
private fun NameDialog(
    title: String,
    initial: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    suffix: String? = null,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MogarColors.Dialog,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                suffix = suffix?.let { { Text(it) } },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = !busy && value.isNotBlank() && value.trim() != initial, onClick = { onSave(value.trim()) }) {
                Text(if (busy) "Saving…" else "Save")
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}
