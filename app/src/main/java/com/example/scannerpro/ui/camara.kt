import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.ArrayList
import kotlin.math.abs
import kotlin.math.sqrt

// Enum para controlar qué método de pre-procesamiento se utiliza
private enum class ProcessingMethod {
    STANDARD,
    CLAHE, // Para problemas de contraste/iluminación
    MEDIAN_BLUR, // Para problemas de ruido
    MORPHOLOGICAL_CLOSE, // Para bordes rotos
    ADAPTIVE_THRESHOLD, // Para condiciones de iluminación muy variables
    SPECULAR_REFLECTION // Para reflejos de flash
}

@Composable
fun DocumentScannerScreen(
    onDocumentScanned: (Bitmap) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var finalImagePreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var validationLog by remember { mutableStateOf("Selecciona una imagen para iniciar el proceso.") }
    var isLoading by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                isLoading = true
                coroutineScope.launch(Dispatchers.Default) {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

                    // La función ahora devuelve un Bitmap puro
                    val resultBitmap = findBestSizeAndProcess(mutableBitmap) { logLine ->
                        launch(Dispatchers.Main) { validationLog = logLine }
                    }

                    // CORRECCIÓN: Mover el callback y la actualización de la UI al hilo principal
                    launch(Dispatchers.Main) {
                        // MODIFICACIÓN: Se comenta la siguiente línea para que la imagen procesada
                        // permanezca en pantalla para revisión del usuario, según lo solicitado.
                        // El callback onDocumentScanned ya no se invoca automáticamente.
                        // onDocumentScanned(resultBitmap)

                        // Mostramos una vista previa en la UI actual
                        finalImagePreview = resultBitmap.asImageBitmap()
                        isLoading = false
                    }
                }
            }
        }
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // --- BARRA SUPERIOR PERSONALIZADA ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
            Text("Escanear Documento", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(48.dp)) // Espacio para centrar el título
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            finalImagePreview = null
            validationLog = "Selecciona una imagen..."
            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) {
            Text("Cargar y Procesar Imagen")
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (finalImagePreview != null) {
                Image(
                    bitmap = finalImagePreview!!,
                    contentDescription = "Vista Previa del Contorno Detectado",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("Esperando imagen...", textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = validationLog,
            modifier = Modifier.fillMaxWidth().height(120.dp).border(1.dp, Color.LightGray).padding(8.dp).verticalScroll(rememberScrollState()),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

// Data class para el resultado de la detección inicial
private data class InitialDetection(
    val bestContour: MatOfPoint?,
    val bestWidth: Double,
    val finalMethod: ProcessingMethod
)

private fun findBestSizeAndProcess(sourceBitmap: Bitmap, logUpdater: (String) -> Unit): Bitmap {
    val initialDetection = findBestContour(sourceBitmap, logUpdater)
    return drawFinalContour(sourceBitmap, initialDetection)
}


private fun findBestContour(sourceBitmap: Bitmap, logUpdater: (String) -> Unit): InitialDetection {
    val sizesToTest = listOf(240.0, 320.0, 480.0, 640.0, 800.0, 960.0, 1080.0, 1200.0)
    var bestWidth = 0.0
    var maxQuads = -1
    val logBuilder = StringBuilder()
    var finalMethod = ProcessingMethod.STANDARD
    var bestContour: MatOfPoint? = null

    val phases = listOf(
        ProcessingMethod.STANDARD, ProcessingMethod.CLAHE, ProcessingMethod.MEDIAN_BLUR,
        ProcessingMethod.MORPHOLOGICAL_CLOSE, ProcessingMethod.ADAPTIVE_THRESHOLD,
        ProcessingMethod.SPECULAR_REFLECTION
    )

    for ((index, method) in phases.withIndex()) {
        if (maxQuads > 0) break
        logBuilder.append("\n--- Iniciando Fase ${index + 1}: ${method.name} ---\n")
        logUpdater(logBuilder.toString())
        finalMethod = method
        maxQuads = -1
        sizesToTest.forEach { width ->
            val (quads, contours) = countQuadsAtWidth(sourceBitmap, width, method)
            val logLine = "-> Prueba ${method.name} a ${width.toInt()}px: $quads cuadriláteros.\n"
            logBuilder.append(logLine)
            logUpdater(logBuilder.toString())
            if (quads >= maxQuads) {
                maxQuads = quads
                bestWidth = width
                bestContour = contours.maxByOrNull { Imgproc.contourArea(it) }
            }
        }
    }

    if (bestWidth == 0.0 || maxQuads <= 0) {
        logBuilder.append("\nNo se encontraron cuadriláteros.")
        bestContour = null
    } else {
        logBuilder.append("\nMétodo exitoso: $finalMethod. Mejor tamaño: ${bestWidth.toInt()}px.")
    }

    logUpdater(logBuilder.toString())

    return InitialDetection(bestContour, bestWidth, finalMethod)
}

private fun countQuadsAtWidth(sourceBitmap: Bitmap, testWidth: Double, method: ProcessingMethod): Pair<Int, List<MatOfPoint>> {
    val originalMat = Mat()
    Utils.bitmapToMat(sourceBitmap, originalMat)
    val processedMat = Mat()
    val ratio = testWidth / originalMat.width()
    Imgproc.resize(originalMat, processedMat, Size(testWidth, originalMat.height() * ratio))
    Imgproc.cvtColor(processedMat, processedMat, Imgproc.COLOR_RGBA2GRAY)

    val edges = Mat()

    when (method) {
        ProcessingMethod.STANDARD -> Imgproc.GaussianBlur(processedMat, processedMat, Size(5.0, 5.0), 0.0)
        ProcessingMethod.CLAHE -> {
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(processedMat, processedMat)
            Imgproc.GaussianBlur(processedMat, processedMat, Size(5.0, 5.0), 0.0)
        }
        ProcessingMethod.MEDIAN_BLUR -> Imgproc.medianBlur(processedMat, processedMat, 5)
        ProcessingMethod.MORPHOLOGICAL_CLOSE, ProcessingMethod.ADAPTIVE_THRESHOLD -> Imgproc.GaussianBlur(processedMat, processedMat, Size(5.0, 5.0), 0.0)
        ProcessingMethod.SPECULAR_REFLECTION -> {
            val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
            clahe.apply(processedMat, processedMat)
            Imgproc.bilateralFilter(processedMat.clone(), processedMat, 9, 75.0, 75.0)
        }
    }

    if (method == ProcessingMethod.ADAPTIVE_THRESHOLD) {
        Imgproc.adaptiveThreshold(processedMat, edges, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 15, 4.0)
    } else {
        Imgproc.Canny(processedMat, edges, 50.0, 150.0)
    }

    if (method == ProcessingMethod.MORPHOLOGICAL_CLOSE) {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
    }

    val contours = ArrayList<MatOfPoint>()
    Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

    val goodQuads = contours.filter {
        if (Imgproc.contourArea(it) < testWidth * testWidth * 0.02) return@filter false
        val contour2f = MatOfPoint2f(*it.toArray())
        val approxCurve = MatOfPoint2f()
        Imgproc.approxPolyDP(contour2f, approxCurve, 0.02 * Imgproc.arcLength(contour2f, true), true)
        approxCurve.total() == 4L && isGoodQuadrilateral(approxCurve)
    }

    return Pair(goodQuads.size, goodQuads)
}

private fun drawFinalContour(sourceBitmap: Bitmap, detection: InitialDetection): Bitmap {
    val highResMat = Mat()
    Utils.bitmapToMat(sourceBitmap, highResMat)

    if (detection.bestContour != null) {
        val scaleRatio = sourceBitmap.width / detection.bestWidth
        val highResQuads = mutableListOf<MatOfPoint>()
        val scaledContour = MatOfPoint()
        Core.multiply(detection.bestContour, Scalar(scaleRatio, scaleRatio), scaledContour)
        highResQuads.add(scaledContour)
        Imgproc.drawContours(highResMat, highResQuads, -1, Scalar(0.0, 255.0, 0.0, 255.0), 5)
    }

    return highResMat.toBitmap()
}

private fun isGoodQuadrilateral(contour: MatOfPoint2f): Boolean {
    val points = contour.toArray()
    if (points.size != 4) return false

    val matOfPoint = MatOfPoint(*points)
    if (!Imgproc.isContourConvex(matOfPoint)) {
        return false
    }

    val rect = Imgproc.boundingRect(matOfPoint)
    val aspectRatio = rect.width.toDouble() / rect.height.toDouble()
    val validRatioRange = 0.5..2.0
    if (aspectRatio !in validRatioRange && (1/aspectRatio) !in validRatioRange) {
        return false
    }

    val maxCosine = 0.3
    for (i in 2..4) {
        val pt1 = points[i % 4]; val pt2 = points[i - 2]; val pt0 = points[i - 1]
        val dx1 = pt1.x - pt0.x; val dy1 = pt1.y - pt0.y
        val dx2 = pt2.x - pt0.x; val dy2 = pt2.y - pt0.y
        val dotProduct = dx1 * dx2 + dy1 * dy2
        val magnitude1 = sqrt(dx1 * dx1 + dy1 * dy1)
        val magnitude2 = sqrt(dx2 * dx2 + dy2 * dy2)
        if (magnitude1 == 0.0 || magnitude2 == 0.0) return false
        val cosine = abs(dotProduct / (magnitude1 * magnitude2))
        if (cosine > maxCosine) return false
    }
    return true
}

// Extension function to convert Mat to Bitmap easily
private fun Mat.toBitmap(): Bitmap {
    val bmp = Bitmap.createBitmap(this.cols(), this.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(this, bmp)
    return bmp
}
