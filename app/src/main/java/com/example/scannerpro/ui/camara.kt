package com.example.scannerpro.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.scannerpro.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.Imgproc.threshold
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

// Data class to hold the image and its detected corners
private data class ImageWithCorners(val bitmap: Bitmap, val corners: List<Point>)




// Top-level value to initialize OpenCV safely once
private val openCvInitialized = run {
    if (OpenCVLoader.initDebug()) {
        Log.d("OpenCV", "OpenCV initialized successfully.")
        true
    } else {
        Log.e("OpenCV", "OpenCV initialization failed!")
        false
    }
}

/**
 * Main Composable that orchestrates the entire scanning flow.
 * It manages permissions and navigates between Camera, Crop, and Result views.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DocumentScannerScreen(
    modifier: Modifier = Modifier,
    onDocumentScanned: (Bitmap) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    var imageToCrop by remember { mutableStateOf<ImageWithCorners?>(null) }
    var finalBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                val originalBitmap = uriToBitmap(context, it)
                val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                if (openCvInitialized) {
                    val corners = detectCorners(mutableBitmap,context)
                    // --- DEBUG LOG 1 ---
                    Log.d("ScannerDebug", "Corners detected (Gallery): $corners")
                    imageToCrop = ImageWithCorners(mutableBitmap, corners)
                }
            }
        }
    )

    // This `when` block acts as a state machine to display the correct view
    when {
        finalBitmap != null -> {
            ResultView(
                bitmap = finalBitmap!!,
                onAccept = { onDocumentScanned(it) },
                onRetry = { finalBitmap = null; imageToCrop = null } // Reset flow
            )
        }
        imageToCrop != null -> {
            CropView(
                imageWithCorners = imageToCrop!!,
                onCrop = { croppedBitmap -> finalBitmap = croppedBitmap },
                onRetry = { imageToCrop = null } // Go back to camera
            )
        }
        cameraPermissionState.status.isGranted -> {
            CameraView(
                onImageCaptured = { bitmap ->
                    if (openCvInitialized) {
                        val corners = detectCorners(bitmap,context)
                        // --- DEBUG LOG 2 ---
                        Log.d("ScannerDebug", "Corners detected (Camera): $corners")
                        imageToCrop = ImageWithCorners(bitmap, corners)
                    }
                },
                onError = { Log.e("CameraView", "Image capture error: ", it) },
                onGalleryClick = { galleryLauncher.launch("image/*") },
                onCloseClick = onClose
            )
        }
        else -> {
            PermissionRequestView(onRequestPermission = { cameraPermissionState.launchPermissionRequest() })
        }
    }
}

/**
 * The interactive crop view. Displays the image with draggable handles
 * on the detected corners.
 */
