import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.ArrayList
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

// Data class para el resultado de la detección. Separa la imagen de los puntos.
data class DetectionResult(
    val originalBitmap: Bitmap,
    val cornerPoints: List<Point>
)

// Enum para controlar qué método de pre-procesamiento se utiliza (sin cambios)
private enum class ProcessingMethod {
    STANDARD,
    CLAHE,
    MEDIAN_BLUR,
    MORPHOLOGICAL_CLOSE,
    ADAPTIVE_THRESHOLD,
    SPECULAR_REFLECTION,
    ADAPTIVE_MORPH
}

// Enum para controlar el estado de la pantalla de recorte
private enum class CropScreenState {
    CROP_PREVIEW,
    MANUAL_ADJUST
}

// Enum para los tipos de filtro de imagen
private enum class FilterType {
    NONE,
    SCANNER_LIGHT,
}

// Enum para el flujo general de la pantalla
private enum class ScannerFlowState {
    CAMERA,
    EDITING,
    FINAL_REVIEW
}

@Composable
fun DocumentScannerScreen(
    documentIdToEdit: Long?, // NUEVO: ID del documento a editar, o null si es nuevo
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val documentRepository = remember {
        DocumentRepository(context, DocumentDatabase.getDatabase(context).documentDao())
    }

    var flowState by remember { mutableStateOf(ScannerFlowState.CAMERA) }
    var currentDocumentId by remember { mutableStateOf(documentIdToEdit) }
    var scannedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var editingBitmapIndex by remember { mutableStateOf<Int?>(null) }

    var detectionResult by remember { mutableStateOf<DetectionResult?>(null) }
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editState by remember { mutableStateOf(CropScreenState.CROP_PREVIEW) }
    var selectedFilter by remember { mutableStateOf(FilterType.NONE) }
    var filteredBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isAdjustingFilterIntensity by remember { mutableStateOf(false) }
    var filterIntensity by remember { mutableStateOf(1.1f) }
    var bitmapForProcessing by remember { mutableStateOf<Bitmap?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var hasCamPermission by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCamPermission = granted }
    )
    var isFlashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // Si recibimos un ID, cargamos el documento existente y saltamos a la revisión
    LaunchedEffect(documentIdToEdit) {
        if (documentIdToEdit != null) {
            isLoading = true
            scannedBitmaps = documentRepository.getDocumentPages(documentIdToEdit)
            isLoading = false
            flowState = ScannerFlowState.FINAL_REVIEW
        }
    }

    fun resetToCameraState() {
        detectionResult = null
        croppedBitmap = null
        filteredBitmap = null
        editState = CropScreenState.CROP_PREVIEW
        isAdjustingFilterIntensity = false
        bitmapForProcessing = null
        editingBitmapIndex = null
        flowState = ScannerFlowState.CAMERA
    }

    LaunchedEffect(key1 = true) {
        permissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    LaunchedEffect(croppedBitmap, selectedFilter, filterIntensity) {
        if (croppedBitmap == null) {
            filteredBitmap = null
            return@LaunchedEffect
        }
        isLoading = true
        withContext(Dispatchers.Default) {
            val newFilteredBitmap = when (selectedFilter) {
                FilterType.SCANNER_LIGHT -> applyScannerLightFilter(croppedBitmap!!, filterIntensity)
                FilterType.NONE -> croppedBitmap
            }
            withContext(Dispatchers.Main) {
                filteredBitmap = newFilteredBitmap
                isLoading = false
            }
        }
    }

    fun runDetectionAndCrop(bitmapToProcess: Bitmap) {
        isLoading = true
        bitmapForProcessing = bitmapToProcess
        coroutineScope.launch(Dispatchers.Default) {

            // Intenta la detección automática
            var result = findBestSizeAndProcess(bitmapToProcess)

            // Variable para decidir a qué pantalla ir
            var initialEditState = CropScreenState.CROP_PREVIEW

            // ¡AQUÍ ESTÁ EL CAMBIO!
            // Si la detección falla (resultado nulo o sin puntos), crea un resultado genérico
            if (result == null || result.cornerPoints.isEmpty()) {
                Log.d("DocumentScanner", "Detección automática falló. Creando rectángulo genérico.")
                val defaultPoints = getDefaultCornerPoints(bitmapToProcess, marginPercent = 0.2f) // 20% de margen
                result = DetectionResult(originalBitmap = bitmapToProcess, cornerPoints = defaultPoints)

                // Como la detección falló, forzamos al usuario a ajustar manualmente
                initialEditState = CropScreenState.MANUAL_ADJUST
            }

            // Procede a recortar con los puntos (automáticos o genéricos)
            // Si la detección falló, esto recortará la imagen con el margen del 20%
            val newCroppedBitmap = result?.let { cropAndWarp(it.originalBitmap, it.cornerPoints) }

            launch(Dispatchers.Main) {
                detectionResult = result // ¡Ahora 'detectionResult' NUNCA será nulo!
                croppedBitmap = newCroppedBitmap
                isLoading = false
                bitmapForProcessing = null
                editState = initialEditState // <--- Estado inicial (MANUAL_ADJUST si falló)
                selectedFilter = FilterType.SCANNER_LIGHT
                flowState = ScannerFlowState.EDITING
            }
        }
    }

    val processBitmap: (Bitmap) -> Unit = { bitmap ->
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        runDetectionAndCrop(mutableBitmap)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                processBitmap(bitmap)
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        when (flowState) {
            ScannerFlowState.CAMERA -> {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onUseCase = { previewUseCase ->
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                previewUseCase,
                                imageCapture
                            )
                        }, ContextCompat.getMainExecutor(context))
                    }
                )
            }
            ScannerFlowState.EDITING -> {
                when(editState) {
                    CropScreenState.CROP_PREVIEW -> {
                        filteredBitmap?.let {
                            // ########## INICIO DE CORRECCIÓN 1 ##########
                            // Calcula el padding inferior dinámicamente
                            // 80dp (slider) + 80dp (acciones) = 160dp
                            // 60dp (filtros) + 80dp (acciones) = 140dp
                            val bottomPadding = if (isAdjustingFilterIntensity) 160.dp else 140.dp

                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1C1C1E)), contentAlignment = Alignment.Center) {
                                Image(bitmap = it.asImageBitmap(), contentDescription = "Documento recortado", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 130.dp))
                            }
                            // ########## FIN DE CORRECCIÓN 1 ##########
                        }
                    }
                    CropScreenState.MANUAL_ADJUST -> {
                        InteractiveDocumentView(
                            bitmap = detectionResult!!.originalBitmap,
                            initialPoints = detectionResult!!.cornerPoints,
                            onPointsUpdated = { /* No se necesita en este flujo */ }
                        )
                    }
                }
            }
            ScannerFlowState.FINAL_REVIEW -> {
                FinalReviewScreen(
                    initialBitmaps = scannedBitmaps,

                    repository = documentRepository,
                    onAddAnotherScan = { resetToCameraState() },
                    onEditRequest = { index ->
                        val bitmapToEdit = scannedBitmaps[index]

                        detectionResult = DetectionResult(
                            originalBitmap = bitmapToEdit,
                            cornerPoints = getDefaultCornerPoints(bitmapToEdit, 0.05f)
                        )

                        croppedBitmap = bitmapToEdit
                        selectedFilter = FilterType.NONE
                        editingBitmapIndex = index
                        flowState = ScannerFlowState.EDITING
                    },
                    onFinish = { onClose() }
                )
            }
        }

