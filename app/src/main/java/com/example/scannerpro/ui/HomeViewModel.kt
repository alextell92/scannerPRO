package com.example.scannerpro.ui

import DocumentRepository
import DocumentWithPages
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Define un estado para la UI, conteniendo la lista de documentos
data class HomeUiState(
    val documents: List<DocumentWithPages> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        val documentDao = DocumentDatabase.getDatabase(application).documentDao()
        repository = DocumentRepository(application, documentDao)
        loadDocuments()
    }

    fun loadDocuments() {
        viewModelScope.launch {
            _uiState.value = HomeUiState(documents = repository.getAllDocumentsWithPages())
        }
    }

    /**
     * NUEVA FUNCIÓN PARA RENOMBRAR
     * (Asegúrate de tener la función 'renameDocument' implementada en tu DocumentRepository)
     */
    fun renameDocument(documentId: Long, newName: String) {
        viewModelScope.launch {
            // 1. Llama al repositorio para actualizar la base de datos
            // (Debes crear esta función en tu DocumentRepository)
            repository.renameDocument(documentId, newName)

            // 2. Actualiza el estado local (uiState) para reflejar el cambio en la UI
            val currentDocs = _uiState.value.documents
            val updatedDocs = currentDocs.map { docWithPages ->
                if (docWithPages.document.id == documentId) {
                    // Crea una copia del documento con el nombre actualizado
                    docWithPages.copy(
                        document = docWithPages.document.copy(name = newName)
                    )
                } else {
                    docWithPages
                }
            }
            // 3. Emite el nuevo estado
            _uiState.value = _uiState.value.copy(documents = updatedDocs)
        }
    }

    // Dentro de tu clase HomeViewModel

    fun mergeDocuments(targetDocumentId: Long, sourceDocumentIds: Set<Long>) {
        viewModelScope.launch {
            // Llama a la función del repositorio (que crearás en el paso 3)
            repository.mergeDocuments(targetDocumentId, sourceDocumentIds)

            // Vuelve a cargar los documentos para reflejar los cambios
            loadDocuments()
        }
    }

    // --- INICIO DE LA NUEVA FUNCIÓN ---
    fun deleteDocuments(documentIds: Set<Long>) {
        viewModelScope.launch {
            // 1. Llama al repositorio para borrar de la DB y del almacenamiento
            // (Crearemos esta función en DocumentRepository a continuación)
            repository.deleteDocuments(documentIds)

            // 2. Actualiza el estado local (uiState) para reflejar el cambio
            val currentDocs = _uiState.value.documents
            val updatedDocs = currentDocs.filterNot { it.document.id in documentIds }

            // 3. Emite el nuevo estado
            _uiState.value = _uiState.value.copy(documents = updatedDocs)
        }
    }
}