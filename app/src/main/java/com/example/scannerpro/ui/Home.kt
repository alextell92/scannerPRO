package com.example.scannerpro.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.scannerpro.ui.theme.AppShapes
import com.example.scannerpro.ui.theme.ScannerPROTheme

import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.scannerpro.Screen




@Composable
fun HomeScreen(  scannedItems: List<String>,
                 irA: (String) -> Unit) {
    // Usamos LocalConfiguration para obtener screenWidthDp / screenHeightDp (enteros)
    val configuration = LocalConfiguration.current
    val screenW = configuration.screenWidthDp.toFloat()   // ancho en dp (Float)
    val screenH = configuration.screenHeightDp.toFloat()  // alto en dp (Float)

    // tamaño de fuente calculado respecto al ancho (en sp)
    val titleFont = (screenW * 0.07f).coerceIn(14f, 34f).sp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // permite scroll si se supera pantalla
            .padding(10.dp),
       // verticalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pantalla Home", style = MaterialTheme.typography.titleLarge, fontSize = titleFont)

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Menu", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Resumen: acciones rápidas", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { irA(Screen.Home.route) }) {
                    Text("Acción")
                }
            }
        }

        // 2) Opciones (scroll horizontal dentro de la sección)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Opciones", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ejemplo de varias opciones; podrían venir de un array dinámico
                    val options = listOf("Nueva búsqueda", "Ajustes", "Exportar", "Filtrar", "Ayuda", "Limpiar")
                    options.forEach { opt ->
                        Button(onClick = { irA(Screen.Home.route) }, modifier = Modifier.height(40.dp)) {
                            Text(opt)
                        }
                    }
                }
            }
        }

        // 3) Sección desplegable con ítems escaneados
        var expanded by remember { mutableStateOf(true) } // control de desplegado
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Escaneados", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Button(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Ocultar" else "Mostrar")
                    }
                }

                if (expanded) {
                    // Limitar altura para que no ocupe toda la pantalla; se hace scroll interno si hace falta
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (scannedItems.isEmpty()) {
                            Text("No hay elementos escaneados aún.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp), // máximo alto de la lista
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(scannedItems) { item ->
                                    // cada item es clickable
                                    Card(modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { irA(Screen.Home.route) }
                                    ) {
                                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item, fontWeight = FontWeight.Medium)
                                                Text("Detalle / subtítulo", style = MaterialTheme.typography.bodySmall)
                                            }
                                            Text("Ver", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4) Footer o sección adicional
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Estado: listo", style = MaterialTheme.typography.bodySmall)
                Text("v1.0", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}



//
//@Composable
//fun HomeView(
//    onNavigateToList: () -> Unit,
//    onNavigateToDetail: () -> Unit,
//    onNavigateTest: () -> Unit
//) {
//    // Simplemente reusa tu DesignScreen y pasa las acciones
//    DesignScreen(
//        onPrimaryClick = onNavigateToList,
//        onSecondaryClick = onNavigateToDetail,
//        onNavigateTest=onNavigateTest
//    )
//}
//
//@Composable
//fun DesignScreen(
//    onPrimaryClick: () -> Unit,
//    onSecondaryClick: () -> Unit,
//    onNavigateTest: () -> Unit
//) {
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(MaterialTheme.colorScheme.background)
//            .padding(16.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.SpaceBetween
//    ) {
//        // Título
//        Text(
//            text = "Bienvenido",
//            style = MaterialTheme.typography.titleLarge,
//            color = MaterialTheme.colorScheme.onBackground
//        )
//
//        // Área de contenido
//        Box(
//            modifier = Modifier
//                .fillMaxWidth()
//                .weight(1f)
//                .padding(vertical = 24.dp),
//            contentAlignment = Alignment.Center
//        ) {
//            Text(
//                text = "Aquí va tu contenido principal",
//                style = MaterialTheme.typography.bodyMedium,
//                color = MaterialTheme.colorScheme.onBackground
//            )
//        }
//
//        // Fila de botones al pie
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            horizontalArrangement = Arrangement.spacedBy(12.dp)
//        ) {
//            Button(
//                onClick = onPrimaryClick,
//                modifier = Modifier
//                    .weight(1f)
//                    .height(50.dp),
//                shape = AppShapes.medium,
//                colors = ButtonDefaults.buttonColors(
//                    containerColor = MaterialTheme.colorScheme.primary,
//                    contentColor = MaterialTheme.colorScheme.onPrimary
//                )
//            ) {
//
//                Text("Ir a Lista")
//            }
//
//
//
//            Button(onClick = {
//                Log.d("NAV", "Home: botón TEST presionado")
//                onNavigateTest()
//            }) {
//                Text("Este tonto boton")
//            }
//
//            OutlinedButton(
//                onClick = onSecondaryClick,
//                modifier = Modifier
//                    .weight(1f)
//                    .height(50.dp),
//                shape = AppShapes.medium,
//                colors = ButtonDefaults.outlinedButtonColors(
//                    containerColor = MaterialTheme.colorScheme.surface,
//                    contentColor = MaterialTheme.colorScheme.secondary
//                ),
//                border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
//            ) {
//                Text("Ir a Detalle")
//            }
//        }
//    }
//}
//
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun SmallTopAppBar(
//    title: String,
//    onBackClick: (() -> Unit)? = null,
//    onMoreClick: (() -> Unit)? = null
//) {
//    TopAppBar(
//        colors = TopAppBarDefaults.topAppBarColors(
//            containerColor = MaterialTheme.colorScheme.primaryContainer,
//            titleContentColor = MaterialTheme.colorScheme.onPrimary
//        ),
//        title = { Text(title) },
//        navigationIcon = {
//            onBackClick?.let {
//                IconButton(onClick = it) {
//                    Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
//                }
//            }
//        },
//        actions = {
//            onMoreClick?.let {
//                IconButton(onClick = it) {
//                    Icon(Icons.Default.MoreVert, contentDescription = "Más")
//                }
//            }
//        }
//    )
//}
//
//
//
//
///** Ejemplo simple de ListScreen */
//@Composable
//fun ListScreen(onItemClick: (Int) -> Unit) {
//    Log.e("TEST","Entra")
//    Column(modifier = Modifier
//        .fillMaxSize()
//        .padding(16.dp)) {
//        SmallTopAppBar(title = "Lista de elementos")
//        Spacer(Modifier.height(12.dp))
//        // ejemplo: 6 filas
//        repeat(6) { index ->
//            Row(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(72.dp)
//                    .padding(vertical = 6.dp)
//                    .clip(MaterialTheme.shapes.medium)
//                    .background(MaterialTheme.colorScheme.surfaceVariant)
//                    .clickable { onItemClick(index) }
//                    .padding(12.dp),
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                Text(text = "Elemento #$index", modifier = Modifier.weight(1f))
//                Text(text = "›")
//            }
//        }
//    }
//}
//
//
//
//@Composable
//fun Testvista(onBack: () -> Unit) {
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center
//    ) {
//        Text("Pantalla TEST", style = MaterialTheme.typography.titleLarge)
//        Spacer(modifier = Modifier.height(16.dp))
//        Button(onClick = {
//            Log.d("NAV", "TEST: botón Volver presionado")
//            onBack()
//        }) {
//            Text("Volver al Home")
//        }
//    }
//}
//
//
//
///** Ejemplo simple de DetailScreen que recibe el itemId */
//@Composable
//fun DetailScreen(itemId: Int) {
//    Column(modifier = Modifier
//        .fillMaxSize()
//        .padding(16.dp)) {
//        SmallTopAppBar(title = "Detalle")
//
//        Spacer(Modifier.height(12.dp))
//        Text(text = "Detalle del elemento con id = $itemId", style = MaterialTheme.typography.titleLarge)
//        Spacer(Modifier.height(8.dp))
//        Text(text = "Aquí puedes mostrar la imagen, descripción, botones, etc.")
//    }
//}
//
//@Preview(showBackground = true)
//@Composable
//fun HomePreview() {
//    ScannerPROTheme {
//        HomeView(onNavigateToList = {}, onNavigateToDetail = {}
//        , onNavigateTest = {})
//    }
//}

