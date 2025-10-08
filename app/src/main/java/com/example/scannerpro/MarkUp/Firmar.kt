package com.example.scannerpro.signature

import android.R.attr.x
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Parcelable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitDragOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.parcelize.Parcelize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

// --- Helpers to make state saveable ---
@Parcelize
private data class ParcelableOffset(val x: Float, val y: Float) : Parcelable {
    fun toOffset() = Offset(x, y)
}

@Parcelize
private data class ParcelableStroke(val points: List<ParcelableOffset>) : Parcelable

private fun Offset.toParcelable() = ParcelableOffset(x, y)

private val ColorSaver = Saver<Color, Long>(
    save = { it.value.toLong() },
    restore = { Color(it.toULong()) }
)

private val OffsetSaver = Saver<Offset, List<Float>>(
    save = { listOf(it.x, it.y) },
    restore = { Offset(it[0], it[1]) }
)

@Parcelize
private enum class SignatureMode : Parcelable { DRAWING, PLACING }

@Composable
fun SignatureScreen(
    baseBitmaps: List<Bitmap>,
    initialPageIndex: Int,
    onSignatureComplete: (Int, Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(SignatureMode.DRAWING) }
    var parcelableStrokes by rememberSaveable { mutableStateOf<List<ParcelableStroke>>(emptyList()) }

    val strokes = remember(parcelableStrokes) {
        parcelableStrokes.map { stroke -> stroke.points.map { it.toOffset() } }
    }

    val onAddStroke: (List<Offset>) -> Unit = { newStroke ->
        parcelableStrokes = parcelableStrokes + ParcelableStroke(newStroke.map { it.toParcelable() })
    }

    var strokeColor by rememberSaveable(stateSaver = ColorSaver) { mutableStateOf(Color.Black) }
    var strokeWidth by rememberSaveable { mutableStateOf(5f) }
    var signatureBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var signatureOffset by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset.Zero) }
    var signatureScale by rememberSaveable { mutableStateOf(0f) }


    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation
        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(activity, mode) {
        activity?.requestedOrientation = if (mode == SignatureMode.DRAWING) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(mode, strokes, strokeColor, strokeWidth) {
        if (mode == SignatureMode.PLACING && signatureBitmap == null && strokes.any { it.isNotEmpty() }) {
            signatureBitmap = captureSignature(strokes, strokeColor, strokeWidth)
        }
    }

    if (mode == SignatureMode.DRAWING) {
        DrawingContent(
            strokes = strokes,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            onAddStroke = onAddStroke,
            onCancel = onCancel,
            onColorChange = { strokeColor = it },
            onStrokeWidthChange = { strokeWidth = it },
            onUndo = {
                if (parcelableStrokes.isNotEmpty()) {
                    parcelableStrokes = parcelableStrokes.dropLast(1)
                }
            },
            onClear = { parcelableStrokes = emptyList() },
            onConfirm = {
                if (strokes.any { it.isNotEmpty() }) {
                    mode = SignatureMode.PLACING
                }
            }
        )
    } else { // PLACING mode
        PlacingContent(
            baseBitmaps = baseBitmaps,
            initialPageIndex = initialPageIndex,
            signatureBitmap = signatureBitmap,
            signatureOffset = signatureOffset,
            signatureScale = signatureScale,
            onOffsetChange = { signatureOffset = it },
            onScaleChange = { signatureScale = it },
            onCancel = onCancel,
            onDeleteSignature = {
                parcelableStrokes = emptyList()
                signatureBitmap = null
                mode = SignatureMode.DRAWING
            },
            onSignatureComplete = onSignatureComplete
        )
    }
}

