
package com.example.scannerpro.signature

// ... otros imports
// --- AÑADÍ ESTA LÍNEA ---
// --- FIN ---
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Parcelable
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

import androidx.compose.foundation.lazy.LazyRow // --- NUEVO ---
import androidx.compose.foundation.lazy.items // --- NUEVO ---
import androidx.compose.material.icons.filled.Add // --- NUEVO ---
import androidx.compose.ui.input.pointer.pointerInput // --- NUEVO ---
import androidx.compose.foundation.gestures.detectTapGestures // --- NUEVO ---

import androidx.compose.ui.unit.Dp // <-- Añade esta importación si falta
import androidx.compose.foundation.layout.widthIn // <-- Añade esta importación
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Surface // <-- Asegúrate de usar esta (material 1)
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import java.util.UUID


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


private data class SignatureInstance(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: ImageBitmap,
    var pageIndex: Int,
    var relativeOffset: Offset, // Posición (0f-1f) relativa al centro de la página
    var scale: Float,
    var rotation: Float // En radianes
)



@Composable
fun SignatureScreen(
    baseBitmaps: List<Bitmap>,
    initialPageIndexFromProps: Int,
    savedSignatures: List<ImageBitmap>, // Lista de firmas ya guardadas
    onSignatureComplete: (Int, Bitmap) -> Unit,
    onCancel: () -> Unit,
    onNewSignatureCreated: (Bitmap) -> Unit,
    onSavedSignatureDeleted: (Int) -> Unit // <-- 1. AÑADE ESTE PARÁMETRO
) {

    // --- ESTADO NUEVO (MULTI-FIRMA) ---
    var signatureInstances by remember { mutableStateOf<List<SignatureInstance>>(emptyList()) }
    var activeSignatureId by rememberSaveable { mutableStateOf<String?>(null) }
    // --- FIN ESTADO NUEVO ---

    // --- ESTADO COMPARTIDO (Dibujo y Posicionamiento) ---
    var mode by rememberSaveable { mutableStateOf(SignatureMode.PLACING) }
    var parcelableStrokes by rememberSaveable { mutableStateOf<List<ParcelableStroke>>(emptyList()) }
    var activePageIndex by rememberSaveable { mutableStateOf(initialPageIndexFromProps) }
    // --- FIN ESTADO COMPARTIDO ---

    // --- ESTADO SOLO DE DIBUJO ---
    val strokes = remember(parcelableStrokes) {
        parcelableStrokes.map { stroke -> stroke.points.map { it.toOffset() } }
    }
    val onAddStroke: (List<Offset>) -> Unit = { newStroke ->
        parcelableStrokes = parcelableStrokes + ParcelableStroke(newStroke.map { it.toParcelable() })
    }
    var strokeColor by rememberSaveable(stateSaver = ColorSaver) { mutableStateOf(Color.Black) }
    var strokeWidth by rememberSaveable { mutableStateOf(5f) }
    // --- FIN ESTADO SOLO DE DIBUJO ---

    // --- EFECTOS (para la orientación de pantalla) ---
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

    // --- SE ELIMINARON TODAS LAS VARIABLES "ZOMBIS" ---
    // (Se fueron: signatureRotation, isSignatureActive, signatureBitmap,
    // signatureOffset, signatureScale, isInitialPosSet y el LaunchedEffect viejo)
    // --- FIN DE LA LIMPIEZA ---

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
                    val newSigImageBitmap = captureSignature(strokes, strokeColor, strokeWidth)

                    // 1. Crea la nueva instancia
                    val newInstance = SignatureInstance(
                        bitmap = newSigImageBitmap,
                        pageIndex = activePageIndex,
                        relativeOffset = Offset(0.5f, 0.5f), // Centro por defecto
                        scale = 0.8f, // Escala por defecto
                        rotation = 0f
                    )
                    // 2. Añádela a la lista
                    signatureInstances = signatureInstances + newInstance
                    // 3. Actívala
                    activeSignatureId = newInstance.id
                    // 4. Cambia de modo
                    mode = SignatureMode.PLACING
                    // 5. Llama al callback para guardarla en la BD
                    onNewSignatureCreated(newSigImageBitmap.asAndroidBitmap())
                }
            }
        )
    } else { // PLACING mode
        PlacingContent(
            baseBitmaps = baseBitmaps,
            initialPageIndex = activePageIndex,

            // Pasa el estado limpio
            instances = signatureInstances,
            activeInstanceId = activeSignatureId,
            savedSignatures = savedSignatures,

            onCancel = onCancel,
            onSignatureComplete = onSignatureComplete,

            onRequestDrawing = { currentPageIndex ->
                mode = SignatureMode.DRAWING
                parcelableStrokes = emptyList()
                activePageIndex = currentPageIndex
            },
            onInstanceAdd = { bitmapToAdd, pageIndex ->
                val newInstance = SignatureInstance(
                    bitmap = bitmapToAdd,
                    pageIndex = pageIndex,
                    relativeOffset = Offset(0.5f, 0.5f),
                    scale = 0.8f,
                    rotation = 0f
                )
                signatureInstances = signatureInstances + newInstance
                activeSignatureId = newInstance.id
            },
            onInstanceUpdate = { updatedInstance ->
                signatureInstances = signatureInstances.map {
                    if (it.id == updatedInstance.id) updatedInstance else it
                }
            },
            onInstanceDelete = { instanceId ->
                signatureInstances = signatureInstances.filterNot { it.id == instanceId }
                if (activeSignatureId == instanceId) {
                    activeSignatureId = null
                }
            },
            onInstanceActivate = { instanceId ->
                activeSignatureId = instanceId
            },
            onDeactivate = {
                activeSignatureId = null
            },
            onSavedSignatureDeleted = onSavedSignatureDeleted // <-- 2. PASA EL CALLBACK
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
private fun DeleteSignatureDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar eliminación") },
        text = { Text("¿Estás seguro de que quieres eliminar esta firma permanentemente?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Eliminar")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}


@Composable
private fun PlacingContent(
    baseBitmaps: List<Bitmap>,
    initialPageIndex: Int,

    // --- NUEVO ESTADO ---
    instances: List<SignatureInstance>,
    activeInstanceId: String?,
    savedSignatures: List<ImageBitmap>,
    // --- FIN NUEVO ESTADO ---

    onCancel: () -> Unit,
    onSignatureComplete: (Int, Bitmap) -> Unit,
    onRequestDrawing: (currentPageIndex: Int) -> Unit,

    // --- NUEVAS LAMBDAS ---
    onInstanceAdd: (bitmap: ImageBitmap, pageIndex: Int) -> Unit,
    onInstanceUpdate: (SignatureInstance) -> Unit,
    onInstanceDelete: (String) -> Unit,
    onInstanceActivate: (String) -> Unit,
    onDeactivate: () -> Unit,
    onSavedSignatureDeleted: (Int) -> Unit

) {
    var signatureIndexToDelete by rememberSaveable { mutableStateOf<Int?>(null) }
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialPageIndex)
    val density = LocalDensity.current

    val currentPageIndex by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                initialPageIndex
            } else {
                val viewportCenterY = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.height / 2
                visibleItems.minByOrNull {
                    val itemCenterY = it.offset + it.size / 2
                    abs(itemCenterY - viewportCenterY)
                }?.index ?: initialPageIndex
            }
        }
    }

    // La página en la que se guardará (la que tiene la firma activa o la visible)
    val finalPageIndex = remember(activeInstanceId, currentPageIndex, instances) {
        activeInstanceId?.let { id ->
            instances.firstOrNull { it.id == id }?.pageIndex
        } ?: currentPageIndex
    }

    var containerIntSize by remember { mutableStateOf(IntSize(0, 0)) }
    var showSubmenu by rememberSaveable { mutableStateOf(false) }
    var bottomBarHeightPx by remember { mutableStateOf(0f) }
    val bottomBarHeightDp = with(LocalDensity.current) { bottomBarHeightPx.toDp() }

    // --- Funciones de Ayuda para Coordenadas ---

    /**
     * Calcula las dimensiones y la posición de la imagen de la página DENTRO del contenedor
     */
    fun computePageLayout(
        pageIndex: Int,
        base: Bitmap
    ): Rect? { // Devuelve (left, top, right, bottom) del layout de la página
        if (containerIntSize.width == 0 || containerIntSize.height == 0) return null

        val visibleItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == pageIndex }
            ?: return null // La página no está visible

        val pagePaddingPx = with(density) { 16.dp.toPx() } // Asumimos padding
        val viewWidth = (containerIntSize.width - (pagePaddingPx * 2)).coerceAtLeast(1f)
        val viewHeight = (containerIntSize.height - (pagePaddingPx * 2)).coerceAtLeast(1f)
        val bitmapAspectRatio = base.width.toFloat() / base.height.toFloat()
        val viewAspectRatio = viewWidth / viewHeight

        val (scaledW, scaledH) = if (bitmapAspectRatio > viewAspectRatio) {
            viewWidth to viewWidth / bitmapAspectRatio
        } else {
            viewHeight * bitmapAspectRatio to viewHeight
        }

        val imageLeft = (containerIntSize.width - scaledW) / 2f
        val imageTop = (visibleItem.offset) + (containerIntSize.height - scaledH) / 2f

        return Rect(left = imageLeft, top = imageTop, right = imageLeft + scaledW, bottom = imageTop + scaledH)
    }

    /**
     * Convierte la posición LÓGICA (relativa) de una firma a su posición ABSOLUTA (en pantalla)
     */
    fun computeAbsoluteOffsetFromRelative(
        instance: SignatureInstance,
        pageLayout: Rect
    ): Offset {
        val scaledW = pageLayout.width
        val scaledH = pageLayout.height

        // 1. Centro de la firma en coordenadas de la imagen (0-N)
        val centerXOnImage = instance.relativeOffset.x * scaledW
        val centerYOnImage = instance.relativeOffset.y * scaledH

        // 2. Centro de la firma en coordenadas de la pantalla (absoluto)
        val centerXOnScreen = pageLayout.left + centerXOnImage
        val centerYOnScreen = pageLayout.top + centerYOnImage

        // 3. Top-left de la firma en coordenadas de la pantalla (absoluto)
        val sigDrawW = instance.bitmap.width * instance.scale
        val sigDrawH = instance.bitmap.height * instance.scale
        return Offset(centerXOnScreen - sigDrawW / 2f, centerYOnScreen - sigDrawH / 2f)
    }

    /**
     * Convierte la posición ABSOLUTA (de pantalla) de una firma a su posición LÓGICA (relativa)
     */
    fun convertAbsoluteToRelative(
        absOffset: Offset,
        scale: Float,
        bitmap: ImageBitmap
    ): Pair<Int, Offset> { // Devuelve (newPageIndex, newRelativeOffset)

        val sigDrawW = bitmap.width * scale
        val sigDrawH = bitmap.height * scale
        val centerX = absOffset.x + sigDrawW / 2f
        val centerY = absOffset.y + sigDrawH / 2f

        val visible = lazyListState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return 0 to Offset(0.5f, 0.5f) // Fallback

        // Encuentra la página más cercana al centro de la firma
        val targetItem = visible.minByOrNull { item ->
            val itemCenterY = item.offset + item.size / 2
            abs(itemCenterY - centerY)
        } ?: visible.first()

        val pageIndex = targetItem.index
        val base = baseBitmaps.getOrNull(pageIndex) ?: return pageIndex to Offset(0.5f, 0.5f)

        // Calcula el layout de ESA página
        val pageLayout = computePageLayout(pageIndex, base) ?: return pageIndex to Offset(0.5f, 0.5f)

        // Convierte
        val relX = (centerX - pageLayout.left) / pageLayout.width
        val relY = (centerY - pageLayout.top) / pageLayout.height

        return pageIndex to Offset(relX.coerceIn(0f, 1f), relY.coerceIn(0f, 1f))
    }

    // --- FIN Funciones de Ayuda ---


    // --- El `Box` raíz para el overlay ---
    Box(modifier = Modifier.fillMaxSize()) {

        // --- La Columna principal de la UI ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF212121))
        ) {

            // --- 1. La barra superior (Row) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Default.Close, "Cancelar", tint = Color.White,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape).padding(4.dp)
                    )
                }
                Text(
                    "Página ${finalPageIndex + 1} de ${baseBitmaps.size}",
                    color = Color.White,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // --- 2. El `Box` de contenido (con weight) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .onGloballyPositioned {
                        containerIntSize = it.size
                    }
            ) {

                // Documento (scrollable)
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = (activeInstanceId == null) // Desactiva scroll si se arrastra una firma
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
                                    .background(Color(0xFF333333), RoundedCornerShape(2.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                // Overlay para detectar taps fuera (desactivar)
                if (activeInstanceId != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    Log.e(TAG, "Clic detectado FUERA. Desactivando.")
                                    onDeactivate()
                                }
                            }
                    )
                }

                // --- RENDERIZADO DE MÚLTIPLES FIRMAS ---
                // Dibuja las firmas que están en páginas visibles
                instances.forEach { instance ->
                    val baseBmp = baseBitmaps.getOrNull(instance.pageIndex) ?: return@forEach
                    val pageLayout = computePageLayout(instance.pageIndex, baseBmp)

                    // Dibuja solo si la página está visible (layout != null)
                    pageLayout?.let {
                        // Calcula el offset absoluto actual
                        val absoluteOffset = computeAbsoluteOffsetFromRelative(instance, it)
                        key(instance.id) {
                            DraggableSignature(
                                //key = instance.id, // ¡Importante para el rendimiento!
                                sigBmp = instance.bitmap,
                                signatureOffset = absoluteOffset, // Se actualiza con el scroll
                                signatureScale = instance.scale,
                                signatureRotation = instance.rotation,
                                isSignatureActive = (instance.id == activeInstanceId),

                                onIsSignatureActiveChange = {
                                    if (it) onInstanceActivate(instance.id) else onDeactivate()
                                },
                                onDeleteSignature = {
                                    onInstanceDelete(instance.id)
                                },
                                onTransformChange = { newAbsOffset, newScale, newRotation ->
                                    // Convierte el drag a estado lógico
                                    val (newPageIndex, newRelativeOffset) =
                                        convertAbsoluteToRelative(
                                            newAbsOffset,
                                            newScale,
                                            instance.bitmap
                                        )

                                    // Envía el estado actualizado al padre
                                    onInstanceUpdate(
                                        instance.copy(
                                            pageIndex = newPageIndex,
                                            relativeOffset = newRelativeOffset,
                                            scale = newScale,
                                            rotation = newRotation
                                        )
                                    )
                                },
                                onDragEnd = { finalAbsOffset ->
                                    // La lógica ya está en onTransformChange
                                    // Opcional: podrías querer hacer un "snap" aquí
                                }
                            )
                        }
                    }
                }
                // --- FIN RENDERIZADO DE FIRMAS ---

            } // --- FIN DEL BOX DE CONTENIDO ---


            // --- 3. La barra inferior (Surface) ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        bottomBarHeightPx = it.size.height.toFloat()
                    },
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
                                .clickable { showSubmenu = true }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Receipt, "Firma (sello)", Modifier.size(28.dp), tint = Color.White)
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
                            Icon(Icons.Default.CalendarToday, "Fecha (calendario)", Modifier.size(28.dp), tint = Color.White)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Fecha", color = Color.White, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = {
                                // --- LÓGICA DE GUARDADO FINAL ---
                                val pageToSave = finalPageIndex
                                val base = baseBitmaps.getOrNull(pageToSave)
                                if (base != null) {
                                    val finalBitmap = mergeSignaturesOnPage(
                                        base = base,
                                        instances = instances,
                                        pageIndexToSave = pageToSave,
                                        containerSize = containerIntSize,
                                        density = density
                                    )
                                    onSignatureComplete(pageToSave, finalBitmap)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30D5C8)),
                            shape = CircleShape,
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Check, "Aceptar", Modifier.size(20.dp), tint = Color.White)
                        }
                    }
                }
            }
            // --- FIN DE LA SURFACE ---

        } // --- FIN DE LA COLUMN PRINCIPAL ---


        // --- 4. El Submenú (Overlay) ---
        if (showSubmenu) {
            SignatureSubmenu(
                savedSignatures = savedSignatures,
                onCreateNew = {
                    showSubmenu = false
                    onRequestDrawing(currentPageIndex)
                },
                onSignatureSelected = { selectedBmp ->
                    showSubmenu = false
                    // --- Llama al nuevo callback ---
                    onInstanceAdd(selectedBmp, currentPageIndex)
                },
                onRequestDelete = { index ->
                    signatureIndexToDelete = index
                },
                onDismiss = {
                    showSubmenu = false
                },
                bottomBarHeight = bottomBarHeightDp
            )
        }

        if (signatureIndexToDelete != null) {
            val index = signatureIndexToDelete!! // Sabemos que no es nulo

            DeleteSignatureDialog(
                onConfirm = {
                    // Si confirma, llama al callback de borrado original
                    onSavedSignatureDeleted(index)
                    // Cierra el diálogo
                    signatureIndexToDelete = null
                },
                onDismiss = {
                    // Si cancela, solo cierra el diálogo
                    signatureIndexToDelete = null
                }
            )
        }

    } // --- FIN DEL BOX RAÍZ ---
}
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

    // --- ESTADO ACTUALIZADO (LA SOLUCIÓN) ---
    // Estas referencias siempre tendrán el valor MÁS RECIENTE
    // de los parámetros, evitando el "estado obsoleto" (stale state)
    // dentro de los gestos.
    val latestOnTransformChange by rememberUpdatedState(onTransformChange)
    val latestOnIsSignatureActiveChange by rememberUpdatedState(onIsSignatureActiveChange)
    val latestOnDeleteSignature by rememberUpdatedState(onDeleteSignature)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestIsSignatureActive by rememberUpdatedState(isSignatureActive)
    // --- FIN DE LA SOLUCIÓN ---

    // Sensibilidades / límites / snap config (sin cambios)
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
    // (Esta lógica está bien, no se toca)
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
                    transformOrigin = TransformOrigin(0.5f, 0.5f) // pivote en el centro
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        // Usar la referencia más reciente
                        if (!latestIsSignatureActive) latestOnIsSignatureActiveChange(true)
                    }
                )
                // --- CAMBIO: Volver a pointerInput(Unit) ---
                .pointerInput(Unit) { // 'Unit' evita que el detector se reinicie
                    detectDragGestures(
                        onDragStart = { start ->
                            // Usar la referencia más reciente
                            if (!latestIsSignatureActive) {
                                latestOnIsSignatureActiveChange(true)
                            }
                            isInteracting = true
                            touchOffsetInElement = start
                        },
                        onDragEnd = {
                            isInteracting = false
                            touchOffsetInElement = null
                            latestOnDragEnd(localOffset) // Usar la más reciente
                        },
                        onDragCancel = {
                            isInteracting = false
                            touchOffsetInElement = null
                            latestOnDragEnd(localOffset) // Usar la más reciente
                        },
                        onDrag = { change, dragAmount ->
                            if (dragAmount == Offset.Zero) return@detectDragGestures
                            change.consumePositionChange()

                            val globalDelta = dragAmount.rotateBy(localRotation)

                            localOffset += globalDelta
                            // Usar la referencia más reciente
                            latestOnTransformChange(localOffset, localScale, localRotation)
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

            // Usar la referencia más reciente para decidir si se muestra
            if (latestIsSignatureActive) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(3.dp, Color(0xFF00C853), RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                )
            }

            // Botón eliminar (top-start) y handle (bottom-end)
            // Usar la referencia más reciente para decidir si se muestran
            if (latestIsSignatureActive) {
                IconButton(
                    onClick = latestOnDeleteSignature, // Usar la más reciente
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .graphicsLayer {
                            translationX = with(density) { -14.dp.toPx() }
                            translationY = with(density) { -14.dp.toPx() }
                        }
                        .background(Color(0xFF2C2C2E), CircleShape)
                        .size(28.dp)
                ) {
                    Icon(Icons.Default.Close, "Eliminar", tint = Color.White)
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .graphicsLayer {
                            translationX = with(density) { 8.dp.toPx() }
                            translationY = with(density) { 8.dp.toPx() }
                        }
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isHandleDragging) Color(0xFF616161) else Color(0xFF414141))
                        // --- AÑADIDO: Clickable para el handle ---
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (!latestIsSignatureActive) latestOnIsSignatureActiveChange(true)
                            }
                        )
                        // --- CAMBIO: Volver a pointerInput(Unit) ---
                        .pointerInput(Unit) { // 'Unit' evita que el detector se reinicie
                            detectDragGestures(
                                onDragStart = {
                                    // Usar la referencia más reciente
                                    if (!latestIsSignatureActive) latestOnIsSignatureActiveChange(true)
                                    isHandleDragging = true
                                    isInteracting = true
                                    accumulatedDrag = Offset.Zero
                                },
                                onDragEnd = {
                                    isHandleDragging = false
                                    isInteracting = false
                                    accumulatedDrag = Offset.Zero
                                    coroutineScope.launch {
                                        scaleAnim.snapTo(localScale)
                                        rotationAnim.snapTo(localRotation)
                                        val targetScale = snapToStep(localScale, SNAP_SCALE_STEP).coerceIn(MIN_SCALE, MAX_SCALE)
                                        val targetRotDeg = snapToStep(Math.toDegrees(localRotation.toDouble()).toFloat(), SNAP_ROT_DEG)
                                        val targetRot = Math.toRadians(targetRotDeg.toDouble()).toFloat()

                                        val preSnapW = bmpWidthPx * scaleAnim.value
                                        val preSnapH = bmpHeightPx * scaleAnim.value
                                        val preSnapCenterLocal = Offset(preSnapW / 2f, preSnapH / 2f)
                                        val curCenterGlobal = localOffset + preSnapCenterLocal

                                        val j1 = launch { scaleAnim.animateTo(targetScale, animationSpec = snapAnimSpec) }
                                        val j2 = launch { rotationAnim.animateTo(targetRot, animationSpec = snapAnimSpec) }
                                        j1.join(); j2.join()

                                        localScale = scaleAnim.value
                                        localRotation = rotationAnim.value

                                        val postSnapW = bmpWidthPx * localScale
                                        val postSnapH = bmpHeightPx * localScale
                                        val postSnapCenterLocal = Offset(postSnapW / 2f, postSnapH / 2f)

                                        val newOffset = curCenterGlobal - postSnapCenterLocal

                                        localOffset = newOffset
                                        // Usar las referencias más recientes
                                        latestOnTransformChange(localOffset, localScale, localRotation)
                                        latestOnDragEnd(localOffset)
                                    }
                                },
                                onDragCancel = {
                                    isHandleDragging = false
                                    isInteracting = false
                                    accumulatedDrag = Offset.Zero
                                    // Usar la referencia más reciente
                                    coroutineScope.launch { latestOnDragEnd(localOffset) }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consumePositionChange()
                                    accumulatedDrag += dragAmount

                                    // ... (lógica de mixScale, mixRot)
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

                                    // ... (lógica de compensación de offset)
                                    val displayW = bmpWidthPx * localScale
                                    val displayH = bmpHeightPx * localScale
                                    val centerLocal = Offset(displayW / 2f, displayH / 2f)
                                    val curCenterGlobal = localOffset + centerLocal

                                    val newDisplayW = bmpWidthPx * newScale
                                    val newDisplayH = bmpHeightPx * newScale
                                    val newCenterLocal = Offset(newDisplayW / 2f, newDisplayH / 2f)
                                    val newOffset = curCenterGlobal - newCenterLocal

                                    localScale = newScale
                                    localRotation = newRotation
                                    localOffset = newOffset

                                    // Usar la referencia más reciente
                                    latestOnTransformChange(localOffset, localScale, localRotation)
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



/**
 * Rota un Offset por un ángulo en radianes.
 */
private fun Offset.rotateRad(rad: Float): Offset {
    val c = kotlin.math.cos(rad)
    val s = kotlin.math.sin(rad)
    return Offset(x * c - y * s, x * s + y * c)
}











// helpers: rotación por radianes y operaciones con Offset

private operator fun Offset.times(scale: Float) = Offset(x * scale, y * scale)
private operator fun Offset.plus(other: Offset) = Offset(x + other.x, y + other.y)
private operator fun Offset.minus(other: Offset) = Offset(x - other.x, y - other.y)
















private fun Offset.rotateBy(angleRad: Float): Offset {
    val c = kotlin.math.cos(angleRad)
    val s = kotlin.math.sin(angleRad)
    return Offset(this.x * c - this.y * s, this.x * s + this.y * c)
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
                modifier = Modifier.size(46.dp)
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
    signatureRotation: Float,
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

    val rotationInDegrees = Math.toDegrees(signatureRotation.toDouble()).toFloat()

    // 2. Calcula el centro de la firma (el pivote) DESPUÉS de escalarla
    val pivotX = (signature.width / 2f) * finalSignatureScale
    val pivotY = (signature.height / 2f) * finalSignatureScale

    val matrix = android.graphics.Matrix().apply {
        // 1. Escala la firma (desde el 0,0)
        postScale(finalSignatureScale, finalSignatureScale)

        // 2. ROTA la firma escalada alrededor de su nuevo centro (pivote)
        postRotate(rotationInDegrees, pivotX, pivotY)

        // 3. Mueve la firma (ya escalada y rotada) a su posición final (top-left)
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



@Composable
private fun SignatureSubmenu(
    savedSignatures: List<ImageBitmap>,
    onCreateNew: () -> Unit,
    onSignatureSelected: (ImageBitmap) -> Unit,
    onRequestDelete: (Int) -> Unit,
    onDismiss: () -> Unit,
    bottomBarHeight: Dp
) {
    // Estado para rastrear si estamos en modo de eliminación
    var isDeleteMode by rememberSaveable { mutableStateOf(false) }

    // Wrapper para el dismiss, para resetear también el estado local
    val onDismissRequest = {
        isDeleteMode = false
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismissRequest() } }, // Usa el nuevo wrapper
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomBarHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            color = Color(0xFF2C2C2E),
            elevation = 8.dp
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- Item 1: Botón "Crear nueva" ---
                item {
                    Column(
                        modifier = Modifier
                            .size(width = 72.dp, height = 64.dp) // Tamaño fijo
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF3A3A3C))
                            .clickable {
                                if (!isDeleteMode) { // Solo funciona si NO está en modo borrado
                                    onCreateNew()
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Add, "Crear", tint = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Crear", color = Color.White, fontSize = 12.sp)
                    }
                }

                // --- Items 2...N: Las firmas guardadas ---
                itemsIndexed(savedSignatures) { index, sigBmp -> // <-- USAR itemsIndexed
                    Box(
                        modifier = Modifier
                            // REQUISITO 1: Tamaño uniforme (igual que el botón "Crear")
                            .size(width = 72.dp, height = 64.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                            // REQUISITO 2: Detectar long press y tap
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { isDeleteMode = true },
                                    onTap = {
                                        if (!isDeleteMode) {
                                            onSignatureSelected(sigBmp)
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center // Centra la imagen (ContentScale.Fit)
                    ) {
                        Image(
                            bitmap = sigBmp,
                            contentDescription = "Firma guardada",
                            modifier = Modifier
                                .fillMaxSize() // La imagen llena el Box
                                .padding(4.dp), // Padding interno
                            contentScale = ContentScale.Fit // Mantiene la relación de aspecto
                        )

                        // REQUISITO 2: Botón de eliminar
                        if (isDeleteMode) {
                            IconButton(
                                onClick = { onRequestDelete(index) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp) // Tamaño del botón
                                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Eliminar",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp) // Tamaño del ícono
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
// --- FIN DEL REEMPLAZO ---
// --- FIN NUEVO ---


// --- REEMPLAZA placeSignatureOnBitmap CON ESTA ---

/**
 * Fusiona una lista de SignatureInstances en un Bitmap base.
 * Esta función usa la posición LÓGICA (relativa) para dibujar,
 * haciéndola independiente del estado del scroll.
 */
private fun mergeSignaturesOnPage(
    base: Bitmap,
    instances: List<SignatureInstance>, // Pasa *todas* las instancias
    pageIndexToSave: Int,
    containerSize: IntSize,
    density: Density // Necesitamos la densidad para los cálculos
): Bitmap {
    // Filtra solo las firmas de la página que queremos guardar
    val instancesForThisPage = instances.filter { it.pageIndex == pageIndexToSave }
    if (instancesForThisPage.isEmpty()) {
        Log.d(TAG, "No hay firmas para la página $pageIndexToSave, devolviendo base.")
        return base // No hay nada que hacer
    }

    Log.d(TAG, "Fusionando ${instancesForThisPage.size} firmas en la página $pageIndexToSave.")

    val resultBitmap = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(resultBitmap)

    // --- Get Page Scaling Info (basado en la lógica de computePageLayout) ---
    // Esto calcula cómo se *vería* la página en el contenedor
    val pagePaddingPx = with(density) { 16.dp.toPx() } // Asumimos padding
    val viewWidth = (containerSize.width - (pagePaddingPx * 2)).coerceAtLeast(1f)
    val viewHeight = (containerSize.height - (pagePaddingPx * 2)).coerceAtLeast(1f)
    val bitmapAspectRatio = base.width.toFloat() / base.height.toFloat()
    val viewAspectRatio = viewWidth / viewHeight

    val (scaledW, scaledH) = if (bitmapAspectRatio > viewAspectRatio) {
        viewWidth to viewWidth / bitmapAspectRatio
    } else {
        viewHeight * bitmapAspectRatio to viewHeight
    }
    // El ratio para convertir de "píxeles de vista escalada" a "píxeles de bitmap original"
    val scaleRatio = base.width / scaledW
    // --- End Scaling Info ---

    // Dibuja cada firma
    instancesForThisPage.forEach { instance ->
        val sigBmp = instance.bitmap
        val sigScale = instance.scale
        val sigRot = instance.rotation // radianes
        val relOffset = instance.relativeOffset // 0f-1f

        // --- Convertir Posición Relativa (0-1f) a Coordenadas Finales del Bitmap ---

        // 1. Encontrar el centro de la firma en "píxeles de vista escalada"
        val scaledImageCenterX = relOffset.x * scaledW
        val scaledImageCenterY = relOffset.y * scaledH

        // 2. Encontrar el top-left de la firma en "píxeles de vista escalada"
        val sigDrawW = sigBmp.width * sigScale
        val sigDrawH = sigBmp.height * sigScale
        val scaledImageTopLeftX = scaledImageCenterX - (sigDrawW / 2f)
        val scaledImageTopLeftY = scaledImageCenterY - (sigDrawH / 2f)

        // 3. Convertir a "píxeles de bitmap original"
        val finalX = scaledImageTopLeftX * scaleRatio
        val finalY = scaledImageTopLeftY * scaleRatio
        val finalSignatureScale = sigScale * scaleRatio
        // --- Fin Conversión ---

        // --- Lógica de Dibujo (copiada de tu placeSignatureOnBitmap original) ---
        val paint = android.graphics.Paint().apply { isAntiAlias = true }
        val rotationInDegrees = Math.toDegrees(sigRot.toDouble()).toFloat()

        // El pivote para rotar es el centro de la firma (en su nueva escala final)
        val pivotX = (sigBmp.width / 2f) * finalSignatureScale
        val pivotY = (sigBmp.height / 2f) * finalSignatureScale

        val matrix = android.graphics.Matrix().apply {
            // 1. Escala
            postScale(finalSignatureScale, finalSignatureScale)
            // 2. Rota alrededor del pivote
            postRotate(rotationInDegrees, pivotX, pivotY)
            // 3. Mueve a la posición top-left final
            postTranslate(finalX, finalY)
        }
        canvas.drawBitmap(sigBmp.asAndroidBitmap(), matrix, paint)
        // --- Fin Lógica de Dibujo ---
    }

    return resultBitmap
}