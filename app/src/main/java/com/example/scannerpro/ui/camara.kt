
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
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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
                    val corners = detectCorners(mutableBitmap, context)
                    Log.d("ScannerDebug", "Corners detected (Gallery): $corners")
                    imageToCrop = ImageWithCorners(mutableBitmap, corners)
                }
            }
        }
    )

    when {
        finalBitmap != null -> {
            ResultView(
                bitmap = finalBitmap!!,
                onAccept = { onDocumentScanned(it) },
                onRetry = { finalBitmap = null; imageToCrop = null }
            )
        }
        imageToCrop != null -> {
            CropView(
                imageWithCorners = imageToCrop!!,
                onCrop = { croppedBitmap -> finalBitmap = croppedBitmap },
                onRetry = { imageToCrop = null }
            )
        }
        cameraPermissionState.status.isGranted -> {
            CameraView(
                onImageCaptured = { bitmap ->
                    if (openCvInitialized) {
                        val corners = detectCorners(bitmap, context)
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

    LaunchedEffect(imageWithCorners, canvasSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
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
                    draggedCornerIndex = cornerOffsets
                        .map { (it - startOffset).getDistanceSquared() }
                        .withIndex()
                        .minByOrNull { it.value }
                        ?.takeIf { it.value < (handleRadius * 2).let { r -> r * r } }
                        ?.index
                },
                onDrag = { change, dragAmount ->
                    draggedCornerIndex?.let { index ->
                        cornerOffsets = cornerOffsets.toMutableList().apply { set(index, get(index) + dragAmount) }
                    }
                    change.consume()
                },
                onDragEnd = { draggedCornerIndex = null }
            )
        }) {
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
// Helper function for distance
private fun distance(p1: Point, p2: Point): Double {
    return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
}

private fun detectCorners(bitmap: Bitmap, context: Context): List<Point> {
    // Downscaling for efficiency
    val downscaledBitmap = downscaleBitmap(bitmap, 1000)
    val scaleX = bitmap.width.toDouble() / downscaledBitmap.width
    val scaleY = bitmap.height.toDouble() / downscaledBitmap.height

    val originalMat = Mat()
    Utils.bitmapToMat(downscaledBitmap, originalMat)

    // --- PRE-PROCESAMIENTO (Improved for textured backgrounds) ---
    val hsvMat = Mat()
    Imgproc.cvtColor(originalMat, hsvMat, Imgproc.COLOR_BGR2HSV)

    // Color mask to isolate card (gray-blue tones: hue 180-250, low saturation, medium value)
    val lowerBound = Scalar(180.0 / 2, 10.0, 50.0)  // Hue in OpenCV is 0-180
    val upperBound = Scalar(250.0 / 2, 100.0, 200.0)
    val mask = Mat()
    Core.inRange(hsvMat, lowerBound, upperBound, mask)

    // Apply mask to grayscale
    val grayMat = Mat()
    Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_BGR2GRAY)
    Core.bitwise_and(grayMat, grayMat, grayMat, mask)

    // Histogram equalization for contrast
    Imgproc.equalizeHist(grayMat, grayMat)

    // Bilateral filter instead of Gaussian for edge-preserving blur
    val blurredMat = Mat()
    Imgproc.bilateralFilter(grayMat, blurredMat, 5, 75.0, 75.0)  // d=5, sigmaColor=75, sigmaSpace=75

    // Adaptive Canny
    val median = Mat()
    Imgproc.medianBlur(grayMat, median, 5)
    val medianVal = median.get(median.rows() / 2, median.cols() / 2)[0]
    val lowThreshold = max(20.0, 0.66 * medianVal)
    val highThreshold = min(255.0, 1.33 * medianVal)

    val cannyMat = Mat()
    Imgproc.Canny(blurredMat, cannyMat, lowThreshold, highThreshold)

    // Larger kernel for dilation/erosion to connect fragmented edges from texture
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))  // Increased to 7x7
    val dilatedMat = Mat()
    Imgproc.dilate(cannyMat, dilatedMat, kernel)
    Imgproc.erode(dilatedMat, dilatedMat, kernel)

    // Save intermediates for debug
    saveBitmapToGallery(context, matToBitmap(mask), "mask_debug.jpg")
    saveBitmapToGallery(context, matToBitmap(cannyMat), "canny_debug.jpg")
    saveBitmapToGallery(context, matToBitmap(dilatedMat), "dilated_debug.jpg")

    // --- INTENTO 1: CONTORNOS ---
    val contours = ArrayList<MatOfPoint>()
    val hierarchy = Mat()
    Imgproc.findContours(dilatedMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

    Log.d("ScannerDebug", "Número de contornos encontrados: ${contours.size}")

    val bestCandidate = contours
        .mapNotNull { contour ->
            val approx = MatOfPoint2f()
            val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * peri, true)
            if (approx.rows() == 4) approx else null
        }
        .maxByOrNull { Imgproc.contourArea(it) }

    if (bestCandidate != null) {
        val points = sortPointsClockwise(bestCandidate.toArray())

        val (tl, tr, br, bl) = points

        val topWidth = distance(tl, tr)
        val bottomWidth = distance(bl, br)
        val leftHeight = distance(tl, bl)
        val rightHeight = distance(tr, br)

        val aspectRatio = maxOf(topWidth, bottomWidth) / maxOf(leftHeight, rightHeight)
        val aspectRatioIsGood = aspectRatio > 0.5 && aspectRatio < 2.5

        val widthSymmetry = abs(topWidth - bottomWidth) / maxOf(topWidth, bottomWidth) < 0.30
        val heightSymmetry = abs(leftHeight - rightHeight) / maxOf(leftHeight, rightHeight) < 0.30

        val angles = listOf(
            calculateAngle(tr, bl, tl),
            calculateAngle(tl, br, tr),
            calculateAngle(tr, bl, br),
            calculateAngle(tl, br, bl)
        )
        val anglesAreGood = angles.all { abs(it - 90.0) < 30.0 }

        if (aspectRatioIsGood && widthSymmetry && heightSymmetry && anglesAreGood) {
            Log.d("ScannerDebug", "Éxito con Contornos.")
            Toast.makeText(context, "Plan A: Detección Válida", Toast.LENGTH_SHORT).show()
            return points.map { Point(it.x * scaleX, it.y * scaleY) }
        }
    }

    // --- INTENTO 2: HOUGH LINES (Added validation) ---
    Log.w("ScannerDebug", "Plan A falló. Activando Plan B: Hough Lines.")
    val houghCorners = findCornersWithHoughLines(cannyMat)
    if (houghCorners != null) {
        val points = sortPointsClockwise(houghCorners.toTypedArray())
        val (tl, tr, br, bl) = points

        val topWidth = distance(tl, tr)
        val bottomWidth = distance(bl, br)
        val leftHeight = distance(tl, bl)
        val rightHeight = distance(tr, br)

        val aspectRatio = maxOf(topWidth, bottomWidth) / maxOf(leftHeight, rightHeight)
        val aspectRatioIsGood = aspectRatio > 0.5 && aspectRatio < 2.5

        val widthSymmetry = abs(topWidth - bottomWidth) / maxOf(topWidth, bottomWidth) < 0.30
        val heightSymmetry = abs(leftHeight - rightHeight) / maxOf(leftHeight, rightHeight) < 0.30

        val angles = listOf(
            calculateAngle(tr, bl, tl),
            calculateAngle(tl, br, tr),
            calculateAngle(tr, bl, br),
            calculateAngle(tl, br, bl)
        )
        val anglesAreGood = angles.all { abs(it - 90.0) < 30.0 }

        if (aspectRatioIsGood && widthSymmetry && heightSymmetry && anglesAreGood) {
            Log.d("ScannerDebug", "Éxito con Hough Lines.")
            Toast.makeText(context, "Plan B: Detección por Líneas", Toast.LENGTH_SHORT).show()
            return points.map { Point(it.x * scaleX, it.y * scaleY) }
        }
    }

    // --- FALLBACK ---
    Log.w("ScannerDebug", "Plan B falló. Activando Fallback.")
    val fallbackCorners = fallbackDetectCorners(downscaledBitmap, context)
    if (fallbackCorners != null) {
        return fallbackCorners.map { Point(it.x * scaleX, it.y * scaleY) }
    }

    return getDefaultCorners(bitmap)
}

