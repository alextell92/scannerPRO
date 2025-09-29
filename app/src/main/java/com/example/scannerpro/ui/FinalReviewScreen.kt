import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.scannerpro.Collage.CollageScreen
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


private enum class ViewMode { LIST, GRID }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalReviewScreen(
    initialBitmaps: List<Bitmap>,
    repository: DocumentRepository, // <-- AÑADE ESTE PARÁMETRO
    onEditRequest: (Int) -> Unit,
    onAddAnotherScan: () -> Unit,
    onFinish: () -> Unit
) {
    var bitmaps by remember { mutableStateOf(initialBitmaps) }
    var isMarkupMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var viewMode by remember {
        mutableStateOf(AppPreferences.getViewMode(context))
    }
    var selectedIndex by remember { mutableStateOf<Int?>(if (bitmaps.isNotEmpty()) 0 else null) }
    var showShareSheet by remember { mutableStateOf(false) }

    var isSelectionModeActive by remember { mutableStateOf(false) }
    var selectedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var isCollageMode by remember { mutableStateOf(false) }
    var bitmapsForCollage by remember { mutableStateOf<List<Bitmap>>(emptyList()) }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
    ) {
        if (isMarkupMode) {
            selectedIndex?.let { index ->
                MarkupScreen(
                    bitmap = bitmaps[index],
                    onMarkupComplete = { newBitmap ->
                        bitmaps = bitmaps.toMutableList().also { it[index] = newBitmap }
                        isMarkupMode = false
                    }
                )
            }
        } else if (isCollageMode) {
            CollageScreen(
                initialBitmaps = bitmapsForCollage,
                repository = repository, // <-- PARÁMETRO QUE FALTA
                onClose = { isCollageMode = false },
                onSave = { pages ->
                    // Ya no necesitas el Toast aquí, el guardado se maneja dentro
                    isCollageMode = false
                }
            )
        } else {
            // --- INICIO DEL CAMBIO ---
            // Ahora la vista normal (galería) y su barra de botones están dentro de un Box
            // para que la barra se pueda alinear correctamente abajo.
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = {
                            Text(
                                if (isSelectionModeActive) "${selectedIndices.size} seleccionados" else "Documentos (${bitmaps.size})",
                                color = Color.White
                            )
                        },
                        navigationIcon = {
                            if (isSelectionModeActive) {
                                IconButton(onClick = {
                                    isSelectionModeActive = false
                                    selectedIndices = emptySet()
                                }) {
                                    Icon(Icons.Default.ArrowBack, "Cancelar selección", tint = Color.White)
                                }
                            } else {
                                IconButton(onClick = onFinish) {
                                    Icon(Icons.Default.ArrowBack, "Volver a Home", tint = Color.White)
                                }
                            }
                        },
                        actions = {
                            if (isSelectionModeActive) {
                                IconButton(onClick = {
                                    selectedIndices = if (selectedIndices.size == bitmaps.size) emptySet() else bitmaps.indices.toSet()
                                }) {
                                    Icon(Icons.Default.SelectAll, "Seleccionar todo", tint = Color.White)
                                }
                            } else {
                                IconButton(onClick = { isSelectionModeActive = true }) {
                                    Icon(Icons.Default.CheckBox, "Seleccionar", tint = Color.White)
                                }
                                IconButton(onClick = {
                                    viewMode = if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                                    AppPreferences.setViewMode(context, viewMode)
                                }) {
                                    Icon(
                                        imageVector = if (viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.List,
                                        contentDescription = "Cambiar vista",
                                        tint = Color.White
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2C2C2E))
                    )

                    if (viewMode == ViewMode.LIST) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 80.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val count = bitmaps.size
                            items(count + 1, key = { index -> if (index < count) bitmaps[index].hashCode() else "add_button_list" }) { index ->
                                if (index < count) {
                                    val bitmap = bitmaps[index]
                                    BitmapListItem(
                                        bitmap = bitmap,
                                        pageNumber = index + 1,
                                        isSelected = if (isSelectionModeActive) index in selectedIndices else selectedIndex == index,
                                        isSelectionModeActive = isSelectionModeActive,
                                        onClick = {
                                            if (isSelectionModeActive) {
                                                selectedIndices = if (index in selectedIndices) selectedIndices - index else selectedIndices + index
                                            } else {
                                                selectedIndex = index
                                            }
                                        }
                                    )
                                } else {
                                    AddPageListItem(onClick = onAddAnotherScan)
                                }
                            }
                        }
                    } else { // GRID View
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val count = bitmaps.size
                            items(count + 1, key = { index -> if (index < count) bitmaps[index].hashCode() else "add_button_grid" }) { index ->
                                if (index < count) {
                                    val bitmap = bitmaps[index]
                                    BitmapGridItem(
                                        bitmap = bitmap,
                                        pageNumber = index + 1,
                                        isSelected = if (isSelectionModeActive) index in selectedIndices else selectedIndex == index,
                                        isSelectionModeActive = isSelectionModeActive,
                                        onClick = {
                                            if (isSelectionModeActive) {
                                                selectedIndices = if (index in selectedIndices) selectedIndices - index else selectedIndices + index
                                            } else {
                                                selectedIndex = index
                                            }
                                        }
                                    )
                                } else {
                                    AddPageGridItem(onClick = onAddAnotherScan)
                                }
                            }
                        }
                    }
                }

                // La barra de botones AHORA ESTÁ AQUÍ, dentro del Box de la vista normal
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = Color(0xFF2C2C2E),
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelectionModeActive) {
                            ActionButton(icon = Icons.Default.Delete, text = "Eliminar", enabled = selectedIndices.isNotEmpty(), onClick = {
                                bitmaps = bitmaps.filterIndexed { index, _ -> index !in selectedIndices }
                                selectedIndices = emptySet()
                                isSelectionModeActive = false
                                selectedIndex = if(bitmaps.isNotEmpty()) 0 else null
                            })
                            ActionButton(
                                icon = Icons.Default.AutoAwesomeMosaic,
                                text = "Collage",
                                enabled = selectedIndices.isNotEmpty(),
                                onClick = {
                                    bitmapsForCollage = bitmaps.filterIndexed { index, _ -> index in selectedIndices }
                                    isCollageMode = true
                                    isSelectionModeActive = false
                                    selectedIndices = emptySet()
                                }
                            )
                            ActionButton(icon = Icons.Default.Share, text = "Compartir", enabled = selectedIndices.isNotEmpty(), onClick = { showShareSheet = true })
                        } else {
                            ActionButton(icon = Icons.Default.Add, text = "Agregar", onClick = onAddAnotherScan)
                            ActionButton(icon = Icons.Default.Edit, text = "Editar", enabled = selectedIndex != null, onClick = { selectedIndex?.let { onEditRequest(it) } })
                            ActionButton(icon = Icons.Default.Brush, text = "Markup", enabled = selectedIndex != null, onClick = { isMarkupMode = true })
                            ActionButton(icon = Icons.Default.Check, text = "Finalizar", enabled = bitmaps.isNotEmpty(), onClick = onFinish)
                        }
                    }
                }
            }
            // --- FIN DEL CAMBIO ---
        }

        // El ShareBottomSheet se queda aquí fuera, porque es un modal que se superpone a todo
        if (showShareSheet) {
            ShareBottomSheet(
                onDismiss = { showShareSheet = false },
                selectionCount = selectedIndices.size,
                onShareAsPdf = {
                    coroutineScope.launch {
                        showShareSheet = false
                        val bitmapsToShare = bitmaps.filterIndexed { index, _ -> index in selectedIndices }
                        if (bitmapsToShare.isNotEmpty()) {
                            val pdfUri = createPdfFromBitmapsAndGetUri(context, bitmapsToShare)
                            pdfUri?.let { uri ->
                                shareUri(context, uri, "application/pdf")
                            } ?: Toast.makeText(context, "Error al crear el PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onSaveToGallery = {
                    showShareSheet = false
                    if (selectedIndices.isNotEmpty()) {
                        selectedIndices.forEach { index ->
                            val bitmap = bitmaps[index]
                            saveBitmapToGallery(context, bitmap, "Scan_${System.currentTimeMillis()}_p${index + 1}")
                        }
                    }
                },
                onShareImages = {
                    coroutineScope.launch {
                        showShareSheet = false
                        val bitmapsToShare = bitmaps.filterIndexed { index, _ -> index in selectedIndices }
                        if (bitmapsToShare.size == 1) {
                            shareBitmapAsImage(context, bitmapsToShare.first())
                        } else if (bitmapsToShare.size > 1) {
                            val zipUri = createZipFromBitmapsAndGetUri(context, bitmapsToShare, "escaneo.zip")
                            zipUri?.let { uri ->
                                shareUri(context, uri, "application/zip")
                            } ?: Toast.makeText(context, "Error al crear el ZIP", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareBottomSheet(
    onDismiss: () -> Unit,
    onShareAsPdf: () -> Unit,
    onShareImages: () -> Unit,
    onSaveToGallery: () -> Unit,
    selectionCount: Int
) {
    val modalBottomSheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = Color(0xFF2C2C2E)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Compartir", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
            ShareOption(icon = Icons.Default.Description, text = "Compartir como PDF (Selección)", onClick = onShareAsPdf, enabled = selectionCount > 0)
            ShareOption(
                icon = Icons.Default.Image,
                text = if (selectionCount == 1) "Compartir como Imagen" else "Compartir como ZIP ($selectionCount pág.)",
                onClick = onShareImages,
                enabled = selectionCount > 0
            )
            ShareOption(
                icon = Icons.Default.Download,
                text = "Guardar en Galería (Selección)",
                onClick = onSaveToGallery,
                enabled = selectionCount > 0
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShareOption(icon: ImageVector, text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (enabled) Color.White else Color.Gray

        Icon(imageVector = icon, contentDescription = text, tint = tint)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = tint)
    }
}

@Composable
private fun BitmapListItem(
    bitmap: Bitmap,
    pageNumber: Int,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected && !isSelectionModeActive) Color(0xFF30D5C8) else Color.Transparent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .border(2.dp, borderColor, shape = CircleShape.copy(all = CornerSize(8.dp)))
            .clip(CircleShape.copy(all = CornerSize(8.dp)))
            .background(Color.DarkGray)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Página $pageNumber",
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f / 1.41f),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(16.dp))
            Text("Página $pageNumber", color = Color.White, fontWeight = FontWeight.Bold)
        }
        if (isSelectionModeActive) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = "Seleccionar",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}

@Composable
private fun BitmapGridItem(
    bitmap: Bitmap,
    pageNumber: Int,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected && !isSelectionModeActive) Color(0xFF30D5C8) else Color.Transparent
    Box(
        modifier = modifier
            .border(2.dp, borderColor, shape = CircleShape.copy(all = CornerSize(8.dp)))
            .clip(CircleShape.copy(all = CornerSize(8.dp)))
            .background(Color.DarkGray)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Página $pageNumber",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / 1.41f),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(8.dp))
            Text("Página $pageNumber", color = Color.White, fontSize = 12.sp)
        }
        if (isSelectionModeActive) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = "Seleccionar",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}


@Composable
private fun MarkupScreen(
    bitmap: Bitmap,
    onMarkupComplete: (Bitmap) -> Unit
) {
    var paths by remember { mutableStateOf<List<Pair<Path, Stroke>>>(emptyList()) }
    var currentPath by remember { mutableStateOf(Path()) }
    var currentPathColor by remember { mutableStateOf(Color.Red) }
    var currentPathStrokeWidth by remember { mutableStateOf(5.dp) }
    val density = LocalDensity.current

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp, vertical = 130.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentPath.moveTo(offset.x, offset.y)
                    },
                    onDrag = { change, _ ->
                        currentPath.lineTo(change.position.x, change.position.y)
                        paths = paths
                            .toMutableList()
                            .apply { add(currentPath to Stroke(width = density.run { currentPathStrokeWidth.toPx() }, cap = StrokeCap.Round)) }
                    },
                    onDragEnd = {
                        currentPath = Path()
                    }
                )
            }
        ) {
            drawImage(
                image = imageBitmap,
                topLeft = Offset.Zero
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

@Composable
private fun MarkupToolbar(
    onColorChanged: (Color) -> Unit,
    onStrokeWidthChanged: (Dp) -> Unit,
    onUndo: () -> Unit,
    onConfirm: () -> Unit
) {
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

private fun captureBitmap(
    width: Int,
    height: Int,
    content: DrawScope.() -> Unit
): Bitmap {
    val imageBitmap = ImageBitmap(width, height)
    val canvas = Canvas(imageBitmap)
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


@Composable
private fun ActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (enabled) Color.White else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (enabled) Color.White else Color.Gray
        )
    }
}


private fun shareUri(context: Context, uri: Uri, mimeType: String) {
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, uri)
        type = mimeType
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Compartir documento"))
}

private fun shareBitmapAsImage(context: Context, bitmap: Bitmap) {
    val cachePath = File(context.cacheDir, "images")
    cachePath.mkdirs()
    val file = File(cachePath, "shared_image.png")
    val fileOutputStream = FileOutputStream(file)
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
    fileOutputStream.close()

    val fileUri: Uri? = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: IllegalArgumentException) {
        Log.e("FileSharing", "File URI creation failed.", e)
        null
    }

    fileUri?.let {
        shareUri(context, it, "image/png")
    }
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String) {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.png")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ScannedDocuments")
        }
    }

    val resolver = context.contentResolver
    var uri: Uri? = null
    try {
        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri == null) {
            throw IOException("Failed to create new MediaStore record.")
        }
        resolver.openOutputStream(uri)?.use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw IOException("Failed to save bitmap.")
            }
        }
        Toast.makeText(context, "Guardado en Galería", Toast.LENGTH_SHORT).show()
    } catch (e: IOException) {
        if (uri != null) {
            resolver.delete(uri, null, null)
        }
        Log.e("SaveToGallery", "Failed to save image", e)
        Toast.makeText(context, "Error al guardar la imagen", Toast.LENGTH_SHORT).show()
    }
}

