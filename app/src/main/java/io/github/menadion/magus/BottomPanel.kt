package io.github.menadion.magus

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

// The family box's top corners. The panel's bottom hides behind the box by this much, so it looks
// like it rises out of the box, whatever the corner radius.
const val BOX_CORNER = 24

// The panel that rises out of the family box: the person's card, or the See all list. M's spec
// (2026-09-25 evening, from a ride app's bottom sheet): half the screen tall for both, a margin
// each side so it reads narrower than the box, touching the box. Swiping it down closes it (a
// pull-down on the list once the list is at its top counts), and so do a tap on the map, the back
// button, and each content's own close button.
@Composable
fun BottomPanel(onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val height = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
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
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .height(height)
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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colors.surfaceContainerLow,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(bottom = BOX_CORNER.dp)) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(32.dp).height(4.dp).background(colors.outlineVariant, CircleShape))
            }
            content()
        }
    }
}
