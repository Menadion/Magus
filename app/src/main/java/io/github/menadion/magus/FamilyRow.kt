package io.github.menadion.magus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// The floating card at the bottom of the map: "Family", See all, and one column per person.
// Spec: HANDOFF.md section 3, "Family row". You first, then the family in a fixed order.
@Composable
fun FamilyRow(people: List<Person>, now: Long, onPick: (String) -> Unit, onSeeAll: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLowest,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Family", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onSeeAll, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("See all", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = MaterialTheme.typography.labelLarge.fontWeight))
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                }
            }
            Row(modifier = Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (person in people) {
                    PersonColumn(person, now, onPick = { onPick(person.uid) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PersonColumn(person: Person, now: Long, onPick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val state = person.state(now)
    val description = when (state) {
        Markers.State.YOU -> "Go to you on the map"
        Markers.State.PAUSED -> "Go to ${person.name} on the map, sharing paused"
        Markers.State.QUIET -> "Go to ${person.name} on the map, phone quiet"
        else -> "Go to ${person.name} on the map"
    }
    Column(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clickable(onClick = onPick)
            .semantics { contentDescription = description }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Avatar(state, person.letter, 44.dp, photo = person.member.photo)
        Text(
            person.name,
            style = MaterialTheme.typography.labelMedium,
            color = when (state) {
                Markers.State.YOU -> colors.primary
                Markers.State.PAUSED -> colors.onSurfaceVariant
                else -> colors.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// See all: every person as a row, in a sheet over the map. Spec: HANDOFF.md section 6.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyListSheet(people: List<Person>, now: Long, onPick: (String) -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = colors.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                Text("Family", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close list")
                }
            }
            for (person in people) {
                PersonRow(person, now, onPick = { onPick(person.uid) })
            }
            Text(
                "Tap a name to see them on the map",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight),
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun PersonRow(person: Person, now: Long, onPick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val state = person.state(now)
    val (status, statusColor) = listStatus(person, now)
    Surface(
        onClick = onPick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = colors.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Avatar(state, person.letter, 44.dp, photo = person.member.photo)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    person.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (state == Markers.State.PAUSED) colors.onSurfaceVariant else colors.onSurface,
                )
                Text(status, style = MaterialTheme.typography.bodyMedium, color = statusColor)
            }
            person.member.battery?.let { battery ->
                val low = battery < LOW_BATTERY
                val tint = if (low) colors.error else colors.onSurfaceVariant
                BatteryGlyph(tint)
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    "$battery%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (low) colors.error else colors.onSurface,
                )
            }
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
        }
    }
}
