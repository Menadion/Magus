package io.github.menadion.magus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

// A family member whose dot is off the visible map gets a small circle on the edge of the gap
// between the top card and the bottom box, its tip pointing towards them (M's call, 2026-09-26).
// A tap does what tapping their dot does. Not for you: the locate button brings you back.
private val MARKER = 88.dp  // the touch area: the 48 dp white circle and the arrowhead outside it
private val AVATAR = 38.dp

// points: where each member's dot sits on screen, in pixels. area: the visible gap, in pixels.
@Composable
fun EdgeMarkers(people: List<Person>, now: Long, points: Map<String, Offset>, area: Rect, onPick: (String) -> Unit) {
    if (area.width <= 0f || area.height <= 0f) return
    val density = LocalDensity.current
    val half = with(density) { MARKER.toPx() } / 2
    val gap = with(density) { 52.dp.toPx() } // closest two markers may sit, centre to centre
    // A dot this close inside the gap's edge is visible enough to need no marker.
    val inside = area.deflate(with(density) { 14.dp.toPx() })
    // Markers sit on this smaller box, so the whole circle and its tip stay in the gap.
    val track = area.deflate(half)
    val centre = area.center

    val placed = mutableListOf<Offset>()
    people.filter { !it.isYou }.forEach { person ->
        val point = points[person.uid] ?: return@forEach
        if (inside.contains(point)) return@forEach
        val dx = point.x - centre.x
        val dy = point.y - centre.y
        if (dx == 0f && dy == 0f) return@forEach
        // Along the line from the gap's centre towards the dot, stop at the track's edge.
        val reach = min(
            if (dx != 0f) track.width / 2 / abs(dx) else Float.MAX_VALUE,
            if (dy != 0f) track.height / 2 / abs(dy) else Float.MAX_VALUE,
        )
        var at = Offset(centre.x + dx * reach, centre.y + dy * reach)
        // Two people in the same direction: slide the later one along the edge until it's clear.
        val onSide = abs(at.x - track.left) < 1f || abs(at.x - track.right) < 1f
        repeat(placed.size + 1) {
            val clash = placed.firstOrNull { hypot(it.x - at.x, it.y - at.y) < gap } ?: return@repeat
            at = if (onSide) {
                Offset(at.x, (if (at.y >= clash.y) clash.y + gap else clash.y - gap).coerceIn(track.top, track.bottom))
            } else {
                Offset((if (at.x >= clash.x) clash.x + gap else clash.x - gap).coerceIn(track.left, track.right), at.y)
            }
        }
        placed += at
        val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        EdgeMarker(person, now, angle, onPick, Modifier.offset { IntOffset((at.x - half).toInt(), (at.y - half).toInt()) })
    }
}

// The white circle, their avatar upright in the middle, and outside it an arrowhead in their own
// colour turned towards them, set far enough out to clear the pause or clock badge.
@Composable
private fun EdgeMarker(person: Person, now: Long, angle: Float, onPick: (String) -> Unit, modifier: Modifier) {
    val label = stringResource(R.string.go_to_person, person.name)
    Box(
        modifier = modifier
            .size(MARKER)
            .clip(CircleShape)
            .clickable { onPick(person.uid) }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(MARKER).rotate(angle)) {
            val r = 24.dp.toPx()
            val c = center
            val arrow = Path().apply {
                moveTo(c.x + r + 18.dp.toPx(), c.y)
                lineTo(c.x + r + 7.dp.toPx(), c.y - 8.dp.toPx())
                lineTo(c.x + r + 7.dp.toPx(), c.y + 8.dp.toPx())
                close()
            }
            // A soft shadow under the circle, then the circle; the arrowhead gets a white edge so it
            // reads on any map colour.
            drawCircle(Color(0x33000000), r + 1.5.dp.toPx(), c)
            drawCircle(Color.White, r, c)
            drawPath(arrow, Color.White, style = Stroke(width = 4.dp.toPx(), join = StrokeJoin.Round))
            drawPath(arrow, Color(person.color))
        }
        Avatar(
            person.state(now),
            person.letter,
            AVATAR,
            photo = person.member.photo,
            you = person.isYou,
            color = Color(person.color),
        )
    }
}
