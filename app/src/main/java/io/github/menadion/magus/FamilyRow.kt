package io.github.menadion.magus

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// The family box on the bottom edge of the map: "Family", See all, and one column per person.
// Spec: HANDOFF.md section 3, "Family row". You first, then the family in a fixed order. Since
// 2026-09-25 evening (M's call, from a ride app's sheet) it sits flush with the screen's sides and
// bottom, top corners rounded. Since 2026-09-26 the card and the list (BottomPanel) take its place.
@Composable
fun FamilyRow(
    people: List<Person>,
    now: Long,
    scroll: LazyListState, // held by the map screen, so a scrolled row stays put while a card is open
    onPick: (String) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = BOX_CORNER.dp, topEnd = BOX_CORNER.dp),
        color = colors.surfaceContainerLowest,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(start = 20.dp, top = 8.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.family), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onSeeAll, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.see_all), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = MaterialTheme.typography.labelLarge.fontWeight))
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                }
            }
            People(people, now, scroll, onPick, modifier = Modifier.padding(end = 12.dp))
        }
    }
}

// Everyone spread evenly while each gets at least FIT_WIDTH (five fit on a phone); past that the
// row gives each person PERSON_WIDTH and scrolls sideways, the last one cut off at the edge to show
// there's more, with < and > at its ends that slide it by a screenful less one person (M's call,
// 2026-09-26). Each arrow shows only while there's more that way.
private val FIT_WIDTH = 64.dp
private val PERSON_WIDTH = 72.dp
private val PERSON_GAP = 4.dp

@Composable
private fun People(people: List<Person>, now: Long, list: LazyListState, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fits = FIT_WIDTH * people.size + PERSON_GAP * (people.size - 1) <= maxWidth
        if (fits) {
            Row(horizontalArrangement = Arrangement.spacedBy(PERSON_GAP)) {
                for (person in people) {
                    PersonColumn(person, now, onPick = { onPick(person.uid) }, modifier = Modifier.weight(1f))
                }
            }
            return@BoxWithConstraints
        }
        val scope = rememberCoroutineScope()
        val page = with(LocalDensity.current) { (maxWidth - PERSON_WIDTH - PERSON_GAP).toPx() }
        LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(PERSON_GAP)) {
            items(people, key = { it.uid }) { person ->
                PersonColumn(person, now, onPick = { onPick(person.uid) }, modifier = Modifier.width(PERSON_WIDTH))
            }
        }
        // A layer the row's own size, so each arrow's fade can run the row's full height.
        Box(modifier = Modifier.matchParentSize()) {
            ScrollArrow(
                visible = list.canScrollBackward,
                left = true,
                onClick = { scope.launch { list.animateScrollBy(-page) } },
                modifier = Modifier.align(Alignment.TopStart),
            )
            ScrollArrow(
                visible = list.canScrollForward,
                left = false,
                onClick = { scope.launch { list.animateScrollBy(page) } },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

// A small round < or > over a fade from the box's colour, so people and their names slide under
// it. The fade runs the row's full height; the button sits level with the avatars.
@Composable
private fun ScrollArrow(visible: Boolean, left: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        if (left) listOf(colors.surfaceContainerLowest, colors.surfaceContainerLowest.copy(alpha = 0f))
                        else listOf(colors.surfaceContainerLowest.copy(alpha = 0f), colors.surfaceContainerLowest),
                    ),
                ),
            contentAlignment = if (left) Alignment.TopStart else Alignment.TopEnd,
        ) {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = colors.surfaceContainerHigh,
                shadowElevation = 2.dp,
                // The avatar's centre is 26 dp down its column (4 dp padding, half of 44 dp).
                modifier = Modifier.padding(top = 10.dp).size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(if (left) R.string.family_scroll_left else R.string.family_scroll_right),
                        tint = colors.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
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
        Markers.State.YOU -> stringResource(R.string.go_to_you)
        Markers.State.PAUSED -> stringResource(R.string.go_to_person_paused, person.name)
        Markers.State.QUIET -> stringResource(R.string.go_to_person_quiet, person.name)
        else -> stringResource(R.string.go_to_person, person.name)
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
        Avatar(state, person.letter, 44.dp, photo = person.member.photo, you = person.isYou, color = Color(person.color))
        Text(
            person.name,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                person.isYou -> colors.primary
                state == Markers.State.PAUSED -> colors.onSurfaceVariant
                else -> colors.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// See all: every person as a row, the bottom panel's content after See all. The rows scroll inside
// the panel, so a big family never grows it. Spec: HANDOFF.md section 6; since 2026-09-25 evening
// the panel around it (BottomPanel) sets the size and shape.
@Composable
fun FamilyList(people: List<Person>, now: Long, onPick: (String) -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
            Text(stringResource(R.string.family), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.close_list))
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(people, key = { it.uid }) { person ->
                PersonRow(person, now, onPick = { onPick(person.uid) })
            }
        }
        Text(
            stringResource(R.string.tap_name_hint),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
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
            Avatar(state, person.letter, 44.dp, photo = person.member.photo, you = person.isYou, color = Color(person.color))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    person.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (state == Markers.State.PAUSED) colors.onSurfaceVariant else colors.onSurface,
                )
                Text(status, style = MaterialTheme.typography.bodyMedium, color = statusColor)
            }
            person.member.battery?.let { battery ->
                Text(
                    "$battery%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (battery < LOW_BATTERY) colors.error else MogarColors.FamilyGreen,
                )
            }
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
        }
    }
}
