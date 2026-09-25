package io.github.menadion.magus

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
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
                problem = e.message ?: context.getString(R.string.something_wrong_connection)
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_to_map))
                }
                Text(stringResource(R.string.family), style = MaterialTheme.typography.headlineMedium)
            }

            // The name and the code, centred like a group's header.
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Family.familyLabel(context, familyName),
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                    )
                    if (canRename) {
                        IconButton(onClick = { editFamilyName = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.change_family_name), tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
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
                    if (copied) stringResource(R.string.copied) else stringResource(R.string.code_hint),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight),
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // The list box is a fixed share of the screen, so You and Leave sit in the same place
                // whether the family has one member or ten; past about four people it scrolls inside.
                val listMax = (LocalConfiguration.current.screenHeightDp * 0.38f).dp
                Group(if (people.size == 1) stringResource(R.string.members) else stringResource(R.string.members_count, people.size)) {
                    LazyColumn(
                        modifier = Modifier.height(listMax),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(people, key = { it.uid }) { person ->
                            PersonRow(person, now, onPick = { onPick(person.uid) })
                        }
                    }
                }

                Group(stringResource(R.string.you)) {
                    Row(
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Avatar(me?.state(now) ?: Markers.State.YOU, me?.letter ?: myName.take(1).uppercase(), 56.dp, photo = myPhoto, you = true)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(myName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.shown_above_dot),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight),
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.padding(start = 20.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(onClick = { editMyName = true }, enabled = !busy) { Text(stringResource(R.string.change_name)) }
                        FilledTonalButton(
                            onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !busy,
                        ) { Text(stringResource(R.string.change_picture)) }
                    }
                    if (myPhoto != null) {
                        TextButton(
                            onClick = { attempt { Family.setPhoto(context, null) } },
                            enabled = !busy,
                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                        ) { Text(stringResource(R.string.remove_picture), color = colors.error) }
                    }
                }

                Group(stringResource(R.string.leave)) {
                    NavRow(
                        title = stringResource(R.string.leave_family),
                        subtitle = stringResource(R.string.leave_family_subtitle),
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
            title = stringResource(R.string.family_name),
            initial = familyName,
            suffix = stringResource(R.string.family),
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
            title = stringResource(R.string.your_name),
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
            containerColor = colors.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            icon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = colors.error) },
            title = { Text(stringResource(R.string.leave_family_q, Family.familyLabel(context)), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center) },
            text = {
                Text(
                    stringResource(R.string.leave_family_text, code),
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
                ) { Text(stringResource(if (busy) R.string.leaving else R.string.leave), color = colors.error) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                Text(stringResource(if (busy) R.string.saving else R.string.save))
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