//        val closeAction = { onClose() }
//        IconButton(onClick = closeAction, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
//            Icon(Icons.Default.Close, "Cerrar", tint = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(8.dp))
//        }

        if (flowState == ScannerFlowState.CAMERA && hasCamPermission && flowState != ScannerFlowState.FINAL_REVIEW) {


            val closeAction: () -> Unit = {
                // Si ya tenemos bitmaps escaneados (scannedBitmaps no está vacío),
                // significa que venimos de "FinalReviewScreen". Debemos volver allí.
                if (scannedBitmaps.isNotEmpty()) {
                    flowState = ScannerFlowState.FINAL_REVIEW
                } else {
                    // Si no hay bitmaps, es la primera vez que abrimos la cámara.
                    // Salimos a "Home".
                    onClose()
                }
            }

            IconButton(
                onClick = closeAction, // onClose es el parámetro que ya recibe DocumentScannerScreen
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
            {
                Icon(
                    imageVector = Icons.Default.Close, // (Asegúrate de importar Icons.Default.Close)
                    contentDescription = "Cerrar",
                    tint = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(8.dp)
                )
            }
            IconButton(onClick = { isFlashOn = !isFlashOn }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, "Flash", tint = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(8.dp))
            }
        }

        LaunchedEffect(camera, isFlashOn) {
            camera?.cameraControl?.enableTorch(isFlashOn)
        }

        when (flowState) {
            ScannerFlowState.CAMERA -> {
                Row(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(32.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.PhotoLibrary, "Abrir Galería", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = { takePhoto(context, imageCapture, isFlashOn, processBitmap) }, modifier = Modifier.size(72.dp)) {
                        Icon(Icons.Default.CameraAlt, "Tomar Foto", tint = Color.White, modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.3f), CircleShape).padding(8.dp))
                    }
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }
            ScannerFlowState.EDITING -> {
                // ########## INICIO DE CORRECCIÓN 2 ##########
                Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                    // Esta sección (Slider o Filtros) se muestra en la parte SUPERIOR de la columna
                    if (isAdjustingFilterIntensity) {
                        BottomAppBar(containerColor = Color(0xFF1C1C1E).copy(alpha = 0.95f), contentColor = Color.White, contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.height(80.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Slider(value = filterIntensity, onValueChange = { filterIntensity = it }, valueRange = 0.5f..2.0f, modifier = Modifier.weight(1f))
                                IconButton(onClick = { isAdjustingFilterIntensity = false }) {
                                    Icon(Icons.Default.Check, "Aceptar ajuste de filtro")
                                }
                            }
                        }
                    } else {
                        if (editState == CropScreenState.CROP_PREVIEW) {
                            BottomAppBar(containerColor = Color(0xFF1C1C1E).copy(alpha = 0.8f), contentColor = Color.White, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(80.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                    FilterActionButton(text = "Original", isSelected = selectedFilter == FilterType.NONE, onClick = { selectedFilter = FilterType.NONE })
                                    FilterActionButton(text = "Luz Escáner", isSelected = selectedFilter == FilterType.SCANNER_LIGHT, onClick = {
                                        if (selectedFilter == FilterType.SCANNER_LIGHT) isAdjustingFilterIntensity = true else {
                                            selectedFilter = FilterType.SCANNER_LIGHT
                                            filterIntensity = 1.1f
                                        }
                                    })
                                }
                            }
                        }
                        // La barra de acciones (abajo) se MOVIÓ FUERA de este bloque 'else'
                    }

                    // Esta BottomAppBar (Acciones) AHORA ESTÁ FUERA del 'else',
                    // por lo que se muestra SIEMPRE en el estado de EDICIÓN,
                    // debajo del Slider o de los Filtros.
                    BottomAppBar(containerColor = Color(0xFF2C2C2E), contentColor = Color.White, modifier = Modifier.height(80.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            when (editState) {
                                CropScreenState.CROP_PREVIEW -> {
                                    ActionButton(icon = Icons.Default.PhotoLibrary, text = "Importar", onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                                    ActionButton(icon = Icons.Default.RotateLeft, text = "Girar", onClick = { croppedBitmap?.let { croppedBitmap = it.rotate(-90f) } })
                                    ActionButton(icon = Icons.Default.Crop, text = "Recortar", onClick = { editState = CropScreenState.MANUAL_ADJUST })
                                    ActionButton(icon = Icons.Default.Check, text = "Ok", onClick = {
                                        coroutineScope.launch {
                                            filteredBitmap?.let { editedBitmap ->
                                                isLoading = true
                                                if (editingBitmapIndex != null) {
                                                    documentRepository.updatePageInDocument(currentDocumentId!!, editingBitmapIndex!!, editedBitmap)
                                                } else if (currentDocumentId != null) {
                                                    documentRepository.addPageToDocument(currentDocumentId!!, editedBitmap)
                                                } else {
                                                    currentDocumentId = documentRepository.createDocumentAndAddFirstPage(editedBitmap)
                                                }
                                                scannedBitmaps = documentRepository.getDocumentPages(currentDocumentId!!)
                                                isLoading = false
                                                flowState = ScannerFlowState.FINAL_REVIEW
                                            }
                                        }
                                    }, enabled = filteredBitmap != null && !isLoading)
                                }
                                CropScreenState.MANUAL_ADJUST -> {
                                    ActionButton(icon = Icons.Default.AutoFixHigh, text = "Automático", onClick = { detectionResult?.originalBitmap?.let { runDetectionAndCrop(it) } })
                                    ActionButton(icon = Icons.Default.Check, text = "Aplicar", onClick = {
                                        isLoading = true
                                        coroutineScope.launch {
                                            val newCroppedBitmap = detectionResult?.let { withContext(Dispatchers.Default) { cropAndWarp(it.originalBitmap, it.cornerPoints) } }
                                            launch(Dispatchers.Main) {
                                                croppedBitmap = newCroppedBitmap
                                                isLoading = false
                                                editState = CropScreenState.CROP_PREVIEW
                                            }
                                        }
                                    }, enabled = !isLoading)
                                }
                            }
                        }
                    }
                }
                // ########## FIN DE CORRECCIÓN 2 ##########
            }
            ScannerFlowState.FINAL_REVIEW -> {
                // La barra de herramientas se gestiona dentro de FinalReviewScreen
            }
        }


        if (isLoading) {
            bitmapForProcessing?.let { ProcessingAnimation(modifier = Modifier.fillMaxSize(), bitmap = it) } ?: CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}