@Composable
private fun DrawingContent(
    strokes: List<List<Offset>>,
    strokeColor: Color,
    strokeWidth: Float,
    onAddStroke: (List<Offset>) -> Unit,
    onCancel: () -> Unit,
    onColorChange: (Color) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1E))
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SignatureDrawingCanvas(
                    modifier = Modifier.weight(1f),
                    strokes = strokes,
                    strokeColor = strokeColor,
                    strokeWidth = strokeWidth,
                    onAddStroke = onAddStroke
                )
                Text(
                    text = "Firme formalmente y claramente",
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        SignatureDrawingControlsVertical(
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            onCancel = onCancel,
            onColorChange = onColorChange,
            onStrokeWidthChange = onStrokeWidthChange,
            onUndo = onUndo,
            onClear = onClear,
            onConfirm = onConfirm
        )
    }
}
// ------------------ PlacingContent (actualizado) ------------------

// ------------------ PlacingContent (actualizado) ------------------
@Composable
private fun PlacingContent(
    baseBitmaps: List<Bitmap>,
    initialPageIndex: Int,
    signatureBitmap: ImageBitmap?,
    signatureOffset: Offset,
    signatureScale: Float,
    onOffsetChange: (Offset) -> Unit,
    onScaleChange: (Float) -> Unit,
    onCancel: () -> Unit,
    onDeleteSignature: () -> Unit,
    onSignatureComplete: (Int, Bitmap) -> Unit
) {
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialPageIndex)
    val density = LocalDensity.current

    // guardamos por página y relativa (0..1)
    var signaturePageIndex by rememberSaveable { mutableStateOf(initialPageIndex) }
    var signatureRelative by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset(0.5f, 0.5f)) }
    var signatureScreenOffset by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(signatureOffset) }
    var currentSignatureScale by rememberSaveable { mutableStateOf(signatureScale) }

    var isSignatureActive by rememberSaveable { mutableStateOf(false) }
    var isInitialPosSet by remember { mutableStateOf(false) }
    var finalPageIndex by rememberSaveable { mutableStateOf(initialPageIndex) }

    var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var containerIntSize by remember { mutableStateOf(IntSize(0, 0)) }

    // canal para auto-scroll (-ve = up, +ve = down, 0 = stop)
    var scrollChannel by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF212121))
            .onGloballyPositioned {
                containerCoords = it
                containerIntSize = IntSize(it.size.width, it.size.height)
            }
    ) {
        // helper: tamaño escalado de la imagen dentro del contenedor
        fun computeImageLayoutForPage(base: Bitmap): Pair<Float, Float> {
            val pagePaddingPx = with(density) { 16.dp.toPx() }
            val viewWidth = (containerIntSize.width - (pagePaddingPx * 2)).coerceAtLeast(1f)
            val viewHeight = (containerIntSize.height - (pagePaddingPx * 2)).coerceAtLeast(1f)
            val bitmapAspectRatio = base.width.toFloat() / base.height.toFloat()
            val viewAspectRatio = viewWidth / viewHeight
            return if (bitmapAspectRatio > viewAspectRatio) {
                Pair(viewWidth, viewWidth / bitmapAspectRatio)
            } else {
                Pair(viewHeight * bitmapAspectRatio, viewHeight)
            }
        }

        // calcula offset absoluto (top-left) de la firma a partir de (pageIndex, relative)
        fun computeAbsoluteOffsetFromRelative(pageIndex: Int, relative: Offset, signatureBmp: ImageBitmap?, sigScale: Float): Offset? {
            val base = baseBitmaps.getOrNull(pageIndex) ?: return null
            if (containerIntSize.width == 0 || containerIntSize.height == 0 || signatureBmp == null) return null
            val (scaledW, scaledH) = computeImageLayoutForPage(base)
            val imageLeft = (containerIntSize.width - scaledW) / 2f

            val visibleItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == pageIndex } ?: return null
            val imageTop = visibleItem.offset + (containerIntSize.height - scaledH) / 2f

            val centerX = imageLeft + relative.x * scaledW
            val centerY = imageTop + relative.y * scaledH

            val sigDrawW = signatureBmp.width * sigScale
            val sigDrawH = signatureBmp.height * sigScale

            val topLeftX = centerX - sigDrawW / 2f
            val topLeftY = centerY - sigDrawH / 2f

            return Offset(topLeftX, topLeftY)
        }

        // Recalcula pantalla -> aplica sólo cuando hay cambio significativo (>1px)
        fun applyAbsoluteIfSignificant(newAbs: Offset) {
            val dx = kotlin.math.abs(newAbs.x - signatureScreenOffset.x)
            val dy = kotlin.math.abs(newAbs.y - signatureScreenOffset.y)
            if (dx > 1f || dy > 1f) {
                signatureScreenOffset = Offset(newAbs.x.roundToInt().toFloat(), newAbs.y.roundToInt().toFloat())
                onOffsetChange(signatureScreenOffset)
            }
        }

        // Auto-scroll job: ejecuta scrollBy mientras scrollChannel != 0
        LaunchedEffect(scrollChannel) {
            if (scrollChannel == 0f) return@LaunchedEffect
            while (isActive && scrollChannel != 0f) {
                // usa un paso moderado; ajusta la magnitud en DraggableSignature si quieres
                lazyListState.scrollBy(scrollChannel)
                delay(35) // 35ms para evitar micro-movimientos (≈28fps)
            }
        }

        // Observa cambios de scroll / layout. NO sobrescribas mientras el usuario arrastra (isSignatureActive),
        // salvo que estemos auto-scroll (scrollChannel != 0f) — en ese caso permitimos actualizar posiciones teóricas.
        LaunchedEffect(lazyListState, isSignatureActive, containerIntSize, signatureBitmap, currentSignatureScale) {
            snapshotFlow { Triple(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset, scrollChannel) }
                .collect {
                    // si estamos arrastrando y NO hay auto-scroll, no recalculamos (para evitar "pelea")
                    if (isSignatureActive && scrollChannel == 0f) return@collect
                    val newAbs = computeAbsoluteOffsetFromRelative(signaturePageIndex, signatureRelative, signatureBitmap, currentSignatureScale)
                    if (newAbs != null) applyAbsoluteIfSignificant(newAbs)
                }
        }

        // inicializar scale y posicion inicial centrada (una sola vez)
        LaunchedEffect(signatureBitmap, containerIntSize) {
            if (signatureBitmap == null) return@LaunchedEffect
            if (!isInitialPosSet && containerIntSize.width > 0 && containerIntSize.height > 0) {
                currentSignatureScale = if (currentSignatureScale == 0f)
                    (containerIntSize.width * 0.18f) / signatureBitmap.width
                else currentSignatureScale
                onScaleChange(currentSignatureScale)
                // centro relativo
                signatureRelative = Offset(0.5f, 0.5f)
                computeAbsoluteOffsetFromRelative(signaturePageIndex, signatureRelative, signatureBitmap, currentSignatureScale)?.let {
                    signatureScreenOffset = Offset(it.x.roundToInt().toFloat(), it.y.roundToInt().toFloat())
                    onOffsetChange(signatureScreenOffset)
                }
                isInitialPosSet = true
            }
        }

        // LazyColumn
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isSignatureActive
        ) {
            items(count = baseBitmaps.size, key = { index -> index }) { index ->
                val pageBitmap = baseBitmaps[index]
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(16.dp)
                        .pointerInput(isSignatureActive) {
                            detectTapGestures { _ ->
                                if (isSignatureActive) isSignatureActive = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = pageBitmap.asImageBitmap(),
                        contentDescription = "Documento página ${index + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(4.dp, RoundedCornerShape(2.dp))
                            .background(Color.White, RoundedCornerShape(2.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // Overlay de la firma
        signatureBitmap?.let { sigBmp ->
            DraggableSignature(
                sigBmp = sigBmp,
                signatureOffset = signatureScreenOffset,
                signatureScale = currentSignatureScale,
                isSignatureActive = isSignatureActive,
                containerCoords = containerCoords,
                onIsSignatureActiveChange = { isSignatureActive = it },
                onOffsetChange = { newOffset ->
                    signatureScreenOffset = newOffset
                    onOffsetChange(newOffset)
                },
                onScaleChange = { newScale ->
                    currentSignatureScale = newScale
                    onScaleChange(newScale) // para propagar hacia arriba si el caller lo necesita
                },
                onDeleteSignature = onDeleteSignature,
                onDragEnd = { finalLocalOffset ->
                    // centro de la firma
                    val sigDrawW = sigBmp.width * currentSignatureScale
                    val sigDrawH = sigBmp.height * currentSignatureScale
                    val centerX = finalLocalOffset.x + sigDrawW / 2f
                    val centerY = finalLocalOffset.y + sigDrawH / 2f

                    // items visibles del LazyList
                    val visible = lazyListState.layoutInfo.visibleItemsInfo
                    if (visible.isEmpty()) return@DraggableSignature

                    // encontrar la página más cercana verticalmente
                    val target = visible.minByOrNull { item ->
                        val itemCenterY = item.offset + item.size / 2
                        kotlin.math.abs(itemCenterY - centerY)
                    } ?: visible.first()

                    val pageIndex = target.index
                    val base = baseBitmaps.getOrNull(pageIndex) ?: return@DraggableSignature

                    // usa tu helper computeImageLayoutForPage() que ya usa `density` (capturado en la composición)
                    val (scaledW, scaledH) = computeImageLayoutForPage(base)

                    // left/top de la imagen dentro del contenedor (centrada horizontalmente y centrada vertical dentro del item)
                    val imageLeft = (containerIntSize.width - scaledW) / 2f
                    val imageTop = target.offset + (containerIntSize.height - scaledH) / 2f

// Simplemente calcula la posición relativa sin restricciones.
                    val relX = (centerX - imageLeft) / scaledW
                    val relY = (centerY - imageTop) / scaledH

                    signaturePageIndex = pageIndex
                    signatureRelative = Offset(relX, relY)
                    finalPageIndex = signaturePageIndex

                    // recalcula y aplica offset absoluto (redondeado)
                    val newAbs = computeAbsoluteOffsetFromRelative(signaturePageIndex, signatureRelative, signatureBitmap, currentSignatureScale)
                    newAbs?.let {
                        signatureScreenOffset = Offset(it.x.roundToInt().toFloat(), it.y.roundToInt().toFloat())
                        onOffsetChange(signatureScreenOffset)
                    }
                },

                onAutoScroll = { channelValue -> scrollChannel = channelValue },
                modifier = Modifier.fillMaxSize()
            )

        }

        // controles superiores (igual que antes)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, "Cancelar", tint = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape).padding(4.dp))
            }
            Text(
                "Página ${finalPageIndex + 1} de ${baseBitmaps.size}",
                color = Color.White,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
            )
            IconButton(onClick = {
                if (baseBitmaps.isNotEmpty() && signatureBitmap != null) {
                    val finalBitmap = placeSignatureOnBitmap(
                        base = baseBitmaps[finalPageIndex],
                        signature = signatureBitmap,
                        signatureOffset = signatureScreenOffset,
                        signatureScale = currentSignatureScale,
                        containerSize = containerIntSize
                    )
                    onSignatureComplete(finalPageIndex, finalBitmap)
                }
            }) {
                Icon(Icons.Default.Check, "Aplicar Firma", tint = Color(0xFF30D5C8), modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape).padding(4.dp))
            }
        }
    }
}
// ------------------ Fin PlacingContent ------------------



// ------------------ DraggableSignature (actualizado) ------------------
@Composable
private fun DraggableSignature(
    modifier: Modifier = Modifier,
    sigBmp: ImageBitmap,
    signatureOffset: Offset,
    signatureScale: Float,
    isSignatureActive: Boolean,
    containerCoords: LayoutCoordinates?,
    onIsSignatureActiveChange: (Boolean) -> Unit,
    onOffsetChange: (Offset) -> Unit,
    onScaleChange: (Float) -> Unit,            // <-- nuevo
    onDeleteSignature: () -> Unit,
    onDragEnd: (Offset) -> Unit,
    onAutoScroll: (Float) -> Unit
) {
    var localOffset by remember { mutableStateOf(signatureOffset) }
    var isDragging by remember { mutableStateOf(false) }
    var isResizing by remember { mutableStateOf(false) }
    var signatureLayoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val density = LocalDensity.current
    val imageWidthDp = with(density) { (sigBmp.width * signatureScale).toDp() }
    val imageHeightDp = with(density) { (sigBmp.height * signatureScale).toDp() }

    val minScale = 0.1f
    val maxScale = 3f
    var resizeInitialScale by remember { mutableStateOf(signatureScale) }

    // sincronizar desde el padre sólo cuando NO estemos arrastrando ni redimensionando
    LaunchedEffect(signatureOffset) {
        if (!isDragging && !isResizing) {
            val dx = kotlin.math.abs(signatureOffset.x - localOffset.x)
            val dy = kotlin.math.abs(signatureOffset.y - localOffset.y)
            if (dx > 1f || dy > 1f) {
                localOffset = Offset(signatureOffset.x.roundToInt().toFloat(), signatureOffset.y.roundToInt().toFloat())
            }
        }
    }

    // Si el padre cambia la escala (por ejemplo al iniciar), no lo sobreescribimos cuando estamos interactuando
    LaunchedEffect(signatureScale) {
        if (!isResizing) {
            // no tocar localOffset aquí; la escala solo afecta tamaño dibujado (imageWidthDp/imageHeightDp)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.TopStart) {
        Box(
            modifier = Modifier
                .offset { IntOffset(localOffset.x.roundToInt(), localOffset.y.roundToInt()) }
                .onGloballyPositioned { signatureLayoutCoords = it }
                .then(Modifier.size(width = imageWidthDp, height = imageHeightDp))
                .pointerInput(sigBmp, signatureScale) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!isSignatureActive) onIsSignatureActiveChange(true)
                            down.consume()
                            isDragging = true
                            var pointerId = down.id
                            while (true) {
                                val dragChange = awaitDragOrCancellation(pointerId)
                                if (dragChange == null) {
                                    isDragging = false
                                    onAutoScroll(0f)
                                    onOffsetChange(localOffset)
                                    onDragEnd(localOffset)
                                    break
                                }
                                val delta = dragChange.positionChange()
                                localOffset = Offset(
                                    x = (localOffset.x + delta.x).roundToInt().toFloat(),
                                    y = (localOffset.y + delta.y).roundToInt().toFloat()
                                )

                                // Auto-scroll: calculamos posición absoluta del puntero y si estamos cerca de borde pedimos scroll
                                val absPointer = signatureLayoutCoords?.localToWindow(dragChange.position)
                                val containerTop = containerCoords?.localToWindow(Offset.Zero)?.y ?: 0f
                                val containerBottom = containerTop + (containerCoords?.size?.height ?: 0)
                                if (absPointer != null && containerCoords != null) {
                                    val y = absPointer.y
                                    val zone = (containerBottom - containerTop) * 0.15f
                                    when {
                                        y < containerTop + zone -> onAutoScroll(-40f)
                                        y > containerBottom - zone -> onAutoScroll(40f)
                                        else -> onAutoScroll(0f)
                                    }
                                }

                                onOffsetChange(localOffset)
                                dragChange.consume()
                                pointerId = dragChange.id
                            }
                        }
                    }
                }
        ) {
            // Imagen de la firma
            Image(
                bitmap = sigBmp,
                contentDescription = "Firma",
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, if (isSignatureActive) Color(0xFF30D5C8) else Color.Transparent, RoundedCornerShape(4.dp))
            )

            // Botón eliminar en top-start (igual que antes)
            if (isSignatureActive) {
                IconButton(
                    onClick = onDeleteSignature,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-16).dp, y = (-16).dp)
                        .background(Color(0xFF2C2C2E), CircleShape)
                        .size(24.dp)
                ) {
                    Icon(Icons.Default.Close, "Eliminar Firma", tint = Color.White)
                }
            }

            // === Handle de redimensión en la esquina inferior-derecha ===
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 8.dp) // pequeño offset para que quede fuera del borde
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF414141))
                    .pointerInput(sigBmp) {
                        detectDragGestures(
                            onDragStart = {
                                isResizing = true
                                resizeInitialScale = signatureScale
                            },
                            onDrag = { change, dragAmount ->
                                // usamos el cambio horizontal para ajustar escala relativa al ancho del bitmap
                                val delta = dragAmount.x + dragAmount.y / 2f
                                // cálculo simple: delta / bitmapWidth -> change in scale
                                val widthPx = sigBmp.width.toFloat()
                                val newScale = (resizeInitialScale + (delta / max(1f, widthPx))).coerceIn(minScale, maxScale)
                                // avisamos al padre para que actualice la escala y cause recomposición
                                onScaleChange(newScale)
                                change.consume()
                            },
                            onDragEnd = {
                                isResizing = false
                            },
                            onDragCancel = {
                                isResizing = false
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Redimensionar",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            }
        }
    }
}

