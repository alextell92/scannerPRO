package com.example.scannerpro.signature

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Parcelable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import kotlin.math.min
import kotlin.math.roundToInt

@Parcelize
private enum class SignatureMode : Parcelable { DRAWING, PLACING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScreen(
    baseBitmaps: List<Bitmap>,
    initialPageIndex: Int,
    onSignatureComplete: (Int, Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(SignatureMode.DRAWING) }
    var strokes by rememberSaveable { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var strokeColor by remember { mutableStateOf(Color.Black) }
    var signatureBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var signatureOffset by rememberSaveable { mutableStateOf(Offset.Zero) }
    var signatureScale by rememberSaveable { mutableStateOf(1f) }

    val activity = LocalContext.current as Activity
    DisposableEffect(Unit) {
        val originalOrientation = activity.requestedOrientation
        onDispose {
            activity.requestedOrientation = originalOrientation
        }
    }

    LaunchedEffect(mode) {
        activity.requestedOrientation = if (mode == SignatureMode.DRAWING) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(mode, strokes) {
        if (mode == SignatureMode.PLACING && signatureBitmap == null && strokes.any { it.isNotEmpty() }) {
            signatureBitmap = captureSignature(strokes, strokeColor)
        }
    }

    if (mode == SignatureMode.DRAWING) {
        DrawingContent(
            strokes = strokes,
            strokeColor = strokeColor,
            onStrokesChange = { strokes = it },
            onCancel = onCancel,
            onColorChange = { strokeColor = it },
            onUndo = { strokes = strokes.dropLast(1) },
            onClear = { strokes = emptyList() },
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
                strokes = emptyList()
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
    onStrokesChange: (List<List<Offset>>) -> Unit,
    onCancel: () -> Unit,
    onColorChange: (Color) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit
) {
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
                onStrokesChange = onStrokesChange
            )
            Text(
                text = "Firme formalmente y claramente",
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        SignatureDrawingControlsVertical(
            onCancel = onCancel,
            onColorChange = onColorChange,
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
    onOffsetChange: (Offset) -> Unit,
    onScaleChange: (Float) -> Unit,
    onCancel: () -> Unit,
    onDeleteSignature: () -> Unit,
    onSignatureComplete: (Int, Bitmap) -> Unit
) {
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialPageIndex)
    val currentPageIndex by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) initialPageIndex
            else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - viewportCenter) }?.index ?: initialPageIndex
            }
        }
    }
    var isSignatureActive by rememberSaveable { mutableStateOf(false) }


    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF212121))
    ) {
        val containerSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        var isInitialScaleSet by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(signatureBitmap, containerSize) {
            if (signatureBitmap != null && !isInitialScaleSet) {
                val initialScale = (containerSize.width * 0.25f) / signatureBitmap.width
                onScaleChange(initialScale)
                isInitialScaleSet = true
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(baseBitmaps.size) { page ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(2.dp))
                        .background(Color.White, RoundedCornerShape(2.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = baseBitmaps[page].asImageBitmap(),
                        contentDescription = "Documento página ${page + 1}",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        signatureBitmap?.let { sigBmp ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(signatureOffset.x.roundToInt(), signatureOffset.y.roundToInt()) }
                    .graphicsLayer(
                        scaleX = signatureScale,
                        scaleY = signatureScale,
                    )
                    .pointerInput(isSignatureActive) {
                        if (isSignatureActive) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onOffsetChange(signatureOffset + dragAmount)
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isSignatureActive) {
                            isSignatureActive = true
                        }
                    }
            ) {
                Image(
                    bitmap = sigBmp,
                    contentDescription = "Firma",
                    modifier = if (isSignatureActive) Modifier.border(2.dp, Color(0xFF30D5C8), RoundedCornerShape(4.dp)) else Modifier
                )
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
            }
        }

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
            Text("Página ${currentPageIndex + 1}", color = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
            IconButton(onClick = {
                val finalBitmap = placeSignatureOnBitmap(
                    base = baseBitmaps[currentPageIndex],
                    signature = signatureBitmap,
                    signatureOffset = signatureOffset,
                    signatureScale = signatureScale,
                    containerSize = containerSize
                )
                onSignatureComplete(currentPageIndex, finalBitmap)
            }) {
                Icon(Icons.Default.Check, "Aplicar Firma", tint = Color(0xFF30D5C8), modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape).padding(4.dp))
            }
        }
    }
}


