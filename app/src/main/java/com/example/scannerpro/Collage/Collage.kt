package com.example.scannerpro.Collage

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitDragOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt


data class CollageItemData(
    val id: Long = System.nanoTime(),
    val bitmap: Bitmap,
    val offset: Offset = Offset.Zero,
    val size: Size = Size.Zero
)


data class CollageTemplate(val name: String, val rows: Int, val cols: Int)


// CAMBIO: Definimos las clases para el tamaño de página para que el código sea completo.
data class PageSize(val name: String, val aspectRatio: Float)

object PageSizes {
    val A4 = PageSize("A4", 1f / 1.414f)
    val Oficio = PageSize("Oficio", 1f / 1.545f)
    val Carta = PageSize("Carta", 1f / 1.294f)
    val A4Horizontal = PageSize("A4 Horiz", 1.414f)
    val default = A4
    val all = listOf(A4, Oficio, Carta, A4Horizontal)
}


// ---------------- CollagePage (hijo) ----------------
@Composable
fun CollagePage(
    pageIndex: Int,
    pageData: CollagePageData,
    draggingItemId: Long?,
    isDragging: Boolean,
    dragPreviewWidthPx: Float,
    dragPreviewHeightPx: Float,
    watermarkToDraw: WatermarkData?,
    isWatermarkDraggable: Boolean,
    onWatermarkDrag: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    onItemRemoved: (itemId: Long) -> Unit,
    onStartDrag: (itemId: Long, bmp: Bitmap, absolutePointerPos: Offset, sourcePageIndex: Int, imageWidthPx: Float, imageHeightPx: Float, touchOffset: Offset, wasSelected: Boolean) -> Unit,
    onDragMove: (absolutePointerPos: Offset) -> Unit,
    onDrop: () -> Unit,
    onPositioned: (pageIndex: Int, coords: LayoutCoordinates) -> Unit,
    onInitialLayoutComputed: (pageIndex: Int, updatedItems: List<CollageItemData>) -> Unit,
    currentTargetPageIndex: Int?,
    dragPreviewOffsetInTarget: Offset?,
    isInteractive: Boolean = true
) {
    var selectedItemId by remember { mutableStateOf<Long?>(null) }
    var layoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .background(Color.White)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { selectedItemId = null }
            .onGloballyPositioned {
                layoutCoords = it
                onPositioned(pageIndex, it)
            }
    ) {
        val canvasWidthPx = constraints.maxWidth.toFloat()
        val canvasHeightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        // Efecto para centrar imágenes nuevas (cuando su offset es Zero)
        LaunchedEffect(pageData.items, canvasWidthPx, canvasHeightPx) {
            val itemsToCenter = pageData.items.filter { it.offset == Offset.Zero }

            if (itemsToCenter.isNotEmpty() && canvasHeightPx > 0 && canvasWidthPx > 0) {
                val imageMaxWidthPx = canvasWidthPx * 0.3f
                val spacingPx = with(density) { 16.dp.toPx() }
                val newOffsets = mutableMapOf<Long, Offset>()
                val newSizes = mutableMapOf<Long, Size>()
                val itemHeights = itemsToCenter.map { item ->
                    val aspect = if (item.bitmap.height > 0) item.bitmap.width.toFloat() / item.bitmap.height.toFloat() else 1f
                    imageMaxWidthPx / aspect
                }
                val totalImagesHeight = itemHeights.sum()
                val totalSpacing = (itemsToCenter.size - 1).coerceAtLeast(0) * spacingPx
                val totalBlockHeight = totalImagesHeight + totalSpacing
                var currentY = (canvasHeightPx - totalBlockHeight) / 2f
                if (currentY < 0) currentY = spacingPx

                itemsToCenter.forEachIndexed { index, item ->
                    val imageHeightPx = itemHeights[index]
                    newOffsets[item.id] = Offset(x = (canvasWidthPx - imageMaxWidthPx) / 2f, y = currentY)
                    newSizes[item.id] = Size(imageMaxWidthPx, imageHeightPx)
                    currentY += imageHeightPx + spacingPx
                }
                val updatedItems = pageData.items.map { item ->
                    item.copy(offset = newOffsets[item.id] ?: item.offset, size = newSizes[item.id] ?: item.size)
                }
                onInitialLayoutComputed(pageIndex, updatedItems)
            }
        }

        val closeSizePx = with(density) { 24.dp.toPx() }

        pageData.items.forEach { item ->
            val currentOffset = item.offset
            val isBeingDragged = isDragging && draggingItemId == item.id
            val imageWidthPx = item.size.width.takeIf { it > 0 } ?: (canvasWidthPx * 0.3f)
            val aspect = if (item.bitmap.height > 0) item.bitmap.width.toFloat() / item.bitmap.height.toFloat() else 1f
            val imageHeightPx = item.size.height.takeIf { it > 0 } ?: (imageWidthPx / aspect)
            val imageWidthDp = with(density) { imageWidthPx.toDp() }

            if (!isBeingDragged) {
                Image(
                    bitmap = item.bitmap.asImageBitmap(),
                    contentDescription = "item-${item.id}",
                    modifier = Modifier
                        .width(imageWidthDp)
                        .aspectRatio(aspect)
                        .offset { IntOffset(currentOffset.x.roundToInt(), currentOffset.y.roundToInt()) }
                        .border(
                            width = if (selectedItemId == item.id) 3.dp else 1.dp,
                            color = if (selectedItemId == item.id) Color.Green else Color.Black
                        )
                )
            }

            val dragModifier = if(isInteractive) {
                Modifier.pointerInput(item.id, layoutCoords, currentOffset) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val touchOffsetInsideImage = down.position
                            val wasSelected = selectedItemId == item.id
                            selectedItemId = item.id
                            down.consume()
                            var hasStartedDrag = false
                            if (wasSelected) {
                                layoutCoords?.let { coords ->
                                    val pointerPositionInPage = currentOffset + down.position
                                    val absolutePointer = coords.localToWindow(pointerPositionInPage)
                                    onStartDrag(
                                        item.id, item.bitmap, absolutePointer, pageIndex,
                                        imageWidthPx, imageHeightPx, touchOffsetInsideImage, wasSelected
                                    )
                                    hasStartedDrag = true
                                }
                            }
                            var pointerId = down.id
                            while (true) {
                                val dragChange = awaitDragOrCancellation(pointerId)
                                if (dragChange == null) {
                                    if (hasStartedDrag) onDrop()
                                    break
                                }
                                layoutCoords?.let { coords ->
                                    val pointerPositionInPage = currentOffset + dragChange.position
                                    val absolute = coords.localToWindow(pointerPositionInPage)
                                    if (!hasStartedDrag) {
                                        onStartDrag(
                                            item.id, item.bitmap, absolute, pageIndex,
                                            imageWidthPx, imageHeightPx, touchOffsetInsideImage, wasSelected
                                        )
                                        hasStartedDrag = true
                                    }
                                    onDragMove(absolute)
                                }
                                dragChange.consume()
                                pointerId = dragChange.id
                            }
                        }
                    }
                }
            } else Modifier

            Box(
                modifier = Modifier
                    .offset { IntOffset(currentOffset.x.roundToInt(), currentOffset.y.roundToInt()) }
                    .width(imageWidthDp)
                    .aspectRatio(aspect)
                    .then(dragModifier)
            )

            if (isInteractive && selectedItemId == item.id && !isBeingDragged) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (currentOffset.x + imageWidthPx - (closeSizePx / 2f)).roundToInt(),
                                (currentOffset.y - (closeSizePx / 2f)).roundToInt()
                            )
                        }
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                        .clickable { onItemRemoved(item.id) }
                ) {
                    Icon(Icons.Default.Close, "Eliminar", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }

        val snapThresholdPx = 5f

        val (horizontalSnapLines, verticalSnapLines) = remember(isDragging, dragPreviewOffsetInTarget, pageData.items, draggingItemId) {
            if (currentTargetPageIndex != pageIndex || dragPreviewOffsetInTarget == null || !isDragging) {
                Pair(emptyList(), emptyList())
            } else {
                val otherItems = pageData.items.filter { it.id != draggingItemId && it.size != Size.Zero }
                val previewX = dragPreviewOffsetInTarget.x
                val previewY = dragPreviewOffsetInTarget.y
                val previewW = dragPreviewWidthPx
                val previewH = dragPreviewHeightPx

                val hSnaps = mutableListOf<Triple<Float, Float, Float>>()
                val vSnaps = mutableListOf<Triple<Float, Float, Float>>()

                val previewBoundsX = listOf(previewX, previewX + previewW)
                val allBoundsX = otherItems.flatMap { listOf(it.offset.x, it.offset.x + it.size.width) } + previewBoundsX
                val contentMinX = allBoundsX.minOrNull() ?: 0f
                val contentMaxX = allBoundsX.maxOrNull() ?: canvasWidthPx

                val previewBoundsY = listOf(previewY, previewY + previewH)
                val allBoundsY = otherItems.flatMap { listOf(it.offset.y, it.offset.y + it.size.height) } + previewBoundsY
                val contentMinY = allBoundsY.minOrNull() ?: 0f
                val contentMaxY = allBoundsY.maxOrNull() ?: canvasHeightPx

                otherItems.forEach { other ->
                    val oX = other.offset.x; val oY = other.offset.y; val oW = other.size.width; val oH = other.size.height
                    listOf(oX, oX + oW / 2f, oX + oW).forEach { snapX ->
                        listOf(previewX, previewX + previewW / 2f, previewX + previewW).forEach { previewSnapX ->
                            if (abs(previewSnapX - snapX) <= snapThresholdPx) vSnaps.add(Triple(snapX, contentMinY, contentMaxY))
                        }
                    }
                    listOf(oY, oY + oH / 2f, oY + oH).forEach { snapY ->
                        listOf(previewY, previewY + previewH / 2f, previewY + previewH).forEach { previewSnapY ->
                            if (abs(previewSnapY - snapY) <= snapThresholdPx) hSnaps.add(Triple(snapY, contentMinX, contentMaxX))
                        }
                    }
                }
                Pair(hSnaps.distinct(), vSnaps.distinct())
            }
        }

        if (horizontalSnapLines.isNotEmpty() || verticalSnapLines.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)
                horizontalSnapLines.forEach { (y, startX, endX) ->
                    drawLine(Color.Red, start = Offset(startX, y), end = Offset(endX, y), strokeWidth = 4f, pathEffect = dashEffect)
                }
                verticalSnapLines.forEach { (x, startY, endY) ->
                    drawLine(Color.Red, start = Offset(x, startY), end = Offset(x, endY), strokeWidth = 4f, pathEffect = dashEffect)
                }
            }
        }

        if (watermarkToDraw != null) {
            WatermarkCanvas(
                watermark = watermarkToDraw,
                isDraggable = isWatermarkDraggable,
                onDrag = onWatermarkDrag,
                pageWidthPx = canvasWidthPx,
                pageHeightPx = canvasHeightPx
            )
        }
    }
}