// ------------------ Fin DraggableSignature ------------------




// Estado auxiliar local (para evitar múltiples remember dentro del bloque)
private object currentDragState {
    var isDragging: Boolean = false
    var dragPointerOffset: Offset = Offset.Zero
}





@Composable
private fun SignatureDrawingCanvas(
    modifier: Modifier = Modifier,
    strokes: List<List<Offset>>,
    strokeColor: Color,
    strokeWidth: Float,
    onAddStroke: (List<Offset>) -> Unit
) {
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (offset.x in 0f..size.width.toFloat() && offset.y in 0f..size.height.toFloat()) {
                            currentStroke = listOf(offset)
                        }
                    },
                    onDrag = { change: PointerInputChange, _ ->
                        val position = change.position
                        val isInside =
                            position.x in 0f..size.width.toFloat() && position.y in 0f..size.height.toFloat()

                        if (isInside) {
                            currentStroke = currentStroke + position
                        } else {
                            if (currentStroke.isNotEmpty()) {
                                onAddStroke(currentStroke)
                                currentStroke = emptyList()
                            }
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        if (currentStroke.isNotEmpty()) {
                            onAddStroke(currentStroke)
                        }
                        currentStroke = emptyList()
                    },
                    onDragCancel = {
                        currentStroke = emptyList()
                    }
                )
            }
    ) {
        strokes.forEach { stroke ->
            if (stroke.size > 1) {
                val path = Path().apply {
                    moveTo(stroke.first().x, stroke.first().y)
                    stroke.subList(1, stroke.size).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path,
                    color = strokeColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        if (currentStroke.size > 1) {
            val currentPath = Path().apply {
                moveTo(currentStroke.first().x, currentStroke.first().y)
                currentStroke
                    .subList(1, currentStroke.size)
                    .forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                currentPath,
                color = strokeColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun SignatureDrawingControlsVertical(
    strokeColor: Color,
    strokeWidth: Float,
    onCancel: () -> Unit,
    onColorChange: (Color) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit
) {
    var showDrawingOptions by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(80.dp)
                .background(Color(0xFF2C2C2E))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, "Cancelar", tint = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { showDrawingOptions = !showDrawingOptions }) {
                    Icon(Icons.Default.Tune, "Opciones de Pincel", tint = Color.White)
                }
                IconButton(onClick = onUndo) {
                    Icon(Icons.Default.Undo, "Deshacer", tint = Color.White)
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.DeleteOutline, "Limpiar", tint = Color.White)
                }
            }

            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30D5C8)),
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Done, contentDescription = "Confirmar")
            }
        }

        if (showDrawingOptions) {
            DrawingOptionsPopup(
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                onColorChange = onColorChange,
                onStrokeWidthChange = onStrokeWidthChange,
                onDismiss = { showDrawingOptions = false }
            )
        }
    }
}