private fun createPdfFromBitmapsAndGetUri(context: Context, bitmaps: List<Bitmap>): Uri? {
    if (bitmaps.isEmpty()) return null

    val pdfDocument = PdfDocument()
    try {
        bitmaps.forEachIndexed { index, bitmap ->
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val scale = (canvas.width.toFloat() / bitmap.width).coerceAtMost(canvas.height.toFloat() / bitmap.height)
            val left = (canvas.width - bitmap.width * scale) / 2
            val top = (canvas.height - bitmap.height * scale) / 2
            val destRect = android.graphics.Rect(left.toInt(), top.toInt(), (left + bitmap.width * scale).toInt(), (top + bitmap.height * scale).toInt())
            canvas.drawBitmap(bitmap, null, destRect, null)
            pdfDocument.finishPage(page)
        }

        val cachePath = File(context.cacheDir, "documents")
        cachePath.mkdirs()
        val file = File(cachePath, "document.pdf")
        pdfDocument.writeTo(FileOutputStream(file))

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: Exception) {
        Log.e("CreatePdf", "Error creating PDF", e)
        return null
    } finally {
        pdfDocument.close()
    }
}


@Composable
private fun AddPageListItem(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(CircleShape.copy(all = CornerSize(8.dp)))
            .background(Color.DarkGray)
            .border(
                2.dp,
                Color(0xFF444444),
                shape = CircleShape.copy(all = CornerSize(8.dp))
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Añadir Página",
                tint = Color.Gray,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Añadir Página", color = Color.Gray)
        }
    }
}

