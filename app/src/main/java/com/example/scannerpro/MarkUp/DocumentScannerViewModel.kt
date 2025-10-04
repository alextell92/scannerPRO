package com.example.scannerpro.scanner

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Represents the entire state for the DocumentScannerScreen.
 * Using a data class makes the state immutable and predictable.
 */
data class ScannerUiState(
    val scannedBitmaps: List<Bitmap> = emptyList(),
    val flowState: ScannerFlowState = ScannerFlowState.CAMERA,
    val currentDocumentId: Long? = null,
    val isLoading: Boolean = false
    // You can add other state properties here if they need to survive configuration changes.
)

/**
 * Defines the main navigation states within the scanner flow.
 */
enum class ScannerFlowState {
    CAMERA,
    EDITING,
    FINAL_REVIEW
}

/**
 * ViewModel for the DocumentScannerScreen.
 *
 * This ViewModel is responsible for holding and managing the UI state,
 * surviving configuration changes (like screen rotations), and handling
 * the business logic associated with the scanner.
 */
class DocumentScannerViewModel : ViewModel() {

    // A private MutableStateFlow that the ViewModel uses to update the state.
    private val _uiState = MutableStateFlow(ScannerUiState())

    // A public, read-only StateFlow that the UI can collect to observe state changes.
    val uiState = _uiState.asStateFlow()

    /**
     * Updates the list of scanned bitmaps.
     */
    fun setScannedBitmaps(bitmaps: List<Bitmap>) {
        _uiState.update { it.copy(scannedBitmaps = bitmaps) }
    }

    /**
     * Navigates the scanner flow to a new state.
     */
    fun setFlowState(newState: ScannerFlowState) {
        _uiState.update { it.copy(flowState = newState) }
    }

    /**
     * Sets the ID of the document currently being edited or created.
     */
    fun setCurrentDocumentId(id: Long?) {
        _uiState.update { it.copy(currentDocumentId = id) }
    }

    /**
     * Shows or hides a loading indicator.
     */
    fun setLoading(isLoading: Boolean) {
        _uiState.update { it.copy(isLoading = isLoading) }
    }

    /**
     * Resets the UI state to return to the camera view.
     */
    fun resetToCameraState() {
        _uiState.update {
            it.copy(
                // Reset other relevant states here if needed in the future
                flowState = ScannerFlowState.CAMERA
            )
        }
    }
}

