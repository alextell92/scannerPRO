@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.scannerpro

import android.graphics.pdf.content.PdfPageGotoLinkContent
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
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialogDefaults.containerColor
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
//import com.example.scannerpro.ui.DetailScreen
//import com.example.scannerpro.ui.HomeView
//import com.example.scannerpro.ui.ListScreen
//import com.example.scannerpro.ui.Testvista
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.scannerpro.ui.DocumentScannerScreen
import com.example.scannerpro.ui.HomeScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Camara : Screen("camara")
    object Archivos : Screen("archivos")
    object Usuario : Screen("usuario")
    object Acciones : Screen("herramientas")
}


//data class BottomNavItem(val screen: Screen, val label: String, val icon: Int)

// Data class que guarda un id de drawable
data class BottomNavItem(@DrawableRes val iconRes: Int, val screen: Screen, val label: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Entrada de aplicacion
            AppEntry()
        }
    }
}
//NavHost(navController, startDestination = "home") {
//
//        composable("home") {
//            HomeView(
//                onNavigateToList = { navController.navigate("list") },
//                onNavigateToDetail = { navController.navigate("detail/0") },
//                onNavigateTest = { navController.navigate("test") }
//            )
//        }

@Composable
fun AppEntry() {
    val navController = rememberNavController()
    val sample = listOf(
        "Foto_2025_08_01.jpg",
        "QR_1234567890",
        "Documento_factura_001.pdf",
        "Tarjeta_contacto.vcf",
        "Imagen_duplicada_02.jpg",
        "Foto_2025_08_01.jpg",
        "QR_1234567890",
        "Documento_factura_001.pdf",
        "Tarjeta_contacto.vcf",
        "Imagen_duplicada_02.jpg"
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    Scaffold(bottomBar = {


        if (currentRoute != Screen.Camara.route ) {
            BottomBar(navController = navController)
        }
    }) { inner ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(inner)
        ) {

            composable(Screen.Home.route) {
                HomeScreen(sample, { dest -> navController.navigate(dest) })
            }
            composable(Screen.Archivos.route) {
                ArchivoView(
                    irAHome = { navController.navigate(Screen.Archivos.route) },
                    irAPantallaB = { navController.navigate(Screen.Archivos.route) },
                    volver = { navController.popBackStack() }
                )
            }
            composable(Screen.Camara.route) {
                // Ejemplo de cómo lo llamarías
DocumentScannerScreen(
onDocumentScanned = { bitmapResult ->
    // Aquí recibes el bitmap final.
    // Puedes navegar hacia atrás y pasar este bitmap a la pantalla anterior,
    // guardarlo en un ViewModel, o subirlo a un servidor.
    Log.d("MainActivity", "Bitmap recibido! Tamaño: ${bitmapResult.width}x${bitmapResult.height}")

    // Lógica para volver a la pantalla anterior
    navController.popBackStack()
}, onClose = {
        // Simplemente volvemos a la pantalla anterior
        navController.popBackStack()
    }
)
//                CamaraView(
//                    irAHome = { navController.navigate(Screen.Camara.route) },
//                    volver = { navController.popBackStack() }
//                )
            }

            composable(Screen.Acciones.route) {
                AccionesView(
                    irAHome = { navController.navigate(Screen.Acciones.route) },
                    volver = { navController.popBackStack() }
                )
            }

            composable(Screen.Usuario.route) {
                UsuarioView(
                    irAHome = { navController.navigate(Screen.Usuario.route) },
                    volver = { navController.popBackStack() }
                )
            }

        }
    }
}

/**
 * HOME: ejemplo de layout responsivo:
 * - BoxWithConstraints para obtener maxWidth/maxHeight
 * - botones con ancho relativo fillMaxWidth(0.7f)
 * - texto con tamaño "aprox" derivado de maxWidth
 */


/** BottomBar (footer) con items y manejo del estado seleccionado */
@Composable
fun BottomBar(navController: androidx.navigation.NavHostController) {
    val items = listOf(
        BottomNavItem(R.drawable.hogar, Screen.Home, "Inicio"),
        BottomNavItem(R.drawable.expediente, Screen.Archivos, "Archivos"),
        BottomNavItem(R.drawable.camara_fotografica, Screen.Camara, ""),
        BottomNavItem(R.drawable.app, Screen.Acciones, "Acciones"),
        BottomNavItem(R.drawable.perfil, Screen.Usuario, "Usuario")
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {

        items.forEach { item ->
            val selected = currentRoute == item.screen.route
            NavigationBarItem(
                selected = selected,

                onClick = {
                    navController.navigate(item.screen.route) {
                        // evita apilar destinos repetidos y restaura estado
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
                            .size(36.dp) // tamaño del círculo
                            .background(
                                color = if (selected) Color(0xFF4CAF50) // verde solo cuando está seleccionado
                                else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = item.iconRes),
                            contentDescription = item.label,
                            modifier = Modifier.size(20.dp), // tamaño real del ícono
                            tint = if (selected) Color.White // ícono blanco cuando está activo
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                label = { Text(item.label) }
            )
        }
    }
}

@Composable
fun ArchivoView(irAHome: () -> Unit, irAPantallaB: () -> Unit, volver: () -> Unit) {
    val configuration = LocalConfiguration.current


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Archivo", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = volver, modifier = Modifier
            .fillMaxWidth(0.7f)
            .height(50.dp)) {
            Text("Volver")
        }
    }
}

@Composable
fun CamaraView(irAHome: () -> Unit, volver: () -> Unit) {
    val configuration = LocalConfiguration.current



    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camara", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = volver, modifier = Modifier
            .fillMaxWidth(0.7f)
            .height(50.dp)) {
            Text("Volver")
        }
    }
}


@Composable
fun AccionesView(irAHome: () -> Unit, volver: () -> Unit) {
    val configuration = LocalConfiguration.current


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Acciones", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = volver, modifier = Modifier
            .fillMaxWidth(0.7f)
            .height(50.dp)) {
            Text("Volver")
        }
    }

}


@Composable
fun UsuarioView(irAHome: () -> Unit, volver: () -> Unit) {
    val configuration = LocalConfiguration.current


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Usuario", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = volver, modifier = Modifier
            .fillMaxWidth(0.7f)
            .height(50.dp)) {
            Text("Volver")
        }
    }
}


//@Composable
//fun AppNavHost() {
//    val navController = rememberNavController()
//
//    NavHost(navController, startDestination = "home") {
//
//        composable("home") {
//            HomeView(
//                onNavigateToList = { navController.navigate("list") },
//                onNavigateToDetail = { navController.navigate("detail/0") },
//                onNavigateTest = { navController.navigate("test") }
//            )
//        }
//
//        composable("list") {
//            ListScreen(onItemClick = { itemId ->
//                navController.navigate("detail/$itemId")
//            })
//        }
//
//        composable("test") {
//            Testvista(
//                onBack = { navController.popBackStack() }
//            )
//        }
//
//        composable(
//            "detail/{itemId}",
//            arguments = listOf(navArgument("itemId") { type = NavType.IntType })
//        ) { backStackEntry ->
//            val itemId = backStackEntry.arguments?.getInt("itemId") ?: 0
//            DetailScreen(itemId)
//        }
//    }
//}

