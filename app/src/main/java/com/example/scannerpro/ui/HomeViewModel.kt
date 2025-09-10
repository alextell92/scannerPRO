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
}
