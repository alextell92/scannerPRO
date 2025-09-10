@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.scannerpro

import DocumentScannerScreen
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.scannerpro.ui.HomeScreen
import com.example.scannerpro.ui.theme.ScannerPROTheme
import org.opencv.android.OpenCVLoader

//Rutas
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Camara : Screen("camara")
    object Archivos : Screen("archivos")
    object Usuario : Screen("usuario")
    object Acciones : Screen("herramientas")
}

data class BottomNavItem(@DrawableRes val iconRes: Int, val screen: Screen, val label: String)

// Argumento para la pantalla de escaneo
const val SCANNER_ARG_ID = "documentId"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // --- 1. INICIAR OPENCV ---
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

@Composable
fun AppEntry() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(bottomBar = {
        // La barra de navegación no se muestra en la pantalla de la cámara/escáner
        if (currentRoute?.startsWith(Screen.Camara.route) == false) {
            BottomBar(navController = navController)
        }
    }) { inner ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(inner)
        ) {

            composable(Screen.Home.route) {
                HomeScreen(
                    onScanNewDocument = {
                        // Navega al scanner para un nuevo documento (sin ID)
                        navController.navigate(Screen.Camara.route)
                    },
                    onEditDocument = { documentId ->
                        // Navega al scanner para editar un documento existente
                        navController.navigate("${Screen.Camara.route}?$SCANNER_ARG_ID=$documentId")
                    }
                )
            }

            composable(Screen.Archivos.route) {
                ArchivoView(volver = { navController.popBackStack() })
            }

            composable(
                // La ruta del escáner ahora acepta un argumento opcional.
                route = "${Screen.Camara.route}?$SCANNER_ARG_ID={$SCANNER_ARG_ID}",
                arguments = listOf(navArgument(SCANNER_ARG_ID) {
                    type = NavType.LongType
                    defaultValue = -1L // Valor que indica que no se está editando
                })
            ) { backStackEntry ->
                val documentId = backStackEntry.arguments?.getLong(SCANNER_ARG_ID)
                DocumentScannerScreen(
                    documentIdToEdit = if (documentId != -1L) documentId else null,
                    onClose = {
                        // La única acción de salida es volver a la pantalla anterior.
                        // HomeScreen se actualizará sola gracias al Lifecycle event.
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Acciones.route) {
                AccionesView(volver = { navController.popBackStack() })
            }

            composable(Screen.Usuario.route) {
                UsuarioView(volver = { navController.popBackStack() })
            }
        }
    }
}


/** BottomBar (footer) con items y manejo del estado seleccionado */
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
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(if (item.label.isEmpty()) 56.dp else 36.dp) // Círculo más grande para la cámara
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

// --- Vistas de ejemplo para las otras pestañas ---

@Composable
fun ArchivoView(volver: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Archivos", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = volver) { Text("Volver") }
    }
}

@Composable
fun AccionesView(volver: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Acciones", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = volver) { Text("Volver") }
    }
}

@Composable
fun UsuarioView(volver: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Usuario", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = volver) { Text("Volver") }
    }
}

