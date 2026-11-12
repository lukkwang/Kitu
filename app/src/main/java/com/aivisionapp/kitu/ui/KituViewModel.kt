package com.aivisionapp.kitu.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aivisionapp.kitu.KituApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface CameraUiState {
    data object Preview : CameraUiState
    object Capturing : CameraUiState  // capture start (preview background color)
    data class Captured(val bitmap: Bitmap) : CameraUiState  // capture end
}

class KituViewModel : ViewModel()
{
    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Preview)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    fun onImageCapturing() {
        _uiState.value = CameraUiState.Capturing
    }

    // Called when an image is successfully captured and loaded into memory
    fun onImageCaptured(bitmap: Bitmap) {
        _uiState.value = CameraUiState.Captured(bitmap)
    }

    // Called when the delete/retake button is pressed to return to preview mode
    fun resetToPreview() {
        _uiState.value = CameraUiState.Preview
    }


    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as KituApplication)
                KituViewModel()
            }
        }
    }
}