@Composable
private fun SignatureDrawingCanvas(
    modifier: Modifier = Modifier,
    strokes: List<List<Offset>>,
    strokeColor: Color,
    onStrokesChange: (List<List<Offset>>) -> Unit
) {
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentStroke = listOf(offset)
                    },
                    onDrag = { change, _ ->
                        currentStroke = currentStroke + change.position
                        change.consume()
                    },
                    onDragEnd = {
                        onStrokesChange(strokes + listOf(currentStroke))
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
                drawPath(path, color = strokeColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
            }
        }
        if (currentStroke.size > 1) {
            val currentPath = Path().apply {
                moveTo(currentStroke.first().x, currentStroke.first().y)
                currentStroke.subList(1, currentStroke.size).forEach { lineTo(it.x, it.y) }
            }
            drawPath(currentPath, color = strokeColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun SignatureDrawingControlsVertical(
    onCancel: () -> Unit,
    onColorChange: (Color) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit
) {
    var selectedColor by remember { mutableStateOf(Color.Black) }
    val colors = listOf(Color.Black, Color(0xFF0D47A1), Color(0xFFB71C1C))

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(Color(0xFF2C2C2E))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, "Cancelar", tint = Color.White)
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onUndo) { Icon(Icons.Default.Undo, "Deshacer", tint = Color.White) }
            IconButton(onClick = onClear) { Icon(Icons.Default.DeleteOutline, "Limpiar", tint = Color.White) }
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
}


private fun captureSignature(strokes: List<List<Offset>>, color: Color): ImageBitmap {
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
        left = minOf(left, b.left)
        top = minOf(top, b.top)
        right = maxOf(right, b.right)
        bottom = maxOf(bottom, b.bottom)
    }

    if (left == Float.POSITIVE_INFINITY) return ImageBitmap(1, 1)

    val bounds = Rect(left, top, right, bottom)
    val padding = 20f
    val strokeWidth = 5f
    val bitmapWidth = ((bounds.width + padding * 2 + strokeWidth).toInt()).coerceAtLeast(1)
    val bitmapHeight = ((bounds.height + padding * 2 + strokeWidth).toInt()).coerceAtLeast(1)

    return captureBitmap(bitmapWidth, bitmapHeight) {
        translate(-bounds.left + padding + strokeWidth / 2f, -bounds.top + padding + strokeWidth / 2f) {
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

    val containerPaddedWidth = containerSize.width - (32.dp.value) // Horizontal padding * 2
    val containerPaddedHeight = containerSize.height

    val imageScale: Float
    var imageOffsetX: Float
    var imageOffsetY: Float

    val imageAspectRatio = base.width.toFloat() / base.height.toFloat()
    val boxAspectRatio = containerPaddedWidth / containerPaddedHeight

    if (imageAspectRatio > boxAspectRatio) {
        imageScale = containerPaddedWidth / base.width.toFloat()
        val scaledHeight = base.height * imageScale
        imageOffsetX = 16.dp.value
        imageOffsetY = (containerSize.height - scaledHeight) / 2f
    } else {
        imageScale = containerPaddedHeight / base.height.toFloat()
        val scaledWidth = base.width * imageScale
        imageOffsetY = 0f
        imageOffsetX = (containerSize.width - scaledWidth) / 2f
    }

    val signatureCenterX = containerSize.width / 2f + signatureOffset.x
    val signatureCenterY = containerSize.height / 2f + signatureOffset.y

    val signatureOnImageX = signatureCenterX - (signature.width * signatureScale / 2f) - imageOffsetX
    val signatureOnImageY = signatureCenterY - (signature.height * signatureScale / 2f) - imageOffsetY

    val finalX = signatureOnImageX / imageScale
    val finalY = signatureOnImageY / imageScale
    val finalSignatureScale = signatureScale / imageScale

    val resultBitmap = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(resultBitmap)
    val paint = android.graphics.Paint()

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