// Composable para la animación de carga que simula IA
@Composable
fun ProcessingAnimation(modifier: Modifier = Modifier, bitmap: Bitmap) {
    val infiniteTransition = rememberInfiniteTransition()
    val scanLinePosition by infiniteTransition.animateFloat(initialValue = -0.1f, targetValue = 1.1f, animationSpec = infiniteRepeatable(animation = tween(durationMillis = 2000, easing = LinearEasing), repeatMode = RepeatMode.Restart))
    val dotAlpha by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(durationMillis = 700), repeatMode = RepeatMode.Reverse))
    Box(modifier = modifier.background(Color.Black.copy(alpha = 0.8f)).fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Procesando Imagen", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), alpha = 0.4f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val gridSize = 25
            val dotRadius = 1.5.dp.toPx()
            for (i in 0..gridSize) {
                for (j in 0..gridSize) {
                    val x = (canvasWidth / gridSize) * i
                    val y = (canvasHeight / gridSize) * j
                    val currentAlpha = if ((i + j) % 2 == 0) dotAlpha else dotAlpha * 0.5f
                    drawCircle(color = Color(0xFF30D5C8).copy(alpha = (Math.random() * currentAlpha).toFloat()), radius = dotRadius, center = Offset(x, y))
                }
            }
            val yPos = scanLinePosition * canvasHeight
            drawLine(brush = Brush.verticalGradient(colors = listOf(Color.Transparent, Color(0xFF30D5C8).copy(alpha = 0.7f), Color.Transparent)), start = Offset(0f, yPos), end = Offset(canvasWidth, yPos), strokeWidth = 3.dp.toPx())
        }
        Text("Analizando documento...", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}


