
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
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.first
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

@Composable
private fun PlacingContent(
    baseBitmaps: List<Bitmap>,
    initialPageIndex: Int,
    signatureBitmap: ImageBitmap?,
    signatureOffset: Offset,
    signatureScale: Float,
    signatureRotation: Float,
    isSignatureActive: Boolean,
    onOffsetChange: (Offset) -> Unit,
    onScaleChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    onIsSignatureActiveChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onDeleteSignature: () -> Unit,
    onSignatureComplete: (Int, Bitmap) -> Unit,
    onRequestDrawing: () -> Unit
) {
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialPageIndex)
    val density = LocalDensity.current

    // --- FUENTE DE LA VERDAD para la posición LÓGICA ---
    var signatureRelative by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset(0.5f, 0.5f)) }
    var finalPageIndex by rememberSaveable { mutableStateOf(initialPageIndex) }
    // ---

    // Flag para la configuración INICIAL
    var isInitialPosSet by rememberSaveable(signatureBitmap) { mutableStateOf(false) }
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


        fun computeAbsoluteOffsetFromRelative(
            pageIndex: Int,
            relative: Offset,
            signatureBmp: ImageBitmap?,
            sigScale: Float
        ): Offset? {
            val base = baseBitmaps.getOrNull(pageIndex) ?: return null
            if (containerIntSize.width == 0 || containerIntSize.height == 0 || signatureBmp == null) return null

            // 1. Encuentra la página... (ahora esto compila)
            val visibleItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == pageIndex }
                ?: return null

            // 2. Llama a la función que ya existe
            val (scaledW, scaledH) = computeImageLayoutForPage(base) // <-- ¡AHORA SÍ COMPILA!

            // ... (resto de la función)
            val imageLeft = (containerIntSize.width - scaledW) / 2f
            val imageTop = (visibleItem.offset) + (containerIntSize.height - scaledH) / 2f
            val centerX = imageLeft + relative.x * scaledW
            val centerY = imageTop + relative.y * scaledH
            val sigDrawW = signatureBmp.width * sigScale
            val sigDrawH = signatureBmp.height * sigScale
            return Offset(centerX - sigDrawW / 2f, centerY - sigDrawH / 2f)
        }




        // LaunchedEffect para la configuración INICIAL
        LaunchedEffect(signatureBitmap, containerIntSize) {
            if (isInitialPosSet || signatureBitmap == null || containerIntSize.width == 0) return@LaunchedEffect

            // Espera a que el LazyColumn esté listo
            snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.isNotEmpty() }
                .first { it } // Espera a que 'it' sea true

            val initialScale = (containerIntSize.width * 0.60f) / signatureBitmap.width
            val initialOffset = computeAbsoluteOffsetFromRelative(
                initialPageIndex,
                Offset(0.5f, 0.5f),
                signatureBitmap,
                initialScale
            )

            initialOffset?.let {
                // Setea el estado LÓGICO
                signatureRelative = Offset(0.5f, 0.5f)
                finalPageIndex = initialPageIndex

                // Setea el estado del PADRE (la posición dibujada)
                onOffsetChange(it)
                onScaleChange(initialScale)
                isInitialPosSet = true
            }
        }


        // --- ESTE ES EL EFFECT CLAVE QUE ARREGLA EL SCROLL ---
        // Se ejecuta CADA VEZ que el scroll cambia
        LaunchedEffect(
            lazyListState.firstVisibleItemScrollOffset,
            lazyListState.firstVisibleItemIndex,
            isSignatureActive // No actualices si el usuario está arrastrando
        ) {
            // No actualices si el usuario está arrastrando o si no está inicializado
            if (isSignatureActive || !isInitialPosSet || signatureBitmap == null) return@LaunchedEffect

            // Recalcula dónde debería estar la firma AHORA MISMO
            val currentTargetOffset = computeAbsoluteOffsetFromRelative(
                finalPageIndex,
                signatureRelative,
                signatureBitmap,
                signatureScale
            )

            // Si la página está visible (currentTargetOffset != null)
            // Y la posición dibujada (signatureOffset) no coincide
            if (currentTargetOffset != null && (currentTargetOffset - signatureOffset).getDistance() > 1.0f) {
                // Mueve la firma a su nueva posición
                onOffsetChange(currentTargetOffset)
            }
        }
        // --- FIN DEL EFFECT DEL SCROLL ---


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

        // Overlay para detectar taps fuera de la firma... (sin cambios)
        if (isSignatureActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            Log.e(TAG, "Clic detectado FUERA de la firma (en overlay). Desactivando.")
                            onIsSignatureActiveChange(false)
                        }
                    }
            )
        }


        // --- Firma con renderizado condicional ---

        // 1. Calcula la posición actual OTRA VEZ.
        val currentAbsoluteOffset = if (isInitialPosSet && signatureBitmap != null) {
            computeAbsoluteOffsetFromRelative(
                finalPageIndex, signatureRelative, signatureBitmap, signatureScale
            )
        } else {
            null
        }

        // 2. Dibuja la firma SÓLO SI su página está visible (offset != null)
        if (currentAbsoluteOffset != null && signatureBitmap != null) {
            DraggableSignature(
                sigBmp = signatureBitmap,
                // Le pasamos el offset del padre, que ya actualizamos en el LaunchedEffect
                signatureOffset = signatureOffset,
                signatureScale = signatureScale,
                signatureRotation = signatureRotation,
                isSignatureActive = isSignatureActive,
                onIsSignatureActiveChange = onIsSignatureActiveChange,
                onDeleteSignature = onDeleteSignature,
                onTransformChange = { newOffset, newScale, newRotation ->
                    // 1. DURANTE EL ARRASTRE:
                    // Solo actualiza el estado visual (en el padre).
                    // ¡No calcules la lógica relativa aquí!
                    onOffsetChange(newOffset)
                    onScaleChange(newScale)
                    onRotationChange(newRotation)
                },
                onDragEnd = { finalLocalOffset ->
                    // 2. AL SOLTAR:
                    // Actualiza el estado visual final.
                    onOffsetChange(finalLocalOffset)

                    // Y AHORA, calcula y guarda la posición LÓGICA (el "ancla").
                    val sigDrawW = signatureBitmap.width * signatureScale
                    val sigDrawH = signatureBitmap.height * signatureScale
                    val centerX = finalLocalOffset.x + sigDrawW / 2f
                    val centerY = finalLocalOffset.y + sigDrawH / 2f

                    val visible = lazyListState.layoutInfo.visibleItemsInfo
                    if (visible.isNotEmpty()) {
                        val target = visible.minByOrNull { item ->
                            val itemCenterY = item.offset + item.size / 2
                            abs(itemCenterY - centerY)
                        } ?: visible.first()

                        val pageIndex = target.index
                        val base = baseBitmaps.getOrNull(pageIndex)
                        if (base != null) {
                            val (scaledW, scaledH) = computeImageLayoutForPage(base)
                            val imageLeft = (containerIntSize.width - scaledW) / 2f
                            val imageTop = target.offset + (containerIntSize.height - scaledH) / 2f
                            val relX = (centerX - imageLeft) / scaledW
                            val relY = (centerY - imageTop) / scaledH

                            // ¡Guarda el nuevo estado LÓGICO!
                            signatureRelative = Offset(relX.coerceIn(0f, 1f), relY.coerceIn(0f, 1f))
                            finalPageIndex = pageIndex
                        }
                    }
                }
            )
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
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        .padding(4.dp)
                )
            }
            Text(
                "Página ${finalPageIndex + 1} de ${baseBitmaps.size}",
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
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
                                // --- CAMBIO: Asegúrate de usar el 'signatureOffset' del padre ---
                                // (que es el último valor dibujado y sincronizado)
                                val finalBitmap = placeSignatureOnBitmap(
                                    base = baseBitmaps[finalPageIndex],
                                    signature = signatureBitmap,
                                    signatureOffset = signatureOffset, // Correcto
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
    signatureOffset: Offset, // top-left absoluto en px (del padre)
    signatureScale: Float,   // scale proveniente del padre
    signatureRotation: Float, // radianes, proveniente del padre
    onTransformChange: (newOffset: Offset, newScale: Float, newRotation: Float) -> Unit,
    isSignatureActive: Boolean,
    onIsSignatureActiveChange: (Boolean) -> Unit,
    onDeleteSignature: () -> Unit,
    onDragEnd: (Offset) -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Sensibilidades / límites / snap config
    val SENSITIVITY_SCALE = 0.0035f
    val SENSITIVITY_ROT = 0.0045f
    val MIN_SCALE = 0.1f
    val MAX_SCALE = 1.2f
    val SNAP_SCALE_STEP = 0.05f
    val SNAP_ROT_DEG = 10f
    val MIX_EXP = 1.25f
    val snapAnimSpec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

    // bmp dims px
    val bmpWidthPx = sigBmp.width.toFloat()
    val bmpHeightPx = sigBmp.height.toFloat()

    // estados locales (sincronizados con padre cuando no se interactúa)
    var localOffset by remember { mutableStateOf(signatureOffset) }
    var localScale by remember { mutableStateOf(signatureScale) }
    var localRotation by remember { mutableStateOf(signatureRotation) } // radianes

    val scaleAnim = remember { Animatable(localScale) }
    val rotationAnim = remember { Animatable(localRotation) }

    var isInteracting by remember { mutableStateOf(false) }
    var isHandleDragging by remember { mutableStateOf(false) }
    var touchOffsetInElement by remember { mutableStateOf<Offset?>(null) }
    var accumulatedDrag by remember { mutableStateOf(Offset.Zero) }

    // Sincronizar desde el padre cuando no interactuamos
    LaunchedEffect(signatureOffset) {
        if (!isInteracting) {
            if ((localOffset - signatureOffset).getDistance() > 0.5f) {
                localOffset = signatureOffset
            }
        }
    }
    LaunchedEffect(signatureScale) {
        if (!isInteracting && kotlin.math.abs(localScale - signatureScale) > 1e-3f) {
            localScale = signatureScale
            scaleAnim.snapTo(signatureScale)
        } else if (isInteracting) {
            scaleAnim.snapTo(localScale)
        }
    }
    LaunchedEffect(signatureRotation) {
        if (!isInteracting && kotlin.math.abs(localRotation - signatureRotation) > 1e-3f) {
            localRotation = signatureRotation
            rotationAnim.snapTo(signatureRotation)
        } else if (isInteracting) {
            rotationAnim.snapTo(localRotation)
        }
    }

    // Helpers locales para dp conversion del tamaño visible
    val displayWidthDp = with(density) { (bmpWidthPx * localScale).toDp() }
    val displayHeightDp = with(density) { (bmpHeightPx * localScale).toDp() }

    Box(modifier = modifier.fillMaxSize()) {
        // Contenedor que representa la firma (layout YA reflejando localScale)
        Box(
            modifier = Modifier
                // top-left absoluto dentro del contenedor
                .offset { IntOffset(localOffset.x.roundToInt(), localOffset.y.roundToInt()) }
                .size(width = displayWidthDp, height = displayHeightDp)
                .graphicsLayer {
                    rotationZ = Math.toDegrees(localRotation.toDouble()).toFloat()
                    transformOrigin = TransformOrigin(0.5f, 0.5f) // <-- pivote en el centro
                    // NO scaleX/scaleY aquí (el .size ya incorpora scale)
                }
                //.background(Color.Red.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // Sin efecto ripple
                    onClick = {
                        // Si no está activa, la activamos.
                        if (!isSignatureActive) {
                            onIsSignatureActiveChange(true)
                        }
                        // Si ya estaba activa, este tap no hace nada,
                        // pero "consume" el clic, evitando que el
                        // overlay de "clic fuera" lo reciba.
                    }
                )

                // Tap para activar la firma (consume la pulsación)
                .pointerInput(Unit) { // Clave Unit: El detector NUNCA se reinicia
                    detectDragGestures(
                        onDragStart = { start ->
                            // 3. ¡AQUÍ ESTÁ LA MAGIA!
                            // No importa si está activo o no,
                            // si empezamos a arrastrar la firma, se activa.
                            if (!isSignatureActive) {
                                onIsSignatureActiveChange(true)
                            }

                            // Lógica de drag normal
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
                            // 4. LÓGICA DE PRIORIDAD (LA CLAVE DE LOS ICONOS)
                            // Si el hijo (el handle) consumió el gesto,
                            // dragAmount será Zero y el padre (la firma) NO se moverá.
                            if (dragAmount == Offset.Zero) return@detectDragGestures

                            // Si el hijo no lo consumió, el padre se mueve.
                            change.consumePositionChange()
                            localOffset += dragAmount
                            onTransformChange(localOffset, localScale, localRotation)
                        }
                    )
                }
        ) {
            // Imagen que ocupa todo el container (ya escalada por .size)
            Image(
                bitmap = sigBmp,
                contentDescription = "Firma",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // Borde verde que ahora coincide exactamente con la imagen visible
            if (isSignatureActive) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(3.dp, Color(0xFF00C853), RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                )
            }

            // Botón eliminar (top-start) y handle (bottom-end)
            if (isSignatureActive) {
                // val iconScale = 1f / localScale // <-- YA NO SE USA

                IconButton(
                    onClick = onDeleteSignature,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .graphicsLayer {
                            // --- CORRECCIÓN 1: Iconos ---
                            // scaleX = iconScale // <-- ELIMINADO
                            // scaleY = iconScale // <-- ELIMINADO
                            // La traslación SÍ debe escalarse para que el icono
                            // quede "fuera" del borde proporcionalmente
                            translationX = with(density) { -14.dp.toPx() } // <-- QUITA * iconTranslate
                            translationY = with(density) { -14.dp.toPx() } // <-- QUITA * iconTranslate
                        }
                        .background(Color(0xFF2C2C2E), CircleShape)
                        .size(28.dp)
                ) {
                    Icon(Icons.Default.Close, "Eliminar", tint = Color.White)
                }

                // Handle bottom-end: escala/rotación mezclada
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .graphicsLayer {
                            // --- CORRECCIÓN 1: Iconos ---
                            // val inv = iconScale // <-- ELIMINADO
                            // scaleX = inv; scaleY = inv // <-- ELIMINADO
                            // La traslación SÍ debe escalarse
                            translationX = with(density) { 8.dp.toPx() } // <-- QUITA * iconTranslate
                            translationY = with(density) { 8.dp.toPx() } // <-- QUITA * iconTranslate
                        }
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isHandleDragging) Color(0xFF616161) else Color(0xFF414141))
                        .pointerInput(isSignatureActive) {
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
                                    // Snap animado
                                    coroutineScope.launch {
                                        scaleAnim.snapTo(localScale)
                                        rotationAnim.snapTo(localRotation)
                                        val targetScale = snapToStep(localScale, SNAP_SCALE_STEP).coerceIn(MIN_SCALE, MAX_SCALE)
                                        val targetRotDeg = snapToStep(Math.toDegrees(localRotation.toDouble()).toFloat(), SNAP_ROT_DEG)
                                        val targetRot = Math.toRadians(targetRotDeg.toDouble()).toFloat()

                                        // --- CORRECCIÓN 3: onDragEnd ---
                                        // Centro global ANTES del snap (estado actual)
                                        val preSnapW = bmpWidthPx * scaleAnim.value // (localScale antes del snap)
                                        val preSnapH = bmpHeightPx * scaleAnim.value
                                        val preSnapCenterLocal = Offset(preSnapW / 2f, preSnapH / 2f)
                                        // El centro global es el offset + el centro local (sin rotación)
                                        val curCenterGlobal = localOffset + preSnapCenterLocal // <-- SIN .rotateRad()

                                        val j1 = launch { scaleAnim.animateTo(targetScale, animationSpec = snapAnimSpec) }
                                        val j2 = launch { rotationAnim.animateTo(targetRot, animationSpec = snapAnimSpec) }
                                        j1.join(); j2.join()

                                        localScale = scaleAnim.value
                                        localRotation = rotationAnim.value

                                        // Centro local DESPUÉS del snap (estado final)
                                        val postSnapW = bmpWidthPx * localScale // (localScale después del snap)
                                        val postSnapH = bmpHeightPx * localScale
                                        val postSnapCenterLocal = Offset(postSnapW / 2f, postSnapH / 2f)

                                        // El nuevo offset es el centro que mantuvimos fijo, menos el nuevo centro local
                                        val newOffset = curCenterGlobal - postSnapCenterLocal // <-- SIN .rotateRad()

                                        localOffset = newOffset
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
                                    change.consumePositionChange()
                                    accumulatedDrag += dragAmount

                                    val ax = abs(accumulatedDrag.x)
                                    val ay = abs(accumulatedDrag.y)
                                    if (ax + ay == 0f) return@detectDragGestures
                                    val axp = ax.toDouble().pow(MIX_EXP.toDouble()).toFloat()
                                    val ayp = ay.toDouble().pow(MIX_EXP.toDouble()).toFloat()
                                    val sum = (axp + ayp).coerceAtLeast(1e-6f)
                                    val mixScale = axp / sum
                                    val mixRot = ayp / sum

                                    val dx = dragAmount.x
                                    val dy = dragAmount.y

                                    val newScale = (localScale * (1f + dx * SENSITIVITY_SCALE * mixScale)).coerceIn(MIN_SCALE, MAX_SCALE)
                                    val newRotation = localRotation + dy * SENSITIVITY_ROT * mixRot

                                    // --- CORRECCIÓN 2: onDrag (Handle) ---
                                    // Compensación SÓLO por escala, ya que origin(0.5, 0.5) maneja la rotación

                                    // 1. Centro actual (el punto que queremos mantener fijo)
                                    val displayW = bmpWidthPx * localScale
                                    val displayH = bmpHeightPx * localScale
                                    val centerLocal = Offset(displayW / 2f, displayH / 2f)
                                    val curCenterGlobal = localOffset + centerLocal // <-- SIN .rotateRad()

                                    // 2. Nuevo centro (basado en la nueva escala)
                                    val newDisplayW = bmpWidthPx * newScale
                                    val newDisplayH = bmpHeightPx * newScale
                                    val newCenterLocal = Offset(newDisplayW / 2f, newDisplayH / 2f)

                                    // 3. Nuevo offset es la diferencia
                                    val newOffset = curCenterGlobal - newCenterLocal // <-- SIN .rotateRad()


                                    // Aplicar cambios en vivo
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
    var isDrawing by remember { mutableStateOf(false) }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentStroke = listOf(offset) // Inicia el trazo
                    },
                    onDrag = { change: PointerInputChange, _ ->
                        currentStroke = currentStroke + change.position // Añade todos los puntos
                        change.consume()
                    },
                    onDragEnd = {
                        val canvasSize = this.size // Obtiene el tamaño real del Canvas

                        // FILTRA el trazo al final para eliminar puntos "fantasma"
                        val filteredStroke = currentStroke.filter { point ->
                            point.x in 0f..canvasSize.width.toFloat() &&
                                    point.y in 0f..canvasSize.height.toFloat()
                        }

                        if (filteredStroke.size > 1) {
                            onAddStroke(filteredStroke)
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

    val halfStroke = strokeWidth / 2f

    paths.forEach { path ->
        val b = path.getBounds()
        // Inflamos los bounds para incluir el grosor de la tinta
        left = min(left, b.left - halfStroke)
        top = min(top, b.top - halfStroke)
        right = max(right, b.right + halfStroke)
        bottom = max(bottom, b.bottom + halfStroke)
    }

    if (left == Float.POSITIVE_INFINITY) return ImageBitmap(1, 1)

    val bounds = Rect(left, top, right, bottom)

    val padding = 2f

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