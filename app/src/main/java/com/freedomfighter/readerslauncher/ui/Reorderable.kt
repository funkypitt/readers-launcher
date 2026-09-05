package com.freedomfighter.readerslauncher.ui

import androidx.compose.foundation.background
import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

/**
 * A text list whose rows are dragged (after a long press) to any position, across as many
 * rows as needed, with auto-scroll near the edges. The list works on a local copy while the
 * finger is down and hands the final order to [onReorder] once — so the gesture never restarts
 * because the backing data changed mid-drag.
 */
@Composable
fun <T> ReorderableList(
    items: List<T>,
    key: (T) -> Any,
    label: (T) -> String,
    onReorder: (List<T>) -> Unit,
    onTap: ((T) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState: LazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val tick = rememberTick()
    val colors = LocalColors.current

    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var working by remember { mutableStateOf(items) }
    if (dragIndex < 0 && working !== items) working = items

    LazyColumn(
        state = listState,
        // The list's own scrolling would consume the vertical drag; hand it over while a row is lifted.
        userScrollEnabled = dragIndex < 0,
        modifier = modifier.pointerInput(Unit) {
            // Own long-press-then-drag detector in the Initial pass: once the row is lifted every
            // event is consumed here, so neither the list's scrolling nor any row can steal it.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val slop = viewConfiguration.touchSlop
                val deadline = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                while (true) {
                    val remaining = deadline - SystemClock.uptimeMillis()
                    if (remaining <= 0) break
                    val event = withTimeoutOrNull(remaining) { awaitPointerEvent(PointerEventPass.Initial) } ?: break
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!change.pressed || change.isConsumed) return@awaitEachGesture
                    if ((change.position - down.position).getDistance() > slop) return@awaitEachGesture
                }
                val startInfo = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { down.position.y.toInt() in it.offset..(it.offset + it.size) } ?: return@awaitEachGesture
                dragIndex = startInfo.index; dragOffset = 0f; tick()
                var lastY = down.position.y
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    val delta = change.position.y - lastY
                    lastY = change.position.y
                    dragOffset += delta
                    val info = listState.layoutInfo.visibleItemsInfo
                    val current = info.firstOrNull { it.index == dragIndex } ?: continue
                    val centerY = current.offset + current.size / 2f + dragOffset
                    val target = info.firstOrNull { it.index != dragIndex && centerY >= it.offset && centerY < it.offset + it.size }
                    if (target != null) {
                        val list = working.toMutableList()
                        val moved = list.removeAt(dragIndex)
                        list.add(target.index, moved)
                        working = list
                        dragOffset += (current.offset - target.offset)
                        dragIndex = target.index
                    }
                    val viewportStart = listState.layoutInfo.viewportStartOffset
                    val viewportEnd = listState.layoutInfo.viewportEndOffset
                    val edge = 96.dp.toPx()
                    val top = current.offset + dragOffset
                    val bottom = top + current.size
                    val step = 18f
                    if (top < viewportStart + edge && listState.canScrollBackward) {
                        scope.launch { listState.scrollBy(-step) }; dragOffset += step
                    } else if (bottom > viewportEnd - edge && listState.canScrollForward) {
                        scope.launch { listState.scrollBy(step) }; dragOffset -= step
                    }
                }
                if (dragIndex >= 0 && working != items) onReorder(working)
                dragIndex = -1; dragOffset = 0f
            }
        },
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        itemsIndexed(working, key = { _, it -> key(it) }) { index, item ->
            val dragging = index == dragIndex
            Box(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                    .background(if (dragging) colors.fg else colors.bg)
                    .then(if (onTap != null) Modifier.noRippleClickable { onTap(item) } else Modifier)
                    .padding(horizontal = rowPadH, vertical = rowPadV)
            ) {
                T(label(item), Modifier.fillMaxWidth(), color = if (dragging) colors.bg else colors.fg, maxLines = 1)
            }
        }
    }
}