@Composable
private fun ActionButton(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(modifier = modifier.fillMaxHeight().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(imageVector = icon, contentDescription = text, tint = if (enabled) Color.White else Color.Gray, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = text, fontSize = 12.sp, color = if (enabled) Color.White else Color.Gray)
    }
}

@Composable
private fun FilterActionButton(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val textColor = if (isSelected) Color(0xFF30D5C8) else Color.White
    Column(modifier = modifier.fillMaxHeight().padding(horizontal = 8.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text = text, color = textColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
        if (isSelected) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.width(24.dp).height(3.dp).background(textColor, CircleShape))
        }
    }
}


@Composable
fun InteractiveDocumentView(bitmap: Bitmap, initialPoints: List<Point>, onPointsUpdated: (List<Point>) -> Unit, modifier: Modifier = Modifier) {
    var cornerPoints by remember { mutableStateOf(initialPoints) }
    var draggingCornerIndex by remember { mutableStateOf<Int?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(initialPoints) { if (initialPoints != cornerPoints) { cornerPoints = initialPoints } }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val (scale, offset) = remember(viewSize, bitmap) {
        if (viewSize.width == 0 || viewSize.height == 0) Pair(1f, Offset.Zero) else {
            val bitmapAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val viewAspectRatio = viewSize.width.toFloat() / viewSize.height.toFloat()
            val scaleFactor: Float
            val contentOffset: Offset
            if (bitmapAspectRatio > viewAspectRatio) {
                scaleFactor = viewSize.width.toFloat() / bitmap.width
                val scaledHeight = bitmap.height * scaleFactor
                contentOffset = Offset(0f, (viewSize.height - scaledHeight) / 2f)
            } else {
                scaleFactor = viewSize.height.toFloat() / bitmap.height
                val scaledWidth = bitmap.width * scaleFactor
                contentOffset = Offset((viewSize.width - scaledWidth) / 2f, 0f)
            }
            Pair(scaleFactor, contentOffset)
        }
    }
    fun screenToBitmapCoords(screenOffset: Offset): Point {
        val x = (screenOffset.x - offset.x) / scale
        val y = (screenOffset.y - offset.y) / scale
        return Point(x.toDouble(), y.toDouble())
    }
    fun bitmapToScreenCoords(bitmapPoint: Point): Offset {
        val x = (bitmapPoint.x * scale) + offset.x
        val y = (bitmapPoint.y * scale) + offset.y
        return Offset(x.toFloat(), y.toFloat())
    }
    Box(modifier = modifier.fillMaxSize().background(Color.Black).onSizeChanged { viewSize = it }, contentAlignment = Alignment.Center) {
        Image(bitmap = imageBitmap, contentDescription = "Documento a ajustar", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        val touchRadius = with(LocalDensity.current) { 24.dp.toPx() }
        Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { startOffset ->
                    var minDistance = Float.MAX_VALUE
                    var closestCorner: Int? = null
                    cornerPoints.forEachIndexed { index, point ->
                        val screenPoint = bitmapToScreenCoords(point)
                        val distance = (startOffset - screenPoint).getDistance()
                        if (distance < minDistance && distance < touchRadius) {
                            minDistance = distance
                            closestCorner = index
                        }
                    }
                    draggingCornerIndex = closestCorner
                },
                onDrag = { change, _ ->
                    draggingCornerIndex?.let { index ->
                        val newBitmapPoint = screenToBitmapCoords(change.position)
                        val updatedPoints = cornerPoints.toMutableList()
                        updatedPoints[index] = newBitmapPoint
                        cornerPoints = updatedPoints
                        onPointsUpdated(updatedPoints)
                    }
                },
                onDragEnd = { draggingCornerIndex = null }
            )
        }) {
            if (cornerPoints.isNotEmpty()) {
                val screenPoints = cornerPoints.map { bitmapToScreenCoords(it) }
                for (i in screenPoints.indices) {
                    drawLine(color = Color.Green, start = screenPoints[i], end = screenPoints[(i + 1) % screenPoints.size], strokeWidth = 4.dp.toPx())
                }
                screenPoints.forEach { point ->
                    drawCircle(color = Color.Green, radius = 8.dp.toPx(), center = point)
                    drawCircle(color = Color.White, radius = 8.dp.toPx(), center = point, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
    }
}



@Composable
private fun CameraPreview(modifier: Modifier = Modifier, onUseCase: (Preview) -> Unit) {
    AndroidView(modifier = modifier, factory = { context ->
        val previewView = PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        onUseCase(Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) })
        previewView
    })
}


private fun takePhoto(context: Context, imageCapture: ImageCapture, isFlashOn: Boolean, onPhotoTaken: (Bitmap) -> Unit) {
    imageCapture.flashMode = if (isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    val executor = ContextCompat.getMainExecutor(context)
    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
            val bitmap = image.toBitmap()
            val rotatedBitmap = bitmap.rotate(90f)
            onPhotoTaken(rotatedBitmap)
            image.close()
        }
        override fun onError(exception: ImageCaptureException) { println("Error taking photo: $exception") }
    })
}

