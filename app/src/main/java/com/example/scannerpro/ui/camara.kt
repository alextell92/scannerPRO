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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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

// Data class para contener ambos resultados del procesamiento
data class ProcessingResult(
    val contourView: ImageBitmap,
    val processedView: ImageBitmap // Renombrado para ser más genérico
)

// Enum para controlar qué método de pre-procesamiento se utiliza
private enum class ProcessingMethod {
    STANDARD,
    CLAHE, // Para problemas de contraste/iluminación
    MEDIAN_BLUR, // Para problemas de ruido
    MORPHOLOGICAL_CLOSE, // Para bordes rotos
    ADAPTIVE_THRESHOLD, // Para condiciones de iluminación muy variables
    SPECULAR_REFLECTION, // Para reflejos de flash
    ADAPTIVE_MORPH // Combinación final para fondos complejos
}

@Composable
fun SimpleCannyDetectorScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Estados de la UI
    var processingResult by remember { mutableStateOf<ProcessingResult?>(null) }
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
                    val result = findBestSizeAndProcess(mutableBitmap) { logLine ->
                        launch(Dispatchers.Main) { validationLog = logLine }
                    }
                    launch(Dispatchers.Main) {
                        processingResult = result
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
        Button(onClick = {
            processingResult = null
            validationLog = "Selecciona una imagen..."
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }) {
            Text("Cargar y Procesar Automáticamente")
        }

        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (processingResult != null) {
            // Fila para mostrar ambas imágenes
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Contorno Detectado", fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Image(
                        bitmap = processingResult!!.contourView,
                        contentDescription = "Contorno en Verde",
                        modifier = Modifier.fillMaxSize().border(1.dp, Color.Gray),
                        contentScale = ContentScale.Fit
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Vista de Procesamiento", fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Image(
                        bitmap = processingResult!!.processedView,
                        contentDescription = "Vista de Procesamiento",
                        modifier = Modifier.fillMaxSize().border(1.dp, Color.Gray),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
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

private fun findBestSizeAndProcess(sourceBitmap: Bitmap, logUpdater: (String) -> Unit): ProcessingResult {
    val sizesToTest = listOf(240.0, 320.0, 480.0, 640.0, 800.0, 960.0, 1080.0, 1200.0)
    var bestWidth = 0.0
    var maxQuads = -1
    val logBuilder = StringBuilder()
    var finalMethod = ProcessingMethod.STANDARD

    // Pipeline de Fases
    val phases = listOf(
        ProcessingMethod.STANDARD,
        ProcessingMethod.CLAHE,
        ProcessingMethod.MEDIAN_BLUR,
        ProcessingMethod.MORPHOLOGICAL_CLOSE,
        ProcessingMethod.ADAPTIVE_THRESHOLD,
        ProcessingMethod.SPECULAR_REFLECTION,
        ProcessingMethod.ADAPTIVE_MORPH
    )

    for ((index, method) in phases.withIndex()) {
        if (maxQuads > 0) break // Si ya encontramos algo, no seguimos

        logBuilder.append("\n--- Iniciando Fase ${index + 1}: ${method.name} ---\n")
        logUpdater(logBuilder.toString())
        finalMethod = method
        maxQuads = -1 // Resetear para la nueva búsqueda

        sizesToTest.forEach { width ->
            val quadCount = countQuadsAtWidth(sourceBitmap, width, method)
            val logLine = "-> Prueba ${method.name} a ${width.toInt()}px: $quadCount cuadriláteros.\n"
            logBuilder.append(logLine)
            logUpdater(logBuilder.toString())
            if (quadCount >= maxQuads) {
                maxQuads = quadCount
                bestWidth = width
            }
        }
    }


    if (bestWidth == 0.0 || maxQuads <= 0) {
        logBuilder.append("\nNo se encontraron cuadriláteros. Usando tamaño por defecto (640px).")
        bestWidth = 640.0
    } else {
        logBuilder.append("\nMétodo exitoso: $finalMethod. Mejor tamaño: ${bestWidth.toInt()}px. Generando vistas...")
    }

    logUpdater(logBuilder.toString())

    val contourView = detectAndDrawOnHighRes(sourceBitmap, bestWidth, finalMethod)
    val processedView = generateHighResProcessedView(sourceBitmap, finalMethod)

    return ProcessingResult(contourView, processedView)
}

private fun countQuadsAtWidth(sourceBitmap: Bitmap, testWidth: Double, method: ProcessingMethod): Int {
    val originalMat = Mat()
    Utils.bitmapToMat(sourceBitmap, originalMat)
    val processedMat = Mat()
    val ratio = testWidth / originalMat.width()
    Imgproc.resize(originalMat, processedMat, Size(testWidth, originalMat.height() * ratio))
    Imgproc.cvtColor(processedMat, processedMat, Imgproc.COLOR_RGBA2GRAY)

    val edges = Mat()

    // Aplicar el pre-procesamiento adecuado según el método
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
        ProcessingMethod.ADAPTIVE_MORPH -> Imgproc.GaussianBlur(processedMat, processedMat, Size(7.0, 7.0), 0.0)
    }

    if (method == ProcessingMethod.ADAPTIVE_THRESHOLD || method == ProcessingMethod.ADAPTIVE_MORPH) {
        Imgproc.adaptiveThreshold(processedMat, edges, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 15, 4.0)
        if (method == ProcessingMethod.ADAPTIVE_MORPH) {
            val openKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_OPEN, openKernel)
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, closeKernel)
        }
    } else {
        Imgproc.Canny(processedMat, edges, 50.0, 150.0)
    }

    if (method == ProcessingMethod.MORPHOLOGICAL_CLOSE) {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
    }

    val contours = ArrayList<MatOfPoint>()
    Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

    var quadrilateralCount = 0
    val minArea = testWidth * testWidth * 0.02

    for (contour in contours) {
        if (Imgproc.contourArea(contour) < minArea) continue
        val contour2f = MatOfPoint2f(*contour.toArray())
        val approxCurve = MatOfPoint2f()
        Imgproc.approxPolyDP(contour2f, approxCurve, 0.02 * Imgproc.arcLength(contour2f, true), true)

        if (approxCurve.total() == 4L && isGoodQuadrilateral(approxCurve)) {
            quadrilateralCount++
        }
    }
    return quadrilateralCount
}