@Composable
private fun CropView(
    imageWithCorners: ImageWithCorners,
    onCrop: (Bitmap) -> Unit,
    onRetry: () -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    var cornerOffsets by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var draggedCornerIndex by remember { mutableStateOf<Int?>(null) }
    val handleRadius = with(density) { 16.dp.toPx() }

    fun pointToOffset(point: Point): Offset {
        if (canvasSize.width == 0 || canvasSize.height == 0) return Offset.Zero
        val scaleX = canvasSize.width.toFloat() / imageWithCorners.bitmap.width
        val scaleY = canvasSize.height.toFloat() / imageWithCorners.bitmap.height
        return Offset((point.x * scaleX).toFloat(), (point.y * scaleY).toFloat())
    }

    fun offsetToPoint(offset: Offset): Point {
        if (canvasSize.width == 0 || canvasSize.height == 0) return Point(0.0, 0.0)
        val scaleX = imageWithCorners.bitmap.width.toFloat() / canvasSize.width
        val scaleY = imageWithCorners.bitmap.height.toFloat() / canvasSize.height
        return Point((offset.x * scaleX).toDouble(), (offset.y * scaleY).toDouble())
    }

    // This LaunchedEffect is the key to the auto-detection. It runs whenever the
    // image or the canvas size changes, ensuring the corners are mapped correctly.
    LaunchedEffect(imageWithCorners, canvasSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            // --- DEBUG LOG 3 ---
            Log.d("ScannerDebug", "CropView recalculating offsets. Canvas: $canvasSize")
            cornerOffsets = imageWithCorners.corners.map { pointToOffset(it) }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = imageWithCorners.bitmap.asImageBitmap(),
            contentDescription = "Imagen a recortar",
            modifier = Modifier.fillMaxSize().onSizeChanged { canvasSize = it }
        )

        Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { startOffset ->
                    // Find the corner handle closest to the drag start position
                    draggedCornerIndex = cornerOffsets
                        .map { (it - startOffset).getDistanceSquared() }
                        .withIndex()
                        .minByOrNull { it.value }
                        ?.takeIf { it.value < (handleRadius * 2).let { r -> r * r } }
                        ?.index
                },
                onDrag = { change, dragAmount ->
                    draggedCornerIndex?.let { index ->
                        // Update the position of the dragged corner
                        cornerOffsets = cornerOffsets.toMutableList().apply { set(index, get(index) + dragAmount) }
                    }
                    change.consume()
                },
                onDragEnd = { draggedCornerIndex = null }
            )
        }) {
            // Draw only if we have 4 corners
            if (cornerOffsets.size == 4) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cornerOffsets[0].x, cornerOffsets[0].y)
                    lineTo(cornerOffsets[1].x, cornerOffsets[1].y)
                    lineTo(cornerOffsets[2].x, cornerOffsets[2].y)
                    lineTo(cornerOffsets[3].x, cornerOffsets[3].y)
                    close()
                }
                drawPath(path, Color.White.copy(alpha = 0.5f))

                cornerOffsets.forEachIndexed { index, offset ->
                    drawCircle(
                        color = if (draggedCornerIndex == index) Color.Green else Color.White,
                        center = offset,
                        radius = handleRadius
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Button(onClick = onRetry) { Text("Reintentar") }
            Button(onClick = {
                if (cornerOffsets.isNotEmpty()) {
                    val finalCorners = cornerOffsets.map { offsetToPoint(it) }
                    val croppedBitmap = warpPerspective(imageWithCorners.bitmap, finalCorners)
                    onCrop(croppedBitmap)
                }
            }) { Text("Recortar") }
        }
    }
}


/**
 * Uses OpenCV to detect the corners of the largest 4-sided contour in the bitmap.
 */
