package com.example.scannerpro.ui

import DocumentWithPages
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
//import com.example.scannerpro.DocumentWithPages
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel(),
    onScanNewDocument: () -> Unit,
    onEditDocument: (Long) -> Unit // Pasa el ID del documento a editar
) {
    val uiState by homeViewModel.uiState.collectAsState()

    // Este efecto escucha el ciclo de vida para recargar los documentos
    // cada vez que la pantalla vuelve a estar visible.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.loadDocuments()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Limpia el observador cuando la pantalla se va
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Mis Documentos",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        Button(
            onClick = onScanNewDocument,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Escanear Nuevo Documento")
        }

        if (uiState.documents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No tienes documentos guardados.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.documents, key = { it.document.id }) { documentWithPages ->
                    DocumentItem(
                        documentWithPages = documentWithPages,
                        onClick = {
                            onEditDocument(documentWithPages.document.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentItem(
    documentWithPages: DocumentWithPages,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(documentWithPages.document.name, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    .format(Date(documentWithPages.document.createdAt))
                Text(
                    "${documentWithPages.pages.size} página(s) - $date",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("Editar", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