private fun detectAndDrawOnHighRes(sourceBitmap: Bitmap, optimalWidth: Double, method: ProcessingMethod): ImageBitmap {
    val processingMat = Mat()
    val scaleRatio = sourceBitmap.width / optimalWidth
    val newSize = Size(optimalWidth, sourceBitmap.height / scaleRatio)

    val originalMatForProcessing = Mat()
    Utils.bitmapToMat(sourceBitmap, originalMatForProcessing)
    Imgproc.resize(originalMatForProcessing, processingMat, newSize)
    Imgproc.cvtColor(processingMat, processingMat, Imgproc.COLOR_RGBA2GRAY)

    val edges = Mat()

    when (method) {
        ProcessingMethod.STANDARD -> Imgproc.GaussianBlur(processingMat, processingMat, Size(5.0, 5.0), 0.0)
        ProcessingMethod.CLAHE -> {
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(processingMat, processingMat)
            Imgproc.GaussianBlur(processingMat, processingMat, Size(5.0, 5.0), 0.0)
        }
        ProcessingMethod.MEDIAN_BLUR -> Imgproc.medianBlur(processingMat, processingMat, 5)
        ProcessingMethod.MORPHOLOGICAL_CLOSE, ProcessingMethod.ADAPTIVE_THRESHOLD -> Imgproc.GaussianBlur(processingMat, processingMat, Size(5.0, 5.0), 0.0)
        ProcessingMethod.SPECULAR_REFLECTION -> {
            val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
            clahe.apply(processingMat, processingMat)
            Imgproc.bilateralFilter(processingMat.clone(), processingMat, 9, 75.0, 75.0)
        }
        ProcessingMethod.ADAPTIVE_MORPH -> Imgproc.GaussianBlur(processingMat, processingMat, Size(7.0, 7.0), 0.0)
    }

    if (method == ProcessingMethod.ADAPTIVE_THRESHOLD || method == ProcessingMethod.ADAPTIVE_MORPH) {
        Imgproc.adaptiveThreshold(processingMat, edges, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 15, 4.0)
        if (method == ProcessingMethod.ADAPTIVE_MORPH) {
            val openKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_OPEN, openKernel)
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, closeKernel)
        }
    } else {
        Imgproc.Canny(processingMat, edges, 50.0, 150.0)
    }

    if (method == ProcessingMethod.MORPHOLOGICAL_CLOSE) {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
    }

    val contours = ArrayList<MatOfPoint>()
    Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

    val lowResQuads = mutableListOf<MatOfPoint>()
    val minArea = optimalWidth * optimalWidth * 0.02

    for (contour in contours) {
        if (Imgproc.contourArea(contour) < minArea) continue
        val contour2f = MatOfPoint2f(*contour.toArray())
        val approxCurve = MatOfPoint2f()
        Imgproc.approxPolyDP(contour2f, approxCurve, 0.02 * Imgproc.arcLength(contour2f, true), true)

        if (approxCurve.total() == 4L && isGoodQuadrilateral(approxCurve)) {
            lowResQuads.add(MatOfPoint(*approxCurve.toArray()))
        }
    }

    val highResMat = Mat()
    Utils.bitmapToMat(sourceBitmap, highResMat)

    val highResQuads = mutableListOf<MatOfPoint>()
    for (quad in lowResQuads) {
        Core.multiply(quad, Scalar(scaleRatio, scaleRatio), quad)
        highResQuads.add(quad)
    }

    if (highResQuads.isNotEmpty()) {
        Imgproc.drawContours(highResMat, highResQuads, -1, Scalar(0.0, 255.0, 0.0, 255.0), 5)
    }

    val resultBitmap = Bitmap.createBitmap(highResMat.cols(), highResMat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(highResMat, resultBitmap)
    return resultBitmap.asImageBitmap()
}