@Composable
private fun AddPageGridItem(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape.copy(all = CornerSize(8.dp)))
            .background(Color.DarkGray)
            .border(
                2.dp,
                Color(0xFF444444),
                shape = CircleShape.copy(all = CornerSize(8.dp))
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.41f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Añadir Página",
                tint = Color.Gray,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text("Añadir Página", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

private fun createZipFromBitmapsAndGetUri(context: Context, bitmaps: List<Bitmap>, fileName: String = "documento.zip"): Uri? {
    if (bitmaps.isEmpty()) return null

    val cachePath = File(context.cacheDir, "documents")
    cachePath.mkdirs()
    val zipFile = File(cachePath, fileName)

    try {
        val fos = FileOutputStream(zipFile)
        val zipOut = ZipOutputStream(fos)

        bitmaps.forEachIndexed { index, bitmap ->
            val entryName = "pagina_${index + 1}.png"
            val zipEntry = ZipEntry(entryName)
            zipOut.putNextEntry(zipEntry)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, zipOut)
            zipOut.closeEntry()
        }

        zipOut.close()
        fos.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)

    } catch (e: IOException) {
        Log.e("CreateZip", "Error creando el archivo ZIP", e)
        return null
    }
}

private object AppPreferences {
    private const val PREFS_NAME = "scanner_prefs"
    private const val KEY_VIEW_MODE = "view_mode"

    fun setViewMode(context: Context, mode: ViewMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_VIEW_MODE, mode.name).apply()
    }

    fun getViewMode(context: Context): ViewMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeName = prefs.getString(KEY_VIEW_MODE, ViewMode.LIST.name)
        return try {
            ViewMode.valueOf(modeName ?: ViewMode.LIST.name)
        } catch (e: IllegalArgumentException) {
            ViewMode.LIST
        }
    }
}
