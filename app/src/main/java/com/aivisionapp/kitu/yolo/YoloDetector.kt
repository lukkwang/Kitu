package com.aivisionapp.kitu.yolo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.aivisionapp.kitu.config.AppConfig
import com.aivisionapp.kitu.ui.ParsedBox
import com.aivisionapp.kitu.utils.getCurrentAppLanguage
import com.aivisionapp.kitu.utils.loadBitmapFromAssets
import com.aivisionapp.kitu.utils.padBottomRightSquare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream

class YoloDetector(private val appContext: Context)
{
    private var module: Module? = null

    suspend fun loadModel(): Boolean {
        if (module != null) return true

        return withContext(Dispatchers.IO) {
            try {
                val file = File(appContext.cacheDir, AppConfig.MODEL_FILE_NAME)
                appContext.assets.open(AppConfig.MODEL_FILE_NAME).use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                module = LiteModuleLoader.load(file.absolutePath)
                Log.d("YOLO", "model load success")
                module != null
            } catch (e: Exception) {
                module = null
                Log.e("YOLO", "model load failed", e)
                false
            }
        }
    }

    suspend fun detect(bitmap: Bitmap): List<DetectionResult> {

        val bitmap: Bitmap = loadBitmapFromAssets(appContext, "cycle.png") ?: Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)

        Log.d("test temp source image size", "${bitmap.width}:${bitmap.height}")

        val isLoaded = loadModel()
        val currentModule = module
        if (!isLoaded || currentModule == null) {
            return emptyList()
        }

        val currentLocale = getCurrentAppLanguage(appContext)

        return withContext(Dispatchers.Default) {
            val resizedBitmap = padBottomRightSquare(bitmap)

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
            /* tensor data (parentheses is data index) (8400 is total grid block count)
                c = 0       (0 ~ 8,399): xCenter (8400 record)
                c = 1       (8,400 ~ 16,799): yCenter (8400 record)
                c = 2       (16,800 ~ 25,199): w (8400 record)
                c = 3       (25,200 ~ 33,599): h (8400 record)
                c = 4 ~ 83  (33,600 ~ 705,599): 80 class confidence threshold (80 × 8,400 = 672,000 record)
             */
            val outputTuple = currentModule.forward(IValue.from(inputTensor))
            val outputTensor = outputTuple.toTensor()
            val tensorData = outputTensor.dataAsFloatArray  // data type = float[], value Length = 1 * 84 * 8400 = 705,600
            val shape = outputTensor.shape()  // data type = long[], value = [1, 84, 8400]

            ///////////
            Log.d("YOLO_DEBUG", "tensorSize=${tensorData.size}, first5=${tensorData.take(100)}")
            //////////////

            val channels = shape[1].toInt()       // 84 = 4 pint + 80 COCO class confidence score
            val numPredictions = shape[2].toInt() // 8400 = bounding box center point (x, y, w, h) + class confidence score = 4 + 80

            val confThreshold = AppConfig.CONFIDENCE_THRESHOLD  // Confidence Threshold
            val rawDetections = mutableListOf<ParsedBox>()

            ///////////
            var debugMaxScore = -1f
            var debugBestClassId = -1
            ////////////////////

            // 8400개의 예측 결과를 순회하며 유효한 객체 추출
            for (i in 0 until numPredictions) {
                var maxScore = 0f
                var classId = -1

                // 80개 클래스 점수 중 가장 높은 확률을 가진 클래스 탐색
                for (c in 4 until channels) {
                    val score = tensorData[c * numPredictions + i]
                    //////////////
                    if (score > 0.1)
                        Log.d("YOLO DEBUG score", String.format("%.6f", score))
                    //////////////
                    if (score > maxScore) {
                        maxScore = score
                        classId = c - 4
                    }
                }

                /////////////
                if (maxScore > debugMaxScore) {
                    debugMaxScore = maxScore
                    debugBestClassId = classId
                }
                ////////////

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
                    val scaleY = bitmap.height.toFloat() / AppConfig.MODEL_INPUT_SIZE_Y.toFloat()

                    val scaledRect = RectF(
                        left * scaleX,
                        top * scaleY,
                        right * scaleX,
                        bottom * scaleY
                    )

                    rawDetections.add(ParsedBox(classId, maxScore, scaledRect))
                }
            }

            //////////////
            Log.d("YOLO_DEBUG", "bestScore=$debugMaxScore, bestClassId=$debugBestClassId, rawCount=${rawDetections.size}")
            ///////////

            // 동일한 객체(클래스)별로 그룹화 및 카운트 집계
            val groupedResults = mutableMapOf<String, MutableList<RectF>>()
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
        }  // return withContext(Dispatchers.Default)
    }
}