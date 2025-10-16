
package com.example.scannerpro.signature

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Parcelable
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

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
    var mode by rememberSaveable { mutableStateOf(SignatureMode.PLACING) }
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
    var signatureScale by rememberSaveable { mutableStateOf(1f) }


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
            signatureBitmap =
                captureSignature(strokes, strokeColor, strokeWidth)
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
                    // 1. Generamos el bitmap PRIMERO y lo guardamos en el estado.
                    signatureBitmap = captureSignature(strokes, strokeColor, strokeWidth)

                    // 2. AHORA cambiamos de modo.
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
                // 1. Borra los datos de la firma actual
                parcelableStrokes = emptyList()
                signatureBitmap = null

                // 2. LA CLAVE: Resetea la posición y la escala
                signatureOffset = Offset.Zero
               signatureScale = 1f // <-- Esto evita que se vuelva invisible

                // 3. Vuelve al modo de dibujo
                mode = SignatureMode.DRAWING
            },
            onSignatureComplete = onSignatureComplete,
            onRequestDrawing = {
                mode = SignatureMode.DRAWING
                parcelableStrokes = emptyList()
                signatureBitmap = null

                // 2. LA CLAVE: Resetea la posición y la escala
              signatureOffset = Offset.Zero
            //   signatureScale = 1f // <-- Esto evita que se vuelva invisible


            } // <-- nuevo
        )
    }
}

