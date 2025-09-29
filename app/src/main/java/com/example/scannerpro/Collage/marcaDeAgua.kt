package com.example.scannerpro.Collage

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin


// Dummy data classes for compilation
data class WatermarkData(
    val text: String = "Confidencial",
    val color: Color = Color.Black,
    val size: Float = 24f, // in sp
    val opacity: Float = 0.5f,
    val isPattern: Boolean = false,
    val rotation: Float = -55f,
    val offset: Offset = Offset.Zero
)



data class CollagePageData(
    val id: Long = System.nanoTime(),
    val items: List<CollageItemData>
)


@Composable
fun WatermarkCanvas(
    watermark: WatermarkData,
    isDraggable: Boolean,
    onDrag: (Offset) -> Unit, // recibe nueva posición absoluta del centro en px (solo al terminar drag)
    pageWidthPx: Float,
    pageHeightPx: Float
) {
    val density = LocalDensity.current

    // Paint: recordar solo cuando cambie la configuración visual
    val paint = remember(watermark.text, watermark.color, watermark.size, watermark.opacity, density) {
        android.graphics.Paint().apply {
            color = watermark.color.copy(alpha = watermark.opacity).toArgb()
            textSize = with(density) { watermark.size.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    // Métricas precalculadas (para evitar cálculos por frame)
    val metrics = remember(watermark.text, paint, watermark.rotation) {
        val ascent = paint.ascent() // negativo
        val descent = paint.descent()
        val textHeight = descent - ascent // positivo
        val textBaseline = - (ascent + descent) / 2f
        val textWidth = paint.measureText(watermark.text)
        val padding = 24f
        val rectWidth = textWidth + padding
        val rectHeight = textHeight + padding
        val halfW = rectWidth / 2f
        val halfH = rectHeight / 2f
        val strokeWidthPx = with(density) { 2.dp.toPx() }
        val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        mapOf(
            "ascent" to ascent,
            "descent" to descent,
            "textHeight" to textHeight,
            "textBaseline" to textBaseline,
            "textWidth" to textWidth,
            "rectWidth" to rectWidth,
            "rectHeight" to rectHeight,
            "halfW" to halfW,
            "halfH" to halfH,
            "strokeWidthPx" to strokeWidthPx,
            "dashEffect" to dashPathEffect
        )
    }

    val dashEffect = metrics["dashEffect"] as PathEffect
    val strokeWidthPx = metrics["strokeWidthPx"] as Float

    // Estado local para arrastre. Actualizamos solo localmente durante el drag
    var isDragging by remember { mutableStateOf(false) }
    var dragPointerOffset by remember { mutableStateOf(Offset.Zero) }

    // currentOffset: posición que se usa para dibujar la marca / patrón en pantalla.
    var currentOffset by remember { mutableStateOf(Offset.Zero) }

    val canvasSizeReady = remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val canvasWidthPx = with(density) { maxWidth.toPx() }
        val canvasHeightPx = with(density) { maxHeight.toPx() }
        val canvasCenter = Offset(canvasWidthPx / 2f, canvasHeightPx / 2f)

        // Inicializar currentOffset cuando cambie watermark.offset o cuando la vista se mida
        LaunchedEffect(watermark.offset, canvasWidthPx, canvasHeightPx) {
            val initial = if (watermark.offset == Offset.Zero) canvasCenter else watermark.offset
            currentOffset = initial
            canvasSizeReady.value = true
        }

        val interactionModifier = if (isDraggable) {
            Modifier.pointerInput(watermark, isDraggable) {
                detectDragGestures(
                    onDragStart = { start ->
                        // Use currentOffset (valor local estable)
                        val finalOffset = currentOffset

                        // Métricas extraídas
                        val halfW = metrics["halfW"] as Float
                        val halfH = metrics["halfH"] as Float

                        // Punto relativo al centro (finalOffset)
                        val dx = start.x - finalOffset.x
                        val dy = start.y - finalOffset.y

                        // Des-rotar el punto (rotación inversa)
                        val angleRad = -watermark.rotation * (Math.PI / 180.0)
                        val cosA = cos(angleRad)
                        val sinA = sin(angleRad)
                        val localX = (cosA * dx - sinA * dy).toFloat()
                        val localY = (sinA * dx + cosA * dy).toFloat()

                        val margin = 8f
                        if (localX in (-halfW - margin)..(halfW + margin) &&
                            localY in (-halfH - margin)..(halfH + margin)
                        ) {
                            isDragging = true
                            // Guardamos offset entre puntero y centro para evitar saltos
                            dragPointerOffset = start - finalOffset
                        } else {
                            isDragging = false
                        }
                    },
                    onDrag = { change, _ ->
                        if (!isDragging) return@detectDragGestures
                        change.consume()
                        val pointerPos = change.position
                        // Mover localmente sin notificar al padre (evita recomposiciones externas)
                        currentOffset = pointerPos - dragPointerOffset
                    },
                    onDragEnd = {
                        if (isDragging) {
                            // Al terminar, notificamos la nueva posición absoluta al padre
                            onDrag(currentOffset)
                        }
                        isDragging = false
                    },
                    onDragCancel = {
                        if (isDragging) {
                            onDrag(currentOffset)
                        }
                        isDragging = false
                    }
                )
            }
        } else Modifier

        // Canvas usando currentOffset para dibujar
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(interactionModifier)
        ) {
            if (!canvasSizeReady.value) return@Canvas

            drawIntoCanvas { canvas ->
                // --------------------------
                // 1) Dibujar patrón o no
                // --------------------------
                if (watermark.isPattern) {
                    // Dibujar patrón centrado en currentOffset (ahora garantizando que exista una celda en (0,0))
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.translate(currentOffset.x, currentOffset.y)
                    canvas.nativeCanvas.rotate(watermark.rotation)

                    val textWidth = metrics["textWidth"] as Float
                    val textHeight = paint.textSize
                    val spacingX = textWidth * 1.5f
                    val spacingY = textHeight * 3f
                    val textBase = metrics["textBaseline"] as Float

                    // AREA CENTRAL (no dibujar aquí para evitar duplicado con la instancia central)
                    val halfW = metrics["halfW"] as Float
                    val halfH = metrics["halfH"] as Float
                    val skipPadding = 8f // ajustable si quieres más/menos hueco alrededor de la instancia central
                    val centerSkipW = halfW + skipPadding
                    val centerSkipH = halfH + skipPadding

                    // Calcula rangos a cubrir (misma lógica que antes)
                    val coverHalfWidth = pageWidthPx * 1.5f
                    val coverHalfHeight = pageHeightPx * 1.5f
                    val endX = pageWidthPx * 2.5f
                    val endY = pageHeightPx * 2.5f

                    // Asegurarnos que startX/startY sean múltiplos de spacing para que exista una celda en 0
                    val leftCells = kotlin.math.ceil(coverHalfWidth / spacingX).toInt()
                    val topCells = kotlin.math.ceil(coverHalfHeight / spacingY).toInt()
                    val startX = -leftCells * spacingX
                    val startY = -topCells * spacingY

                    var y = startY
                    while (y < endY) {
                        var x = startX
                        while (x < endX) {
                            // Omitir celdas que caerían dentro del rectángulo central (para no duplicar la instancia)
                            if (!(x >= -centerSkipW && x <= centerSkipW && y >= -centerSkipH && y <= centerSkipH)) {
                                canvas.nativeCanvas.drawText(watermark.text, x, y + textBase, paint)
                            }
                            x += spacingX
                        }
                        y += spacingY
                    }

                    canvas.nativeCanvas.restore()
                }


                // --------------------------
                // 2) Dibujar instancia central (siempre encima, tanto para patrón como para único)
                // --------------------------
                canvas.nativeCanvas.save()
                canvas.nativeCanvas.translate(currentOffset.x, currentOffset.y)
                canvas.nativeCanvas.rotate(watermark.rotation)

                val textBaseline = metrics["textBaseline"] as Float
                // Texto central (la "muestra" que se arrastra)
                canvas.nativeCanvas.drawText(watermark.text, 0f, textBaseline, paint)
                canvas.nativeCanvas.restore()

                // --------------------------
                // 3) Dibujar caja verde rotada (hit area) alrededor de la instancia central
                // --------------------------
                if (isDraggable) {
                    val rectWidth = metrics["rectWidth"] as Float
                    val rectHeight = metrics["rectHeight"] as Float
                    val halfW = metrics["halfW"] as Float
                    val halfH = metrics["halfH"] as Float

                    val rectTopLeft = Offset(currentOffset.x - halfW, currentOffset.y - halfH)

                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.rotate(watermark.rotation, currentOffset.x, currentOffset.y)

                    // dibujar con stroke punteado (usamos drawRect de Compose para aplicar pathEffect)
                    drawRect(
                        color = Color.Green,
                        topLeft = rectTopLeft,
                        size = Size(rectWidth, rectHeight),
                        style = Stroke(width = strokeWidthPx, pathEffect = dashEffect)
                    )

                    canvas.nativeCanvas.restore()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkEditorScreen(
    initialWatermark: WatermarkData?,
    previewPage: CollagePageData?,
    onDismiss: () -> Unit,
    onApply: (WatermarkData) -> Unit,
    onRemove: () -> Unit
) {
    var watermark by remember { mutableStateOf(initialWatermark ?: WatermarkData()) }
    var showTextDialog by remember { mutableStateOf(initialWatermark == null) }
    // Este estado controla si el menú de edición de estilo está visible o no.
    var showStyleEditor by remember { mutableStateOf(false) }

    if (showTextDialog) {
        WatermarkTextDialog(
            initialText = watermark.text,
            onConfirm = {
                watermark = watermark.copy(text = it)
                showTextDialog = false
            },
            onDismiss = {
                if (initialWatermark == null) {
                    onDismiss()
                }
                showTextDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Añadir Marca de Agua", color = Color.White) },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar", tint = Color.White) } },
                actions = {
                    if (!showStyleEditor) {
                        IconButton(onClick = { onApply(watermark) }) { Icon(Icons.Default.Check, "Aplicar", tint = Color.White) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2C2C2E))
            )
        },
        bottomBar = {
            Column(modifier = Modifier.background(Color(0xFF2C2C2E))) {
                if (showStyleEditor) {
                    WatermarkStyleEditor(
                        watermark = watermark,
                        onUpdate = { watermark = it }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MainActionItem(text = "Editar Texto", onClick = { showTextDialog = true }) {
                        Icon(Icons.Default.Edit, "Editar Texto", tint = Color.White)
                    }
                    MainActionItem(text = "Ajustar Estilo", onClick = { showStyleEditor = !showStyleEditor }) {
                        Icon(Icons.Default.Style, "Ajustar Estilo", tint = Color.White)
                    }
                    MainActionItem(text = "Eliminar", onClick = { onRemove(); onDismiss() }) {
                        Icon(Icons.Default.Delete, "Eliminar", tint = Color.White)
                    }
                }
            }
        },
        containerColor = Color.DarkGray
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.41f)) {

                if (previewPage != null) {
                    // CollagePage dentro de su Box normal
                    Box(modifier = Modifier.fillMaxSize()) {
                        CollagePage(
                            pageIndex = 0, pageData = previewPage, draggingItemId = null, isDragging = false,
                            dragPreviewWidthPx = 0f, dragPreviewHeightPx = 0f, watermarkToDraw = watermark,
                            isWatermarkDraggable = true,onWatermarkDrag = { newCenter ->
                                watermark = watermark.copy(offset = newCenter)
                            },
                            onItemRemoved = {}, onStartDrag = { _, _, _, _, _, _, _, _ -> }, onDragMove = {}, onDrop = {},
                            onPositioned = { _, _ -> }, onInitialLayoutComputed = { _, _ -> },
                            currentTargetPageIndex = null, dragPreviewOffsetInTarget = null,
                            isInteractive = false,
                            // --- INICIO DE LA CORRECCIÓN ---

                        )
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    ) {
                        WatermarkCanvas(
                            watermark = watermark,
                            isDraggable = true,
                            onDrag = { newCenter ->
                                // 'newCenter' está en px (coordenadas del canvas)
                                watermark = watermark.copy(offset = newCenter)
                            },
                            pageWidthPx = LocalDensity.current.run { 300.dp.toPx() },
                            pageHeightPx = LocalDensity.current.run { 420.dp.toPx() }
                        )
                    }
                }

                // OVERLAY: cuando showStyleEditor == true, renderizamos una capa que
                // intercepta clics en la zona de contenido y cierra el editor.
                if (showStyleEditor) {
                    // la capa está _dentro_ del content del Scaffold, por lo que el BottomBar (donde está el editor)
                    // seguirá estando por encima y no será cerrada si se hace clic en ella.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                // Cierra el editor al hacer clic en cualquier parte del contenido
                                showStyleEditor = false
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun WatermarkTextDialog(initialText: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Añadir Marca de Agua", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                TextField(value = text, onValueChange = { text = it }, label = { Text("Texto") })
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(text) }) { Text("OK") }
                }
            }
        }
    }
}


@Composable
private fun WatermarkStyleEditor(watermark: WatermarkData, onUpdate: (WatermarkData) -> Unit) {
    val colors = listOf(Color.White, Color.Black, Color.Red, Color.Green, Color.Blue, Color.Yellow)
    Box(modifier = Modifier.height(240.dp)) {
        Column(
            Modifier
                .background(Color(0xE61C1C1E))
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Color", color = Color.White)
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                colors.forEach { color ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                2.dp,
                                if (watermark.color == color) Color.Cyan else Color.Transparent,
                                CircleShape
                            )
                            .clickable { onUpdate(watermark.copy(color = color)) })
                }
            }
            Text("Tamaño: ${watermark.size.roundToInt()}sp", color = Color.White)
            Slider(value = watermark.size, onValueChange = { onUpdate(watermark.copy(size = it)) }, valueRange = 12f..96f)
            Text("Opacidad: ${(watermark.opacity * 100).roundToInt()}%", color = Color.White)
            Slider(value = watermark.opacity, onValueChange = { onUpdate(watermark.copy(opacity = it)) }, valueRange = 0.1f..1f)
            Text("Rotación: ${watermark.rotation.roundToInt()}°", color = Color.White)
            Slider(value = watermark.rotation, onValueChange = { onUpdate(watermark.copy(rotation = it)) }, valueRange = -180f..180f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Patrón", color = Color.White); Spacer(Modifier.width(8.dp))
                Switch(checked = watermark.isPattern, onCheckedChange = {
                    val newRotation = if (it) -55f else 0f
                    onUpdate(watermark.copy(isPattern = it, offset = Offset.Zero, rotation = newRotation))
                })
            }
        }
    }
}