private fun generateHighResProcessedView(sourceBitmap: Bitmap, method: ProcessingMethod): ImageBitmap {
    val highResMat = Mat()
    Utils.bitmapToMat(sourceBitmap, highResMat)

    val grayMat = Mat()
    Imgproc.cvtColor(highResMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

    val edges = Mat()

    when (method) {
        ProcessingMethod.STANDARD -> Imgproc.GaussianBlur(grayMat, grayMat, Size(5.0, 5.0), 0.0)
        ProcessingMethod.CLAHE -> {
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(grayMat, grayMat)
            Imgproc.GaussianBlur(grayMat, grayMat, Size(5.0, 5.0), 0.0)
        }
        ProcessingMethod.MEDIAN_BLUR -> Imgproc.medianBlur(grayMat, grayMat, 5)
        ProcessingMethod.MORPHOLOGICAL_CLOSE, ProcessingMethod.ADAPTIVE_THRESHOLD -> Imgproc.GaussianBlur(grayMat, grayMat, Size(5.0, 5.0), 0.0)
        ProcessingMethod.SPECULAR_REFLECTION -> {
            val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
            clahe.apply(grayMat, grayMat)
            Imgproc.bilateralFilter(grayMat.clone(), grayMat, 9, 75.0, 75.0)
        }
        ProcessingMethod.ADAPTIVE_MORPH -> Imgproc.GaussianBlur(grayMat, grayMat, Size(7.0, 7.0), 0.0)
    }

    if (method == ProcessingMethod.ADAPTIVE_THRESHOLD || method == ProcessingMethod.ADAPTIVE_MORPH) {
        Imgproc.adaptiveThreshold(grayMat, edges, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 15, 4.0)
        if (method == ProcessingMethod.ADAPTIVE_MORPH) {
            val openKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_OPEN, openKernel)
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, closeKernel)
        }
    } else {
        Imgproc.Canny(grayMat, edges, 50.0, 150.0)
    }

    if (method == ProcessingMethod.MORPHOLOGICAL_CLOSE) {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
    }

    if (method != ProcessingMethod.ADAPTIVE_THRESHOLD) {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edges, edges, kernel)
    }

    val matResultadoRGBA = Mat()
    Imgproc.cvtColor(edges, matResultadoRGBA, Imgproc.COLOR_GRAY2RGBA)

    val resultBitmap = Bitmap.createBitmap(matResultadoRGBA.cols(), matResultadoRGBA.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(matResultadoRGBA, resultBitmap)

    return resultBitmap.asImageBitmap()
}

/**
 * Valida si un contorno de 4 puntos es un "buen" cuadrilátero.
 * Verifica que sea convexo y que sus ángulos sean aproximadamente de 90 grados.
 */
private fun isGoodQuadrilateral(contour: MatOfPoint2f): Boolean {
    val points = contour.toArray()
    if (points.size != 4) return false

    // 1. Verificar convexidad
    val matOfPoint = MatOfPoint(*points)
    if (!Imgproc.isContourConvex(matOfPoint)) {
        return false
    }

    // 2. Verificar que los ángulos sean cercanos a 90 grados
    // Se calcula el coseno de cada ángulo. Para 90 grados, el coseno es 0.
    // Se permite una tolerancia (ej. 0.3, que corresponde a ángulos entre 72 y 108 grados)
    val maxCosine = 0.3
    for (i in 2..4) {
        val pt1 = points[i % 4]
        val pt2 = points[i - 2]
        val pt0 = points[i - 1]

        val dx1 = pt1.x - pt0.x
        val dy1 = pt1.y - pt0.y
        val dx2 = pt2.x - pt0.x
        val dy2 = pt2.y - pt0.y

        val dotProduct = dx1 * dx2 + dy1 * dy2
        val magnitude1 = sqrt(dx1 * dx1 + dy1 * dy1)
        val magnitude2 = sqrt(dx2 * dx2 + dy2 * dy2)

        if (magnitude1 == 0.0 || magnitude2 == 0.0) return false // Evitar división por cero

        val cosine = abs(dotProduct / (magnitude1 * magnitude2))

        if (cosine > maxCosine) {
            return false
        }
    }
    return true
}