//Area de firma
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
        //Estes es el menu de las opciones de la firma, el color, tamaño, cancelar,...
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
    onSignatureComplete: (Int, Bitmap) -> Unit,
    onRequestDrawing: () -> Unit
) {
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialPageIndex)
    val density = LocalDensity.current

    var signaturePageIndex by rememberSaveable { mutableStateOf(initialPageIndex) }
    var signatureRelative by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset(0.5f, 0.5f)) }
    var signatureScreenOffset by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(signatureOffset) }
    //var currentSignatureScale by rememberSaveable { mutableFloatStateOf(signatureScale) }
    var signatureRotation by rememberSaveable { mutableFloatStateOf(0f) } // <-- AÑADE ESTA LÍNEA

    var isSignatureActive by rememberSaveable { mutableStateOf(false) }
    var isInitialPosSet by remember { mutableStateOf(false) }
    var finalPageIndex by rememberSaveable { mutableStateOf(initialPageIndex) }
    var containerIntSize by remember { mutableStateOf(IntSize(0, 0)) }
    var scrollChannel by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF212121))
            .onGloballyPositioned {
                containerIntSize = it.size
            }
    ) {
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

        fun computeAbsoluteOffsetFromRelative(pageIndex: Int, relative: Offset, signatureBmp: ImageBitmap?, sigScale: Float): Offset? {
            val base = baseBitmaps.getOrNull(pageIndex) ?: return null
            if (containerIntSize.width == 0 || containerIntSize.height == 0 || signatureBmp == null) return null
            val (scaledW, scaledH) = computeImageLayoutForPage(base)
            val imageLeft = (containerIntSize.width - scaledW) / 2f
            val visibleItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == pageIndex }
            val imageTop = (visibleItem?.offset ?: 0) + (containerIntSize.height - scaledH) / 2f
            val centerX = imageLeft + relative.x * scaledW
            val centerY = imageTop + relative.y * scaledH
            val sigDrawW = signatureBmp.width * sigScale
            val sigDrawH = signatureBmp.height * sigScale
            return Offset(centerX - sigDrawW / 2f, centerY - sigDrawH / 2f)
        }

        LaunchedEffect(signatureBitmap, containerIntSize, lazyListState.isScrollInProgress) {
            // No calcules si ya está posicionado, no hay bitmap, el contenedor no tiene tamaño, o si se está haciendo scroll
            if (isInitialPosSet || signatureBitmap == null || containerIntSize.width == 0 || lazyListState.isScrollInProgress) {
                      return@LaunchedEffect
            }

            // Espera a que el LazyColumn termine su primer layout
            if (lazyListState.layoutInfo.visibleItemsInfo.isEmpty()) {
                return@LaunchedEffect
            }

            val initialScale = (containerIntSize.width * 0.60f) / signatureBitmap.width

            val initialOffset = computeAbsoluteOffsetFromRelative(
                initialPageIndex, // Usa el índice inicial
                Offset(0.5f, 0.5f), // Siempre relativo al centro
                signatureBitmap,
                initialScale
            )

            initialOffset?.let {
                // Actualiza el estado del padre directamente
                onOffsetChange(it)
                onScaleChange(initialScale)

                // Marca como posicionado para no volver a ejecutar esto
                isInitialPosSet = true
            }
        }

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
                        .padding(3.dp)
                        .pointerInput(isSignatureActive) {
                            detectTapGestures { _ -> if (isSignatureActive) isSignatureActive = false }
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

        // En PlacingContent, dentro del Box principal...

        signatureBitmap?.let { sigBmp ->
            if (isInitialPosSet) {
                DraggableSignature(
                    sigBmp = sigBmp,
                    // Pasa los estados del padre directamente
                    signatureOffset = signatureScreenOffset,
                    signatureScale = signatureScale,
                    signatureRotation = signatureRotation, // <-- Pasa el nuevo estado de rotación
                    isSignatureActive = isSignatureActive,
                    onIsSignatureActiveChange = { isSignatureActive = it },
                    onDeleteSignature = onDeleteSignature,
                    // La nueva callback unificada que actualiza todo
                    onTransformChange = { newOffset, newScale, newRotation ->
                        signatureScreenOffset = newOffset
                        onScaleChange(newScale)
                        signatureRotation = newRotation
                    },

                    // La lógica de onDragEnd se queda exactamente igual
                    onDragEnd = { finalLocalOffset ->
                        val sigDrawW = sigBmp.width * signatureScale
                        val sigDrawH = sigBmp.height * signatureScale
                        val centerX = finalLocalOffset.x + sigDrawW / 2f
                        val centerY = finalLocalOffset.y + sigDrawH / 2f
                        val visible = lazyListState.layoutInfo.visibleItemsInfo
                        if (visible.isEmpty()) return@DraggableSignature

                        val target = visible.minByOrNull { item ->
                            val itemCenterY = item.offset + item.size / 2
                            abs(itemCenterY - centerY)
                        } ?: visible.first()

                        val pageIndex = target.index
                        val base = baseBitmaps.getOrNull(pageIndex) ?: return@DraggableSignature
                        val (scaledW, scaledH) = computeImageLayoutForPage(base)
                        val imageLeft = (containerIntSize.width - scaledW) / 2f
                        val imageTop = target.offset + (containerIntSize.height - scaledH) / 2f

                        val relX = (centerX - imageLeft) / scaledW
                        val relY = (centerY - imageTop) / scaledH

                        signaturePageIndex = pageIndex
                        signatureRelative = Offset(relX, relY)
                        finalPageIndex = signaturePageIndex
                    }
                )
            }
        }

        // Controles superiores
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

        }

        // Surface con controles inferiores
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = Color(0xFF2C2C2E),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Divider(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1B1B1B), thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(64.dp)
                            .clickable { onRequestDrawing() }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = "Firma (sello)",
                            modifier = Modifier.size(28.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Firma", color = Color.White, fontSize = 12.sp)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(64.dp)
                            .clickable { /* Placeholder para fecha */ }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Fecha (calendario)",
                            modifier = Modifier.size(28.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Fecha", color = Color.White, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            if (baseBitmaps.isNotEmpty() && signatureBitmap != null) {
                                val finalBitmap = placeSignatureOnBitmap(
                                    base = baseBitmaps[finalPageIndex],
                                    signature = signatureBitmap,
                                    signatureOffset = signatureScreenOffset,
                                    signatureScale = signatureScale,
                                    containerSize = containerIntSize
                                )
                                onSignatureComplete(finalPageIndex, finalBitmap)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30D5C8)),
                        shape = CircleShape,
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Aceptar",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}