fun androidx.camera.core.ImageProxy.toBitmap(): Bitmap {
    val buffer: ByteBuffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}


private fun applyScannerLightFilter(bitmap: Bitmap, contrast: Float): Bitmap {
    val srcMat = Mat()
    Utils.bitmapToMat(bitmap, srcMat)
    Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2RGB)
    val labMat = Mat()
    Imgproc.cvtColor(srcMat, labMat, Imgproc.COLOR_RGB2Lab)
    val labPlanes = ArrayList<Mat>()
    Core.split(labMat, labPlanes)
    val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
    val lightness = labPlanes[0]
    clahe.apply(lightness, lightness)
    Core.merge(labPlanes, labMat)
    val resultMat = Mat()
    Imgproc.cvtColor(labMat, resultMat, Imgproc.COLOR_Lab2RGB)
    val alpha = contrast.toDouble()
    val beta = 10.0
    resultMat.convertTo(resultMat, -1, alpha, beta)
    val resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(resultMat, resultBitmap)
    return resultBitmap
}

private fun findBestSizeAndProcess(sourceBitmap: Bitmap): DetectionResult? {
    val sizesToTest = listOf(240.0, 320.0, 480.0, 640.0, 800.0, 960.0, 1080.0, 1200.0)
    var bestWidth = 0.0
    var maxQuads = -1
    var finalMethod = ProcessingMethod.STANDARD
    val phases = listOf(ProcessingMethod.STANDARD, ProcessingMethod.CLAHE, ProcessingMethod.MEDIAN_BLUR, ProcessingMethod.MORPHOLOGICAL_CLOSE, ProcessingMethod.ADAPTIVE_THRESHOLD, ProcessingMethod.SPECULAR_REFLECTION, ProcessingMethod.ADAPTIVE_MORPH)
    for ((index, method) in phases.withIndex()) {
        if (maxQuads > 0) break
        Log.d("ImageProcessing", "\n--- Iniciando Fase ${index + 1}: ${method.name} ---")
        finalMethod = method
        maxQuads = -1
        sizesToTest.forEach { width ->
            val quadCount = countQuadsAtWidth(sourceBitmap, width, method)
            Log.d("ImageProcessing", "-> Prueba ${method.name} a ${width.toInt()}px: $quadCount cuadriláteros.")
            if (quadCount >= maxQuads) {
                maxQuads = quadCount
                bestWidth = width
            }
        }
    }
    if (bestWidth == 0.0 || maxQuads <= 0) {
        Log.d("ImageProcessing", "\nNo se encontraron cuadriláteros.")
        return null
    } else {
        Log.d("ImageProcessing", "\nMétodo exitoso: $finalMethod. Mejor tamaño: ${bestWidth.toInt()}px. Obteniendo puntos...")
    }
    val cornerPoints = detectQuadrilateralPoints(sourceBitmap, bestWidth, finalMethod)
    return if (cornerPoints.isNotEmpty()) {
        DetectionResult(sourceBitmap, cornerPoints)
    } else {
        null
    }
}

