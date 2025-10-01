package com.example.scannerpro.MarkUp

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
 public fun MarkupScreen(bitmap: Bitmap, onMarkupComplete: (Bitmap) -> Unit) {
    var paths by remember { mutableStateOf<List<Pair<Path, Stroke>>>(emptyList()) }
    var currentPath by remember { mutableStateOf(Path()) }
    var currentPathColor by remember { mutableStateOf(Color.Red) }
    var currentPathStrokeWidth by remember { mutableStateOf(5.dp) }
    val density = LocalDensity.current

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentPath.moveTo(offset.x, offset.y)
                    },
                    onDrag = { change, _ ->
                        currentPath.lineTo(change.position.x, change.position.y)
                        paths = paths + (currentPath to Stroke(
                            width = density.run { currentPathStrokeWidth.toPx() },
                            cap = StrokeCap.Round
                        ))
                    },
                    onDragEnd = {
                        currentPath = Path()
                    }
                )
            }
        ) {
            drawImage(
                image = imageBitmap,
                topLeft = Offset.Zero,
                alpha = 1f
            )

            paths.forEach { (path, stroke) ->
                drawPath(
                    path,
                    color = currentPathColor,
                    style = stroke
                )
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            MarkupToolbar(
                onColorChanged = { currentPathColor = it },
                onStrokeWidthChanged = { currentPathStrokeWidth = it },
                onUndo = { paths = paths.dropLast(1) },
                onConfirm = {
                    val newBitmap = captureBitmap(
                        width = imageBitmap.width,
                        height = imageBitmap.height
                    ) {
                        drawImage(
                            image = imageBitmap,
                            topLeft = Offset.Zero
                        )
                        paths.forEach { (path, stroke) ->
                            drawPath(path, color = currentPathColor, style = stroke)
                        }
                    }
                    onMarkupComplete(newBitmap)
                }
            )
        }
    }
}

// --- Pantalla de Dibujo y Herramientas (sin cambios) ---
@Composable
private fun MarkupToolbar(onColorChanged: (Color) -> Unit, onStrokeWidthChanged: (Dp) -> Unit, onUndo: () -> Unit, onConfirm: () -> Unit) {
    BottomAppBar(containerColor = Color(0xFF2C2C2E), contentColor = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onUndo) { Icon(Icons.Default.Undo, "Deshacer") }
            ColorPicker(onColorSelected = onColorChanged)
            StrokeWidthPicker(onStrokeWidthSelected = onStrokeWidthChanged)
            IconButton(onClick = onConfirm) { Icon(Icons.Default.Check, "Confirmar Markup") }
        }
    }
}
@Composable
private fun ColorPicker(onColorSelected: (Color) -> Unit) {
    val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.White, Color.Black)
    var selectedColor by remember { mutableStateOf(colors.first()) }

    Row {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable {
                        selectedColor = color
                        onColorSelected(color)
                    }
                    .border(
                        width = if (selectedColor == color) 2.dp else 0.dp,
                        color = Color.White,
                        shape = CircleShape
                    )
            )
        }
    }
}
@Composable
private fun StrokeWidthPicker(onStrokeWidthSelected: (Dp) -> Unit) {
    var strokeWidth by remember { mutableStateOf(5.dp) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${strokeWidth.value.toInt()}pt", color = Color.White, fontSize = 12.sp)
        Slider(
            value = strokeWidth.value,
            onValueChange = {
                strokeWidth = it.dp
                onStrokeWidthSelected(strokeWidth)
            },
            valueRange = 2f..20f,
            modifier = Modifier.width(100.dp)
        )
    }
}


// --- Utilidades (sin cambios) ---
private fun captureBitmap(width: Int, height: Int, content: DrawScope.() -> Unit): Bitmap {
    val imageBitmap = ImageBitmap(width, height)
    val canvas = androidx.compose.ui.graphics.Canvas(imageBitmap)
    val drawScope = CanvasDrawScope()
    drawScope.draw(
        density = androidx.compose.ui.unit.Density(1f),
        layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
        canvas = canvas,
        size = androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat()),
        block = content
    )
    return imageBitmap.asAndroidBitmap()
}
