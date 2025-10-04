@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.scannerpro

import DocumentWithPages
import Document
import Page
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape // <-- NUEVO IMPORT
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip // <-- NUEVO IMPORT
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale // <-- NUEVO IMPORT
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage // <-- NUEVO IMPORT (LIBRERÍA COIL)
import com.example.scannerpro.scanner.DocumentScannerScreen
import com.example.scannerpro.ui.HomeScreen
import com.example.scannerpro.ui.HomeUiState
import com.example.scannerpro.ui.HomeViewModel
import com.example.scannerpro.ui.theme.ScannerPROTheme
import org.opencv.android.OpenCVLoader
import java.io.File // <-- NUEVO IMPORT

// --- Clases de Navegación y Modelo ---

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Camara : Screen("camara")
    object Archivos : Screen("archivos")
    object Usuario : Screen("usuario")
    object Acciones : Screen("herramientas")
}

data class BottomNavItem(@DrawableRes val iconRes: Int, val screen: Screen, val label: String)

const val SCANNER_ARG_ID = "documentId"

// --- Estado de Vista (Movido aquí para que AppEntry lo controle) ---
enum class ViewMode { LIST, GRID }
private const val VIEW_MODE_PREFS = "view_mode_preferences"
private const val KEY_VIEW_MODE = "key_view_mode"


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "OpenCV se ha cargado exitosamente (initDebug).")
        } else {
            Log.e("OpenCV", "initDebug falló — intentando carga manual de la lib.")
            try {
                System.loadLibrary("opencv_java4") // nombre típico; ajusta si usas otro
                Log.d("OpenCV", "Cargada libopencv_java4 vía System.loadLibrary.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("OpenCV", "Carga manual falló", e)
            }
        }


        val libDir = applicationInfo.nativeLibraryDir
        Log.d("OpenCV", "SUPPORTED_ABIS: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        Log.d("OpenCV", "nativeLibraryDir = $libDir")
        val files = File(libDir).listFiles()?.joinToString { it.name } ?: "empty"
        Log.d("OpenCV", "native libs at nativeLibraryDir: $files")



        enableEdgeToEdge()
        setContent {
            ScannerPROTheme {
                AppEntry()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEntry() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // --- 1. ESTADO Y VIEWMODEL ELEVADOS ---
    val homeViewModel: HomeViewModel = viewModel()
    val uiState by homeViewModel.uiState.collectAsState()
    var selectedDocumentIds by remember { mutableStateOf(emptySet<Long>()) }
    val isSelectionModeActive = selectedDocumentIds.isNotEmpty()
    var showMoreMenuSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMergeSheet by remember { mutableStateOf(false) }

    // Estado de ViewMode (Lista/Grid) ahora vive aquí
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences(VIEW_MODE_PREFS, Context.MODE_PRIVATE)
    }
    var viewMode by remember {
        val savedModeName = sharedPreferences.getString(KEY_VIEW_MODE, ViewMode.LIST.name)
        mutableStateOf(ViewMode.valueOf(savedModeName ?: ViewMode.LIST.name))
    }


    Scaffold(
        topBar = {
            // --- 2. TOPBAR CONDICIONAL ---
            if (currentRoute?.startsWith(Screen.Camara.route) == false) {
                if (isSelectionModeActive) {
                    // TopBar de Selección
                    SelectionTopBar(
                        selectedCount = selectedDocumentIds.size,
                        onClearSelection = { selectedDocumentIds = emptySet() },
                        onSelectAll = {
                            val allIds = uiState.documents.map { it.document.id }.toSet()
                            selectedDocumentIds = if (selectedDocumentIds == allIds) emptySet() else allIds
                        }
                    )
                } else {
                    // TopBar Normal (con el botón de vista)
                    NormalTopBar(
                        viewMode = viewMode,
                        onViewModeToggle = {
                            val newMode = if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                            viewMode = newMode
                            sharedPreferences.edit().putString(KEY_VIEW_MODE, newMode.name).apply()
                        }
                    )
                }
            }
        },
        bottomBar = {
            // --- 3. BOTTOMBAR CONDICIONAL ---
            if (currentRoute?.startsWith(Screen.Camara.route) == false) {
                if (isSelectionModeActive) {
                    // Barra de Acciones de Selección
                    SelectionBottomBar(
                        selectedCount = selectedDocumentIds.size,
                        onShareClick = { /* TODO */ },
                        onCombineClick = {
                            showMergeSheet = true
                        },
                        onRenameClick = { showRenameDialog = true },
                        onDeleteClick = { showDeleteDialog = true },
                        onMoreClick = { showMoreMenuSheet = true }
                    )
                } else {
                    // Barra de Navegación Principal (tu función original)
                    BottomBar(navController = navController)
                }
            }
        }) { inner ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(inner)
        ) {

            composable(Screen.Home.route) {
                // --- 4. PASAMOS TODO EL ESTADO A HOMESCREEN ---
                HomeScreen(
                    homeViewModel = homeViewModel,
                    uiState = uiState,
                    selectedDocumentIds = selectedDocumentIds,
                    isSelectionModeActive = isSelectionModeActive,
                    viewMode = viewMode, // Pasamos el modo de vista
                    onToggleSelection = { id ->
                        selectedDocumentIds = if (id in selectedDocumentIds) {
                            selectedDocumentIds - id
                        } else {
                            selectedDocumentIds + id
                        }
                    },
                    onStartSelection = { id ->
                        selectedDocumentIds = setOf(id)
                    },
                    onScanNewDocument = {
                        selectedDocumentIds = emptySet()
                        navController.navigate(Screen.Camara.route)
                    },
                    onEditDocument = { documentId ->
                        selectedDocumentIds = emptySet()
                        navController.navigate("${Screen.Camara.route}?$SCANNER_ARG_ID=$documentId")
                    }
                )
            }

            // ... (Resto de tus rutas)
            composable(Screen.Archivos.route) { ArchivoView(volver = { navController.popBackStack() }) }
            composable( route = "${Screen.Camara.route}?$SCANNER_ARG_ID={$SCANNER_ARG_ID}", arguments = listOf(navArgument(SCANNER_ARG_ID) { type = NavType.LongType; defaultValue = -1L }) ) { backStackEntry ->
                val documentId = backStackEntry.arguments?.getLong(SCANNER_ARG_ID)
                DocumentScannerScreen(
                    documentIdToEdit = if (documentId != -1L) documentId else null,
                    onClose = { navController.popBackStack() }
                )
            }
            composable(Screen.Acciones.route) { AccionesView(volver = { navController.popBackStack() }) }
            composable(Screen.Usuario.route) { UsuarioView(volver = { navController.popBackStack() }) }
        }
    }

    // --- 5. MENÚ DESLIZABLE (SHEET) PARA "MÁS" ---
    if (showMoreMenuSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showMoreMenuSheet = false },
            sheetState = sheetState
        ) {
            Column(Modifier.padding(bottom = 32.dp)) {
                SheetActionItem(
                    icon = Icons.Default.DriveFileMove,
                    text = "Mover",
                    onClick = { /* TODO */ ; showMoreMenuSheet = false }
                )
                SheetActionItem(
                    icon = Icons.Default.MoreVert,
                    text = "Opción Futura 1",
                    onClick = { /* TODO */ ; showMoreMenuSheet = false }
                )
            }
        }
    }

    // --- 6. DIÁLOGO DE RENOMBRAR ---
    if (showRenameDialog) {
        val documentIdToRename = selectedDocumentIds.firstOrNull()
        val documentToRename = uiState.documents.find { it.document.id == documentIdToRename }

        if (documentIdToRename != null && documentToRename != null) {
            RenameDocumentDialog(
                currentName = documentToRename.document.name,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName ->
                    homeViewModel.renameDocument(documentIdToRename, newName)
                    showRenameDialog = false
                    selectedDocumentIds = emptySet()
                }
            )
        } else {
            showRenameDialog = false
        }
    }

    // --- 7. DIÁLOGO DE ELIMINAR ---
    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            documentCount = selectedDocumentIds.size,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                if (selectedDocumentIds.isNotEmpty()) {
                    homeViewModel.deleteDocuments(selectedDocumentIds)
                    selectedDocumentIds = emptySet()
                }
                showDeleteDialog = false
            }
        )
    }

    // --- 8. PANEL DE COMBINAR ---
    if (showMergeSheet) {
        MergeDocumentsSheet(
            uiState = uiState,
            initialSelectedIds = selectedDocumentIds,
            onDismiss = { showMergeSheet = false },
            onConfirmMerge = { finalSelectedIds, deleteOriginals ->
                showMergeSheet = false
                if (finalSelectedIds.size > 1) {
                    if (deleteOriginals) {
                        // Opción A: "Fusionar y Eliminar"
                        val sortedIds = finalSelectedIds.sorted()
                        val targetId = sortedIds.first()
                        val sourceIds = sortedIds.drop(1).toSet()
                        homeViewModel.mergeDocuments(targetId, sourceIds)
                    } else {
                        // Opción B: "Crear Nuevo y Mantener"
                        homeViewModel.createNewDocumentFromMerge(finalSelectedIds)
                    }
                }
                selectedDocumentIds = emptySet()
            }
        )
    }
}