/**
 * Fallback detection with different parameters.
 */
private fun fallbackDetectCorners(bitmap: Bitmap, context: Context): List<Point>? {
    val originalMat = Mat()
    Utils.bitmapToMat(bitmap, originalMat)

    val grayMat = Mat()
    Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_BGR2GRAY)

    val blurredMat = Mat()
    Imgproc.bilateralFilter(grayMat, blurredMat, 5, 75.0, 75.0)

    val cannyMat = Mat()
    Imgproc.Canny(blurredMat, cannyMat, 30.0, 100.0)

    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
    val dilatedMat = Mat()
    Imgproc.dilate(cannyMat, dilatedMat, kernel)
    Imgproc.erode(dilatedMat, dilatedMat, kernel)

    return findBestContour(dilatedMat, bitmap)
}

/**
 * Optimized Hough Lines detection.
 */
private fun findCornersWithHoughLines(cannyMat: Mat): List<Point>? {
    val lines = Mat()
    Imgproc.HoughLinesP(cannyMat, lines, 1.0, Math.PI / 180, 50, cannyMat.width() / 4.0, 20.0)

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

    val top = horizontalLines.minByOrNull { (it[1] + it[3]) / 2 }!!
    val bottom = horizontalLines.maxByOrNull { (it[1] + it[3]) / 2 }!!
    val left = verticalLines.minByOrNull { (it[0] + it[2]) / 2 }!!
    val right = verticalLines.maxByOrNull { (it[0] + it[2]) / 2 }!!

    val tl = computeIntersection(Point(top[0], top[1]), Point(top[2], top[3]), Point(left[0], left[1]), Point(left[2], left[3]))
    val tr = computeIntersection(Point(top[0], top[1]), Point(top[2], top[3]), Point(right[0], right[1]), Point(right[2], right[3]))
    val bl = computeIntersection(Point(bottom[0], bottom[1]), Point(bottom[2], bottom[3]), Point(left[0], left[1]), Point(left[2], left[3]))
    val br = computeIntersection(Point(bottom[0], bottom[1]), Point(bottom[2], bottom[3]), Point(right[0], right[1]), Point(right[2], right[3]))

    val corners = listOf(tl, tr, bl, br)
    return if (corners.all { it.x != -1.0 }) corners else null
}

