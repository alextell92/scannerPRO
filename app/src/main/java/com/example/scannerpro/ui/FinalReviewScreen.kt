import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.io.File
import java.io.FileOutputStream

private enum class ViewMode { LIST, GRID }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalReviewScreen(
    initialBitmaps: List<Bitmap>,
    onEditRequest: (Int) -> Unit,
    onAddAnotherScan: () -> Unit
) {
    var bitmaps by remember { mutableStateOf(initialBitmaps) }
    var isMarkupMode by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    var selectedIndex by remember { mutableStateOf<Int?>(if (bitmaps.isNotEmpty()) 0 else null) }

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
        } else {
            // Vista de revisión normal con lista/cuadrícula
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Documentos (${bitmaps.size})", color = Color.White) },
                    actions = {
                        IconButton(onClick = {
                            viewMode = if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                        }) {
                            Icon(
                                imageVector = if (viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.List,
                                contentDescription = "Cambiar vista",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2C2C2E))
                )

                if (viewMode == ViewMode.LIST) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(bitmaps) { index, bitmap ->
                            BitmapListItem(
                                bitmap = bitmap,
                                pageNumber = index + 1,
                                isSelected = selectedIndex == index,
                                onClick = { selectedIndex = index }
                            )
                        }
                    }
                } else { // GRID View
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(bitmaps) { index, bitmap ->
                            BitmapGridItem(
                                bitmap = bitmap,
                                pageNumber = index + 1,
                                isSelected = selectedIndex == index,
                                onClick = { selectedIndex = index }
                            )
                        }
                    }
                }
            }
        }


        if (!isMarkupMode) {
            BottomAppBar(
                containerColor = Color(0xFF2C2C2E),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(80.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionButton(icon = Icons.Default.Add, text = "Agregar", onClick = onAddAnotherScan)
                    ActionButton(icon = Icons.Default.Edit, text = "Editar", enabled = selectedIndex != null, onClick = { selectedIndex?.let { onEditRequest(it) } })
                    ActionButton(icon = Icons.Default.Brush, text = "Markup", enabled = selectedIndex != null, onClick = { isMarkupMode = true })
                    ActionButton(icon = Icons.Default.Share, text = "Compartir", enabled = selectedIndex != null, onClick = { selectedIndex?.let { shareBitmap(context, bitmaps[it]) } })
                }
            }
        }
    }
}

@Composable
private fun BitmapListItem(bitmap: Bitmap, pageNumber: Int, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) Color(0xFF30D5C8) else Color.DarkGray
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .border(2.dp, borderColor, shape = CircleShape.copy(all = CornerSize(8.dp)))
            .clip(CircleShape.copy(all = CornerSize(8.dp)))
            .background(Color.DarkGray)
            .clickable(onClick = onClick)
            .padding(8.dp),
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
}

@Composable
private fun BitmapGridItem(bitmap: Bitmap, pageNumber: Int, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) Color(0xFF30D5C8) else Color.DarkGray
    Column(
        modifier = Modifier
            .border(2.dp, borderColor, shape = CircleShape.copy(all = CornerSize(8.dp)))
            .clip(CircleShape.copy(all = CornerSize(8.dp)))
            .background(Color.DarkGray)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Página $pageNumber",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.41f), // A4 aspect ratio
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(8.dp))
        Text("Página $pageNumber", color = Color.White, fontSize = 12.sp)
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
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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


private fun shareBitmap(context: Context, bitmap: Bitmap) {
    val cachePath = File(context.cacheDir, "images")
    cachePath.mkdirs()
    val file = File(cachePath, "shared_image.png")
    val fileOutputStream = FileOutputStream(file)
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
    fileOutputStream.close()

    val fileUri: Uri? = try {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    } catch (e: IllegalArgumentException) {
        Log.e("FileSharing", "File URI creation failed.", e)
        null
    }

    fileUri?.let {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, it)
            type = "image/png"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartir documento"))
    }
}

