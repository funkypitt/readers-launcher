package com.freedomfighter.readerslauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
 * A text list whose rows can be dragged (after a long press) to reorder.
 * Kept deliberately small: single column, fixed keys, no animations beyond the lifted row.
 */
@Composable
fun <T> ReorderableList(
    items: List<T>,
    key: (T) -> Any,
    label: (T) -> String,
    onMove: (from: Int, to: Int) -> Unit,
    onTap: ((T) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState: LazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val tick = rememberTick()
    val colors = LocalColors.current
    var dragKey by remember { mutableStateOf<Any?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun itemInfo(k: Any?): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == k }

    LazyColumn(
        state = listState,
        modifier = modifier
            .pointerInput(items) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { pos.y.toInt() in it.offset..(it.offset + it.size) }
                            ?.let { dragKey = it.key; dragOffset = 0f; tick() }
                    },
                    onDragEnd = { dragKey = null; dragOffset = 0f },
                    onDragCancel = { dragKey = null; dragOffset = 0f },
                    onDrag = { change, delta ->
                        change.consume()
                        dragOffset += delta.y
                        val current = itemInfo(dragKey) ?: return@detectDragGesturesAfterLongPress
                        val centerY = current.offset + dragOffset + current.size / 2f
                        val target = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key != dragKey && centerY >= it.offset && centerY <= it.offset + it.size }
                        if (target != null) {
                            val from = current.index
                            val to = target.index
                            dragOffset -= (target.offset - current.offset)
                            onMove(from, to)
                        }
                        // Auto-scroll near the edges.
                        val viewportEnd = listState.layoutInfo.viewportEndOffset
                        val edge = 80.dp.toPx()
                        val y = current.offset + dragOffset
                        if (y < edge) scope.launch { listState.scrollBy(-12f) }
                        else if (y + current.size > viewportEnd - edge) scope.launch { listState.scrollBy(12f) }
                    }
                )
            },
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        itemsIndexed(items, key = { _, it -> key(it) }) { _, item ->
            val k = key(item)
            val dragging = k == dragKey
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

private suspend fun LazyListState.scrollBy(px: Float) {
    scroll { scrollBy(px) }
}