// ---------------- CollageScreen (padre) ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(
    initialBitmaps: List<Bitmap>,
    onClose: () -> Unit,
    onSave: (List<CollagePageData>) -> Unit
) {
    var pages by remember {
        mutableStateOf(
            initialBitmaps.chunked(2).map { chunk ->
                CollagePageData(items = chunk.map { CollageItemData(bitmap = it) })
            }.ifEmpty { listOf(CollagePageData(items = emptyList())) }
        )
    }

    var draggingItemId by remember { mutableStateOf<Long?>(null) }
    var dragPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPointerPosition by remember { mutableStateOf(Offset.Zero) }
    var dragTouchOffsetInsideImage by remember { mutableStateOf(Offset.Zero) }
    var sourcePageIndexForDrag by remember { mutableStateOf<Int?>(null) }
    var dragPreviewWidthPx by remember { mutableStateOf(0f) }
    var dragPreviewHeightPx by remember { mutableStateOf(0f) }
    var dragPreviewShowBorder by remember { mutableStateOf(false) }
    var currentTargetPageIndex by remember { mutableStateOf<Int?>(null) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var pageIndexToDelete by remember { mutableStateOf<Int?>(null) }

    var isTemplateMenuVisible by remember { mutableStateOf(false) }
    var selectedTemplateName by remember { mutableStateOf<String?>(null) }
    var showWatermarkEditor by remember { mutableStateOf(false) }
    var appliedWatermark by remember { mutableStateOf<WatermarkData?>(null) }

    var selectedPageSize by remember { mutableStateOf(PageSizes.default) }
    var isSizeMenuVisible by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var listLayoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val pageCoords = remember { mutableStateMapOf<Int, LayoutCoordinates>() }

    val density = LocalDensity.current
    var lazyColumnWidthPx by remember { mutableStateOf(0f) }

    val templates = remember {
        listOf(
            CollageTemplate("Pasaporte", 1, 1), CollageTemplate("Licencia", 2, 1), CollageTemplate("ID Card", 2, 1),
            CollageTemplate("2x1", 2, 1), CollageTemplate("1x2", 1, 2), CollageTemplate("1x3", 1, 3),
            CollageTemplate("3x1", 3, 1), CollageTemplate("1x4", 1, 4), CollageTemplate("4x1", 4, 1),
            CollageTemplate("2x2", 2, 2), CollageTemplate("2x3", 2, 3), CollageTemplate("3x2", 3, 2),
            CollageTemplate("3x3", 3, 3), CollageTemplate("8x1", 8, 1), CollageTemplate("1x8", 1, 8),
        )
    }

    fun reapplyLayout(template: CollageTemplate, pageSize: PageSize) {
        if (lazyColumnWidthPx <= 0) return

        val pageContentWidthPx = lazyColumnWidthPx
        val pageContentHeightPx = pageContentWidthPx / pageSize.aspectRatio
        val allItems = pages.flatMap { it.items }
        if (allItems.isEmpty()) return

        val itemsPerPage = when (template.name) {
            "Pasaporte" -> 1
            "Licencia", "ID Card" -> 2
            else -> template.rows * template.cols
        }

        val chunkedItems = allItems.chunked(itemsPerPage)
        val newPages = chunkedItems.map { pageItems ->
            val updatedItemsForPage = applyTemplateToItems(pageItems, template, pageContentWidthPx, pageContentHeightPx, density)
            CollagePageData(items = updatedItemsForPage)
        }
        if (newPages.isNotEmpty()) pages = newPages
    }

    LaunchedEffect(selectedPageSize) {
        selectedTemplateName?.let { templateName ->
            templates.find { it.name == templateName }?.let { template ->
                reapplyLayout(template, selectedPageSize)
            }
        }
    }


    LaunchedEffect(isDragging, dragPointerPosition) {
        if (!isDragging) return@LaunchedEffect
        while (isActive) {
            listLayoutCoords?.let { coords ->
                val listTop = coords.localToWindow(Offset.Zero).y
                val listBottom = listTop + coords.size.height
                val threshold = coords.size.height * 0.15f
                val scrollAmount = 40f
                if (dragPointerPosition.y < listTop + threshold) {
                    scope.launch { lazyListState.animateScrollBy(-scrollAmount) }
                } else if (dragPointerPosition.y > listBottom - threshold) {
                    scope.launch { lazyListState.animateScrollBy(scrollAmount) }
                }
            }
            delay(50)
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onConfirm = {
                pageIndexToDelete?.let { index ->
                    if (pages.size > 1) {
                        pages = pages.filterIndexed { i, _ -> i != index }
                    }
                }
                showDeleteDialog = false
                pageIndexToDelete = null
            },
            onDismiss = {
                showDeleteDialog = false
                pageIndexToDelete = null
            }
        )
    }


    if (showWatermarkEditor) {
        WatermarkEditorScreen(
            initialWatermark = appliedWatermark,
            previewPage = pages.firstOrNull(),
            onDismiss = { showWatermarkEditor = false },
            onApply = { newWatermark ->
                appliedWatermark = newWatermark
                showWatermarkEditor = false
            },
            onRemove = {
                appliedWatermark = null
                showWatermarkEditor = false
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val pageCount = pages.size
                        val currentPage = (lazyListState.firstVisibleItemIndex + 1).coerceIn(1, pageCount)
                        Text("Editor ($currentPage / $pageCount)", color = Color.White)
                    },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar", tint = Color.White) } },
                    actions = {},
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2C2C2E))
                )
            },
            bottomBar = {
                CollageBottomMenu(
                    templates = templates,
                    isTemplateMenuVisible = isTemplateMenuVisible,
                    selectedTemplateName = selectedTemplateName,
                    isSizeMenuVisible = isSizeMenuVisible,
                    selectedPageSize = selectedPageSize,
                    onTemplateButtonClicked = {
                        isSizeMenuVisible = false
                        isTemplateMenuVisible = !isTemplateMenuVisible
                    },
                    onSizeButtonClicked = {
                        isTemplateMenuVisible = false
                        isSizeMenuVisible = !isSizeMenuVisible
                    },
                    onWatermarkClicked = {
                        isTemplateMenuVisible = false
                        isSizeMenuVisible = false
                        showWatermarkEditor = true
                    },
                    onTemplateSelected = { template ->
                        selectedTemplateName = template.name
                        reapplyLayout(template, selectedPageSize)
                    },
                    onPageSizeSelected = { newSize ->
                        selectedPageSize = newSize
                    },
                    onAddPage = {
                        pages = pages + CollagePageData(items = emptyList())
                        scope.launch { lazyListState.animateScrollToItem(pages.lastIndex) }
                    },
                    onDeletePage = {
                        pageIndexToDelete = lazyListState.firstVisibleItemIndex
                        showDeleteDialog = true
                    },
                    isDeleteEnabled = pages.size > 1,
                    onSave = { onSave(pages) }
                )
            },
            containerColor = Color.DarkGray
        ) { paddingValues ->
            Box(Modifier.fillMaxSize().padding(paddingValues)) {
                var dragContainerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize().onGloballyPositioned { listLayoutCoords = it },
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(count = pages.size, key = { pages[it].id }) { pageIndex ->
                        val page = pages[pageIndex]
                        CollagePage(
                            pageIndex = pageIndex,
                            pageData = page,
                            draggingItemId = draggingItemId,
                            isDragging = isDragging,
                            dragPreviewWidthPx = dragPreviewWidthPx,
                            dragPreviewHeightPx = dragPreviewHeightPx,
                            watermarkToDraw = appliedWatermark,
                            isWatermarkDraggable = false,
                            onWatermarkDrag = {},
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .onGloballyPositioned {
                                    if (lazyColumnWidthPx == 0f) {
                                        lazyColumnWidthPx = it.size.width.toFloat()
                                    }
                                }
                                .aspectRatio(selectedPageSize.aspectRatio),
                            onItemRemoved = { itemIdToRemove ->
                                pages = pages.map { p -> p.copy(items = p.items.filterNot { it.id == itemIdToRemove }) }
                            },
                            onStartDrag = { itemId, bmp, pos, pageIdx, w, h, touch, wasSelected ->
                                draggingItemId = itemId; dragPreviewBitmap = bmp; isDragging = true; dragPointerPosition = pos
                                sourcePageIndexForDrag = pageIdx; dragTouchOffsetInsideImage = touch
                                dragPreviewWidthPx = w; dragPreviewHeightPx = h; dragPreviewShowBorder = wasSelected
                            },
                            onDragMove = { pos ->
                                dragPointerPosition = pos
                                currentTargetPageIndex = pageCoords.entries.firstOrNull { (_, layoutCoordinates) ->
                                    if (!layoutCoordinates.isAttached) return@firstOrNull false
                                    val pageTop = layoutCoordinates.localToWindow(Offset.Zero).y
                                    val pageBottom = pageTop + layoutCoordinates.size.height
                                    pos.y in pageTop..pageBottom
                                }?.key
                            },
                            onDrop = {
                                val itemId = draggingItemId ?: return@CollagePage
                                val bmp = dragPreviewBitmap ?: return@CollagePage
                                val targetIdx = currentTargetPageIndex ?: sourcePageIndexForDrag ?: return@CollagePage
                                val targetCoords = pageCoords[targetIdx]
                                if (targetCoords == null || !targetCoords.isAttached) {
                                    isDragging = false
                                    return@CollagePage
                                }
                                val localPos = targetCoords.windowToLocal(dragPointerPosition)
                                val newOffset = localPos - dragTouchOffsetInsideImage
                                val finalItem = CollageItemData(itemId, bmp, newOffset, Size(dragPreviewWidthPx, dragPreviewHeightPx))
                                pages = pages.mapIndexed { index, pageData ->
                                    var items = pageData.items.filterNot { it.id == itemId }
                                    if (index == targetIdx) items = items + finalItem
                                    pageData.copy(items = items)
                                }
                                isDragging = false; draggingItemId = null
                            },
                            onPositioned = { idx, coords -> pageCoords[idx] = coords },
                            onInitialLayoutComputed = { idx, updatedItems ->
                                pages = pages.toMutableList().apply {
                                    if (idx < this.size) this[idx] = this[idx].copy(items = updatedItems)
                                }
                            },
                            currentTargetPageIndex = currentTargetPageIndex,
                            dragPreviewOffsetInTarget = if (currentTargetPageIndex == pageIndex && isDragging) {
                                pageCoords[pageIndex]?.windowToLocal(dragPointerPosition)?.minus(dragTouchOffsetInsideImage)
                            } else null
                        )
                    }
                    item {
                        AddPageCanvas(
                            onClick = {
                                pages = pages + CollagePageData(items = emptyList())
                                scope.launch { lazyListState.animateScrollToItem(pages.lastIndex) }
                            },
                            modifier = Modifier.fillParentMaxWidth().height(150.dp)
                        )
                    }
                }

                if (isDragging && dragPreviewBitmap != null) {
                    val containerBasePos = dragContainerCoords?.localToWindow(Offset.Zero) ?: Offset.Zero
                    val left = with(density) { (dragPointerPosition.x - containerBasePos.x - dragTouchOffsetInsideImage.x).toDp() }
                    val top = with(density) { (dragPointerPosition.y - containerBasePos.y - dragTouchOffsetInsideImage.y).toDp() }
                    val width = with(density) { dragPreviewWidthPx.toDp() }

                    Image(
                        bitmap = dragPreviewBitmap!!.asImageBitmap(),
                        contentDescription = "Arrastrando item",
                        modifier = Modifier.offset(x = left, y = top).width(width)
                            .aspectRatio(dragPreviewWidthPx / dragPreviewHeightPx.coerceAtLeast(1f))
                            .shadow(12.dp, RoundedCornerShape(4.dp))
                            .border(width = if (dragPreviewShowBorder) 3.dp else 1.dp, color = if (dragPreviewShowBorder) Color.Green else Color.Black.copy(alpha = 0.5f))
                            .zIndex(10f)
                    )
                }
                Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { dragContainerCoords = it })
            }
        }
    }
}