private fun detectCorners(bitmap: Bitmap, context: Context): List<Point> {
    val originalMat = Mat()
    Utils.bitmapToMat(bitmap, originalMat)
    val imageWidth = originalMat.width().toDouble()
    val imageHeight = originalMat.height().toDouble()

    // --- PRE-PROCESAMIENTO ---
    val grayMat = Mat()
    Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_BGR2GRAY)
    val blurredMat = Mat()
    Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0)
    val cannyMat = Mat()
    Imgproc.Canny(blurredMat, cannyMat, 50.0, 150.0)
    // --- FIN ---

    // --- DETECCIÓN DE LÍNEAS (MÉTODO ÚNICO) ---
    val lines = Mat()
    Imgproc.HoughLinesP(cannyMat, lines, 1.0, Math.PI / 180, 80, imageWidth / 4, 20.0)

    if (lines.rows() > 10) { // Necesitamos un número razonable de líneas para promediar
        val horizontalLines = mutableListOf<DoubleArray>()
        val verticalLines = mutableListOf<DoubleArray>()

        for (i in 0 until lines.rows()) {
            val line = lines.get(i, 0)
            val p1 = Point(line[0], line[1])
            val p2 = Point(line[2], line[3])
            val angle = Math.toDegrees(atan2(p2.y - p1.y, p2.x - p1.x))

            if (abs(angle) < 45 || abs(angle - 180) < 45) {
                horizontalLines.add(line)
            } else if (abs(abs(angle) - 90) < 45) {
                verticalLines.add(line)
            }
        }

        // --- CLUSTERING Y PROMEDIADO DE LÍNEAS ---
        if (horizontalLines.size >= 2 && verticalLines.size >= 2) {
            // Cluster "Top" y "Bottom"
            val topCluster = horizontalLines.filter { (it[1] + it[3]) / 2 < imageHeight / 2 }
            val bottomCluster = horizontalLines.filter { (it[1] + it[3]) / 2 > imageHeight / 2 }
            // Cluster "Left" y "Right"
            val leftCluster = verticalLines.filter { (it[0] + it[2]) / 2 < imageWidth / 2 }
            val rightCluster = verticalLines.filter { (it[0] + it[2]) / 2 > imageWidth / 2 }

            if (topCluster.isNotEmpty() && bottomCluster.isNotEmpty() && leftCluster.isNotEmpty() && rightCluster.isNotEmpty()) {
                // Calcula la línea promedio para cada cluster
                val avgTop = topCluster.flatMap { listOf(Point(it[0], it[1]), Point(it[2], it[3])) }.let { points ->
                    val avgY = points.map { it.y }.average()
                    doubleArrayOf(0.0, avgY, imageWidth, avgY)
                }
                val avgBottom = bottomCluster.flatMap { listOf(Point(it[0], it[1]), Point(it[2], it[3])) }.let { points ->
                    val avgY = points.map { it.y }.average()
                    doubleArrayOf(0.0, avgY, imageWidth, avgY)
                }
                val avgLeft = leftCluster.flatMap { listOf(Point(it[0], it[1]), Point(it[2], it[3])) }.let { points ->
                    val avgX = points.map { it.x }.average()
                    doubleArrayOf(avgX, 0.0, avgX, imageHeight)
                }
                val avgRight = rightCluster.flatMap { listOf(Point(it[0], it[1]), Point(it[2], it[3])) }.let { points ->
                    val avgX = points.map { it.x }.average()
                    doubleArrayOf(avgX, 0.0, avgX, imageHeight)
                }

                // Calcula las intersecciones de las líneas promedio
                val tl = computeIntersection(Point(avgTop[0], avgTop[1]), Point(avgTop[2], avgTop[3]), Point(avgLeft[0], avgLeft[1]), Point(avgLeft[2], avgLeft[3]))
                val tr = computeIntersection(Point(avgTop[0], avgTop[1]), Point(avgTop[2], avgTop[3]), Point(avgRight[0], avgRight[1]), Point(avgRight[2], avgRight[3]))
                val bl = computeIntersection(Point(avgBottom[0], avgBottom[1]), Point(avgBottom[2], avgBottom[3]), Point(avgLeft[0], avgLeft[1]), Point(avgLeft[2], avgLeft[3]))
                val br = computeIntersection(Point(avgBottom[0], avgBottom[1]), Point(avgBottom[2], avgBottom[3]), Point(avgRight[0], avgRight[1]), Point(avgRight[2], avgRight[3]))

                val corners = listOf(tl, tr, bl, br)
                if (corners.all { it.x >= 0 && it.y >= 0 }) {
                    Log.d("ScannerDebug", "¡ÉXITO DEFINITIVO! Detección por Promedio de Líneas.")
                    Toast.makeText(context, "Detección por Promedio", Toast.LENGTH_SHORT).show()
                    return sortPointsClockwise(corners.toTypedArray())
                }
            }
        }
    }

    // --- FALLBACK FINAL ---
    Log.e("ScannerDebug", "El método de promedio de líneas falló. Usando esquinas por defecto.")
    Toast.makeText(context, "No se pudo detectar, ajusta manualmente", Toast.LENGTH_LONG).show()
    val margin = bitmap.width * 0.1
    return listOf(
        Point(margin, margin),
        Point(bitmap.width - margin, margin),
        Point(bitmap.width - margin, bitmap.height - margin),
        Point(margin, bitmap.height - margin)
    )
}

// Asegúrate de que tus funciones de ayuda 'computeIntersection' y 'sortPointsClockwise' sigan en el archivo.
// Asegúrate que tu findCornersWithHoughLines y otras funciones de ayuda sigan en el archivo