// ------------------ DraggableSignature (con logs adicionales y sensibilidad aumentada) ------------------
// ------------------ DraggableSignature (CORREGIDO) ------------------
@Composable
private fun DraggableSignature2(
    modifier: Modifier = Modifier,
    sigBmp: ImageBitmap,
    signatureOffset: Offset, // Lee directamente del padre
    signatureScale: Float,   // Lee directamente del padre
    signatureRotation: Float, // Lee directamente del padre
    onTransformChange: (Offset, Float, Float) -> Unit,
    isSignatureActive: Boolean,
    onIsSignatureActiveChange: (Boolean) -> Unit,
    onDeleteSignature: () -> Unit,
    onDragEnd: (Offset) -> Unit
) {
    val density = LocalDensity.current
    val minScale = 0.3f
    val maxScale = 5.0f

    Box(modifier = modifier) {
        Image(
            bitmap = sigBmp,
            contentDescription = "Firma",
            modifier = Modifier
                .graphicsLayer {
                    // Usa los props directamente, no estados locales
                    translationX = signatureOffset.x
                    translationY = signatureOffset.y
                    scaleX = signatureScale
                    scaleY = signatureScale
                    rotationZ = signatureRotation
                }
                .size(
                    width = with(density) { sigBmp.width.toDp() },
                    height = with(density) { sigBmp.height.toDp() }
                )
                .pointerInput(Unit) {
                    // Gesto para activar con un toque
                    detectTapGestures(
                        onTap = {
                            if (!isSignatureActive) onIsSignatureActiveChange(true)
                        }
                    )
                }
                .pointerInput(Unit) {
                    // Gesto para transformar (mover, escalar, rotar)
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        if (!isSignatureActive) onIsSignatureActiveChange(true)

                        val newScale = (signatureScale * zoom).coerceIn(minScale, maxScale)

                        val newRotation = signatureRotation + rotation

                        // El cálculo ahora usa el offset y escala del padre directamente
                        val newOffset = signatureOffset + (centroid - signatureOffset) -
                                ((centroid - signatureOffset).rotateBy(-rotation) * (signatureScale / newScale)) + pan

                        // Notifica al padre INMEDIATAMENTE
                        onTransformChange(newOffset, newScale, newRotation)
                        onDragEnd(newOffset)
                    }
                }
        )

        // Contenedor del borde y los botones (sin cambios, ya funciona bien)
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = signatureOffset.x
                    translationY = signatureOffset.y
                    scaleX = signatureScale
                    scaleY = signatureScale
                    rotationZ = signatureRotation
                }
                .size(
                    width = with(density) { sigBmp.width.toDp() },
                    height = with(density) { sigBmp.height.toDp() }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, if (isSignatureActive) Color(0xFF30D5C8) else Color.Transparent, RoundedCornerShape(4.dp))
            )

            // Botones que mantienen su tamaño gracias a la escala inversa
            if (isSignatureActive) {
                val iconScale = 1f / signatureScale

                IconButton(
                    onClick = onDeleteSignature,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            translationX = with(density) { -16.dp.toPx() } * iconScale
                            translationY = with(density) { -16.dp.toPx() } * iconScale
                        }
                        .background(Color(0xFF2C2C2E), CircleShape)
                        .size(24.dp)
                ) { Icon(Icons.Default.Close, "Eliminar", tint = Color.White) }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            translationX = with(density) { 8.dp.toPx() } * iconScale
                            translationY = with(density) { 8.dp.toPx() } * iconScale
                        }
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF414141)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Tune, "Redimensionar", Modifier.size(16.dp), tint = Color.White)
                }
            }
        }
    }
}






// helpers: rotación por radianes y operaciones con Offset
private fun Offset.rotateRad(rad: Float): Offset {
    val c = cos(rad)
    val s = sin(rad)
    return Offset(x * c - y * s, x * s + y * c)
}
private operator fun Offset.times(scale: Float) = Offset(x * scale, y * scale)
private operator fun Offset.plus(other: Offset) = Offset(x + other.x, y + other.y)
private operator fun Offset.minus(other: Offset) = Offset(x - other.x, y - other.y)