@Composable
private fun DrawingOptionsPopup(
    strokeColor: Color,
    strokeWidth: Float,
    onColorChange: (Color) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = listOf(Color.Black, Color(0xFF0D47A1), Color(0xFFB71C1C))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF3A3A3C))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Opciones de Pincel", color = Color.White)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Grosor: ${strokeWidth.toInt()}", color = Color.LightGray)
                Slider(
                    value = strokeWidth,
                    onValueChange = onStrokeWidthChange,
                    valueRange = 2f..20f,
                    steps = 8
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onColorChange(color) }
                            .border(
                                width = if (strokeColor == color) 2.dp else 0.dp,
                                color = Color.White,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}


private fun captureSignature(strokes: List<List<Offset>>, color: Color, strokeWidth: Float): ImageBitmap {
    val paths = strokes.mapNotNull { stroke ->
        if (stroke.size > 1) {
            Path().apply {
                moveTo(stroke.first().x, stroke.first().y)
                stroke.subList(1, stroke.size).forEach { lineTo(it.x, it.y) }
            }
        } else null
    }

    if (paths.isEmpty()) return ImageBitmap(1, 1)

    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY

    paths.forEach { path ->
        val b = path.getBounds()
        left = min(left, b.left)
        top = min(top, b.top)
        right = max(right, b.right)
        bottom = max(bottom, b.bottom)
    }

    if (left == Float.POSITIVE_INFINITY) return ImageBitmap(1, 1)

    val bounds = Rect(left, top, right, bottom)
    val padding = strokeWidth * 4
    val bitmapWidth = ((bounds.width + padding * 2).toInt()).coerceAtLeast(1)
    val bitmapHeight = ((bounds.height + padding * 2).toInt()).coerceAtLeast(1)

    return captureBitmap(bitmapWidth, bitmapHeight) {
        translate(
            -bounds.left + padding,
            -bounds.top + padding
        ) {
            paths.forEach { path ->
                drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }
        }
    }
}