// **IMPORTANTE**: Actualizar la firma de findCornersWithHoughLines
private fun findCornersWithHoughLines(processedMat: Mat, threshold: Int, minLineLength: Double, maxLineGap: Double): List<Point>? {
    val lines = Mat()
    Imgproc.HoughLinesP(processedMat, lines, 1.0, Math.PI / 180, threshold, minLineLength, maxLineGap)

    if (lines.rows() < 4) return null

    val horizontalLines = mutableListOf<DoubleArray>()
    val verticalLines = mutableListOf<DoubleArray>()

    for (i in 0 until lines.rows()) {
        val line = lines.get(i, 0)
        val p1 = Point(line[0], line[1])
        val p2 = Point(line[2], line[3])
        val angle = Math.toDegrees(atan2(p2.y - p1.y, p2.x - p1.x))

        if (abs(angle) < 45 || abs(angle - 180) < 45) {
            horizontalLines.add(line)
        } else if (abs(abs(angle) - 90) < 45) {
            verticalLines.add(line)
        }
    }

    if (horizontalLines.size < 2 || verticalLines.size < 2) return null

    val top = horizontalLines.minByOrNull { (it[1] + it[3]) / 2 } ?: return null
    val bottom = horizontalLines.maxByOrNull { (it[1] + it[3]) / 2 } ?: return null
    val left = verticalLines.minByOrNull { (it[0] + it[2]) / 2 } ?: return null
    val right = verticalLines.maxByOrNull { (it[0] + it[2]) / 2 } ?: return null

    val tl = computeIntersection(Point(top[0], top[1]), Point(top[2], top[3]), Point(left[0], left[1]), Point(left[2], left[3]))
    val tr = computeIntersection(Point(top[0], top[1]), Point(top[2], top[3]), Point(right[0], right[1]), Point(right[2], right[3]))
    val bl = computeIntersection(Point(bottom[0], bottom[1]), Point(bottom[2], bottom[3]), Point(left[0], left[1]), Point(left[2], left[3]))
    val br = computeIntersection(Point(bottom[0], bottom[1]), Point(bottom[2], bottom[3]), Point(right[0], right[1]), Point(right[2], right[3]))

    val corners = listOf(tl, tr, bl, br)

    // Validamos que todas las esquinas se hayan encontrado
    if (corners.any { it.x < 0 || it.y < 0 }) return null

    return sortPointsClockwise(corners.toTypedArray())
}
// Asegúrate de que calculateAngle y computeIntersection estén correctos
// ... (resto de tus funciones auxiliares) ...


// También actualiza la firma de findCornersWithHoughLines para aceptar el umbral
/**
 * Applies a perspective transformation to the bitmap based on the provided corner points.
 */
private fun warpPerspective(bitmap: Bitmap, corners: List<Point>): Bitmap {
    val originalMat = Mat()
    Utils.bitmapToMat(bitmap, originalMat)

    // Sort corners: tl, tr, br, bl
    val sortedCorners = corners.sortedWith(compareBy({ p: Point -> p.y }, { p: Point -> p.x }))
        .let {
            val top = it.take(2).sortedBy { p: Point -> p.x }
            val bottom = it.drop(2).sortedByDescending { p: Point -> p.x }
            listOf(top[0], top[1], bottom[0], bottom[1])
        }

    val (tl, tr, br, bl) = sortedCorners

    val widthA = Math.hypot(br.x - bl.x, br.y - bl.y)
    val widthB = Math.hypot(tr.x - tl.x, tr.y - tl.y)
    val maxWidth = maxOf(widthA, widthB)

    val heightA = Math.hypot(tr.x - br.x, tr.y - br.y)
    val heightB = Math.hypot(tl.x - bl.x, tl.y - bl.y)
    val maxHeight = maxOf(heightA, heightB)

    val srcPoints = MatOfPoint2f().apply { fromList(sortedCorners) }
    val dstPoints = MatOfPoint2f(
        Point(0.0, 0.0),
        Point(maxWidth - 1, 0.0),
        Point(maxWidth - 1, maxHeight - 1),
        Point(0.0, maxHeight - 1)
    )

    val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
    val dstMat = Mat()
    Imgproc.warpPerspective(originalMat, dstMat, transform, Size(maxWidth, maxHeight))

    val resultBitmap = Bitmap.createBitmap(dstMat.cols(), dstMat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(dstMat, resultBitmap)
    return resultBitmap
}

// region Boilerplate Composables (Permission, Camera, Result, etc.)

@Composable
private fun ResultView(bitmap: Bitmap, onAccept: (Bitmap) -> Unit, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Documento Escaneado", modifier = Modifier.fillMaxSize())
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Button(onClick = onRetry) { Text("Escanear de nuevo") }
            Button(onClick = { onAccept(bitmap) }) { Text("Aceptar") }
        }
    }
}

