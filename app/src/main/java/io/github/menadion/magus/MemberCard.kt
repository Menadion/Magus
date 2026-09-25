package io.github.menadion.magus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private val WARNING_RED = Color(0xFFC62828)

// Sharing is on, but nothing has arrived for a while: dead phone, no signal, or the phone killed Mogar.
fun Member.isQuiet(now: Long): Boolean =
    sharing && updatedAtMillis != null && now - updatedAtMillis > QUIET_AFTER_MS

// What the dot looks like: green sharing, hollow gone quiet, grey paused.
fun Member.dotState(now: Long): String = when {
    !sharing -> "paused"
    isQuiet(now) -> "quiet"
    else -> "active"
}

// "just now", "3 min ago", "2 h ago", "yesterday 8:14 PM", then "Sep 22, 8:14 PM".
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
        sameDay(thenDay, today) -> "${minutes / 60} h ago"
        sameDay(thenDay, yesterday) -> "yesterday $time"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.ENGLISH).format(Date(then))
    }
}

// Slides up from the bottom when a dot is tapped. Reads the live list, so it updates while open.
@Composable
fun MemberCard(member: Member, now: Long, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(member.name, style = MaterialTheme.typography.headlineSmall)

            if (!member.sharing) {
                Text("Sharing paused", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            }

            member.updatedAtMillis?.let { then ->
                Text(
                    "Last seen ${lastSeenText(then, now)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (member.isQuiet(now)) WARNING_RED else Color.Unspecified,
                )
            }

            member.battery?.let { battery ->
                Text(
                    "Battery $battery%",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (battery < LOW_BATTERY) WARNING_RED else Color.Unspecified,
                )
            }
        }
    }
}
