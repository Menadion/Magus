package io.github.menadion.magus

import androidx.compose.ui.res.stringResource
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Settings, opened by the gear on the map. A full screen. Spec: HANDOFF.md section 7.
// Settings, opened by the gear: the app's own rows, and the version at the bottom. Everything about
// the family and you lives on the family page (FamilyPage), opened from the family name on the map.
@Composable
fun SettingsScreen(onBack: () -> Unit, onKeepRunning: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var showAbout by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.height(64.dp).padding(start = 4.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_to_map))
                    }
                    Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
                }

                Column(
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Group(stringResource(R.string.appearance)) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                stringResource(R.string.theme),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight),
                                color = colors.onSurfaceVariant,
                            )
                            val choices = listOf(
                                ThemeSetting.SYSTEM to stringResource(R.string.theme_auto),
                                ThemeSetting.LIGHT to stringResource(R.string.theme_light),
                                ThemeSetting.DARK to stringResource(R.string.theme_dark),
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                choices.forEachIndexed { index, (value, label) ->
                                    SegmentedButton(
                                        selected = ThemeSetting.mode == value,
                                        onClick = { ThemeSetting.set(context, value) },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
                                    ) { Text(label) }
                                }
                            }
                        }
                    }
                    Group(stringResource(R.string.app)) {
                        NavRow(
                            title = stringResource(R.string.keep_running),
                            subtitle = stringResource(R.string.keep_running_subtitle),
                            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colors.primary) },
                            onClick = onKeepRunning,
                        )
                        Divider()
                        UpdateRow()
                        Divider()
                        NavRow(
                            title = stringResource(R.string.about_map),
                            subtitle = stringResource(R.string.about_map_subtitle),
                            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = colors.primary) },
                            onClick = { showAbout = true },
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.version_line, Diagnostics.appVersion(context)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }

    if (showAbout) {
        AboutMapDialog(
            onClose = { showAbout = false },
            onOpenStreetMap = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright")))
            },
        )
    }
}

// Check for updates: a tap asks GitHub and the subtitle says what it found. A newer Mogar puts the
// red dot on the row's icon and a Download button under it; the browser downloads the file and
// Android's installer takes over from its notification. Spec: Backlog, "updates", 2026-09-25.
@Composable
fun UpdateRow() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val newer = Updates.newer
    val subtitle = when {
        Updates.state == Updates.State.CHECKING -> stringResource(R.string.update_checking)
        newer != null -> stringResource(R.string.update_out, newer.version) + if (newer.notes.isNotBlank()) ": ${newer.notes}" else ""
        Updates.state == Updates.State.CHECKED -> stringResource(R.string.update_latest, Diagnostics.appVersion(context))
        Updates.state == Updates.State.FAILED -> stringResource(R.string.update_failed)
        else -> stringResource(R.string.update_tap)
    }
    Column {
        NavRow(
            title = stringResource(R.string.check_updates),
            subtitle = subtitle,
            icon = {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.primary)
                    if (newer != null) UpdateDot(modifier = Modifier.align(Alignment.TopEnd))
                }
            },
            onClick = { scope.launch { Updates.check(context) } },
        )
        if (newer != null) {
            Button(
                onClick = { Updates.open(context, newer) },
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 14.dp),
            ) { Text(stringResource(R.string.update_download, newer.version)) }
        }
    }
}

@Composable
fun Group(label: String, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.primary, modifier = Modifier.padding(start = 8.dp))
        Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

@Composable
fun ValueRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight), color = colors.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun Divider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh)
}

@Composable
fun NavRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    iconBackground: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, color = colors.surfaceContainerLowest, modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp)) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(modifier = Modifier.size(40.dp).background(iconBackground, CircleShape), contentAlignment = Alignment.Center) { icon() }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight), color = colors.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

// The map's credit line. OpenStreetMap's licence asks for it to be shown somewhere.
// Spec: HANDOFF.md section 9.
@Composable
fun AboutMapDialog(onClose: () -> Unit, onOpenStreetMap: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        icon = { Icon(Icons.Default.Info, contentDescription = null, tint = colors.primary) },
        title = { Text(stringResource(R.string.about_map), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center) },
        text = {
            Text(
                stringResource(R.string.about_map_text),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
        dismissButton = { TextButton(onClick = onOpenStreetMap) { Text("OpenStreetMap") } },
    )
}