/** BottomBar (footer) principal */
@Composable
fun BottomBar(navController: androidx.navigation.NavHostController) {
    val items = listOf(
        BottomNavItem(R.drawable.hogar, Screen.Home, "Inicio"),
        BottomNavItem(R.drawable.expediente, Screen.Archivos, "Archivos"),
        BottomNavItem(R.drawable.camara_fotografica, Screen.Camara, ""), // El botón central
        BottomNavItem(R.drawable.app, Screen.Acciones, "Acciones"),
        BottomNavItem(R.drawable.perfil, Screen.Usuario, "Usuario")
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        items.forEach { item ->
            val selected = currentRoute == item.screen.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(if (item.label.isEmpty()) 56.dp else 36.dp)
                            .background(
                                color = if (selected) Color(0xFF4CAF50)
                                else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = item.iconRes),
                            contentDescription = item.label,
                            modifier = Modifier.size(if (item.label.isEmpty()) 28.dp else 20.dp),
                            tint = if (selected) Color.White
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                label = { if (item.label.isNotEmpty()) Text(item.label) }
            )
        }
    }
}

// --- Vistas de ejemplo ---
@Composable fun ArchivoView(volver: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Archivos", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = volver) { Text("Volver") }
    }
}
@Composable fun AccionesView(volver: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Acciones", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = volver) { Text("Volver") }
    }
}
@Composable fun UsuarioView(volver: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Usuario", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = volver) { Text("Volver") }
    }
}