private fun detectQuadrilateralPoints(sourceBitmap: Bitmap, optimalWidth: Double, method: ProcessingMethod): List<Point> {
    val matToProcess = Mat()
    val scaleRatio = sourceBitmap.width / optimalWidth
    val newSize = Size(optimalWidth, sourceBitmap.height / scaleRatio)
    val originalMatForProcessing = Mat()
    Utils.bitmapToMat(sourceBitmap, originalMatForProcessing)
    Imgproc.resize(originalMatForProcessing, matToProcess, newSize)
    Imgproc.cvtColor(matToProcess, matToProcess, Imgproc.COLOR_RGBA2GRAY)
    val edges = Mat()
    when (method) {
        ProcessingMethod.STANDARD -> Imgproc.GaussianBlur(matToProcess, matToProcess, Size(5.0, 5.0), 0.0)
        ProcessingMethod.CLAHE -> {
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(matToProcess, matToProcess)
            Imgproc.GaussianBlur(matToProcess, matToProcess, Size(5.0, 5.0), 0.0)
        }
        ProcessingMethod.MEDIAN_BLUR -> Imgproc.medianBlur(matToProcess, matToProcess, 5)
        ProcessingMethod.MORPHOLOGICAL_CLOSE, ProcessingMethod.ADAPTIVE_THRESHOLD -> Imgproc.GaussianBlur(matToProcess, matToProcess, Size(5.0, 5.0), 0.0)
        ProcessingMethod.SPECULAR_REFLECTION -> {
            val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
            clahe.apply(matToProcess, matToProcess)
            Imgproc.bilateralFilter(matToProcess.clone(), matToProcess, 9, 75.0, 75.0)
        }
        ProcessingMethod.ADAPTIVE_MORPH -> Imgproc.GaussianBlur(matToProcess, matToProcess, Size(7.0, 7.0), 0.0)
    }
    if (method == ProcessingMethod.ADAPTIVE_THRESHOLD || method == ProcessingMethod.ADAPTIVE_MORPH) {
        Imgproc.adaptiveThreshold(matToProcess, edges, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 15, 4.0)
        if (method == ProcessingMethod.ADAPTIVE_MORPH) {
            val openKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_OPEN, openKernel)
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, closeKernel)
        }
    } else {
        Imgproc.Canny(matToProcess, edges, 50.0, 150.0)
    }
    if (method == ProcessingMethod.MORPHOLOGICAL_CLOSE) {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
    }
    val contours = ArrayList<MatOfPoint>()
    Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
    var largestQuad: MatOfPoint? = null
    var maxArea = 0.0
    val minArea = optimalWidth * optimalWidth * 0.02
    for (contour in contours) {
        val area = Imgproc.contourArea(contour)
        if (area < minArea) continue
        val contour2f = MatOfPoint2f(*contour.toArray())
        val approxCurve = MatOfPoint2f()
        Imgproc.approxPolyDP(contour2f, approxCurve, 0.02 * Imgproc.arcLength(contour2f, true), true)
        if (approxCurve.total() == 4L && isGoodQuadrilateral(approxCurve) && area > maxArea) {
            maxArea = area
            largestQuad = MatOfPoint(*approxCurve.toArray())
        }
    }
    return largestQuad?.let { quad ->
        Core.multiply(quad, Scalar(scaleRatio, scaleRatio), quad)
        quad.toList()
    } ?: emptyList()
}

