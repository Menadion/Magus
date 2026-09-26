package io.github.menadion.magus

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Each member's own colour (M's call, 2026-09-26), in place of the one green everyone had: the
// filled dot while sharing, the ring and letter while quiet. Picked from the account ID so every
// phone agrees; when two land on the same colour, the later ID in order takes the next free one.
// No blue (that's you) and no grey (that's paused); white letters read at 5:1 or better on each.
object PersonColors {
    private val palette = listOf(
        0xFFC62828, // red
        0xFFB35300, // orange
        0xFF795548, // brown
        0xFF5B7318, // olive
        0xFF26803F, // green, the colour everyone used to be
        0xFF00796B, // teal
        0xFF7B42C8, // purple
        0xFFAD1457, // magenta
    ).map { it.toInt() }

    fun assign(uids: Collection<String>): Map<String, Int> {
        val taken = mutableSetOf<Int>()
        return uids.distinct().sorted().associateWith { uid ->
            var i = Math.floorMod(uid.hashCode(), palette.size)
            if (taken.size < palette.size) while (i in taken) i = (i + 1) % palette.size
            taken += i
            palette[i]
        }
    }
}

// One person as the screens see them: the member record plus whether it is this phone.
// The state rules (section 2 of the handoff) hang off this.
// youLabel is "You" in the phone's language; the screen that builds the list passes it in.
// color is their own colour from PersonColors; mine only shows on other phones.
data class Person(
    val member: Member,
    val isYou: Boolean,
    private val youLabel: String = "You",
    val color: Int = MogarColors.FamilyGreen.toArgb(),
) {
    val uid get() = member.uid
    val name get() = if (isYou) youLabel else member.name
    val letter get() = member.name.trim().take(1).uppercase()

    fun state(now: Long): Markers.State = when {
        isYou && member.sharing -> Markers.State.YOU
        isYou -> Markers.State.PAUSED
        else -> Markers.state(member, now)
    }
}

// Status line on the member card.
@Composable
fun cardStatus(person: Person, now: Long): Pair<String, Color> {
    val colors = MaterialTheme.colorScheme
    val mine = if (person.member.sharing) stringResource(R.string.sharing_on) to colors.primary else stringResource(R.string.sharing_off) to colors.onSurfaceVariant
    if (person.isYou) return mine
    return when (person.state(now)) {
        Markers.State.YOU -> mine
        Markers.State.SHARING -> stringResource(R.string.sharing) to colors.onSurfaceVariant
        Markers.State.PAUSED -> stringResource(R.string.paused_sharing) to colors.onSurfaceVariant
        Markers.State.QUIET -> stringResource(R.string.phone_is_quiet) to colors.error
    }
}

// Status line in the See all list: the state plus how long ago.
@Composable
fun listStatus(person: Person, now: Long): Pair<String, Color> {
    val colors = MaterialTheme.colorScheme
    val ago = person.member.updatedAtMillis?.let { lastSeenText(it, now) }
    val mine = if (person.member.sharing) stringResource(R.string.sharing_on) to colors.primary else stringResource(R.string.sharing_off) to colors.onSurfaceVariant
    if (person.isYou) return mine
    return when (person.state(now)) {
        Markers.State.YOU -> mine
        Markers.State.SHARING -> (if (ago == null) stringResource(R.string.sharing) else stringResource(R.string.seen_ago, ago)) to colors.onSurfaceVariant
        Markers.State.PAUSED -> (if (ago == null) stringResource(R.string.paused_sharing) else stringResource(R.string.paused_sharing_ago, ago)) to colors.onSurfaceVariant
        Markers.State.QUIET -> (if (ago == null) stringResource(R.string.phone_quiet) else stringResource(R.string.phone_quiet_ago, ago)) to colors.error
    }
}

