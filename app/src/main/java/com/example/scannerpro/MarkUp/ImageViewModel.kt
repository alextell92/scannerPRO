package com.example.scannerpro.MarkUp


import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class ImageViewModel : ViewModel() {
    // Usamos un MutableState para que la UI se actualice automáticamente
    var capturedBitmap by mutableStateOf<Bitmap?>(null)
        private set // Solo el ViewModel puede modificarlo

    fun setBitmap(bitmap: Bitmap?) {
        capturedBitmap = bitmap
    }
}