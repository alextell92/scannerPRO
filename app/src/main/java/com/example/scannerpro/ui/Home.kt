package com.example.scannerpro.ui

import DocumentWithPages
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.scannerpro.ViewMode // Importamos el Enum desde MainActivity

// IMPORTANTE: Asegúrate de que este import sea correcto
// Debe apuntar al archivo HomeViewModel.kt
import com.example.scannerpro.ui.HomeUiState
import com.example.scannerpro.ui.HomeViewModel


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    // --- PARÁMETROS RECIBIDOS DESDE APPENTRY ---
    homeViewModel: HomeViewModel,
    uiState: HomeUiState,
    selectedDocumentIds: Set<Long>,
    isSelectionModeActive: Boolean,
    viewMode: ViewMode, // Recibe el modo de vista
    onToggleSelection: (Long) -> Unit,
    onStartSelection: (Long) -> Unit,
    onScanNewDocument: () -> Unit,
    onEditDocument: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // El estado de ViewMode y Selección YA NO VIVE AQUÍ

    // Efecto para recargar documentos cuando la pantalla vuelve a estar visible
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.loadDocuments()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // --- EL COMPOSABLE AHORA EMPIEZA CON COLUMN (SIN SCAFFOLD) ---
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // El TopAppBar se fue a MainActivity

        Button(
            onClick = onScanNewDocument,
            modifier = Modifier
                .padding(top = 16.dp)
                .height(50.dp)
        ) {
            Text("Escanear")
        }

        if (uiState.documents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No tienes documentos guardados.")
            }
        } else {

            // --- Lógica de Clic (ahora llama a los lambdas del padre) ---
            val onItemClick: (Long) -> Unit = { id ->
                if (isSelectionModeActive) {
                    onToggleSelection(id)
                } else {
                    onEditDocument(id)
                }
            }

            val onItemLongClick: (Long) -> Unit = { id ->
                if (!isSelectionModeActive) {
                    onStartSelection(id)
                }
            }

            val onCheckClick: (Long) -> Unit = { id ->
                onToggleSelection(id)
            }

            // Este 'when' ahora lee el parámetro 'viewMode'
            when (viewMode) {
                ViewMode.LIST -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp) // Espacio para el BottomNav
                    ) {
                        items(uiState.documents, key = { it.document.id }) { doc ->
                            DocumentItem(
                                documentWithPages = doc,
                                isSelected = doc.document.id in selectedDocumentIds,
                                onClick = { onItemClick(doc.document.id) },
                                onLongClick = { onItemLongClick(doc.document.id) },
                                onCheckClick = { onCheckClick(doc.document.id) }
                            )
                        }
                    }
                }
                ViewMode.GRID -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp) // Espacio para el BottomNav
                    ) {
                        items(uiState.documents, key = { it.document.id }) { doc ->
                            DocumentItemGrid(
                                documentWithPages = doc,
                                isSelected = doc.document.id in selectedDocumentIds,
                                onClick = { onItemClick(doc.document.id) },
                                onLongClick = { onItemLongClick(doc.document.id) },
                                onCheckClick = { onCheckClick(doc.document.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}


// --- DOCUMENT ITEM (LISTA) ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentItem(
    documentWithPages: DocumentWithPages,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheckClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else Color.Transparent
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.LightGray.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (documentWithPages.pages.isNotEmpty()) {
                    val filePath = documentWithPages.pages.first().filePath
                    AsyncImage(
                        model = File(filePath),
                        contentDescription = "Vista previa",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(documentWithPages.document.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    .format(Date(documentWithPages.document.createdAt))
                Text(
                    "${documentWithPages.pages.size} página(s) - $date",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { _ -> onCheckClick() },
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// --- DOCUMENT ITEM (MOSAICO) ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentItemGrid(
    documentWithPages: DocumentWithPages,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheckClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.LightGray.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (documentWithPages.pages.isNotEmpty()) {
                    val filePath = documentWithPages.pages.first().filePath
                    AsyncImage(
                        model = File(filePath),
                        contentDescription = "Vista previa de ${documentWithPages.document.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = "Documento vacío",
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                }

                IconButton(
                    onClick = onCheckClick,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Seleccionar",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    )
                }

            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    documentWithPages.document.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(2.dp))

                val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    .format(Date(documentWithPages.document.createdAt))
                Text(
                    "${documentWithPages.pages.size} pág. - $date",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}