private fun placeSignatureOnBitmap(
    base: Bitmap,
    signature: ImageBitmap?,
    signatureOffset: Offset,
    signatureScale: Float,
    containerSize: IntSize
): Bitmap {
    if (signature == null) return base

    val pagePaddingPx = 16.dp.value
    val viewWidth = containerSize.width - (pagePaddingPx * 2)
    val viewHeight = containerSize.height - (pagePaddingPx * 2)

    val scaledBitmapWidth: Float
    val scaledBitmapHeight: Float

    val bitmapAspectRatio = base.width.toFloat() / base.height.toFloat()
    val viewAspectRatio = viewWidth / viewHeight

    if (bitmapAspectRatio > viewAspectRatio) {
        scaledBitmapWidth = viewWidth
        scaledBitmapHeight = viewWidth / bitmapAspectRatio
    } else {
        scaledBitmapHeight = viewHeight
        scaledBitmapWidth = viewHeight * bitmapAspectRatio
    }

    val imageOffsetX = (containerSize.width - scaledBitmapWidth) / 2f
    val imageOffsetY = (containerSize.height - scaledBitmapHeight) / 2f

    // signatureOffset es top-left absoluto en el contenedor
    val signatureCenterX = signatureOffset.x + (signature.width * signatureScale / 2f)
    val signatureCenterY = signatureOffset.y + (signature.height * signatureScale / 2f)

    val signatureOnScaledBitmapX = signatureCenterX - imageOffsetX - (signature.width * signatureScale / 2f)
    val signatureOnScaledBitmapY = signatureCenterY - imageOffsetY - (signature.height * signatureScale / 2f)

    val scaleRatio = base.width / scaledBitmapWidth
    val finalX = signatureOnScaledBitmapX * scaleRatio
    val finalY = signatureOnScaledBitmapY * scaleRatio
    val finalSignatureScale = signatureScale * scaleRatio

    val resultBitmap = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(resultBitmap)
    val paint = android.graphics.Paint().apply { isAntiAlias = true }

    val matrix = android.graphics.Matrix().apply {
        postScale(finalSignatureScale, finalSignatureScale)
        postTranslate(finalX, finalY)
    }

    canvas.drawBitmap(signature.asAndroidBitmap(), matrix, paint)
    return resultBitmap
}

private fun captureBitmap(
    width: Int,
    height: Int,
    content: DrawScope.() -> Unit
): ImageBitmap {
    val imageBitmap = ImageBitmap(width, height)
    val canvas = androidx.compose.ui.graphics.Canvas(imageBitmap)
    val drawScope = CanvasDrawScope()
    drawScope.draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat()),
        block = content
    )
    return imageBitmap
}

