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

class KituViewModel : ViewModel()
{
    // capture image Bitmap (saving in memory)
    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    // whether AI is processing
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    /**
     * saving bitmap in memory
     */
    fun setCapturedImage(bitmap: Bitmap) {
        _capturedBitmap.value = bitmap
    }

    /**
     * image init
     */
    fun clearImage() {
        _capturedBitmap.value?.recycle() // release memory
        _capturedBitmap.value = null
    }

    /**
     * AI 객체 인식 요청 함수 (메모리의 Bitmap 전달)
     */
    fun runAiObjectDetection() {
        val bitmap = _capturedBitmap.value ?: return

        _isAnalyzing.value = true

        // TODO: 여기서 ML Kit, TensorFlow Lite 또는 외부 AI 서버로 bitmap을 전달하여 객체 인식 수행
        // 예시 코루틴 작업:
        // viewModelScope.launch(Dispatchers.IO) {
        //     val result = AiModel.detectObjects(bitmap)
        //     // 결과 처리...
        //     _isAnalyzing.value = false
        // }
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