/**
 * Default corners with 10% margin.
 */
private fun getDefaultCorners(bitmap: Bitmap): List<Point> {
    val margin = bitmap.width * 0.1
    return listOf(
        Point(margin, margin),
        Point(bitmap.width - margin, margin),
        Point(bitmap.width - margin, bitmap.height - margin),
        Point(margin, bitmap.height - margin)
    )
}

// Helper for best contour (stricter area filter)
private fun findBestContour(processedMat: Mat, originalBitmap: Bitmap): List<Point>? {
    val contours = ArrayList<MatOfPoint>()
    Imgproc.findContours(processedMat, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

    val bestCandidate = contours
        .mapNotNull { contour ->
            val approx = MatOfPoint2f()
            val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * peri, true)
            if (approx.rows() == 4) approx else null
        }
        .filter { Imgproc.contourArea(it) > (originalBitmap.width * originalBitmap.height / 5.0) }  // Stricter: >20% area
        .maxByOrNull { Imgproc.contourArea(it) }

    return bestCandidate?.let { sortPointsClockwise(it.toArray()) }
}

private fun downscaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val originalWidth = bitmap.width
    val originalHeight = bitmap.height
    val largerDimension = maxOf(originalWidth, originalHeight)

    if (largerDimension <= maxDimension) return bitmap

    val scaleFactor = maxDimension.toFloat() / largerDimension
    val newWidth = (originalWidth * scaleFactor).toInt()
    val newHeight = (originalHeight * scaleFactor).toInt()

    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

/**
 * Applies perspective transformation.
 */
private fun warpPerspective(bitmap: Bitmap, corners: List<Point>): Bitmap {
    val originalMat = Mat()
    Utils.bitmapToMat(bitmap, originalMat)

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

private fun sortPointsClockwise(points: Array<Point>): List<Point> {
    points.sortBy { it.x + it.y }
    val tl = points[0]
    points.sortBy { it.y - it.x }
    val tr = points[0]
    val bl = points[3]
    val br = points[points.size - 1]  // Adjusted for safety

    return listOf(tl, tr, br, bl)
}

// Boilerplate Composables remain the same...

// (Omito el resto del código boilerplate como ResultView, CameraView, etc., ya que no cambiaron. Copia de la versión anterior si es necesario.)

private fun calculateAngle(pt1: Point, pt2: Point, pt0: Point): Double {
    val dx1 = pt1.x - pt0.x
    val dy1 = pt1.y - pt0.y
    val dx2 = pt2.x - pt0.x
    val dy2 = pt2.y - pt0.y
    val dotProduct = dx1 * dx2 + dy1 * dy2
    val mag1 = sqrt(dx1 * dx1 + dy1 * dy1)
    val mag2 = sqrt(dx2 * dx2 + dy2 * dy2)
    if (mag1 * mag2 == 0.0) return 0.0
    val angleRad = acos(dotProduct / (mag1 * mag2))
    return angleRad * 180.0 / Math.PI
}

private fun computeIntersection(p1: Point, p2: Point, p3: Point, p4: Point): Point {
    val d = (p1.x - p2.x) * (p3.y - p4.y) - (p1.y - p2.y) * (p3.x - p4.x)
    if (d == 0.0) return Point(-1.0, -1.0)

    val t = ((p1.x - p3.x) * (p3.y - p4.y) - (p1.y - p3.y) * (p3.x - p4.x)) / d
    val u = -((p1.x - p2.x) * (p1.y - p3.y) - (p1.y - p2.y) * (p1.x - p3.x)) / d

    return if (t > 0 && t < 1 && u > 0) {
        Point(p1.x + t * (p2.x - p1.x), p1.y + t * (p2.y - p1.y))
    } else {
        Point(-1.0, -1.0)
    }
}

// Funciones de utilería como uriToBitmap, matToBitmap, saveBitmapToGallery permanecen iguales.
