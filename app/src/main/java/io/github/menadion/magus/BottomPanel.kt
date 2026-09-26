package io.github.menadion.magus

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

// The top corners of the bottom boxes: the family box and this panel.
const val BOX_CORNER = 24

// The panel that takes the family box's place: the person's card, or the See all list. Since
// 2026-09-26 (M's call) it no longer rises out of the family box; the family box slides down and
// this slides up into the same spot, the same shape, flush with the screen's sides and bottom.
// It is as tall as what it holds: the card only takes the room it needs, so the dot it zoomed to
// stays in view, and the list asks for half the screen (listPanelHeight). Swiping it down closes
// it (a pull-down on the list once the list is at its top counts), and so do a tap on the map, the
// back button, and each content's own close button; the family box then slides back.
// The See all list's height: with the handle above it, the panel comes to half the screen.
@Composable
fun listPanelHeight(): Dp = (LocalConfiguration.current.screenHeightDp * 0.5f).dp - HANDLE_ROOM.dp

// The handle strip at the panel's top: 10 dp above the bar, the 4 dp bar, 6 dp below.
private const val HANDLE_ROOM = 20

@Composable
fun BottomPanel(onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val close by rememberUpdatedState(onClose)
    val dragged = remember { mutableFloatStateOf(0f) }

    // The list inside scrolls itself; what it cannot scroll (pulling down past its top) reaches here.
    val pullDown = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) dragged.floatValue += available.y
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragged.floatValue > threshold) close()
                dragged.floatValue = 0f
                return Velocity.Zero
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .nestedScroll(pullDown)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragged.floatValue = 0f },
                    onDragEnd = {
                        if (dragged.floatValue > threshold) close()
                        dragged.floatValue = 0f
                    },
                    onDragCancel = { dragged.floatValue = 0f },
                ) { _, dy -> dragged.floatValue += dy }
            },
        shape = RoundedCornerShape(topStart = BOX_CORNER.dp, topEnd = BOX_CORNER.dp),
        color = colors.surfaceContainerLowest, // the family box's colour, since it fills the same spot
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(32.dp).height(4.dp).background(colors.outlineVariant, CircleShape))
            }
            content()
        }
    }
}
