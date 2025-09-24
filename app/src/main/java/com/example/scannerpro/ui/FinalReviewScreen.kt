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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitDragOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

import kotlin.math.max
import kotlin.math.min


private enum class ViewMode { LIST, GRID }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalReviewScreen(
    initialBitmaps: List<Bitmap>,
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

    // Estados para el modo de selección
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
        }else
            if (isCollageMode) {
                CollageScreen(
                    initialBitmaps = bitmapsForCollage,
                    onClose = { isCollageMode = false },
                    onSave = { pages ->
                        // pages: List<CollagePageData> donde cada page.items: List<CollageItemData>

                        // 1) Conteos
                        val pageCount = pages.size
                        val totalImages = pages.sumOf { it.items.size }

                        // Mostrar un Toast simple (usa LocalContext en un Composable, aquí asumo que tienes `context`)
                        Toast.makeText(context, "Guardado: $pageCount páginas, $totalImages imágenes", Toast.LENGTH_SHORT).show()

                        // 2) Aplanar todos los bitmaps (si solo necesitas los Bitmaps)
                        val allBitmaps: List<Bitmap> = pages.flatMap { page -> page.items.map { it.bitmap } }

                        // 3) Si quieres además las posiciones (offsets) por página para mantener layout:
                        val pagesWithPositions: List<List<Pair<Bitmap, Offset>>> = pages.map { page ->
                            page.items.map { item -> item.bitmap to item.offset }
                        }

                        // 4) Ejemplo: guardar cada página como un solo Bitmap (puedes implementar tu helper)
                        // pages.forEach { page ->
                        //     val bitmaps = page.items.map { it.bitmap }
                        //     val mergedBitmap = mergeBitmapsIntoOnePage(bitmaps) // implementa este helper si quieres
                        //     // guardar mergedBitmap...
                        // }

                        // 5) Cerrar editor
                        isCollageMode = false
                    }
                )
            } else {

        // Vista de revisión normal con lista/cuadrícula
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
                            // 1. Si está en modo selección, el botón "atrás" cancela la selección
                            IconButton(onClick = {
                                isSelectionModeActive = false
                                selectedIndices = emptySet()
                            }) {
                                Icon(Icons.Default.ArrowBack, "Cancelar selección", tint = Color.White)
                            }
                        } else {
                            // 2. Si está en modo normal, el botón "atrás" llama a onFinish (para ir a Home)
                            IconButton(onClick = onFinish) { // <-- ¡LÍNEA CORREGIDA!
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
                            // --- HEMOS QUITADO EL BOTÓN DE CANCELAR (X) DE AQUÍ ---
                        } else {
                            IconButton(onClick = { isSelectionModeActive = true }) {
                                Icon(Icons.Default.CheckBox, "Seleccionar", tint = Color.White)
                            }
                            IconButton(onClick = {
                                viewMode = if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                                // 2. GUARDAMOS la nueva preferencia
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

                            modifier = Modifier.weight(1f), // <-- Quita el .padding() de aquí
                            // --- AÑADE ESTO ---
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 80.dp // <-- Padding para la barra (80dp + 16dp extra)
                            ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val count = bitmaps.size
                        items(count + 1, key = { index -> if (index < count) bitmaps[index].hashCode() else "add_button_list" }) { index ->
                            if (index < count) {
                                // Es una página existente
                                val bitmap = bitmaps[index]
                                BitmapListItem(
                                    bitmap = bitmap,
                                    pageNumber = index + 1,
                                    isSelected = if (isSelectionModeActive) index in selectedIndices else selectedIndex == index,
                                    isSelectionModeActive = isSelectionModeActive,
                                    onClick = {
                                        if (isSelectionModeActive) {
                                            selectedIndices = if (index in selectedIndices) {
                                                selectedIndices - index
                                            } else {
                                                selectedIndices + index
                                            }
                                        } else {
                                            selectedIndex = index
                                        }
                                    }
                                )
                            } else {
                                // Es el botón de "Añadir Página"
                                AddPageListItem(onClick = onAddAnotherScan)
                            }
                        }
                    }
                } else { // GRID View
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f), // <-- Quita el .padding() de aquí
                        // --- AÑADE ESTO ---
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 96.dp // <-- Padding para la barra (80dp + 16dp extra)
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val count = bitmaps.size
                        // Usamos items(count + 1) en lugar de itemsIndexed
                        items(count + 1, key = { index -> if (index < count) bitmaps[index].hashCode() else "add_button_grid" }) { index ->
                            if (index < count) {
                                // Es una página existente
                                val bitmap = bitmaps[index]
                                BitmapGridItem(
                                    bitmap = bitmap,
                                    pageNumber = index + 1,
                                    isSelected = if (isSelectionModeActive) index in selectedIndices else selectedIndex == index,
                                    isSelectionModeActive = isSelectionModeActive,
                                    onClick = {
                                        if (isSelectionModeActive) {
                                            selectedIndices = if (index in selectedIndices) {
                                                selectedIndices - index
                                            } else {
                                                selectedIndices + index
                                            }
                                        } else {
                                            selectedIndex = index
                                        }
                                    }
                                )
                            } else {
                                // Es el botón de "Añadir Página"
                                AddPageGridItem(onClick = onAddAnotherScan)
                            }
                        }
                    }
                }
                }

        }


        if (!isMarkupMode) {
            // --- ¡CAMBIO GRANDE AQUÍ! ---
            // Reemplazamos BottomAppBar por un Surface para que la altura sea flexible
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter) // Sigue alineado abajo
                    .fillMaxWidth(), // Ocupa todo el ancho
                color = Color(0xFF2C2C2E), // El color de tu barra
                contentColor = Color.White
            ) {
                // La Row que tenías dentro, pero ahora con un padding vertical
                // para que no esté pegada a los bordes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp), // <-- Añade padding vertical
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
                            icon = Icons.Default.AutoAwesomeMosaic, // (Asegúrate de importar esto)
                            text = "Collage",
                            enabled = selectedIndices.isNotEmpty(),
                            onClick = {
                                // Esta es la lógica que ya teníamos:
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
            // --- FIN DEL CAMBIO ---
        }

        if (showShareSheet) {
            ShareBottomSheet(
                onDismiss = { showShareSheet = false },
                selectionCount = selectedIndices.size, // <-- PASAMOS EL CONTEO

                onShareAsPdf = {
                    coroutineScope.launch {
                        showShareSheet = false
                        // Lógica simplificada: siempre usamos selectedIndices
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
                    // ¡AQUÍ ESTÁ EL ARREGLO PRINCIPAL!
                    // Iteramos sobre los índices seleccionados y guardamos cada uno
                    if (selectedIndices.isNotEmpty()) {
                        selectedIndices.forEach { index ->
                            val bitmap = bitmaps[index]
                            // Damos un nombre único a cada archivo
                            saveBitmapToGallery(context, bitmap, "Scan_${System.currentTimeMillis()}_p${index + 1}")
                        }
                    }
                },
                onShareImages = {
                    coroutineScope.launch {
                        showShareSheet = false
                        val bitmapsToShare = bitmaps.filterIndexed { index, _ -> index in selectedIndices }

                        if (bitmapsToShare.size == 1) {
                            // --- LÓGICA DE 1 IMAGEN ---
                            val singleBitmap = bitmapsToShare.first()
                            shareBitmapAsImage(context, singleBitmap)

                        } else if (bitmapsToShare.size > 1) {
                            // --- LÓGICA DE 2+ IMÁGENES (ZIP) ---
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
                icon = Icons.Default.Image, // Siempre el ícono de imagen
                // El texto cambia dinámicamente para informar al usuario
                text = if (selectionCount == 1) "Compartir como Imagen" else "Compartir como ZIP ($selectionCount pág.)",
                onClick = onShareImages,
                enabled = selectionCount > 0
            )
            ShareOption(
                icon = Icons.Default.Download,
                text = "Guardar en Galería (Selección)", // <-- Texto actualizado
                onClick = onSaveToGallery,
                enabled = selectionCount > 0
            )

           // ShareOption(icon = Icons.Default.Description, text = "Compartir como PDF (Selección)", onClick = onShareAsPdf)
//            ShareOption(icon = Icons.Default.Image, text = "Compartir como Imagen (Página actual)", onClick = onShareAsImage)
//            ShareOption(icon = Icons.Default.Download, text = "Guardar en Galería (Página actual)", onClick = onSaveToGallery)
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
        val tint = if (enabled) Color.White else Color.Gray // <-- COLOR CONDICIONAL

        Icon(imageVector = icon, contentDescription = text, tint = Color.White)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = Color.White)
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
                    .aspectRatio(1f / 1.41f), // A4 aspect ratio
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
           // .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
       // verticalArrangement = Arrangement.Center

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
            // A4 page size in points (1/72 inch)
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Scale bitmap to fit page
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
                Color(0xFF444444), // Un borde gris para distinguirlo
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
    // Este Box imita el contenedor de BitmapGridItem
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
            .padding(8.dp) // Igual que en BitmapGridItem
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Esta Columna interior imita la de BitmapGridItem para mantener la proporción
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.41f), // Mantiene la proporción A4
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


/**
 * Crea un archivo ZIP a partir de una lista de bitmaps y devuelve su URI.
 */
private fun createZipFromBitmapsAndGetUri(context: Context, bitmaps: List<Bitmap>, fileName: String = "documento.zip"): Uri? {
    if (bitmaps.isEmpty()) return null

    // Define la ruta del archivo en la caché
    val cachePath = File(context.cacheDir, "documents")
    cachePath.mkdirs()
    val zipFile = File(cachePath, fileName)

    try {
        // Abre los streams para el archivo ZIP
        val fos = FileOutputStream(zipFile)
        val zipOut = ZipOutputStream(fos)

        // Itera sobre cada bitmap para añadirlo al ZIP
        bitmaps.forEachIndexed { index, bitmap ->
            // Define un nombre para el archivo dentro del ZIP (ej. pagina_1.png)
            val entryName = "pagina_${index + 1}.png"
            val zipEntry = ZipEntry(entryName)

            // Añade la nueva entrada (archivo) al ZIP
            zipOut.putNextEntry(zipEntry)

            // Comprime el bitmap (en formato PNG para mantener calidad)
            // directamente en el stream del ZIP
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, zipOut)

            // Cierra la entrada actual
            zipOut.closeEntry()
        }

        // Cierra el stream ZIP (¡importante!)
        zipOut.close()
        fos.close()

        // Devuelve la URI del FileProvider para el archivo .zip
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)

    } catch (e: IOException) {
        Log.e("CreateZip", "Error creando el archivo ZIP", e)
        return null
    }
}

// ... (al final de tu archivo, después de la última '}')

/**
 * Gestiona las preferencias simples de la app usando SharedPreferences.
 */
private object AppPreferences {
    private const val PREFS_NAME = "scanner_prefs"
    private const val KEY_VIEW_MODE = "view_mode"

    /**
     * Guarda la preferencia del modo de vista (LIST o GRID).
     */
    fun setViewMode(context: Context, mode: ViewMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_VIEW_MODE, mode.name).apply()
    }

    /**
     * Obtiene la preferencia guardada. Si no existe, devuelve LIST.
     */
    fun getViewMode(context: Context): ViewMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Usamos LIST.name como valor por defecto si no se encuentra nada
        val modeName = prefs.getString(KEY_VIEW_MODE, ViewMode.LIST.name)
        // Convertimos el nombre (String) de nuevo a nuestro enum ViewMode
        return try {
            ViewMode.valueOf(modeName ?: ViewMode.LIST.name)
        } catch (e: IllegalArgumentException) {
            ViewMode.LIST
        }
    }
}


