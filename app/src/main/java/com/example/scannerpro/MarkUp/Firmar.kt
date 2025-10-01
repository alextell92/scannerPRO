package com.example.scannerpro.signature

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private enum class SignatureMode { DRAWING, PLACING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScreen(
    baseBitmap: Bitmap,
    onSignatureComplete: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    // paths como lista mutable en el estado para mantener los trazos
    var paths by remember { mutableStateOf(mutableListOf<Path>()) }
    var mode by remember { mutableStateOf(SignatureMode.DRAWING) }
    var strokeColor by remember { mutableStateOf(Color.Black) }
    var signatureBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var signatureOffset by remember { mutableStateOf(Offset.Zero) }
    var signatureScale by remember { mutableStateOf(1f) }
    val baseImageBitmap = remember(baseBitmap) { baseBitmap.asImageBitmap() }

    Scaffold(
        containerColor = Color(0xFF1C1C1E),
        topBar = {
            TopAppBar(
                title = { Text(if (mode == SignatureMode.DRAWING) "Crea tu Firma" else "Coloca tu Firma", color = Color.White) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancelar", tint = Color.White) } },
                actions = {
                    if (mode == SignatureMode.PLACING) {
                        IconButton(onClick = {
                            val finalBitmap = placeSignatureOnBitmap(baseBitmap, signatureBitmap, signatureOffset, signatureScale)
                            onSignatureComplete(finalBitmap)
                        }) {
                            Icon(Icons.Default.Check, "Aplicar Firma", tint = Color(0xFF30D5C8))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            if (mode == SignatureMode.DRAWING) {
                SignatureDrawingControls(
                    onColorChange = { strokeColor = it },
                    onUndo = {
                        if (paths.isNotEmpty()) {
                            paths.removeLast()
                            // reasignar una nueva instancia para forzar recomposición
                            paths = paths.toMutableList()
                        }
                    },
                    onClear = {
                        paths.clear()
                        paths = mutableListOf()
                    },
                    onConfirm = {
                        if (paths.any { !it.isEmpty }) {
                            signatureBitmap = captureSignature(paths.toList(), strokeColor)
                            mode = SignatureMode.PLACING
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when (mode) {
                SignatureMode.DRAWING -> SignatureDrawingCanvas(
                    paths = paths,
                    strokeColor = strokeColor,
                    onPathsChange = { newList ->
                        // reasignar nueva instancia para estado
                        paths = newList.toMutableList()
                    }
                )
                SignatureMode.PLACING -> {
                    Image(
                        bitmap = baseImageBitmap,
                        contentDescription = "Documento",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(alpha = 0.5f), // Documento atenuado
                        contentScale = ContentScale.Fit
                    )
                    signatureBitmap?.let { sigBmp ->
                        Image(
                            bitmap = sigBmp,
                            contentDescription = "Firma",
                            modifier = Modifier
                                .graphicsLayer(
                                    scaleX = signatureScale,
                                    scaleY = signatureScale,
                                    translationX = signatureOffset.x,
                                    translationY = signatureOffset.y
                                )
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        signatureScale *= zoom
                                        signatureOffset += pan
                                    }
                                }
                                .border(1.dp, Color.White.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignatureDrawingCanvas(
    paths: MutableList<Path>,
    strokeColor: Color,
    onPathsChange: (MutableList<Path>) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Firma aquí", color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                // Crear nueva Path para este trazo
                                val newPath = Path().apply { moveTo(offset.x, offset.y) }
                                paths.add(newPath)
                                // notificar (reasignará en caller)
                                onPathsChange(paths)
                            },
                            onDrag = { change, _ ->
                                // Añadir línea al último path
                                if (paths.isNotEmpty()) {
                                    val lastIndex = paths.lastIndex
                                    paths[lastIndex].lineTo(change.position.x, change.position.y)
                                    onPathsChange(paths)
                                }
                                // consumir cambios del pointer (si tu versión requiere, usa consumePositionChange)
                                try {
                                    change.consumeAllChanges()
                                } catch (_: Throwable) {
                                    // ignore si el método no existe en la versión concreta
                                }
                            },
                            onDragEnd = {
                                // Nada extra: la path ya está en la lista
                            },
                            onDragCancel = {
                                // Si la última path quedó sin dibujar, eliminarla
                                if (paths.isNotEmpty() && paths.last().isEmpty) {
                                    paths.removeLast()
                                    onPathsChange(paths)
                                }
                            }
                        )
                    }
            ) {
                // Dibujar todas las paths actuales
                paths.forEach { path ->
                    drawPath(path, color = strokeColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

@Composable
private fun SignatureDrawingControls(
    onColorChange: (Color) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit
) {
    var selectedColor by remember { mutableStateOf(Color.Black) }
    val colors = listOf(Color.Black, Color(0xFF0D47A1), Color(0xFFB71C1C)) // Negro, Azul oscuro, Rojo oscuro

    BottomAppBar(containerColor = Color(0xFF2C2C2E)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable {
                                selectedColor = color
                                onColorChange(color)
                            }
                            .border(
                                width = if (selectedColor == color) 2.dp else 0.dp,
                                color = Color.White,
                                shape = CircleShape
                            )
                    )
                }
            }

            Row {
                IconButton(onClick = onUndo) { Icon(Icons.Default.Undo, "Deshacer", tint = Color.White) }
                IconButton(onClick = onClear) { Icon(Icons.Default.DeleteOutline, "Limpiar", tint = Color.White) }
            }

            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30D5C8))
            ) {
                Icon(Icons.Default.Done, contentDescription = "Confirmar")
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text("OK")
            }
        }
    }
}

private fun captureSignature(paths: List<Path>, color: Color): ImageBitmap {
    if (paths.isEmpty()) return ImageBitmap(1, 1)

    // Calcular bounding box manualmente (min/max)
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY

    paths.forEach { path ->
        if (!path.isEmpty) {
            val b = path.getBounds()
            left = minOf(left, b.left)
            top = minOf(top, b.top)
            right = maxOf(right, b.right)
            bottom = maxOf(bottom, b.bottom)
        }
    }

    // Si no hubo paths válidos, devolver bitmap mínimo
    if (left == Float.POSITIVE_INFINITY) return ImageBitmap(1, 1)

    val bounds = Rect(left, top, right, bottom)

    val padding = 20f
    val strokeWidth = 5f
    val bitmapWidth = ((bounds.width + padding * 2 + strokeWidth).toInt()).coerceAtLeast(1)
    val bitmapHeight = ((bounds.height + padding * 2 + strokeWidth).toInt()).coerceAtLeast(1)

    return captureBitmap(bitmapWidth, bitmapHeight) {
        // mover el dibujo para que quede dentro del bitmap con padding
        translate(-bounds.left + padding - strokeWidth / 2f, -bounds.top + padding - strokeWidth / 2f) {
            paths.forEach { path ->
                drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }
        }
    }
}

private fun placeSignatureOnBitmap(
    base: Bitmap,
    signature: ImageBitmap?,
    offset: Offset,
    scale: Float
): Bitmap {
    if (signature == null) return base

    val resultBitmap = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(resultBitmap)
    val paint = android.graphics.Paint()

    val matrix = android.graphics.Matrix()
    matrix.postScale(scale, scale)
    matrix.postTranslate(offset.x, offset.y)

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