// The round avatar used in the family row, the list and the card. Same rules as the map dot.
@Composable
fun Avatar(
    state: Markers.State,
    letter: String,
    size: Dp,
    modifier: Modifier = Modifier,
    photo: ByteArray? = null,
    you: Boolean = false, // my own circle keeps the white centre, even when paused
    color: Color = MogarColors.FamilyGreen, // their own colour, from PersonColors
) {
    val colors = MaterialTheme.colorScheme
    val letterSize = (size.value * 0.43f).sp
    val picture = remember(Photos.key(photo)) { Photos.decode(photo)?.asImageBitmap() }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (picture != null) {
            // The picture inside a ring in the state's colour; paused goes grey.
            val ring = when (state) {
                Markers.State.YOU -> colors.primary
                Markers.State.PAUSED -> MogarColors.Paused
                else -> color
            }
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = if (state == Markers.State.PAUSED) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(if (state == Markers.State.QUIET) 4.dp else 3.dp, ring, CircleShape),
            )
        } else when (state) {
            Markers.State.YOU -> {
                Box(modifier = Modifier.size(size).background(colors.primary, CircleShape))
                Box(modifier = Modifier.size(size * 0.3f).background(colors.onPrimary, CircleShape))
            }
            Markers.State.SHARING, Markers.State.PAUSED -> {
                val fill = if (state == Markers.State.SHARING) color else MogarColors.Paused
                Box(modifier = Modifier.size(size).background(fill, CircleShape))
                if (you) {
                    Box(modifier = Modifier.size(size * 0.3f).background(Color.White, CircleShape))
                } else {
                    Text(letter, style = TextStyle(fontFamily = Figtree, fontSize = letterSize, fontWeight = FontWeight.W700), color = Color.White)
                }
            }
            Markers.State.QUIET -> {
                Box(modifier = Modifier.size(size).background(Color.White, CircleShape).border(4.dp, color, CircleShape))
                Text(letter, style = TextStyle(fontFamily = Figtree, fontSize = letterSize, fontWeight = FontWeight.W700), color = color)
            }
        }
        if (state == Markers.State.PAUSED || state == Markers.State.QUIET) {
            Badge(paused = state == Markers.State.PAUSED, modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp))
        }
    }
}

// The 20 dp badge at a dot's bottom right: pause bars, or a clock for quiet.
@Composable
private fun Badge(paused: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier = modifier.size(20.dp)) {
        val r = size.minDimension / 2
        drawCircle(Color.White, r)
        drawCircle(colors.onSurface, r - 2.dp.toPx())
        if (paused) {
            val w = 2.dp.toPx()
            val h = 7.dp.toPx()
            drawRect(Color.White, Offset(center.x - 3.dp.toPx(), center.y - h / 2), Size(w, h))
            drawRect(Color.White, Offset(center.x + 1.dp.toPx(), center.y - h / 2), Size(w, h))
        } else {
            val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color.White, 5.dp.toPx(), style = stroke)
            drawLine(Color.White, center, Offset(center.x, center.y - 3.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White, center, Offset(center.x + 2.2f.dp.toPx(), center.y), 1.5.dp.toPx(), StrokeCap.Round)
        }
    }
}

// Small line icons for the card tiles and the Show everyone button. Drawn here so the app needs no icon pack.
@Composable
fun ClockGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color, size.minDimension / 2 - 1.dp.toPx(), style = stroke)
        drawLine(color, center, Offset(center.x, center.y - size.height * 0.28f), 1.8.dp.toPx(), StrokeCap.Round)
        drawLine(color, center, Offset(center.x + size.width * 0.2f, center.y), 1.8.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
fun BatteryGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx())
        val bodyW = size.width * 0.72f
        val bodyH = size.height * 0.5f
        val left = (size.width - bodyW) / 2 - 1.dp.toPx()
        val top = (size.height - bodyH) / 2
        drawRect(color, Offset(left, top), Size(bodyW, bodyH), style = stroke)
        drawRect(color, Offset(left + bodyW + 1.dp.toPx(), top + bodyH * 0.3f), Size(2.dp.toPx(), bodyH * 0.4f))
        drawRect(color, Offset(left + 3.dp.toPx(), top + 3.dp.toPx()), Size(bodyW * 0.45f, bodyH - 6.dp.toPx()))
    }
}

// Four corner arrows: "expand to show everyone".
@Composable
fun ExpandGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = 2.dp.toPx()
        val a = size.width * 0.32f
        val corners = listOf(
            Offset(0f, 0f) to Offset(1f, 1f),
            Offset(size.width, 0f) to Offset(-1f, 1f),
            Offset(0f, size.height) to Offset(1f, -1f),
            Offset(size.width, size.height) to Offset(-1f, -1f),
        )
        for ((corner, dir) in corners) {
            drawLine(color, corner, Offset(corner.x + dir.x * a, corner.y), w, StrokeCap.Round)
            drawLine(color, corner, Offset(corner.x, corner.y + dir.y * a), w, StrokeCap.Round)
            drawLine(color, corner, Offset(corner.x + dir.x * a * 1.1f, corner.y + dir.y * a * 1.1f), w, StrokeCap.Round)
        }
    }
}