@Composable
private fun CollageBottomMenu(
    templates: List<CollageTemplate>,
    isTemplateMenuVisible: Boolean,
    selectedTemplateName: String?,
    isSizeMenuVisible: Boolean,
    selectedPageSize: PageSize,
    onTemplateButtonClicked: () -> Unit,
    onSizeButtonClicked: () -> Unit,
    onWatermarkClicked: () -> Unit,
    onTemplateSelected: (CollageTemplate) -> Unit,
    onPageSizeSelected: (PageSize) -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: () -> Unit,
    isDeleteEnabled: Boolean,
    onSave: () -> Unit
) {
    Column {
        AnimatedVisibility(
            visible = isTemplateMenuVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Box(modifier = Modifier.background(Color(0xE61C1C1E))) {
                TemplateSelectionRow(templates, selectedTemplateName, onTemplateSelected)
            }
        }

        AnimatedVisibility(
            visible = isSizeMenuVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Box(modifier = Modifier.background(Color(0xE61C1C1E))) {
                // CORRECCIÓN: Se renombra la llamada a la función para resolver la ambigüedad.
                CollagePageSizeSelectionRow(
                    currentSize = selectedPageSize,
                    onSizeSelected = onPageSizeSelected
                )
            }
        }

        Surface(color = Color(0xFF2C2C2E), contentColor = Color.White) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        MainActionItem("Plantilla", onTemplateButtonClicked) {
                            Icon(Icons.Default.AutoAwesomeMosaic, "Plantilla", tint = Color.White)
                        }
                    }
                    item {
                        MainActionItem("Marca de agua", onWatermarkClicked) {
                            Icon(Icons.Default.BrandingWatermark, "Marca de agua", tint = Color.White)
                        }
                    }
                    // CAMBIO: Se actualiza la llamada para el botón de tamaño
                    item {
                        MainActionItem(
                            text = selectedPageSize.name, // Texto dinámico
                            onClick = onSizeButtonClicked
                        ) {
                            PageSizeIcon(pageSize = selectedPageSize) // Icono dinámico
                        }
                    }
                    item {
                        MainActionItem("Agregar", onAddPage) {
                            Icon(Icons.Default.NoteAdd, "Agregar", tint = Color.White)
                        }
                    }
                    item {
                        MainActionItem("Eliminar", onDeletePage, enabled = isDeleteEnabled) {
                            val color = if (isDeleteEnabled) Color.White else Color.Gray
                            Icon(Icons.Default.Delete, "Eliminar", tint = color)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Green)
                        .clickable(onClick = onSave),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, "Guardar", tint = Color.Black)
                }
            }
        }
    }
}

