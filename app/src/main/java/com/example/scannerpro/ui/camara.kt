package com.example.scannerpro.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
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
import kotlin.math.abs

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
                    val corners = detectCorners(mutableBitmap)
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
                        val corners = detectCorners(bitmap)
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
private fun detectCorners(bitmap: Bitmap): List<Point> {
    val originalMat = Mat()
    Utils.bitmapToMat(bitmap, originalMat)
    val grayMat = Mat(); Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_BGR2GRAY)
    val blurredMat = Mat(); Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0)
    // You can tune these Canny thresholds for better edge detection
    val cannyMat = Mat(); Imgproc.Canny(blurredMat, cannyMat, 75.0, 200.0)

    val contours = ArrayList<MatOfPoint>()
    Imgproc.findContours(cannyMat, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

    val largestContour = contours.maxByOrNull { contour: MatOfPoint -> Imgproc.contourArea(contour) }?.let { contour ->
        val approx = MatOfPoint2f()
        val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
        Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * peri, true)
        if (approx.rows() == 4) approx else null
    }

    if (largestContour != null) {
        return largestContour.toArray().toList()
    }

    // --- DEBUG LOG 4 ---
    Log.e("ScannerDebug", "OpenCV did NOT find a 4-sided contour. Using default corners.")
    val margin = bitmap.width * 0.1
    return listOf(
        Point(margin, margin),
        Point(bitmap.width - margin, margin),
        Point(bitmap.width - margin, bitmap.height - margin),
        Point(margin, bitmap.height - margin)
    )
}

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