data class CollageItemData(
    val id: Long = System.nanoTime(),
    val bitmap: Bitmap,
    val offset: Offset = Offset.Zero,
    val size: Size = Size.Zero  // Para snaps
)

data class CollagePageData(
    val id: Long = System.nanoTime(),
    val items: List<CollageItemData>
)

// ---------------- CollagePage (hijo) ----------------
@Composable
fun CollagePage(
    pageIndex: Int,
    pageData: CollagePageData,
    isFirstPage: Boolean,
    isLastPage: Boolean,
    draggingItemId: Long?,
    isDragging: Boolean,
    dragPreviewWidthPx: Float,
    dragPreviewHeightPx: Float,
    modifier: Modifier = Modifier,
    onItemRemoved: (itemId: Long) -> Unit,
    onStartDrag: (itemId: Long, bmp: Bitmap, absolutePointerPos: Offset, sourcePageIndex: Int, imageWidthPx: Float, imageHeightPx: Float, touchOffset: Offset, wasSelected: Boolean) -> Unit,
    onDragMove: (absolutePointerPos: Offset) -> Unit,
    onDrop: () -> Unit,
    onPositioned: (pageIndex: Int, coords: LayoutCoordinates) -> Unit,
    onInitialLayoutComputed: (pageIndex: Int, updatedItems: List<CollageItemData>) -> Unit,
    currentTargetPageIndex: Int?,
    dragPreviewOffsetInTarget: Offset?
) {
    var selectedItemId by remember { mutableStateOf<Long?>(null) }
    var layoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .background(Color.White)
            .clip(RoundedCornerShape(8.dp))
            .clickable { selectedItemId = null }
            .onGloballyPositioned {
                layoutCoords = it
                onPositioned(pageIndex, it)
            }
    ) {
        val canvasWidthPx = constraints.maxWidth.toFloat()
        val canvasHeightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        LaunchedEffect(pageData.items, canvasWidthPx, canvasHeightPx) {
            val itemsToCenter = pageData.items.filter { it.offset == Offset.Zero }

            if (itemsToCenter.isNotEmpty() && canvasHeightPx > 0 && canvasWidthPx > 0) {
                val imageMaxWidthPx = canvasWidthPx * 0.3f
                val spacingPx = with(density) { 16.dp.toPx() }

                val newOffsets = mutableMapOf<Long, Offset>()
                val newSizes = mutableMapOf<Long, Size>()

                val itemHeights = itemsToCenter.map { item ->
                    val aspect = if (item.bitmap.height > 0) item.bitmap.width.toFloat() / item.bitmap.height.toFloat() else 1f
                    imageMaxWidthPx / aspect
                }

                val totalImagesHeight = itemHeights.sum()
                val totalSpacing = (itemsToCenter.size - 1).coerceAtLeast(0) * spacingPx
                val totalBlockHeight = totalImagesHeight + totalSpacing

                var currentY = (canvasHeightPx - totalBlockHeight) / 2f
                if (currentY < 0) currentY = spacingPx

                itemsToCenter.forEachIndexed { index, item ->
                    val imageHeightPx = itemHeights[index]
                    newOffsets[item.id] = Offset(
                        x = (canvasWidthPx - imageMaxWidthPx) / 2f,
                        y = currentY
                    )
                    newSizes[item.id] = Size(imageMaxWidthPx, imageHeightPx)
                    currentY += imageHeightPx + spacingPx
                }

                val updatedItems = pageData.items.map { item ->
                    item.copy(
                        offset = newOffsets[item.id] ?: item.offset,
                        size = newSizes[item.id] ?: item.size
                    )
                }
                onInitialLayoutComputed(pageIndex, updatedItems)
            }
        }

        val closeSizePx = with(density) { 24.dp.toPx() }

        pageData.items.forEach { item ->
            val currentOffset = item.offset
            val isBeingDragged = isDragging && draggingItemId == item.id
            val imageMaxWidthPx = item.size.width.takeIf { it > 0 } ?: (canvasWidthPx * 0.3f)
            val aspect = if (item.bitmap.height > 0) item.bitmap.width.toFloat() / item.bitmap.height.toFloat() else 1f
            val imageHeightPx = item.size.height.takeIf { it > 0 } ?: (imageMaxWidthPx / aspect)
            val imageWidthDp = with(density) { imageMaxWidthPx.toDp() }

            if (!isBeingDragged) {
                Image(
                    bitmap = item.bitmap.asImageBitmap(),
                    contentDescription = "item-${item.id}",
                    modifier = Modifier
                        .width(imageWidthDp)
                        .aspectRatio(aspect)
                        .offset { IntOffset(currentOffset.x.roundToInt(), currentOffset.y.roundToInt()) }
                        .border(
                            width = if (selectedItemId == item.id) 3.dp else 1.dp,
                            color = if (selectedItemId == item.id) Color.Green else Color.Black
                        )
                )
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(currentOffset.x.roundToInt(), currentOffset.y.roundToInt()) }
                    .width(imageWidthDp)
                    .aspectRatio(aspect)
                    .pointerInput(item.id, layoutCoords, currentOffset) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val touchOffsetInsideImage = down.position
                                val wasSelected = selectedItemId == item.id
                                selectedItemId = item.id
                                down.consume()

                                var pointerId = down.id
                                var hasStartedDrag = false

                                if (wasSelected) {
                                    layoutCoords?.let { coords ->
                                        val pointerPositionInPage = currentOffset + down.position
                                        val absolutePointer = coords.localToWindow(pointerPositionInPage)
                                        onStartDrag(
                                            item.id, item.bitmap, absolutePointer, pageIndex,
                                            imageMaxWidthPx, imageHeightPx, touchOffsetInsideImage, wasSelected
                                        )
                                        hasStartedDrag = true
                                    }
                                }

                                while (true) {
                                    val dragChange = awaitDragOrCancellation(pointerId)
                                    if (dragChange == null) {
                                        if (hasStartedDrag) {
                                            onDrop()
                                        }
                                        break
                                    }

                                    layoutCoords?.let { coords ->
                                        val pointerPositionInPage = currentOffset + dragChange.position
                                        val absolute = coords.localToWindow(pointerPositionInPage)

                                        if (!hasStartedDrag) {
                                            onStartDrag(
                                                item.id, item.bitmap, absolute, pageIndex,
                                                imageMaxWidthPx, imageHeightPx, touchOffsetInsideImage, wasSelected
                                            )
                                            hasStartedDrag = true
                                        }

                                        onDragMove(absolute)
                                    }
                                    dragChange.consume()
                                    pointerId = dragChange.id
                                }
                            }
                        }
                    }
            )

            if (selectedItemId == item.id && !isBeingDragged) {
                IconButton(
                    onClick = { onItemRemoved(item.id) },
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (currentOffset.x + imageMaxWidthPx - (closeSizePx / 2f)).roundToInt(),
                                (currentOffset.y - (closeSizePx / 2f)).roundToInt()
                            )
                        }
                        .size(24.dp)
                        .background(Color.Red, CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Eliminar", tint = Color.White)
                }
            }
        }

        // NEW: Snap guides
        val snapThresholdPx = 5f
        // CORREGIDO: Se inicializa el estado con una lista vacía.
        var horizontalSnapLines by remember { mutableStateOf(emptyList<Pair<Float, Float>>()) }  // (y, startX, endX)
        var verticalSnapLines by remember { mutableStateOf(emptyList<Pair<Float, Float>>()) }    // (x, startY, endY)

        LaunchedEffect(isDragging, dragPreviewOffsetInTarget, pageData.items, dragPreviewWidthPx, dragPreviewHeightPx) {
            if (currentTargetPageIndex != pageIndex || dragPreviewOffsetInTarget == null || !isDragging) {
                horizontalSnapLines = emptyList()
                verticalSnapLines = emptyList()
                return@LaunchedEffect
            }

            val otherItems = pageData.items.filter { it.id != draggingItemId && it.size != Size.Zero }
            val previewX = dragPreviewOffsetInTarget.x
            val previewY = dragPreviewOffsetInTarget.y
            val previewW = dragPreviewWidthPx
            val previewH = dragPreviewHeightPx

            val hSnaps = mutableListOf<Pair<Float, Float>>()
            val vSnaps = mutableListOf<Pair<Float, Float>>()

            otherItems.forEach { other ->
                val oX = other.offset.x
                val oY = other.offset.y
                val oW = other.size.width
                val oH = other.size.height

                // Vertical alignments (vertical lines)
                listOf(oX, oX + oW / 2f, oX + oW).forEach { snapX ->
                    listOf(previewX, previewX + previewW / 2f, previewX + previewW).forEach { previewSnapX ->
                        if (abs(previewSnapX - snapX) <= snapThresholdPx) {
                            val minY = min(previewY, oY)
                            val maxY = max(previewY + previewH, oY + oH)
                            vSnaps.add(snapX to (maxY - minY))
                        }
                    }
                }

                // Horizontal alignments (horizontal lines)
                listOf(oY, oY + oH / 2f, oY + oH).forEach { snapY ->
                    listOf(previewY, previewY + previewH / 2f, previewY + previewH).forEach { previewSnapY ->
                        if (abs(previewSnapY - snapY) <= snapThresholdPx) {
                            val minX = min(previewX, oX)
                            val maxX = max(previewX + previewW, oX + oW)
                            hSnaps.add(snapY to (maxX - minX))
                        }
                    }
                }
            }

            horizontalSnapLines = hSnaps.distinct()
            verticalSnapLines = vSnaps.distinct()
        }

        if (horizontalSnapLines.isNotEmpty() || verticalSnapLines.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)

                horizontalSnapLines.forEach { (y, length) ->
                    val allItemsX = pageData.items
                        .filter { it.size != Size.Zero }
                        .flatMap { listOf(it.offset.x, it.offset.x + it.size.width) }
                    val minX = allItemsX.minOrNull() ?: 0f
                    val maxX = allItemsX.maxOrNull() ?: canvasWidthPx

                    drawLine(
                        color = Color.Blue,
                        start = Offset(minX, y),
                        end = Offset(maxX, y),
                        strokeWidth = 2f,
                        pathEffect = dashEffect
                    )
                }

                verticalSnapLines.forEach { (x, length) ->
                    val allItemsY = pageData.items
                        .filter { it.size != Size.Zero }
                        .flatMap { listOf(it.offset.y, it.offset.y + it.size.height) }
                    val minY = allItemsY.minOrNull() ?: 0f
                    val maxY = allItemsY.maxOrNull() ?: canvasHeightPx
                    drawLine(
                        color = Color.Blue,
                        start = Offset(x, minY),
                        end = Offset(x, maxY),
                        strokeWidth = 2f,
                        pathEffect = dashEffect
                    )
                }
            }
        }
    }
}

