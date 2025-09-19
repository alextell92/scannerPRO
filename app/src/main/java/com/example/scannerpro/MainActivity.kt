@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.scannerpro

import DocumentScannerScreen
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.scannerpro.ui.HomeScreen
import com.example.scannerpro.ui.HomeUiState
import com.example.scannerpro.ui.HomeViewModel
import com.example.scannerpro.ui.theme.ScannerPROTheme
import org.opencv.android.OpenCVLoader

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
        if (OpenCVLoader.initLocal()) {
            Log.d("OpenCV", "OpenCV se ha cargado exitosamente.")
        } else {
            Log.e("OpenCV", "¡Error al cargar OpenCV!")
        }
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

    var showDeleteDialog by remember { mutableStateOf(false) } // <-- 1. AÑADE ESTE ESTADO


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
                            if (selectedDocumentIds.size > 1) {
                                // Ordenamos para tener un "destino" predecible (ej. el doc más antiguo)
                                val sortedIds = selectedDocumentIds.sorted()
                                val targetDocumentId = sortedIds.first()
                                val sourceDocumentIds = sortedIds.drop(1).toSet()

                                homeViewModel.mergeDocuments(targetDocumentId, sourceDocumentIds)
                                selectedDocumentIds = emptySet() // Limpiar selección
                            }
                        },
                        onRenameClick = { showRenameDialog = true },
                        onDeleteClick = {
                            if (selectedDocumentIds.isNotEmpty()) {
                                // 1. Llama al ViewModel para borrar
                                showDeleteDialog = true // <-- Solo muestra el diálogo
                            }
                        },
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
        // El botón de renombrar solo es visible cuando hay 1 ítem seleccionado
        val documentIdToRename = selectedDocumentIds.firstOrNull()
        val documentToRename = uiState.documents.find { it.document.id == documentIdToRename }

        if (documentIdToRename != null && documentToRename != null) {
            RenameDocumentDialog(
                currentName = documentToRename.document.name,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName ->
                    // Asumimos que homeViewModel tiene esta función
                    homeViewModel.renameDocument(documentIdToRename, newName)
                    showRenameDialog = false
                    selectedDocumentIds = emptySet() // Limpiar selección
                }
            )
        } else {
            // Si algo sale mal (ej. el documento desaparece), solo cerramos el diálogo
            showRenameDialog = false
        }
    }
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
}


/** BottomBar (footer) principal - ESTA ES TU FUNCIÓN ORIGINAL COMPLETA */
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


// --- 6. COMPOSABLES PARA BARRAS Y MENÚS DE SELECCIÓN ---

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
    onSelectAll: () -> Unit // <-- AQUÍ ESTABA EL ERROR (DECÍA '()...')
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

            // --- CORRECCIÓN DEL TYPO ---
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

// --- 7. COMPOSABLE DE DIÁLOGO ---

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
            Button(
                onClick = onConfirm
                // Opcional: Dale un color rojo
                // colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
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