package io.github.menadion.magus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Updates come every 5 minutes, so 15 means three missed in a row.
const val QUIET_AFTER_MS = 15 * 60_000L

// Below this, the battery shows in red.
const val LOW_BATTERY = 20

// Sharing is on, but nothing has arrived for a while: dead phone, no signal, or the phone killed Mogar.
fun Member.isQuiet(now: Long): Boolean =
    sharing && updatedAtMillis != null && now - updatedAtMillis > QUIET_AFTER_MS

// What the dot looks like: green sharing, hollow gone quiet, grey paused.
fun Member.dotState(now: Long): String = when {
    !sharing -> "paused"
    isQuiet(now) -> "quiet"
    else -> "active"
}

// "just now", "3 min ago", "2 hr ago", "yesterday 8:14 PM", then "Sep 22, 8:14 PM".
fun lastSeenText(then: Long, now: Long): String {
    val minutes = (now - then) / 60_000L
    if (minutes < 1) return "just now"
    if (minutes < 60) return "$minutes min ago"

    val thenDay = Calendar.getInstance().apply { timeInMillis = then }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val time = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(then))
    return when {
        sameDay(thenDay, today) -> "${minutes / 60} hr ago"
        sameDay(thenDay, yesterday) -> "yesterday $time"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.ENGLISH).format(Date(then))
    }
}

// The sheet that slides up when a person is picked: avatar, name, status, and two tiles.
// Spec: HANDOFF.md section 5. Height hugs its content.
@Composable
fun MemberCard(person: Person, now: Long, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val state = person.state(now)
    val (status, statusColor) = cardStatus(person, now)
    val quiet = state == Markers.State.QUIET
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = colors.surfaceContainerLow,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.width(32.dp).height(4.dp).background(MogarColors.Handle, CircleShape))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Avatar(state, person.letter, 56.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(person.name, style = MaterialTheme.typography.headlineLarge)
                    Text(status, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = MaterialTheme.typography.bodyMedium.fontWeight), color = statusColor)
                }
                Box(modifier = Modifier.size(48.dp).background(colors.surfaceContainerHigh, CircleShape)) {
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close and show everyone", tint = colors.onSurface)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val seen = person.member.updatedAtMillis?.let { lastSeenText(it, now) } ?: "—"
                Tile(
                    label = "Last seen",
                    value = seen,
                    valueColor = if (quiet) colors.error else colors.onSurface,
                    icon = { ClockGlyph(colors.onSurfaceVariant) },
                    modifier = Modifier.weight(1f),
                )
                val battery = person.member.battery
                val low = battery != null && battery < LOW_BATTERY
                Tile(
                    label = "Battery",
                    value = battery?.let { "$it%" } ?: "—",
                    valueColor = if (low) colors.error else colors.onSurface,
                    icon = { BatteryGlyph(if (low) colors.error else colors.onSurfaceVariant) },
                    modifier = Modifier.weight(1f),
                )
            }
            Diagnostics.summary(person.member.diag)?.let { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
            (person.member.diag?.get("lastCrash") as? String)?.let { crash ->
                Text(
                    "Last crash: $crash",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, valueColor: Color, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = colors.surfaceContainerLowest) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                icon()
                Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight), color = colors.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.headlineMedium, color = valueColor)
        }
    }
}
