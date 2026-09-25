package io.github.menadion.magus

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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Settings, opened by the gear on the map. A full screen. Spec: HANDOFF.md section 7.
// Leave family and change name get rows here when they are built.
@Composable
fun SettingsScreen(code: String, onBack: () -> Unit, onKeepRunning: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var showAbout by remember { mutableStateOf(false) }

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
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }

            Column(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Group("You") {
                    ValueRow("Your name", Family.savedName(context) ?: "?")
                }
                Group("Family") {
                    ValueRow("Family name", Family.familyLabel(context))
                    Divider()
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Family code", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight), color = colors.onSurfaceVariant)
                        Text(code, style = MaterialTheme.typography.displayMedium)
                        Text(
                            "Give this code to family so they can join.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight),
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Group("App") {
                    NavRow(
                        title = "Keep Mogar running",
                        subtitle = "Stop your phone from closing Mogar",
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colors.primary) },
                        onClick = onKeepRunning,
                    )
                    Divider()
                    NavRow(
                        title = "About the map",
                        subtitle = "Map credits",
                        icon = { Icon(Icons.Default.Info, contentDescription = null, tint = colors.primary) },
                        onClick = { showAbout = true },
                    )
                }
            }
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

@Composable
private fun Group(label: String, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.primary, modifier = Modifier.padding(start = 8.dp))
        Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight), color = colors.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh)
}

@Composable
private fun NavRow(title: String, subtitle: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, color = colors.surfaceContainerLowest, modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp)) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(modifier = Modifier.size(40.dp).background(colors.primaryContainer, CircleShape), contentAlignment = Alignment.Center) { icon() }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
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
        containerColor = MogarColors.Dialog,
        shape = MaterialTheme.shapes.extraLarge,
        icon = { Icon(Icons.Default.Info, contentDescription = null, tint = colors.primary) },
        title = { Text("About the map", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center) },
        text = {
            Text(
                "Map data © OpenStreetMap contributors.\n" +
                    "Map tiles by OpenFreeMap.\n" +
                    "Drawn with MapLibre.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        dismissButton = { TextButton(onClick = onOpenStreetMap) { Text("OpenStreetMap") } },
    )
}