@Composable
fun DraggableSignature(
    modifier: Modifier = Modifier,
    sigBmp: ImageBitmap,
    signatureOffset: Offset,
    signatureScale: Float,
    signatureRotation: Float, // radianes
    onTransformChange: (newOffset: Offset, newScale: Float, newRotation: Float) -> Unit,
    isSignatureActive: Boolean,
    onIsSignatureActiveChange: (Boolean) -> Unit,
    onDeleteSignature: () -> Unit,
    onDragEnd: (Offset) -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // ------- parámetros (suaves) -------
    val SENSITIVITY_SCALE = 0.003f
    val SENSITIVITY_ROT = 0.005f
    val MIN_SCALE = 0.3f
    val MAX_SCALE = 5f
    val SNAP_SCALE_STEP = 0.05f
    val SNAP_ROT_DEG = 10f
    val snapAnimSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
    // ------------------------------------

    // estados locales
    var localOffset by remember { mutableStateOf(signatureOffset) }
    var localScale by remember { mutableStateOf(signatureScale) }
    var localRotation by remember { mutableStateOf(signatureRotation) }

    // animatables para snap
    val scaleAnim = remember { Animatable(signatureScale) }
    val rotationAnim = remember { Animatable(signatureRotation) }

    // sincronizar si el padre cambia externalmente
    LaunchedEffect(signatureOffset) { localOffset = signatureOffset }
    LaunchedEffect(signatureScale) {
        localScale = signatureScale
        scaleAnim.snapTo(signatureScale)
    }
    LaunchedEffect(signatureRotation) {
        localRotation = signatureRotation
        rotationAnim.snapTo(signatureRotation)
    }

    // para detectar taps fuera, necesitamos las bounds globales de la firma
    var sigTopLeft by remember { mutableStateOf(Offset.Zero) }
    var sigSize by remember { mutableStateOf(IntSize.Zero) }

    // dimensiones del bitmap (px)
    val bmpWidthPx = sigBmp.width.toFloat()
    val bmpHeightPx = sigBmp.height.toFloat()
    val bmpWidthDp = with(density) { sigBmp.width.toDp() }
    val bmpHeightDp = with(density) { sigBmp.height.toDp() }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // overlay que detecta taps fuera sólo cuando la firma está activa
        if (isSignatureActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(isSignatureActive, sigTopLeft, sigSize) {
                        detectDragGestures(onDrag = { _, _ -> /* consumir para bloquear scroll mientras activo */ }) // evita scroll accidental
                    }
                    .pointerInput(isSignatureActive, sigTopLeft, sigSize) {
                        detectTapGestures { tapOffset ->
                            // tapOffset es relativo a esta overlay (llena pantalla) -> mismo sistema de coordenadas que sigTopLeft
                            val left = sigTopLeft.x
                            val top = sigTopLeft.y
                            val right = left + sigSize.width
                            val bottom = top + sigSize.height
                            val x = tapOffset.x
                            val y = tapOffset.y
                            val inside =
                                x >= left && x <= right && y >= top && y <= bottom
                            if (!inside) {
                                // fue tap fuera -> desactivar
                                onIsSignatureActiveChange(false)
                            }
                            // si inside: no hacemos nada aquí, el tap se manejará por el propio signature (activar/drag)
                        }
                    }
            )
        }

        // ------ Contenedor transformado (la firma) ------
        // Dentro de tu Box que actúa como contenedor de la firma,
