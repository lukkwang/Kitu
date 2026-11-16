package com.aivisionapp.kitu.ui

import android.content.Context
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
import com.aivisionapp.kitu.config.AppConfig
import com.aivisionapp.kitu.utils.getCurrentAppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream



// object detection temp result
data class ParsedBox(
    val classId: Int,
    val score: Float,
    val rect: RectF
)

// object detection result for grid
data class DetectionResult(
    val id: Int,
    val name: String,
    val count: String,
    val boundingBoxList: MutableList<RectF>
)


sealed interface CameraUiState {
    object Preview : CameraUiState
    object Capturing : CameraUiState  // capture start (cover the preview by solid color)
    data class Captured(  // capture end
        val bitmap: Bitmap,
        val detections: List<DetectionResult> = emptyList()
    ) : CameraUiState
}


data class YoloLabel(val en: String, val ko: String)


class KituViewModel : ViewModel()
{
    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Preview)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()
    private var module: org.pytorch.Module? = null

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

    private suspend fun loadModel(context: Context) {
        if (module != null) return
        withContext(Dispatchers.Default) {
            try {
                val file = File(context.cacheDir, AppConfig.MODEL_FILE_NAME)
                context.assets.open(AppConfig.MODEL_FILE_NAME).use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                module = LiteModuleLoader.load(file.absolutePath)
                //Log.d("YOLO", "model load success")
            } catch (e: Exception) {
                Log.e("YOLO", "model load failed", e)
            }
        }
    }

    // when user click the AI object detection button
    fun runObjectDetection(context: Context) {
        val currentState = _uiState.value

        if (currentState !is CameraUiState.Captured) return

        viewModelScope.launch {
            try {
                // model load
                loadModel(context)

                if (module == null) return@launch

                val finalDetections = withContext(Dispatchers.Default) {
                    val bitmap = currentState.bitmap
                    // resize photo image to YOLOv8 standard input size (640x640)
                    val resizedBitmap = Bitmap.createScaledBitmap(
                        bitmap,
                        AppConfig.MODEL_INPUT_SIZE_X,
                        AppConfig.MODEL_INPUT_SIZE_Y,
                        true
                    )

                    // tensor transformation (normalization 0~255)
                    val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                        resizedBitmap,
                        floatArrayOf(0f, 0f, 0f),
                        floatArrayOf(255f, 255f, 255f)
                    )

                    // model inferrence execution (Output shape: [1, 84, 8400])
                    /* Output shape
                    1 = Batch Size (value is 1 because one photo image used)
                    84 = bounding box center point (x, y, w, h) + class confidence score = 4 + 80
                    8400 = bounding box count = 6,400 + 1,600 + 400 = 8,400 (3 scale grid is used for different object size)
                            80 x 80 grid = 6,400
                            40 x 40 grid = 1,600
                            20 x 20 grid = 400
                */
                    val outputTuple = module!!.forward(IValue.from(inputTensor))
                    val outputTensor = outputTuple.toTensor()
                    val tensorData = outputTensor.dataAsFloatArray
                    val shape = outputTensor.shape()

                    val channels =
                        shape[1].toInt()       // 84 = 4 pint + 80 COCO class confidence score
                    val numPredictions =
                        shape[2].toInt() // 8400 = bounding box center point (x, y, w, h) + class confidence score = 4 + 80

                    val confThreshold = AppConfig.CONFIDENCE_THRESHOLD  // Confidence Threshold
                    val rawDetections = mutableListOf<ParsedBox>()

                    // 8400개의 예측 결과를 순회하며 유효한 객체 추출
                    for (i in 0 until numPredictions) {
                        var maxScore = 0f
                        var classId = -1

                        // 80개 클래스 점수 중 가장 높은 확률을 가진 클래스 탐색
                        for (c in 4 until channels) {
                            val score = tensorData[c * numPredictions + i]
                            if (score > maxScore) {
                                maxScore = score
                                classId = c - 4
                            }
                        }

                        if (maxScore > confThreshold && classId in AppConfig.cocoClasses.indices) {
                            val xCenter = tensorData[0 * numPredictions + i]
                            val yCenter = tensorData[1 * numPredictions + i]
                            val w = tensorData[2 * numPredictions + i]
                            val h = tensorData[3 * numPredictions + i]

                            // Model input-based coordinates (cx, cy, w, h -> left, top, right, bottom)
                            val left = xCenter - w / 2f
                            val top = yCenter - h / 2f
                            val right = xCenter + w / 2f
                            val bottom = yCenter + h / 2f

                            // 원본 비트맵 크기에 맞게 좌표 스케일링
                            val scaleX = bitmap.width.toFloat() / AppConfig.MODEL_INPUT_SIZE_X.toFloat()
                            val scaleY =
                                bitmap.height.toFloat() / AppConfig.MODEL_INPUT_SIZE_Y.toFloat()

                            val scaledRect = RectF(
                                left * scaleX,
                                top * scaleY,
                                right * scaleX,
                                bottom * scaleY
                            )

                            rawDetections.add(ParsedBox(classId, maxScore, scaledRect))
                        }
                    }

                    // 동일한 객체(클래스)별로 그룹화 및 카운트 집계
                    val groupedResults = mutableMapOf<String, MutableList<RectF>>()
                    val currentLocale = getCurrentAppLanguage(context)
                    for (det in rawDetections) {
                        val labelObj = AppConfig.cocoClasses[det.classId]
                        val displayName = if (currentLocale == "ko") labelObj.ko else labelObj.en
                        groupedResults.getOrPut(displayName) { mutableListOf() }.add(det.rect)
                    }

                    var idCounter = 1
                    val results = mutableListOf<DetectionResult>()
                    for ((name, rects) in groupedResults) {
                        results.add(
                            DetectionResult(
                                id = idCounter++,
                                name = name,
                                count = rects.size.toString(),
                                boundingBoxList = rects
                            )
                        )
                    }

                    results
                }

                _uiState.value = currentState.copy(detections = finalDetections)
                Log.d("YOLO", "Object detection completed. Detected items: ${finalDetections.size}")
            } catch (e: Exception) {
                Log.e("YOLO", "object detection failed", e)
            }
        }  // viewModelScope.launch
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
