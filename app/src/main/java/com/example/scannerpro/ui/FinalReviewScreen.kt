package com.example.scannerpro.ui

import DocumentRepository
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.scannerpro.Collage.CollageScreen
import com.example.scannerpro.MarkUp.MarkupScreen
import com.example.scannerpro.signature.SignatureEditorScreen
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


private enum class ViewMode { LIST, GRID, PAGE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalReviewScreen(
    initialBitmaps: List<Bitmap>,
    repository: DocumentRepository,
    onEditRequest: (Int) -> Unit,
    onAddAnotherScan: () -> Unit,
    onFinish: () -> Unit
) {
    var bitmaps by remember { mutableStateOf(initialBitmaps) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var viewMode by rememberSaveable { mutableStateOf(AppPreferences.getViewMode(context)) }
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(if (bitmaps.isNotEmpty()) 0 else null) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }

    var showShareSheet by rememberSaveable { mutableStateOf(false) }
    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    var selectedIndices by rememberSaveable { mutableStateOf<Set<Int>>(emptySet()) }

    var isCollageMode by rememberSaveable { mutableStateOf(false) }
    var bitmapsForCollage by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    var showMarkupSubMenu by rememberSaveable { mutableStateOf(false) }
    var isMarkupMode by rememberSaveable { mutableStateOf(false) }
    var isSignatureMode by rememberSaveable { mutableStateOf(false) }
    var currentPageInPageView by rememberSaveable { mutableStateOf<Int?>(if (bitmaps.isNotEmpty()) 0 else null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF212121))
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
        } else if (isSignatureMode) {
            // Simplemente reemplaza la llamada a SignatureScreen por SignatureEditorScreen
            SignatureEditorScreen(
                baseBitmaps = bitmaps,
                initialPageIndex = currentPageInPageView ?: selectedIndex ?: 0,
                onComplete = { pageIndex, newBitmap ->
                    // Esta lógica para actualizar el bitmap sigue igual
                    bitmaps = bitmaps.toMutableList().also { it[pageIndex] = newBitmap }
                    isSignatureMode = false
                },
                onCancel = {
                    // Esta lógica para cancelar sigue igual
                    isSignatureMode = false
                }
            )
        } else if (isCollageMode) {
            CollageScreen(
                initialBitmaps = bitmapsForCollage,
                repository = repository,
                onClose = { isCollageMode = false },
                onSave = { isCollageMode = false }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        val titleText = when {
                            isSelectionModeActive -> "${selectedIndices.size} seleccionados"
                            isEditMode && selectedIndex != null -> "Página ${selectedIndex!! + 1} de ${bitmaps.size}"
                            viewMode == ViewMode.PAGE && currentPageInPageView != null && !isEditMode -> "Página ${currentPageInPageView!! + 1} de ${bitmaps.size}"
                            else -> "Documentos (${bitmaps.size})"
                        }
                        Text(titleText, color = Color.White)
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            when {
                                isSelectionModeActive -> {
                                    isSelectionModeActive = false
                                    selectedIndices = emptySet()
                                }
                                isEditMode -> isEditMode = false
                                else -> onFinish()
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, "Atrás", tint = Color.White)
                        }
                    },
                    actions = {
                        if (isSelectionModeActive) {
                            IconButton(onClick = {
                                if (viewMode == ViewMode.PAGE) {
                                    viewMode = ViewMode.GRID
                                    AppPreferences.setViewMode(context, ViewMode.GRID)
                                }
                                selectedIndices = if (selectedIndices.size == bitmaps.size) emptySet() else bitmaps.indices.toSet()
                            }) {
                                Icon(Icons.Default.SelectAll, "Seleccionar todo", tint = Color.White)
                            }
                        } else if (!isEditMode) {
                            IconButton(onClick = {
                                if (viewMode == ViewMode.PAGE) {
                                    viewMode = ViewMode.GRID
                                }
                                isSelectionModeActive = true
                            }) {
                                Icon(Icons.Default.CheckBox, "Seleccionar", tint = Color.White)
                            }
                            IconButton(onClick = {
                                viewMode = when (viewMode) {
                                    ViewMode.LIST -> ViewMode.GRID
                                    ViewMode.GRID -> ViewMode.PAGE
                                    ViewMode.PAGE -> ViewMode.LIST
                                }
                                AppPreferences.setViewMode(context, viewMode)
                            }) {
                                val icon = when (viewMode) {
                                    ViewMode.LIST -> Icons.Default.GridView
                                    ViewMode.GRID -> Icons.Default.Image
                                    ViewMode.PAGE -> Icons.Default.List
                                }
                                Icon(icon, "Cambiar vista", tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2C2C2E))
                )

                Box(modifier = Modifier.weight(1f)) {
                    if (isEditMode && bitmaps.isNotEmpty()) {
                        val pagerState = rememberPagerState(initialPage = selectedIndex ?: 0) { bitmaps.size }
                        LaunchedEffect(pagerState.currentPage) {
                            selectedIndex = pagerState.currentPage
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(0.dp),
                        ) { page ->
                            Image(
                                bitmap = bitmaps[page].asImageBitmap(),
                                contentDescription = "Página ${page + 1}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else {
                        when (viewMode) {
                            ViewMode.LIST -> ListView(bitmaps, isSelectionModeActive, selectedIndices, selectedIndex, { index: Int ->
                                if (isSelectionModeActive) {
                                    selectedIndices = if (index in selectedIndices) selectedIndices - index else selectedIndices + index
                                } else {
                                    selectedIndex = index
                                    isEditMode = true
                                }
                            }, onAddAnotherScan)
                            ViewMode.GRID -> GridView(bitmaps, isSelectionModeActive, selectedIndices, selectedIndex, { index: Int ->
                                if (isSelectionModeActive) {
                                    selectedIndices = if (index in selectedIndices) selectedIndices - index else selectedIndices + index
                                } else {
                                    selectedIndex = index
                                    isEditMode = true
                                }
                            }, onAddAnotherScan)
                            ViewMode.PAGE -> PageView(
                                bitmaps = bitmaps,
                                onItemClick = { index: Int ->
                                    selectedIndex = index
                                    isEditMode = true
                                },
                                onAddClick = onAddAnotherScan,
                                onVisiblePageChange = { pageIndex: Int ->
                                    currentPageInPageView = pageIndex
                                }
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF2C2C2E),
                    contentColor = Color.White
                ) {
                    when {
                        isSelectionModeActive -> SelectionBottomMenu(
                            enabled = selectedIndices.isNotEmpty(),
                            onDelete = {
                                bitmaps = bitmaps.filterIndexed { index, _ -> index !in selectedIndices }
                                selectedIndices = emptySet()
                                isSelectionModeActive = false
                                selectedIndex = if (bitmaps.isNotEmpty()) 0 else null
                            },
                            onCollage = {
                                bitmapsForCollage = bitmaps.filterIndexed { index, _ -> index in selectedIndices }
                                isCollageMode = true
                                isSelectionModeActive = false
                                selectedIndices = emptySet()
                            },
                            onShare = { if (selectedIndices.isNotEmpty()) showShareSheet = true }
                        )
                        isEditMode -> MainBottomMenu(
                            context = context,
                            bitmapsNotEmpty = bitmaps.isNotEmpty(),
                            onEdit = { selectedIndex?.let { onEditRequest(it) } },
                            onSave = {
                                bitmaps.forEachIndexed { index, bitmap ->
                                    saveBitmapToGallery(context, bitmap, "Scan_${System.currentTimeMillis()}_p${index + 1}")
                                }
                            },
                            onShare = {
                                if (bitmaps.isNotEmpty()) {
                                    selectedIndices = bitmaps.indices.toSet()
                                    showShareSheet = true
                                }
                            }
                        )
                        else -> EditBottomMenu(
                            context = context,
                            viewMode = viewMode,
                            markupEnabled = currentPageInPageView != null,
                            shareEnabled = bitmaps.isNotEmpty(),
                            onAdd = onAddAnotherScan,
                            onMarkup = { showMarkupSubMenu = true },
                            onShare = {
                                if (bitmaps.isNotEmpty()) {
                                    selectedIndices = bitmaps.indices.toSet()
                                    showShareSheet = true
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showShareSheet) {
            ShareBottomSheet(
                bitmaps = bitmaps,
                selectedIndices = selectedIndices,
                onSelectionChange = { newSelection -> selectedIndices = newSelection },
                onDismiss = {
                    showShareSheet = false
                    if (!isSelectionModeActive) selectedIndices = emptySet()
                },
                onShareAsPdf = {
                    coroutineScope.launch {
                        showShareSheet = false
                        val bitmapsToShare = bitmaps.filterIndexed { index, _ -> index in selectedIndices }
                        if (bitmapsToShare.isNotEmpty()) {
                            val pdfUri = createPdfFromBitmapsAndGetUri(context, bitmapsToShare)
                            pdfUri?.let { uri -> shareUri(context, uri, "application/pdf") } ?: Toast.makeText(context, "Error al crear el PDF", Toast.LENGTH_SHORT).show()
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
                            zipUri?.let { uri -> shareUri(context, uri, "application/zip") } ?: Toast.makeText(context, "Error al crear el ZIP", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        if (showMarkupSubMenu) {
            MarkupSubMenuSheet(
                context = context,
                onDismiss = { showMarkupSubMenu = false },
                onDrawClick = {
                    showMarkupSubMenu = false
                    val pageToEdit = currentPageInPageView ?: selectedIndex ?: if (bitmaps.isNotEmpty()) 0 else null
                    pageToEdit?.let {
                        selectedIndex = it
                        isMarkupMode = true
                    }
                },
                onSignClick = {
                    showMarkupSubMenu = false
                    val pageToEdit = currentPageInPageView ?: selectedIndex ?: if (bitmaps.isNotEmpty()) 0 else null
                    pageToEdit?.let {
                        selectedIndex = it
                        isSignatureMode = true
                    }
                }
            )
        }
    }
}

@Composable
private fun MainBottomMenu(context: Context, bitmapsNotEmpty: Boolean, onEdit: () -> Unit, onSave: () -> Unit, onShare: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        ActionButton(icon = Icons.Default.Edit, text = "Editar", enabled = bitmapsNotEmpty, onClick = onEdit)
        ActionButton(icon = Icons.Default.Description, text = "Word", onClick = { Toast.makeText(context, "Próximamente", Toast.LENGTH_SHORT).show() })
        ActionButton(icon = Icons.Default.Download, text = "Guardar", enabled = bitmapsNotEmpty, onClick = onSave)
        ActionButton(icon = Icons.Default.Share, text = "Compartir", enabled = bitmapsNotEmpty, onClick = onShare)
    }
}

@Composable
private fun EditBottomMenu(
    context: Context,
    viewMode: ViewMode,
    markupEnabled: Boolean,
    shareEnabled: Boolean,
    onAdd: () -> Unit,
    onMarkup: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(icon = Icons.Default.Add, text = "Agregar", onClick = onAdd)
        ActionButton(icon = Icons.Default.Edit, text = "Editar PDF", enabled = shareEnabled, onClick = { Toast.makeText(context, "Próximamente: Editar PDF", Toast.LENGTH_SHORT).show() })
        ActionButton(icon = Icons.Default.Share, text = "Compartir", enabled = shareEnabled, onClick = onShare)
        ActionButton(icon = Icons.Default.Description, text = "Word", enabled = shareEnabled, onClick = { Toast.makeText(context, "Próximamente: Exportar a Word", Toast.LENGTH_SHORT).show() })
        if (viewMode == ViewMode.PAGE) {
            ActionButton(icon = Icons.Default.Brush, text = "Markup", enabled = markupEnabled, onClick = onMarkup)
        }
    }
}

@Composable
private fun SelectionBottomMenu(enabled: Boolean, onDelete: () -> Unit, onCollage: () -> Unit, onShare: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        ActionButton(icon = Icons.Default.Delete, text = "Eliminar", enabled = enabled, onClick = onDelete)
        ActionButton(icon = Icons.Default.AutoAwesomeMosaic, text = "Collage", enabled = enabled, onClick = onCollage)
        ActionButton(icon = Icons.Default.Share, text = "Compartir", enabled = enabled, onClick = onShare)
    }
}

@Composable
private fun ListView(bitmaps: List<Bitmap>, isSelectionModeActive: Boolean, selectedIndices: Set<Int>, selectedIndex: Int?, onItemClick: (Int) -> Unit, onAddClick: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(bitmaps.size) { index -> BitmapListItem(bitmaps[index], index + 1, if (isSelectionModeActive) index in selectedIndices else selectedIndex == index, isSelectionModeActive) { onItemClick(index) } }
        item { AddPageListItem(onClick = onAddClick) }
    }
}

@Composable
private fun GridView(bitmaps: List<Bitmap>, isSelectionModeActive: Boolean, selectedIndices: Set<Int>, selectedIndex: Int?, onItemClick: (Int) -> Unit, onAddClick: () -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(bitmaps.size) { index -> BitmapGridItem(bitmaps[index], index + 1, if (isSelectionModeActive) index in selectedIndices else selectedIndex == index, isSelectionModeActive) { onItemClick(index) } }
        item { AddPageGridItem(onClick = onAddClick) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageView(
    bitmaps: List<Bitmap>,
    onItemClick: (Int) -> Unit,
    onAddClick: () -> Unit,
    onVisiblePageChange: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex < bitmaps.size) {
            onVisiblePageChange(listState.firstVisibleItemIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()

    ) {
        items(
            count = bitmaps.size,
            key = { index -> bitmaps[index] }
        ) { index ->
            Box(
                modifier = Modifier
                    .fillParentMaxSize()
                    .padding(bottom = 8.dp)
                    .clickable { onItemClick(index) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                ) {
                    Image(
                        bitmap = bitmaps[index].asImageBitmap(),
                        contentDescription = "Página ${index + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
        item {
            Box(
                modifier = Modifier

                    .padding( top = 6.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                AddPageFullScreenItem(onClick = onAddClick)
            }
        }
    }
}

@Composable
private fun BitmapListItem(bitmap: Bitmap, pageNumber: Int, isSelected: Boolean, isSelectionModeActive: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val borderColor = if (isSelected && !isSelectionModeActive) Color(0xFF30D5C8) else Color.Transparent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .border(2.dp, borderColor, shape = RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
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
private fun BitmapGridItem(bitmap: Bitmap, pageNumber: Int, isSelected: Boolean, isSelectionModeActive: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val borderColor = if (isSelected && !isSelectionModeActive) Color(0xFF30D5C8) else Color.Transparent
    Box(
        modifier = modifier
            .border(2.dp, borderColor, shape = RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
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
private fun AddPageListItem(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .border(
                2.dp,
                Color(0xFF444444),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Add, "Añadir Página", tint = Color.Gray, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Añadir Página", color = Color.Gray)
        }
    }
}
@Composable
private fun AddPageGridItem(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .border(
                2.dp,
                Color(0xFF444444),
                shape = RoundedCornerShape(8.dp)
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
            Icon(Icons.Default.Add, "Añadir Página", tint = Color.Gray, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text("Añadir Página", color = Color.Gray, fontSize = 12.sp)
        }
    }
}
@Composable
private fun AddPageFullScreenItem(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .border(
                2.dp,
                Color(0xFF444444),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Add, "Añadir Página", tint = Color.Gray, modifier = Modifier.size(45.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Añadir Página", color = Color.Gray, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkupSubMenuSheet(
    context: Context,
    onDismiss: () -> Unit,
    onDrawClick: () -> Unit,
    onSignClick: () -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = Color(0xFF2C2C2E)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Markup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
            ShareOption(icon = Icons.Default.Edit, text = "Firmar", onClick = onSignClick)
            ShareOption(icon = Icons.Default.Draw, text = "Dibujar", onClick = onDrawClick)
            ShareOption(icon = Icons.Default.WaterDrop, text = "Marca de Agua", onClick = { Toast.makeText(context, "Próximamente", Toast.LENGTH_SHORT).show() })
            ShareOption(icon = Icons.Default.FormatColorText, text = "Agregar Texto", onClick = { Toast.makeText(context, "Próximamente", Toast.LENGTH_SHORT).show() })
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShareBottomSheet(bitmaps: List<Bitmap>, selectedIndices: Set<Int>, onSelectionChange: (Set<Int>) -> Unit, onDismiss: () -> Unit, onShareAsPdf: () -> Unit, onShareImages: () -> Unit, onSaveToGallery: () -> Unit) {
    val selectionCount = selectedIndices.size
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
        ) {
            if (bitmaps.size > 1) {
                SelectablePagesRow(
                    bitmaps = bitmaps,
                    selectedIndices = selectedIndices,
                    onSelectionChange = onSelectionChange
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF2C2C2E),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Compartir", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        if (bitmaps.size > 1) {
                            val allSelected = selectedIndices.size == bitmaps.size
                            val buttonText = if (allSelected) "Deseleccionar" else "Seleccionar todo"
                            Text(
                                text = buttonText,
                                color = Color(0xFF30D5C8),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    onSelectionChange(
                                        if (allSelected) emptySet() else bitmaps.indices.toSet()
                                    )
                                }
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        val pdfText = "Compartir como PDF" + if (selectionCount > 0) " ($selectionCount pág.)" else ""
                        ShareOption(icon = Icons.Default.Description, text = pdfText, onClick = onShareAsPdf, enabled = selectionCount > 0)

                        ShareOption(
                            icon = Icons.Default.Image,
                            text = if (selectionCount == 1) "Compartir como Imagen" else "Compartir como ZIP ($selectionCount pág.)",
                            onClick = onShareImages,
                            enabled = selectionCount > 0
                        )

                        ShareOption(
                            icon = Icons.Default.Download,
                            text = "Guardar en Galería",
                            onClick = onSaveToGallery,
                            enabled = selectionCount > 0
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
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

@Composable
private fun SelectablePageThumbnail(bitmap: Bitmap, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(80.dp)
            .aspectRatio(1f / 1.41f)
            .clip(RoundedCornerShape(4.dp))
            .border(
                width = 2.dp,
                color = if (isSelected) Color(0xFF30D5C8) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Page thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF30D5C8), CircleShape)
                        .padding(4.dp)
                )
            }
        }
    }
}
@Composable
private fun SelectablePagesRow(bitmaps: List<Bitmap>, selectedIndices: Set<Int>, onSelectionChange: (Set<Int>) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(bitmaps.size) { index ->
            SelectablePageThumbnail(
                bitmap = bitmaps[index],
                isSelected = index in selectedIndices,
                onClick = {
                    val newSelection = selectedIndices.toMutableSet()
                    if (index in newSelection) {
                        newSelection.remove(index)
                    } else {
                        newSelection.add(index)
                    }
                    onSelectionChange(newSelection)
                }
            )
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
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