// ---------------- CollageScreen (padre) ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(
    initialBitmaps: List<Bitmap>,
    onClose: () -> Unit,
    onSave: (List<CollagePageData>) -> Unit
) {
    var pages by remember {
        mutableStateOf(
            initialBitmaps.chunked(2).map { chunk ->
                CollagePageData(items = chunk.map { CollageItemData(bitmap = it) })
            }
        )
    }

    var draggingItemId by remember { mutableStateOf<Long?>(null) }
    var dragPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPointerPosition by remember { mutableStateOf(Offset.Zero) }
    var dragTouchOffsetInsideImage by remember { mutableStateOf(Offset.Zero) }
    var sourcePageIndexForDrag by remember { mutableStateOf<Int?>(null) }
    var dragPreviewWidthPx by remember { mutableStateOf(0f) }
    var dragPreviewHeightPx by remember { mutableStateOf(0f) }
    var dragPreviewShowBorder by remember { mutableStateOf(false) }
    var currentTargetPageIndex by remember { mutableStateOf<Int?>(null) }

    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var listLayoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val pageCoords = remember { mutableStateMapOf<Int, LayoutCoordinates>() }

    LaunchedEffect(isDragging, dragPointerPosition) {
        if (!isDragging) return@LaunchedEffect
        while (isActive) {
            listLayoutCoords?.let { coords ->
                val listTop = coords.localToWindow(Offset.Zero).y
                val listBottom = listTop + coords.size.height
                val threshold = coords.size.height * 0.15f
                val scrollAmount = 40f

                if (dragPointerPosition.y < listTop + threshold) {
                    scope.launch { lazyListState.animateScrollBy(-scrollAmount) }
                } else if (dragPointerPosition.y > listBottom - threshold) {
                    scope.launch { lazyListState.animateScrollBy(scrollAmount) }
                }
            }
            delay(50)
        }
    }

    Column(Modifier.fillMaxSize().background(Color.DarkGray)) {
        TopAppBar(
            title = {
                val pageCount = pages.size
                val currentPage = (lazyListState.firstVisibleItemIndex + 1).coerceIn(1, pageCount)
                Text("Editor ($currentPage / $pageCount)", color = Color.White)
            },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar", tint = Color.White) } },
            actions = {
                IconButton(onClick = { onSave(pages) }, enabled = pages.any { it.items.isNotEmpty() }) { Icon(Icons.Default.Check, "Guardar", tint = Color.White) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2C2C2E))
        )

        Box(Modifier.fillMaxSize()) {
            var dragContainerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { listLayoutCoords = it },
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(count = pages.size, key = { pages[it].id }) { pageIndex ->
                    val page = pages[pageIndex]
                    CollagePage(
                        pageIndex = pageIndex,
                        pageData = page,
                        isFirstPage = pageIndex == 0,
                        isLastPage = pageIndex == pages.lastIndex,
                        draggingItemId = draggingItemId,
                        isDragging = isDragging,
                        dragPreviewWidthPx = dragPreviewWidthPx,
                        dragPreviewHeightPx = dragPreviewHeightPx,
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .aspectRatio(1f / 1.41f),
                        onItemRemoved = { itemIdToRemove ->
                            pages = pages.map { p ->
                                p.copy(items = p.items.filterNot { it.id == itemIdToRemove })
                            }
                        },
                        onStartDrag = { itemId, bmp, absolutePointerPos, sourcePage, imageWpx, imageHpx, touchOffset, wasSelected ->
                            draggingItemId = itemId
                            dragPreviewBitmap = bmp
                            isDragging = true
                            dragPointerPosition = absolutePointerPos
                            sourcePageIndexForDrag = sourcePage
                            dragTouchOffsetInsideImage = touchOffset
                            dragPreviewWidthPx = imageWpx
                            dragPreviewHeightPx = imageHpx
                            dragPreviewShowBorder = wasSelected
                        },
                        onDragMove = { absolutePointerPos ->
                            dragPointerPosition = absolutePointerPos
                            currentTargetPageIndex = pageCoords.entries
                                .filter { it.value.isAttached }
                                .minByOrNull { (_, layoutCoordinates) ->
                                    val pageTop = layoutCoordinates.localToWindow(Offset.Zero).y
                                    val pageBottom = pageTop + layoutCoordinates.size.height
                                    if (absolutePointerPos.y in pageTop..pageBottom) 0f
                                    else min(abs(pageTop - absolutePointerPos.y), abs(pageBottom - absolutePointerPos.y))
                                }?.key
                        },
                        onDrop = {
                            val itemId = draggingItemId ?: return@CollagePage
                            val bmp = dragPreviewBitmap ?: return@CollagePage

                            val targetPageIndex = pageCoords.entries
                                .filter { it.value.isAttached }
                                .minByOrNull { (_, layoutCoordinates) ->
                                    val pageTop = layoutCoordinates.localToWindow(Offset.Zero).y
                                    val pageBottom = pageTop + layoutCoordinates.size.height
                                    if (dragPointerPosition.y in pageTop..pageBottom) 0f
                                    else min(abs(pageTop - dragPointerPosition.y), abs(pageBottom - dragPointerPosition.y))
                                }?.key

                            if (targetPageIndex == null) {
                                isDragging = false
                                draggingItemId = null
                                dragPreviewBitmap = null
                                return@CollagePage
                            }

                            val targetCoords = pageCoords[targetPageIndex]!!
                            val localPosInTarget = targetCoords.windowToLocal(dragPointerPosition)
                            val newOffset = localPosInTarget - dragTouchOffsetInsideImage

                            val finalItem = CollageItemData(id = itemId, bitmap = bmp, offset = newOffset, size = Size(dragPreviewWidthPx, dragPreviewHeightPx))

                            pages = pages.mapIndexed { index, pageData ->
                                val itemsWithoutDragged = pageData.items.filterNot { it.id == itemId }

                                if (index == targetPageIndex) {
                                    pageData.copy(items = itemsWithoutDragged + finalItem)
                                } else {
                                    pageData.copy(items = itemsWithoutDragged)
                                }
                            }

                            isDragging = false
                            draggingItemId = null
                            dragPreviewBitmap = null
                        },
                        onPositioned = { idx, coords ->
                            pageCoords[idx] = coords
                        },
                        onInitialLayoutComputed = { idx, updatedItems ->
                            pages = pages.toMutableList().apply {
                                val oldPage = this[idx]
                                this[idx] = oldPage.copy(items = updatedItems)
                            }.toList()
                        },
                        currentTargetPageIndex = currentTargetPageIndex,
                        dragPreviewOffsetInTarget = if (currentTargetPageIndex == pageIndex && isDragging) {
                            pageCoords[pageIndex]?.windowToLocal(dragPointerPosition)?.minus(dragTouchOffsetInsideImage) ?: Offset.Zero
                        } else null
                    )
                }

                item {
                    AddPageCanvas(onClick = {
                        pages = pages + CollagePageData(items = emptyList())
                    }, modifier = Modifier.fillParentMaxWidth().height(150.dp))
                }
            }

            if (isDragging && dragPreviewBitmap != null) {
                val density = LocalDensity.current
                val containerBasePosition = dragContainerCoords?.localToWindow(Offset.Zero) ?: Offset.Zero
                val adjustedPointerX = dragPointerPosition.x - containerBasePosition.x
                val adjustedPointerY = dragPointerPosition.y - containerBasePosition.y
                val offsetLeft = with(density) { (adjustedPointerX - dragTouchOffsetInsideImage.x).toDp() }
                val offsetTop = with(density) { (adjustedPointerY - dragTouchOffsetInsideImage.y).toDp() }
                val widthDp = with(density) { dragPreviewWidthPx.toDp() }
                val heightDp = with(density) { dragPreviewHeightPx.toDp() }

                Image(
                    bitmap = dragPreviewBitmap!!.asImageBitmap(),
                    contentDescription = "Arrastrando item",
                    modifier = Modifier
                        .offset(x = offsetLeft, y = offsetTop)
                        .size(widthDp, heightDp)
                        .shadow(12.dp, RoundedCornerShape(4.dp))
                        .border(
                            width = if (dragPreviewShowBorder) 3.dp else 1.dp,
                            color = if (dragPreviewShowBorder) Color.Green else Color.Black.copy(alpha = 0.5f)
                        )
                        .zIndex(10f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { dragContainerCoords = it }
            )
        }
    }
}

@Composable
fun AddPageCanvas(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Add, contentDescription = "Añadir Página", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text("Añadir Página", color = Color.White.copy(alpha = 0.8f))
        }
    }
}
