package com.aivisionapp.kitu.ui

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aivisionapp.kitu.KituApplication
import com.aivisionapp.kitu.R
import com.aivisionapp.kitu.yolo.DetectionResult
import com.aivisionapp.kitu.yolo.YoloDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch



// object detection temp result
data class ParsedBox(
    val classId: Int,
    val score: Float,
    val rect: RectF
)
// object detection result for grid

sealed interface CameraUiState {
    object Preview : CameraUiState
    object Capturing : CameraUiState  // camera photo capture start (cover the preview by solid color)
    data class Captured(
        val bitmap: Bitmap,
        val errorMessage: String? = null
    ) : CameraUiState  // camera photo capture end
    data class Detecting(val bitmap: Bitmap) : CameraUiState
    data class Detected(
        val bitmap: Bitmap,
        val detections: List<DetectionResult> = emptyList()
    ) : CameraUiState
    data class Error(  // there are two error cases : phpoto capture error (no bitmap),  detecint error
        val bitmap: Bitmap?,  // only exist when state is Detected
        val messageResId: Int  // Error Message Resource ID
    ) : CameraUiState
}


class KituViewModel(private val yoloDetector: YoloDetector) : ViewModel()
{
    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Preview)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()
    private lateinit var module: org.pytorch.Module

    fun setCapturingState() {
        _uiState.value = CameraUiState.Capturing
    }

    // Called when an image is successfully captured and loaded into memory
    fun setCapturedState(bitmap: Bitmap) {
        _uiState.value = CameraUiState.Captured(bitmap)
    }

    // Called when the delete/retake button is pressed to return to preview mode
    fun resetToPreview() {
        _uiState.value = CameraUiState.Preview
    }

    fun setErrorState(bitmap: Bitmap?) {
        _uiState.value = CameraUiState.Error(
            bitmap = bitmap,
            messageResId = if (bitmap != null) R.string.error_object_detect else R.string.error_camera_phpto_capture
        )
    }


//    fun setErrorToPrevState(bitmap: Bitmap) {
//        if (bitmap != null)
//            _uiState.value = CameraUiState.Captured(bitmap)
//        else
//            _uiState.value = CameraUiState.Preview
//    }


    // when user click the AI object detection button
    fun runObjectDetection() {
        val currentState = _uiState.value

        if (currentState !is CameraUiState.Captured) return

        val currentBitmap = currentState.bitmap

        _uiState.value = CameraUiState.Detecting(currentBitmap)

        viewModelScope.launch {
            try {
                val finalDetections = yoloDetector.detect(currentBitmap)

                _uiState.value = CameraUiState.Detected(  // uiState must be changed by main thread
                    bitmap = currentBitmap,
                    detections = finalDetections
                )

                Log.d("YOLO", "Object detection completed. Detected items: ${finalDetections.size}")
            } catch (e: Exception) {
                setErrorState(currentBitmap)

                Log.e("YOLO", "object detection failed", e)
            }
        }
    }



    companion object {
            val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as KituApplication)
                val yoloDetector = YoloDetector(application.applicationContext)
                KituViewModel(yoloDetector)
            }
        }
    }
}