@Composable
private fun CameraView(
    onImageCaptured: (Bitmap) -> Unit,
    onError: (ImageCaptureException) -> Unit,
    onGalleryClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                    } catch (exc: Exception) {
                        Log.e("CameraView", "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(onClick = onCloseClick, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
        }

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onGalleryClick) {
                Icon(
                    painter = painterResource(id = R.drawable.galeria), // Reemplaza con tu icono
                    contentDescription = "Galería",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(
                onClick = {
                    imageCapture?.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                onImageCaptured(image.toBitmap())
                                image.close()
                            }
                            override fun onError(exception: ImageCaptureException) { onError(exception) }
                        }
                    )
                },
                modifier = Modifier.size(72.dp)
            ) {
                Icon(Icons.Filled.Camera, contentDescription = "Capturar", tint = Color.White, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun PermissionRequestView(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Se necesita permiso para usar la cámara.")
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRequestPermission) {
            Text("Otorgar Permiso")
        }
    }
}

private fun uriToBitmap(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
}



fun matToBitmap(mat: Mat): Bitmap {
    val bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(mat, bmp)
    return bmp
}



/**
 * Guarda un Bitmap en la galería del dispositivo.
 *
 * @param context El contexto de la aplicación.
 * @param bitmap El bitmap que se va a guardar.
 * @param displayName El nombre que tendrá el archivo (ej. "debug_image.jpg").
 */
fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String) {
    // Define dónde y cómo se guardará la imagen.
    val imageCollection =
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        // Organiza las imágenes en una carpeta específica dentro de "Pictures"
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScannerDebug")
    }

    var imageUri: Uri? = null
    try {
        // Inserta la nueva imagen y obtiene su URI
        imageUri = context.contentResolver.insert(imageCollection, contentValues)
        imageUri?.let { uri ->
            // Abre un stream para escribir los datos del bitmap en la URI
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            outputStream?.use { // 'use' cierra el stream automáticamente
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            // Notifica al usuario que la imagen se guardó
            Toast.makeText(context, "Imagen de depuración guardada", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error al guardar imagen", Toast.LENGTH_SHORT).show()
    }
}



private fun findCornersWithHoughLines(cannyMat: Mat,threshold: Int): List<Point>? {
    val lines = Mat()
    // Ejecuta la Transformada de Hough para detectar líneas
    Imgproc.HoughLinesP(cannyMat, lines, 1.0, Math.PI / 180, threshold, cannyMat.width() / 3.0, 15.0)

    if (lines.rows() < 4) return null // No hay suficientes líneas para formar un rectángulo

    val horizontalLines = mutableListOf<DoubleArray>()
    val verticalLines = mutableListOf<DoubleArray>()

    // Separa las líneas en horizontales y verticales
    for (i in 0 until lines.rows()) {
        val line = lines.get(i, 0)
        val p1 = Point(line[0], line[1])
        val p2 = Point(line[2], line[3])
        val angle = atan2(p2.y - p1.y, p2.x - p1.x) * 180 / Math.PI
        if (abs(angle) < 45) { // Horizontales
            horizontalLines.add(line)
        } else { // Verticales
            verticalLines.add(line)
        }
    }

    if (horizontalLines.size < 2 || verticalLines.size < 2) return null

    // Encuentra las 4 líneas de los bordes (top, bottom, left, right)
    val top = horizontalLines.minByOrNull { it[1] + it[3] }!!
    val bottom = horizontalLines.maxByOrNull { it[1] + it[3] }!!
    val left = verticalLines.minByOrNull { it[0] + it[2] }!!
    val right = verticalLines.maxByOrNull { it[0] + it[2] }!!

    // Calcula las 4 esquinas donde se intersectan estas líneas
    val tl = computeIntersection(Point(top[0], top[1]), Point(top[2], top[3]), Point(left[0], left[1]), Point(left[2], left[3]))
    val tr = computeIntersection(Point(top[0], top[1]), Point(top[2], top[3]), Point(right[0], right[1]), Point(right[2], right[3]))
    val bl = computeIntersection(Point(bottom[0], bottom[1]), Point(bottom[2], bottom[3]), Point(left[0], left[1]), Point(left[2], left[3]))
    val br = computeIntersection(Point(bottom[0], bottom[1]), Point(bottom[2], bottom[3]), Point(right[0], right[1]), Point(right[2], right[3]))

    // Si todas las esquinas son válidas, devuelve la lista
    return if (listOf(tl, tr, bl, br).all { it.x != -1.0 }) {
        listOf(tl, tr, br, bl)
    } else {
        null
    }
}


// Calcula el punto de intersección entre dos líneas representadas por 4 puntos
private fun computeIntersection(p1: Point, p2: Point, p3: Point, p4: Point): Point {
    val d = (p1.x - p2.x) * (p3.y - p4.y) - (p1.y - p2.y) * (p3.x - p4.x)
    if (d == 0.0) return Point(-1.0, -1.0) // Líneas paralelas

    val t = ((p1.x - p3.x) * (p3.y - p4.y) - (p1.y - p3.y) * (p3.x - p4.x)) / d
    val u = -((p1.x - p2.x) * (p1.y - p3.y) - (p1.y - p2.y) * (p1.x - p3.x)) / d

    return if (t > 0 && t < 1 && u > 0) { // Solo si la intersección está en el segmento de la segunda línea
        Point(p1.x + t * (p2.x - p1.x), p1.y + t * (p2.y - p1.y))
    } else {
        Point(-1.0, -1.0)
    }
}


private fun calculateAngle(pt1: Point, pt2: Point, pt0: Point): Double {
    val dx1 = pt1.x - pt0.x
    val dy1 = pt1.y - pt0.y
    val dx2 = pt2.x - pt0.x
    val dy2 = pt2.y - pt0.y
    val dotProduct = dx1 * dx2 + dy1 * dy2
    val mag1 = sqrt(dx1 * dx1 + dy1 * dy1)
    val mag2 = sqrt(dx2 * dx2 + dy2 * dy2)
    // Evita la división por cero
    if (mag1 * mag2 == 0.0) return 0.0
    val angleRad = acos(dotProduct / (mag1 * mag2))
    return angleRad * 180.0 / Math.PI
}


private fun sortPointsClockwise(points: Array<Point>): List<Point> {
    // Ordena por la suma de coordenadas (y+x).
    // El punto con la menor suma es el superior-izquierdo.
    // El punto con la mayor suma es el inferior-derecho.
    points.sortBy { it.x + it.y }
    val tl = points[0]
    val br = points[3]

    // Ordena por la diferencia de coordenadas (y-x).
    // El punto con la menor diferencia es el superior-derecho.
    // El punto con la mayor diferencia es el inferior-izquierdo.
    points.sortBy { it.y - it.x }
    val tr = points[0]
    val bl = points[3]

    return listOf(tl, tr, br, bl)
}

//@Composable
//fun CameraScreen() {
//    val context = LocalContext.current
//    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
//    remember { Executors.newSingleThreadExecutor() }
//
//    AndroidView(
//        factory = { ctx ->
//            val previewView = PreviewView(ctx)
//            cameraProviderFuture.addListener({
//                val cameraProvider = cameraProviderFuture.get()
//                val preview = androidx.camera.core.Preview.Builder().build().also {
//                    it.surfaceProvider = previewView.surfaceProvider
//                }
//                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//                cameraProvider.unbindAll()
//                cameraProvider.bindToLifecycle(
//                    ctx as androidx.lifecycle.LifecycleOwner,
//                    cameraSelector,
//                    preview
//                )
//            }, ContextCompat.getMainExecutor(ctx))
//            previewView
//        },
//        modifier = Modifier
//    )
//}