private fun cropAndWarp(sourceBitmap: Bitmap, points: List<Point>): Bitmap {
    val sortedPoints = sortPoints(points)
    val srcMat = MatOfPoint2f().apply { fromList(sortedPoints) }
    val (width, height) = getOutputDimensions(sortedPoints)
    val dstMat = MatOfPoint2f(Point(0.0, 0.0), Point(width, 0.0), Point(width, height), Point(0.0, height))
    val perspectiveTransform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
    val inputMat = Mat()
    Utils.bitmapToMat(sourceBitmap, inputMat)
    val outputMat = Mat()
    Imgproc.warpPerspective(inputMat, outputMat, perspectiveTransform, Size(width, height))
    val resultBitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(outputMat, resultBitmap)

    if (resultBitmap.width > resultBitmap.height) {
        return resultBitmap.rotate(90f)
    }

    return resultBitmap
}

private fun sortPoints(points: List<Point>): List<Point> {
    val sortedPoints = points.sortedWith(compareBy { it.y })
    val topPoints = sortedPoints.subList(0, 2).sortedWith(compareBy { it.x })
    val bottomPoints = sortedPoints.subList(2, 4).sortedWith(compareBy { it.x })
    return listOf(topPoints[0], topPoints[1], bottomPoints[1], bottomPoints[0])
}

private fun getOutputDimensions(points: List<Point>): Pair<Double, Double> {
    val (tl, tr, br, bl) = points
    val widthA = sqrt((br.x - bl.x).pow(2) + (br.y - bl.y).pow(2))
    val widthB = sqrt((tr.x - tl.x).pow(2) + (tr.y - tl.y).pow(2))
    val maxWidth = widthA.coerceAtLeast(widthB)
    val heightA = sqrt((tr.x - br.x).pow(2) + (tr.y - br.y).pow(2))
    val heightB = sqrt((tl.x - bl.x).pow(2) + (tl.y - bl.y).pow(2))
    val maxHeight = heightA.coerceAtLeast(heightB)
    return Pair(maxWidth, maxHeight)
}

private fun countQuadsAtWidth(sourceBitmap: Bitmap, testWidth: Double, method: ProcessingMethod): Int {
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

private fun isGoodQuadrilateral(contour: MatOfPoint2f): Boolean {
    val points = contour.toArray()
    if (points.size != 4) return false
    val matOfPoint = MatOfPoint(*points)
    if (!Imgproc.isContourConvex(matOfPoint)) {
        return false
    }
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
        if (magnitude1 == 0.0 || magnitude2 == 0.0) return false
        val cosine = abs(dotProduct / (magnitude1 * magnitude2))
        if (cosine > maxCosine) {
            return false
        }
    }
    return true
}


/**
 * Crea una lista de puntos de esquina genéricos con un margen,
 * en caso de que la detección automática falle.
 */
private fun getDefaultCornerPoints(bitmap: Bitmap, marginPercent: Float = 0.2f): List<Point> {
    val width = bitmap.width.toDouble()
    val height = bitmap.height.toDouble()

    // Calcula el margen en píxeles
    val marginX = width * marginPercent
    val marginY = height * marginPercent

    // Devuelve los 4 puntos: TL, TR, BR, BL
    return listOf(
        Point(marginX, marginY), // Top-left
        Point(width - marginX, marginY), // Top-right
        Point(width - marginX, height - marginY), // Bottom-right
        Point(marginX, height - marginY)  // Bottom-left
    )
}

