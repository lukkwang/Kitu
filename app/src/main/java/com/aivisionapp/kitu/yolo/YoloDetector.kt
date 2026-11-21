package com.aivisionapp.kitu.yolo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.aivisionapp.kitu.config.AppConfig
import com.aivisionapp.kitu.utils.getCurrentAppLanguage
import com.aivisionapp.kitu.utils.padBottomRightSquare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream



data class ParsedBox(
    val classId: Int,
    val maxScore: Float,
    val rect: RectF
)


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
                //Log.d("YOLO", "model load success")
                module != null
            } catch (e: Exception) {
                module = null
                Log.e("YOLO", "model load failed", e)
                false
            }
        }
    }

    suspend fun detect(bitmap: Bitmap): List<DetectionResult> {

        // for test
        //val bitmap: Bitmap = loadBitmapFromAssets(appContext, "cycle.png") ?: Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)

        //Log.d("test temp source image size", "${bitmap.width}:${bitmap.height}")

        val isLoaded = loadModel()
        val currentModule = module
        if (!isLoaded || currentModule == null) {
            return emptyList()
        }

        val currentLocale = getCurrentAppLanguage(appContext)

        return withContext(Dispatchers.Default) {
            val resizePadBitmap = padBottomRightSquare(bitmap)

            //saveBitmapToSdCard(resizePadBitmap)

            // tensor transformation (normalization 0~255)
            val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                resizePadBitmap,
                floatArrayOf(0f, 0f, 0f),
                floatArrayOf(1f, 1f, 1f)
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

            // Log.d("YOLO_DEBUG", "tensorSize=${tensorData.size}, first5=${tensorData.take(100)}")

            val channels = shape[1].toInt()       // 84 = 4 pint + 80 COCO class confidence score
            val numPredictions = shape[2].toInt() // 8400 = bounding box center point (x, y, w, h) + class confidence score = 4 + 80

            val confThreshold = AppConfig.CONFIDENCE_THRESHOLD  // Confidence Threshold
            val rawDetections = mutableListOf<ParsedBox>()

            // Iterate over 8,400 predictions and extract valid objects
            for (i in 0 until numPredictions) {
                var maxScore = 0f
                var classId = -1

                // Find the class with the highest probability among 80 class scores.
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

                    val left = xCenter - w / 2f
                    val top = yCenter - h / 2f
                    val right = xCenter + w / 2f
                    val bottom = yCenter + h / 2f

                    val inputW = AppConfig.MODEL_INPUT_SIZE_X.toFloat()
                    val inputH = AppConfig.MODEL_INPUT_SIZE_Y.toFloat()
                    val imgW = bitmap.width.toFloat()
                    val imgH = bitmap.height.toFloat()

                    // Aspect-preserving resize with a single scale factor based on padding.
                    val scale = minOf(inputW / imgW, inputH / imgH)
                    val validW = imgW * scale
                    val validH = imgH * scale

                    val clampedLeft = left.coerceIn(0f, validW)
                    val clampedTop = top.coerceIn(0f, validH)
                    val clampedRight = right.coerceIn(0f, validW)
                    val clampedBottom = bottom.coerceIn(0f, validH)

                    val scaledRect = RectF(
                        clampedLeft / scale,
                        clampedTop / scale,
                        clampedRight / scale,
                        clampedBottom / scale
                    )

                    rawDetections.add(ParsedBox(classId, maxScore, scaledRect))
                }
            }

            val nmsFilteredDetections = nms(rawDetections, iouThreshold = AppConfig.INTERSECTION_OVER_UNION_THRESHOLD)

            // Group detections by class(object) and aggregate counts
            val groupedResults = mutableMapOf<String, MutableList<RectF>>()
            for (det in nmsFilteredDetections) {
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

    // NMS (Non-Maximum Suppression)
    private fun nms(boxes: List<ParsedBox>, iouThreshold: Float = AppConfig.INTERSECTION_OVER_UNION_THRESHOLD): List<ParsedBox> {
        // 1. Sort boxes in descending order by confidence score (maxScore)
        val sortedBoxes = boxes.sortedByDescending { it.maxScore }.toMutableList()
        val selectedBoxes = mutableListOf<ParsedBox>()

        while (sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.removeAt(0)
            selectedBoxes.add(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                // Remove duplicate boxes of the same class when IoU meets or exceeds the threshold
                if (first.classId == next.classId && calculateIoU(first.rect, next.rect) >= iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return selectedBoxes
    }

    // Calculate Intersection over Union (IoU) between two bounding boxes
    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = maxOf(box1.left, box2.left)
        val intersectionTop = maxOf(box1.top, box2.top)
        val intersectionRight = minOf(box1.right, box2.right)
        val intersectionBottom = minOf(box1.bottom, box2.bottom)

        val intersectionWidth = maxOf(0f, intersectionRight - intersectionLeft)
        val intersectionHeight = maxOf(0f, intersectionBottom - intersectionTop)
        val intersectionArea = intersectionWidth * intersectionHeight

        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)

        val unionArea = box1Area + box2Area - intersectionArea
        return if (unionArea <= 0f) 0f else intersectionArea / unionArea
    }
}