// --- COMPOSABLES PARA BARRAS Y MENÚS DE SELECCIÓN ---

@Composable
private fun NormalTopBar(
    viewMode: ViewMode,
    onViewModeToggle: () -> Unit
) {
    TopAppBar(
        title = { Text("Mis Documentos") },
        actions = {
            IconButton(onClick = onViewModeToggle) {
                Icon(
                    imageVector = if (viewMode == ViewMode.LIST) Icons.Default.ViewModule else Icons.Default.ViewList,
                    contentDescription = "Cambiar vista"
                )
            }
        }
    )
}

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit
) {
    TopAppBar(
        title = { Text("$selectedCount seleccionado(s)") },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Cancelar selección")
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = "Seleccionar todo")
            }
        }
    )
}

@Composable
private fun SelectionBottomBar(
    selectedCount: Int,
    onShareClick: () -> Unit,
    onCombineClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    BottomAppBar(
        actions = {
            BottomActionItem(modifier = Modifier.weight(1f), icon = Icons.Default.Share, text = "Compartir", onClick = onShareClick)

            if (selectedCount > 1) {
                BottomActionItem(modifier = Modifier.weight(1f), icon = Icons.Default.MergeType, text = "Combinar", onClick = onCombineClick)
            }

            if (selectedCount == 1) {
                BottomActionItem(modifier = Modifier.weight(1f), icon = Icons.Default.Edit, text = "Nombre", onClick = onRenameClick)
            }

            BottomActionItem(modifier = Modifier.weight(1f), icon = Icons.Default.Delete, text = "Eliminar", onClick = onDeleteClick)
            BottomActionItem(modifier = Modifier.weight(1f), icon = Icons.Default.MoreVert, text = "Más", onClick = onMoreClick)
        }
    )
}