// CORRECCIÓN: Se renombra la función para resolver la ambigüedad.
@Composable
private fun CollagePageSizeSelectionRow(currentSize: PageSize, onSizeSelected: (PageSize) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(PageSizes.all) { size ->
            val isSelected = size.name == currentSize.name
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
                    .border(2.dp, if (isSelected) Color.Green else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSizeSelected(size) }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
                    .width(60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PageSizeIcon(pageSize = size, isSelected = isSelected)
                Spacer(Modifier.height(4.dp))
                Text(
                    size.name,
                    color = if (isSelected) Color.Green else Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    maxLines = 2,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

@Composable
private fun TemplateSelectionRow(templates: List<CollageTemplate>, selected: String?, onSelect: (CollageTemplate) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(templates) { template ->
            val isSelected = template.name == selected
            Column(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.DarkGray)
                    .border(2.dp, if (isSelected) Color.Green else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(template) }.padding(vertical = 8.dp, horizontal = 4.dp).width(60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TemplateIcon(template, isSelected)
                Spacer(Modifier.height(4.dp))
                Text(template.name, color = if (isSelected) Color.Green else Color.White, textAlign = TextAlign.Center, fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp)
            }
        }
    }
}

// CAMBIO: Se modifica MainActionItem para aceptar un @Composable como icono.
@Composable
fun MainActionItem(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconContent: @Composable () -> Unit
) {
    val color = if (enabled) Color.White else Color.Gray
    Column(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // En lugar de un Icon fijo, usamos el Composable que nos pasan.
        iconContent()
        Spacer(Modifier.height(4.dp))
        Text(text, color = color, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 14.sp)
    }
}

@Composable
private fun TemplateIcon(template: CollageTemplate, isSelected: Boolean) {
    val color = if (isSelected) Color.Green else Color.White
    Canvas(Modifier.size(32.dp)) {
        val p = size.width * 0.1f; val w = size.width - 2 * p; val h = size.height - 2 * p
        val spH = if (template.cols > 1) w * 0.1f else 0f; val spV = if (template.rows > 1) h * 0.1f else 0f
        val cellW = (w - (spH * (template.cols - 1).coerceAtLeast(0))) / template.cols
        val cellH = (h - (spV * (template.rows - 1).coerceAtLeast(0))) / template.rows
        for (row in 0 until template.rows) {
            for (col in 0 until template.cols) {
                drawRect(color, topLeft = Offset(p + col * (cellW + spH), p + row * (cellH + spV)), size = Size(cellW, cellH))
            }
        }
    }
}

// CAMBIO: Nuevo Composable para dibujar un icono dinámico según el tamaño de página.
@Composable
private fun PageSizeIcon(pageSize: PageSize, isSelected: Boolean = true) {
    val color = if (isSelected) Color.Green else Color.White
    Canvas(modifier = Modifier.size(24.dp)) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val aspectRatio = pageSize.aspectRatio

        val rectSize = if (aspectRatio > 1) { // Horizontal
            Size(canvasWidth * 0.9f, (canvasWidth * 0.9f) / aspectRatio)
        } else { // Vertical
            Size((canvasHeight * 0.9f) * aspectRatio, canvasHeight * 0.9f)
        }

        val topLeft = Offset(
            (canvasWidth - rectSize.width) / 2,
            (canvasHeight - rectSize.height) / 2
        )

        drawRect(
            color = color,
            topLeft = topLeft,
            size = rectSize,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
fun AddPageCanvas(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.2f)).clickable(onClick = onClick).padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Add, "Añadir Página", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp)); Text("Añadir Página", color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar Eliminación") },
        text = { Text("¿Estás seguro de que quieres eliminar esta página? Esta acción no se puede deshacer.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun applyTemplateToItems(
    items: List<CollageItemData>,
    template: CollageTemplate,
    canvasWidth: Float,
    canvasHeight: Float,
    density: Density
): List<CollageItemData> {
    if (items.isEmpty() || canvasWidth == 0f || canvasHeight == 0f) return items
    val marginPx = with(density) { 12.dp.toPx() }
    val availableWidth = canvasWidth - (2 * marginPx)
    val availableHeight = canvasHeight - (2 * marginPx)
    val updatedItems: List<CollageItemData>

    when (template.name) {
        "Pasaporte" -> {
            val item = items.firstOrNull() ?: return emptyList()
            val bmpW = item.bitmap.width.toFloat()
            val bmpH = item.bitmap.height.toFloat()
            if (bmpW <= 0f || bmpH <= 0f) return items

            val wScale = availableWidth / bmpW
            val hScale = availableHeight / bmpH
            val scale = min(wScale, hScale) * 0.7f

            val imageSize = Size(width = bmpW * scale, height = bmpH * scale)
            val offsetX = marginPx + (availableWidth - imageSize.width) / 2
            val offsetY = marginPx + (availableHeight - imageSize.height) / 2
            updatedItems = listOf(item.copy(offset = Offset(offsetX, offsetY), size = imageSize))
        }
        "Licencia", "ID Card" -> {
            val initialImageMaxWidthPx = availableWidth * 0.8f
            val initialSpacingPx = with(density) { 16.dp.toPx() }

            val initialItemHeights = items.map {
                val aspect = if (it.bitmap.height > 0) it.bitmap.width.toFloat() / it.bitmap.height.toFloat() else 1f
                initialImageMaxWidthPx / aspect
            }
            val initialTotalBlockHeight = initialItemHeights.sum() + ((items.size - 1).coerceAtLeast(0) * initialSpacingPx)

            val scaleFactor = if (initialTotalBlockHeight > availableHeight) {
                availableHeight / initialTotalBlockHeight
            } else {
                1.0f
            }

            val scaledImageMaxWidthPx = initialImageMaxWidthPx * scaleFactor
            val scaledSpacingPx = initialSpacingPx * scaleFactor
            val scaledItemHeights = initialItemHeights.map { it * scaleFactor }
            val totalBlockHeight = scaledItemHeights.sum() + ((items.size - 1).coerceAtLeast(0) * scaledSpacingPx)

            var currentY = marginPx + (availableHeight - totalBlockHeight) / 2f
            if (currentY < marginPx) currentY = marginPx

            updatedItems = items.mapIndexed { index, item ->
                val imageHeightPx = scaledItemHeights[index]
                val newOffset = Offset(x = marginPx + (availableWidth - scaledImageMaxWidthPx) / 2f, y = currentY)
                val newSize = Size(scaledImageMaxWidthPx, imageHeightPx)
                currentY += imageHeightPx + scaledSpacingPx
                item.copy(offset = newOffset, size = newSize)
            }
        }
        else -> {
            val spacingPx = with(density) { 8.dp.toPx() }
            val totalHSpacing = (template.cols - 1) * spacingPx; val totalVSpacing = (template.rows - 1) * spacingPx
            val cellWidth = (availableWidth - totalHSpacing) / template.cols; val cellHeight = (availableHeight - totalVSpacing) / template.rows
            updatedItems = items.mapIndexedNotNull { index, item ->
                val r = index / template.cols; val c = index % template.cols
                if (r >= template.rows) return@mapIndexedNotNull null
                val newX = marginPx + c * (cellWidth + spacingPx); val newY = marginPx + r * (cellHeight + spacingPx)
                val bmpW = item.bitmap.width.toFloat(); val bmpH = item.bitmap.height.toFloat()
                if (bmpW <= 0f || bmpH <= 0f) return@mapIndexedNotNull item.copy(size = Size.Zero)
                val wScale = cellWidth / bmpW; val hScale = cellHeight / bmpH; val scale = min(wScale, hScale)
                val newSize = Size(width = bmpW * scale, height = bmpH * scale)
                val centeredX = newX + (cellWidth - newSize.width) / 2; val centeredY = newY + (cellHeight - newSize.height) / 2
                item.copy(offset = Offset(centeredX, centeredY), size = newSize)
            }
        }
    }
    return updatedItems
}


