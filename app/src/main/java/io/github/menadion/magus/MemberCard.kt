package io.github.menadion.magus

import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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

// "just now", "3 min ago", "2 hr ago", "yesterday 8:14 PM", then "Sep 22, 8:14 PM". Dates stay in English.
@Composable
fun lastSeenText(then: Long, now: Long): String {
    val minutes = (now - then) / 60_000L
    if (minutes < 1) return stringResource(R.string.just_now)
    if (minutes < 60) return stringResource(R.string.min_ago, minutes.toInt())

    val thenDay = Calendar.getInstance().apply { timeInMillis = then }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val time = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(then))
    return when {
        sameDay(thenDay, today) -> stringResource(R.string.hr_ago, (minutes / 60).toInt())
        sameDay(thenDay, yesterday) -> stringResource(R.string.yesterday_at, time)
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.ENGLISH).format(Date(then))
    }
}

// The person's card, the bottom panel's content when someone is picked: avatar, name, status, and
// two tiles. Spec: HANDOFF.md section 5. Since 2026-09-25 evening the panel around it (BottomPanel)
// sets the size and shape; this only lays out the inside.
@Composable
fun MemberCard(person: Person, now: Long, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val state = person.state(now)
    val (status, statusColor) = cardStatus(person, now)
    val quiet = state == Markers.State.QUIET
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Avatar(state, person.letter, 56.dp, photo = person.member.photo, you = person.isYou, color = Color(person.color))
            Column(modifier = Modifier.weight(1f)) {
                Text(person.name, style = MaterialTheme.typography.headlineLarge)
                Text(status, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = MaterialTheme.typography.bodyMedium.fontWeight), color = statusColor)
            }
            Box(modifier = Modifier.size(48.dp).background(colors.surfaceContainerHigh, CircleShape)) {
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = colors.onSurface)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val seen = person.member.updatedAtMillis?.let { lastSeenText(it, now) } ?: "—"
            Tile(
                label = stringResource(R.string.last_seen),
                value = seen,
                valueColor = if (quiet) colors.error else colors.onSurface,
                icon = { ClockGlyph(colors.onSurfaceVariant) },
                modifier = Modifier.weight(1f),
            )
            val battery = person.member.battery
            val low = battery != null && battery < LOW_BATTERY
            Tile(
                label = stringResource(R.string.battery),
                value = battery?.let { "$it%" } ?: "—",
                valueColor = if (low) colors.error else colors.onSurface,
                icon = { BatteryGlyph(if (low) colors.error else colors.onSurfaceVariant) },
                modifier = Modifier.weight(1f),
            )
        }
        person.member.phone?.let { PhoneRow(it) }
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

// The person's number: tap it to copy, Call opens the dialer, Text the messaging app. On your own
// card too, so you can see what the family sees. Spec: M's calls 2026-09-26.
@Composable
private fun PhoneRow(phone: String) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (copied) stringResource(R.string.copied) else Phone.display(phone),
            style = MaterialTheme.typography.titleMedium,
            color = if (copied) colors.primary else colors.onSurface,
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .clickable {
                    Phone.copy(context, phone)
                    copied = true
                }
                .padding(vertical = 8.dp),
        )
        FilledTonalButton(onClick = { Phone.call(context, phone) }, contentPadding = PaddingValues(horizontal = 14.dp)) {
            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.call))
        }
        FilledTonalButton(onClick = { Phone.text(context, phone) }, contentPadding = PaddingValues(horizontal = 14.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.text_message))
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