@Composable
private fun BottomActionItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = text)
        Spacer(Modifier.height(4.dp))
        Text(text, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun SheetActionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = text)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text)
    }
}

// --- DIÁLOGO DE RENOMBRAR ---

@Composable
fun RenameDocumentDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar nombre") },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Nombre del documento") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(text)
                    }
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// --- DIÁLOGO DE ELIMINAR ---

@Composable
fun DeleteConfirmationDialog(
    documentCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val title = if (documentCount == 1) "Eliminar documento" else "Eliminar $documentCount documentos"
    val text = if (documentCount == 1)
        "¿Estás seguro de que quieres eliminar este documento? Esta acción no se puede deshacer."
    else
        "¿Estás seguro de que quieres eliminar estos $documentCount documentos? Esta acción no se puede deshacer."

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Eliminar")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// --- PANEL DE COMBINAR (MODIFICADO) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeDocumentsSheet(
    uiState: HomeUiState,
    initialSelectedIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirmMerge: (selectedIds: Set<Long>, deleteOriginals: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var checkedIds by remember { mutableStateOf(initialSelectedIds) }
    var deleteOriginals by remember { mutableStateOf(true) }

    val documentsToList = remember(uiState.documents, initialSelectedIds) {
        uiState.documents.filter { it.document.id in initialSelectedIds }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Combinar Documentos",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                "Selecciona los documentos que quieres incluir en la combinación final.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // --- INICIO DE LA MODIFICACIÓN ---
            Surface(
                modifier = Modifier.fillMaxWidth().height(250.dp), // Un poco más de altura
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                LazyColumn { // LazyColumn ya proporciona el scroll
                    items(
                        items = documentsToList,
                        key = { doc: DocumentWithPages -> doc.document.id }
                    ) { docWithPages: DocumentWithPages ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    checkedIds = if (docWithPages.document.id in checkedIds) {
                                        checkedIds - docWithPages.document.id
                                    } else {
                                        checkedIds + docWithPages.document.id
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = docWithPages.document.id in checkedIds,
                                onCheckedChange = { isChecked ->
                                    checkedIds = if (isChecked) {
                                        checkedIds + docWithPages.document.id
                                    } else {
                                        checkedIds - docWithPages.document.id
                                    }
                                }
                            )
                            Spacer(Modifier.width(16.dp))

                            // Miniatura
                            val firstPagePath = docWithPages.pages.firstOrNull()?.filePath
                            AsyncImage(
                                model = if (firstPagePath != null) File(firstPagePath) else null, // Carga desde el archivo
                                contentDescription = "Miniatura de ${docWithPages.document.name}",
                                modifier = Modifier
                                    .size(width = 50.dp, height = 70.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop,
                                // Opcional: un placeholder si la imagen no carga o es nula
                                error = painterResource(id = R.drawable.ic_launcher_foreground), // ¡Necesitarás un drawable para esto!
                                fallback = painterResource(id = R.drawable.ic_launcher_foreground) // O usa un color
                            )

                            Spacer(Modifier.width(16.dp))

                            // Columna para Nombre y conteo de páginas
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = docWithPages.document.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "${docWithPages.pages.size} página(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            // --- FIN DE LA MODIFICACIÓN ---

            Spacer(Modifier.height(24.dp))

            // Opción de borrar originales
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { deleteOriginals = !deleteOriginals }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = deleteOriginals,
                    onCheckedChange = { deleteOriginals = it }
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "Eliminar documentos originales",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        if (deleteOriginals) "Se fusionarán en un solo documento." else "Se creará un nuevo documento con copias.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Botón de confirmación
            Button(
                onClick = {
                    if (checkedIds.size > 1) {
                        onConfirmMerge(checkedIds, deleteOriginals)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = checkedIds.size > 1
            ) {
                Text(if (checkedIds.size > 1) "Combinar ${checkedIds.size} Documentos" else "Selecciona al menos 2")
            }
        }
    }
}