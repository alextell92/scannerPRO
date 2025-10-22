
package com.example.scannerpro.signature

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Parcelable
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.forEachGesture
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

import kotlin.math.pow


private const val TAG = "SignatureDebug"
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

// estado de modo del handle: null = undecided, "SCALE" o "ROTATE"
enum class HandleMode { UNDECIDED, SCALE, ROTATE }
@Composable
fun SignatureScreen(
    baseBitmaps: List<Bitmap>,
    initialPageIndex: Int,
    onSignatureComplete: (Int, Bitmap) -> Unit,
    onCancel: () -> Unit
) {

    var signatureRotation by rememberSaveable { mutableFloatStateOf(0f) } // <-- Ya tenías este

    // --- AÑADE ESTA LÍNEA ---
    var isSignatureActive by rememberSaveable { mutableStateOf(false) }


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
            signatureRotation = signatureRotation,       // <--- AÑADIR
            isSignatureActive = isSignatureActive,     // <--- AÑADIR
            onRotationChange = { signatureRotation = it }, // <--- AÑADIR
            onIsSignatureActiveChange = { isSignatureActive = it }, // <--- AÑADIR

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
    signatureRotation: Float, // Asegúrate de que el padre pase este valor
    isSignatureActive: Boolean, // Asegúrate de que el padre pase este valor
    onOffsetChange: (Offset) -> Unit,
    onScaleChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit, // Asegúrate de que el padre pase este callback
    onIsSignatureActiveChange: (Boolean) -> Unit, // Asegúrate de que el padre pase este callback
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
    var signatureRotation by rememberSaveable { mutableFloatStateOf(0f) }

    //var isSignatureActive by rememberSaveable { mutableStateOf(false) }
    var isInitialPosSet by remember { mutableStateOf(false) }
    var finalPageIndex by rememberSaveable { mutableStateOf(initialPageIndex) }
    var containerIntSize by remember { mutableStateOf(IntSize(0, 0)) }

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

        // Inicializar posición de la firma solo una vez (no se re-ejecuta en cada scroll)
        LaunchedEffect(signatureBitmap, containerIntSize) {
            if (isInitialPosSet || signatureBitmap == null || containerIntSize.width == 0) return@LaunchedEffect
            if (lazyListState.layoutInfo.visibleItemsInfo.isEmpty()) return@LaunchedEffect

            val initialScale = (containerIntSize.width * 0.60f) / signatureBitmap.width
            val initialOffset = computeAbsoluteOffsetFromRelative(
                initialPageIndex,
                Offset(0.5f, 0.5f),
                signatureBitmap,
                initialScale
            )

            initialOffset?.let {
                onOffsetChange(it)    // fija signatureScreenOffset en el padre
                onScaleChange(initialScale)
                isInitialPosSet = true
            }
        }

        // Documento (scrollable)
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
                        .padding(3.dp),
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

        // Overlay para detectar taps fuera de la firma mientras está activa.
        // Está entre el LazyColumn y el DraggableSignature (no estorba a la firma porque la firma está encima).
        if (isSignatureActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { // Solo necesita ejecutarse una vez
                        detectTapGestures {
                            // Si el tap llega aquí, es porque la firma
                            // (que está encima) no lo consumió.
                            // Por lo tanto, es "fuera".
                            Log.e(TAG, "Clic detectado FUERA de la firma (en overlay). Desactivando.")
                            onIsSignatureActiveChange(false)
                        }
                    }
            )
        }

        // Firma como overlay (encima de todo)
        signatureBitmap?.let { sigBmp ->
            if (isInitialPosSet) {
                DraggableSignature(
                    modifier = Modifier.fillMaxSize(),
                    sigBmp = sigBmp,
                    signatureOffset = signatureScreenOffset,
                    signatureScale = signatureScale,
                    signatureRotation = signatureRotation,
                    isSignatureActive = isSignatureActive, // <-- Ahora usa el PARÁMETRO
                    onIsSignatureActiveChange = onIsSignatureActiveChange, // <-- Ahora usa el PARÁMETRO
                    onDeleteSignature = onDeleteSignature,
                    onTransformChange = { newOffset, newScale, newRotation ->
                        // actualizamos la posición en pantalla y escala/rot en el padre
                        signatureScreenOffset = newOffset
                        onScaleChange(newScale)
                        signatureRotation = newRotation
                        onOffsetChange(newOffset)
                    },
                    onDragEnd = { finalLocalOffset ->
                        // 1) Dejamos la firma donde el usuario la soltó (posición pantalla)
                        signatureScreenOffset = finalLocalOffset
                        onOffsetChange(finalLocalOffset) // mantener padre sincronizado

                        // 2) Calculamos la página relativa y la posición dentro de la página para persistencia
                        val sigDrawW = sigBmp.width * signatureScale
                        val sigDrawH = sigBmp.height * signatureScale
                        val centerX = finalLocalOffset.x + sigDrawW / 2f
                        val centerY = finalLocalOffset.y + sigDrawH / 2f

                        val visible = lazyListState.layoutInfo.visibleItemsInfo
                        if (visible.isNotEmpty()) {
                            val target = visible.minByOrNull { item ->
                                val itemCenterY = item.offset + item.size / 2
                                kotlin.math.abs(itemCenterY - centerY)
                            } ?: visible.first()

                            val pageIndex = target.index
                            val base = baseBitmaps.getOrNull(pageIndex)
                            if (base != null) {
                                val (scaledW, scaledH) = computeImageLayoutForPage(base)
                                val imageLeft = (containerIntSize.width - scaledW) / 2f
                                val imageTop = target.offset + (containerIntSize.height - scaledH) / 2f

                                val relX = (centerX - imageLeft) / scaledW
                                val relY = (centerY - imageTop) / scaledH

                                signaturePageIndex = pageIndex
                                signatureRelative = Offset(relX.coerceIn(0f, 1f), relY.coerceIn(0f, 1f))
                                finalPageIndex = signaturePageIndex
                            }
                        }
                    }
                )
            }
        }

        // Controles superior & inferior (igual que antes)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Default.Close,
                    "Cancelar",
                    tint = Color.White,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape).padding(4.dp)
                )
            }
            Text(
                "Página ${finalPageIndex + 1} de ${baseBitmaps.size}",
                color = Color.White,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

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
// Asegúrate de tener este helper en el archivo

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

    // --- Parámetros de gestos ---
    val SENSITIVITY_SCALE = 0.0035f
    val SENSITIVITY_ROT = 0.0045f
    val MIN_SCALE = 0.3f
    val MAX_SCALE = 5f
    val SNAP_SCALE_STEP = 0.05f
    val SNAP_ROT_DEG = 10f
    val MIX_EXP = 1.25f
    val snapAnimSpec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

    // --- Estados locales sincronizados ---
    var localOffset by remember { mutableStateOf(signatureOffset) }
    var localScale by remember { mutableStateOf(signatureScale) }
    var localRotation by remember { mutableStateOf(signatureRotation) }

    val scaleAnim = remember { Animatable(signatureScale) }
    val rotationAnim = remember { Animatable(signatureRotation) }

    var isInteracting by remember { mutableStateOf(false) }
    var touchOffsetInElement by remember { mutableStateOf<Offset?>(null) }

    // --- Sincronización con el estado del padre ---
    // (Sincroniza solo si el usuario no está interactuando activamente)
    val EPS = 0.5f // Pixel tolerance para offset
    LaunchedEffect(signatureOffset) {
        if (!isInteracting) {
            if ((localOffset - signatureOffset).getDistance() > EPS) {
                localOffset = signatureOffset
            }
        }
    }
    LaunchedEffect(signatureScale) {
        if (!isInteracting) {
            if (kotlin.math.abs(localScale - signatureScale) > 1e-3f) {
                localScale = signatureScale
                scaleAnim.snapTo(signatureScale)
            }
        } else {
            scaleAnim.snapTo(localScale)
        }
    }
    LaunchedEffect(signatureRotation) {
        if (!isInteracting) {
            if (kotlin.math.abs(localRotation - signatureRotation) > 1e-3f) {
                localRotation = signatureRotation
                rotationAnim.snapTo(signatureRotation)
            }
        } else {
            rotationAnim.snapTo(localRotation)
        }
    }

    // --- Dimensiones ---
    val bmpWidthPx = sigBmp.width.toFloat()
    val bmpHeightPx = sigBmp.height.toFloat()
    val bmpWidthDp = with(density) { sigBmp.width.toDp() }
    val bmpHeightDp = with(density) { sigBmp.height.toDp() }

    var accumulatedDrag by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier.fillMaxSize(),
        //contentAlignment = Alignment.Center
    ) {

        // --- Contenedor de la Firma ---
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        localOffset.x.roundToInt(),
                        localOffset.y.roundToInt()
                    )
                }
                .size(width = bmpWidthDp, height = bmpHeightDp)
                // (Omitimos onGloballyPositioned ya que no lo usamos para bounds aquí)

                // ----------- INICIO DE LA CORRECCIÓN -----------

                // Gesto 1: Tap para Activar (y consumir el evento)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        Log.e(TAG, "Clic detectado SOBRE la firma.");
                        if (!isSignatureActive) {
                            onIsSignatureActiveChange(true)
                        }
                    }
                )

                // Gesto 2: Drag para Mover (el cuerpo de la firma).
                // Este detector SÓLO se adjunta si la firma ESTÁ ACTIVA.
                .pointerInput(isSignatureActive) {
                    if (!isSignatureActive) return@pointerInput // No hacer nada si está inactiva

                    detectDragGestures(
                        onDragStart = { start ->
                            isInteracting = true
                            touchOffsetInElement = start
                        },
                        onDragEnd = {
                            isInteracting = false
                            touchOffsetInElement = null
                            onDragEnd(localOffset)
                        },
                        onDragCancel = {
                            isInteracting = false
                            touchOffsetInElement = null
                            onDragEnd(localOffset)
                        },
                        onDrag = { change, dragAmount ->
                            // Si el 'handle' (hijo) consumió el evento,
                            // dragAmount será Offset.Zero y no haremos nada.
                            if (dragAmount == Offset.Zero) return@detectDragGestures

                            change.consume() // Consume el drag
                            localOffset += dragAmount
                            onTransformChange(localOffset, localScale, localRotation)
                        }
                    )
                }

                // ----------- FIN DE LA CORRECCIÓN -----------

                .graphicsLayer(
                    // translationX y translationY YA NO VAN AQUÍ
                    scaleX = localScale,
                    scaleY = localScale,
                    rotationZ = Math.toDegrees(localRotation.toDouble()).toFloat(),
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) // TopStart
                )
        ) {
            // --- Contenido Visual de la Firma ---
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))) {
                Image(
                    bitmap = sigBmp,
                    contentDescription = "Firma",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // --- Controles (Borde y Botones) ---
            // Solo se muestran si la firma está activa
            if (isSignatureActive) {
                // Borde verde
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(3.dp, Color(0xFF00C853), RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                )

                // Botón de Eliminar (TopStart)
                val iconScale = 1f / localScale
                IconButton(
                    onClick = onDeleteSignature,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            translationX = with(density) { -14.dp.toPx() } * iconScale
                            translationY = with(density) { -14.dp.toPx() } * iconScale
                        }
                        .background(Color(0xFF2C2C2E), CircleShape)
                        .size(28.dp)
                ) { Icon(Icons.Default.Close, "Eliminar", tint = Color.White) }

                // Handle de Scale/Rotate (BottomEnd)
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
                        .pointerInput(isSignatureActive) { // Solo activo si la firma lo está
                            detectDragGestures(
                                onDragStart = {
                                    isHandleDragging = true
                                    isInteracting = true
                                    accumulatedDrag = Offset.Zero
                                },
                                onDragEnd = {
                                    isHandleDragging = false
                                    isInteracting = false
                                    accumulatedDrag = Offset.Zero
                                    // Animación de "snap"
                                    coroutineScope.launch {
                                        scaleAnim.snapTo(localScale)
                                        rotationAnim.snapTo(localRotation)
                                        val targetScale = snapToStep(localScale, SNAP_SCALE_STEP).coerceIn(MIN_SCALE, MAX_SCALE)
                                        val targetRot = Math.toRadians(snapToStep(Math.toDegrees(localRotation.toDouble()).toFloat(), SNAP_ROT_DEG).toDouble()).toFloat()
                                        val j1 = launch { scaleAnim.animateTo(targetScale, animationSpec = snapAnimSpec) }
                                        val j2 = launch { rotationAnim.animateTo(targetRot, animationSpec = snapAnimSpec) }
                                        j1.join(); j2.join()
                                        localScale = scaleAnim.value
                                        localRotation = rotationAnim.value
                                        onTransformChange(localOffset, localScale, localRotation)
                                        onDragEnd(localOffset)
                                    }
                                },
                                onDragCancel = {
                                    isHandleDragging = false
                                    isInteracting = false
                                    accumulatedDrag = Offset.Zero
                                    coroutineScope.launch { onDragEnd(localOffset) }
                                },
                                onDrag = { change, dragAmount ->
                                    // CRUCIAL: El 'consume()' del hijo (handle)
                                    // evita que el 'onDrag' del padre (imagen) se ejecute.
                                    change.consume()
                                    accumulatedDrag += dragAmount
                                    val ax = kotlin.math.abs(accumulatedDrag.x)
                                    val ay = kotlin.math.abs(accumulatedDrag.y)
                                    if (ax + ay == 0f) return@detectDragGestures

                                    val axp = ax.toDouble().pow(MIX_EXP.toDouble()).toFloat()
                                    val ayp = ay.toDouble().pow(MIX_EXP.toDouble()).toFloat()
                                    val sum = (axp + ayp).coerceAtLeast(1e-6f)
                                    val mixScale = axp / sum
                                    val mixRot = ayp / sum

                                    val dx = dragAmount.x
                                    val dy = dragAmount.y

                                    val scaleDeltaFactor = 1f + dx * SENSITIVITY_SCALE * mixScale
                                    val newScale = (localScale * scaleDeltaFactor).coerceIn(MIN_SCALE, MAX_SCALE)

                                    val newRotation = localRotation + dy * SENSITIVITY_ROT * mixRot

                                    // Compensar offset por el cambio de centro
                                    val centerLocal = Offset(bmpWidthPx / 2f, bmpHeightPx / 2f)
                                    val curCenterGlobal = localOffset + (centerLocal.rotateRad(localRotation) * localScale)
                                    val newOffset = curCenterGlobal - (centerLocal.rotateRad(newRotation) * newScale)

                                    localScale = newScale
                                    localRotation = newRotation
                                    localOffset = newOffset

                                    onTransformChange(localOffset, localScale, localRotation)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Tune, "Redimensionar", Modifier.size(16.dp), tint = Color.White)
                }
            }
        }
    }
}

// --- Funciones Helper ---
// (Colócalas al final de tu archivo, fuera del Composable)

/**
 * Rota un Offset por un ángulo en radianes.
 */
private fun Offset.rotateRad(rad: Float): Offset {
    val c = kotlin.math.cos(rad)
    val s = kotlin.math.sin(rad)
    return Offset(x * c - y * s, x * s + y * c)
}

/**
 * Redondea un valor al múltiplo más cercano de 'step'.
 */
private fun snapToStep(value: Float, step: Float): Float {
    if (step <= 0f) return value
    return (value / step).roundToInt() * step
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

private operator fun Offset.times(scale: Float) = Offset(x * scale, y * scale)
private operator fun Offset.plus(other: Offset) = Offset(x + other.x, y + other.y)
private operator fun Offset.minus(other: Offset) = Offset(x - other.x, y - other.y)
















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