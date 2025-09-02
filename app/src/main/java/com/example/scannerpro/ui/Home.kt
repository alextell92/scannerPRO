package com.example.scannerpro.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.node.RootForTest


@Composable
fun HomeView(
    onNavigateToList: () -> Unit,
    onNavigateToDetail: () -> Unit,
    onNavigateTest: () -> Unit
) {
    // Simplemente reusa tu DesignScreen y pasa las acciones
    DesignScreen(
        onPrimaryClick = onNavigateToList,
        onSecondaryClick = onNavigateToDetail,
        onNavigateTest=onNavigateTest
    )
}

@Composable
fun DesignScreen(
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    onNavigateTest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Título
        Text(
            text = "Bienvenido",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Área de contenido
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Aquí va tu contenido principal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Fila de botones al pie
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onPrimaryClick,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = AppShapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {

                Text("Ir a Lista")
            }



            Button(onClick = {
                Log.d("NAV", "Home: botón TEST presionado")
                onNavigateTest()
            }) {
                Text("Este tonto boton")
            }

            OutlinedButton(
                onClick = onSecondaryClick,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = AppShapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.secondary
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
            ) {
                Text("Ir a Detalle")
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmallTopAppBar(
    title: String,
    onBackClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        title = { Text(title) },
        navigationIcon = {
            onBackClick?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                }
            }
        },
        actions = {
            onMoreClick?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Más")
                }
            }
        }
    )
}




/** Ejemplo simple de ListScreen */
@Composable
fun ListScreen(onItemClick: (Int) -> Unit) {
    Log.e("TEST","Entra")
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        SmallTopAppBar(title = "Lista de elementos")
        Spacer(Modifier.height(12.dp))
        // ejemplo: 6 filas
        repeat(6) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(vertical = 6.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onItemClick(index) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Elemento #$index", modifier = Modifier.weight(1f))
                Text(text = "›")
            }
        }
    }
}



@Composable
fun Testvista(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Pantalla TEST", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            Log.d("NAV", "TEST: botón Volver presionado")
            onBack()
        }) {
            Text("Volver al Home")
        }
    }
}



/** Ejemplo simple de DetailScreen que recibe el itemId */
@Composable
fun DetailScreen(itemId: Int) {
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        SmallTopAppBar(title = "Detalle")

        Spacer(Modifier.height(12.dp))
        Text(text = "Detalle del elemento con id = $itemId", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(text = "Aquí puedes mostrar la imagen, descripción, botones, etc.")
    }
}

@Preview(showBackground = true)
@Composable
fun HomePreview() {
    ScannerPROTheme {
        HomeView(onNavigateToList = {}, onNavigateToDetail = {}
        , onNavigateTest = {})
    }
}