// reemplaza la parte interna por esto:

        Box(
            modifier = Modifier
                .size(width = bmpWidthDp, height = bmpHeightDp)
                .onGloballyPositioned { coords ->
                    val pos = coords.localToRoot(Offset.Zero)
                    sigTopLeft = pos
                    sigSize = coords.size
                }
                // Tap sobre la firma -> activarla
                .pointerInput(isSignatureActive) {
                    detectTapGestures(onTap = {
                        onIsSignatureActiveChange(true)
                    })
                }
                // Pan: sólo si está activa
                .pointerInput(isSignatureActive) {
                    if (isSignatureActive) {
                        detectDragGestures(
                            onDragEnd = { onDragEnd(localOffset) },
                            onDragCancel = { onDragEnd(localOffset) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                localOffset = localOffset + dragAmount
                                onTransformChange(localOffset, localScale, localRotation)
                            }
                        )
                    } else {
                        // cuando NO está activa, no interceptamos drags para permitir el scroll
                        awaitPointerEventScope { awaitPointerEvent() }
                    }
                }
                .graphicsLayer(
                    translationX = localOffset.x,
                    translationY = localOffset.y,
                    scaleX = localScale,
                    scaleY = localScale,
                    rotationZ = Math.toDegrees(localRotation.toDouble()).toFloat(),
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                )
        ) {
            // Dibujar la imagen siempre
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
            ) {
                androidx.compose.foundation.Image(
                    bitmap = sigBmp,
                    contentDescription = "Firma",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }

            // --- Mostrar borde y controles SOLO si está activa ---
            if (isSignatureActive) {
                // Borde verde
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = 3.dp,
                            color = Color(0xFF00C853),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clip(RoundedCornerShape(6.dp))
                )

                // Icono eliminar (arriba-izq)
                val iconScale = 1f / localScale
                IconButton(
                    onClick = { onDeleteSignature() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            translationX = with(density) { -14.dp.toPx() }
                            translationY = with(density) { -14.dp.toPx() }
                        }
                        .background(Color(0xFF2C2C2E), CircleShape)
                        .size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Eliminar firma",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // HANDLE bottom-right (controla zoom y rotación) — igual que antes
                var isHandleDragging by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            translationX = with(density) { 8.dp.toPx() } * iconScale
                            translationY = with(density) { 8.dp.toPx() } * iconScale
                        }
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isHandleDragging) Color(0xFF616161) else Color(0xFF414141))
                        .pointerInput(isSignatureActive) {
                            detectDragGestures(
                                onDragStart = {
                                    isHandleDragging = true; onIsSignatureActiveChange(
                                    true
                                )
                                },
                                onDragEnd = {
                                    isHandleDragging = false
                                    // snap animado (tu lógica existente)
                                    coroutineScope.launch {
                                        scaleAnim.snapTo(localScale)
                                        rotationAnim.snapTo(localRotation)
                                        val targetScale =
                                            snapToStep(localScale, SNAP_SCALE_STEP).coerceIn(
                                                MIN_SCALE,
                                                MAX_SCALE
                                            )
                                        val targetRot = Math.toRadians(
                                            snapToStep(
                                                Math.toDegrees(localRotation.toDouble()).toFloat(),
                                                SNAP_ROT_DEG
                                            ).toDouble()
                                        ).toFloat()
                                        val j1 = launch {
                                            scaleAnim.animateTo(
                                                targetScale,
                                                animationSpec = snapAnimSpec
                                            )
                                        }
                                        val j2 = launch {
                                            rotationAnim.animateTo(
                                                targetRot,
                                                animationSpec = snapAnimSpec
                                            )
                                        }
                                        j1.join(); j2.join()
                                        localScale = scaleAnim.value
                                        localRotation = rotationAnim.value
                                        onTransformChange(localOffset, localScale, localRotation)
                                        onDragEnd(localOffset)
                                    }
                                },
                                onDragCancel = {
                                    isHandleDragging = false
                                    coroutineScope.launch { onDragEnd(localOffset) }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dx = dragAmount.x
                                    val dy = dragAmount.y
                                    val deltaScaleFactor = 1f + dx * SENSITIVITY_SCALE
                                    val newScale = (localScale * deltaScaleFactor).coerceIn(
                                        MIN_SCALE,
                                        MAX_SCALE
                                    )
                                    val newRotation = localRotation + dy * SENSITIVITY_ROT

                                    // anchor: mantener fija la esquina bottom-right (handle)
                                    val handleLocal = Offset(bmpWidthPx / 2f, bmpHeightPx / 2f)
                                    val curHandleGlobal =
                                        localOffset + (handleLocal.rotateRad(localRotation) * localScale)
                                    val newOffset =
                                        curHandleGlobal - (handleLocal.rotateRad(newRotation) * newScale)

                                    localScale = newScale
                                    localRotation = newRotation
                                    localOffset = newOffset

                                    onTransformChange(localOffset, localScale, localRotation)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Tune,
                        "Redimensionar",
                        Modifier.size(16.dp),
                        tint = Color.White
                    )
                }
            } // end if(isSignatureActive)
        }
    }
}





        private fun snapToStep(value: Float, step: Float): Float {
    if (step <= 0f) return value
    return ( (value / step).roundToInt() * step )
}

/** Rota un Offset en grados (positivo = sentido antihorario). */
fun Offset.rotateBy(degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    val c = cos(rad)
    val s = sin(rad)
    val newX = (x * c - y * s).toFloat()
    val newY = (x * s + y * c).toFloat()
    return Offset(newX, newY)
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

//Es el menu de las ocpioens de la firma, color y tamaño, borrar y cancelar
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

//esto genera la imagen en base a los puntos que se toman en el area de grabado.
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