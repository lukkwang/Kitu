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
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
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
                val modelFile = File(appContext.cacheDir, AppConfig.MODEL_FILE_NAME)
                if (!modelFile.exists() || modelFile.length() == 0L) {
                    appContext.assets.open(AppConfig.MODEL_FILE_NAME).use { input ->
                        FileOutputStream(modelFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                // // Load ExecuTorch .pte model (verify Module.load(...) signature for your SDK version)
                module = Module.load(modelFile.absolutePath)
                module != null
            } catch (e: Exception) {
                module = null
                Log.e("YOLO_ET", "ExecuTorch model load failed", e)
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
            val inputW = AppConfig.MODEL_INPUT_SIZE_X
            val inputH = AppConfig.MODEL_INPUT_SIZE_Y

            // // Pad, then resize to model input size (reuse existing padBottomRightSquare)
            val resizePadBitmap = padBottomRightSquare(bitmap, AppConfig.MODEL_INPUT_SIZE_X, AppConfig.MODEL_INPUT_SIZE_Y)

            // Bitmap -> float[1 * 3 * inputH * inputW] (NCHW, 0.0f ~ 1.0f normalization)
            val inputBuffer = bitmapToFloatArrayNormalized(resizePadBitmap)
            if (resizePadBitmap !== bitmap && !resizePadBitmap.isRecycled)
                resizePadBitmap.recycle()
            val inputShape = longArrayOf(1L, 3L, inputH.toLong(), inputW.toLong())
            val inputTensor = Tensor.fromBlob(inputBuffer, inputShape)

            // ExecuTorch inference execute
            val outputs: Array<EValue> = currentModule.forward(EValue.from(inputTensor))
            if (outputs.isEmpty()) {
                return@withContext emptyList()
            }

            val outputTensor = outputs[0].toTensor()
            val outShape = outputTensor.shape()  // data type = long[], value = [1, 84, 8400]
            val outData = outputTensor.dataAsFloatArray  // data type = float[], value Length = 1 * 84 * 8400 = 705,600

            // Log.d("YOLO_ET", "outputShape=${outShape.contentToString()}, size=${outData.size}")  // outputShape=[1, 84, 8400], size=705600

            /* Output shape = [1, 84, 8400]
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

            val rawDetections = parseOutputTensor(
                data = outData,
                shape = outShape,
                srcImgW = bitmap.width.toFloat(),
                srcImgH = bitmap.height.toFloat(),
                inputW = inputW.toFloat(),
                inputH = inputH.toFloat(),
                confThreshold = AppConfig.CONFIDENCE_THRESHOLD
            )

            // Skip NMS for [1, 300, 6] (NMS-free) shape as duplicates are already removed;
            // apply NMS for legacy [1, C, N] shape
            val finalDetections = if (isNmsFreeShape(outShape)) {
                rawDetections
            } else {
                nms(rawDetections, iouThreshold = AppConfig.INTERSECTION_OVER_UNION_THRESHOLD)
            }

            groupDetections(finalDetections, currentLocale)
        }
    }

    private fun bitmapToFloatArrayNormalized(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val channelSize = width * height
        val floatArray = FloatArray(3 * channelSize)

        for (i in 0 until channelSize) {
            val color = pixels[i]
            val r = ((color shr 16) and 0xFF) / 255.0f
            val g = ((color shr 8) and 0xFF) / 255.0f
            val b = (color and 0xFF) / 255.0f

            floatArray[i] = r
            floatArray[channelSize + i] = g
            floatArray[2 * channelSize + i] = b
        }
        return floatArray
    }

    private fun isNmsFreeShape(shape: LongArray): Boolean {
        // // e.g., [1, 300, 6] -> rank 3 and last dimension is 6
        return shape.size == 3 && shape[2] == 6L
    }

    private fun parseOutputTensor(
        data: FloatArray,
        shape: LongArray,
        srcImgW: Float,
        srcImgH: Float,
        inputW: Float,
        inputH: Float,
        confThreshold: Float
    ): List<ParsedBox> {
        val boxes = mutableListOf<ParsedBox>()

        if (isNmsFreeShape(shape)) {
            // [1, N, 6] -> [x1, y1, x2, y2, score, classId]
            val numDetections = shape[1].toInt()  // 84 = 4 pint + 80 COCO class confidence score
            val stride = shape[2].toInt()  // 8400 = bounding box center point (x, y, w, h) + class confidence score = 4 + 80

            for (i in 0 until numDetections) {
                val base = i * stride
                val score = data[base + 4]
                val classId = data[base + 5].toInt()

                if (score > confThreshold && classId in AppConfig.cocoClasses.indices) {
                    val x1 = data[base + 0]
                    val y1 = data[base + 1]
                    val x2 = data[base + 2]
                    val y2 = data[base + 3]

                    // Determine whether coordinates are normalized to [0, 1] or in model input pixel space
                    val isNormalized = x2 <= 1.5f && y2 <= 1.5f
                    val px1 = if (isNormalized) x1 * inputW else x1
                    val py1 = if (isNormalized) y1 * inputH else y1
                    val px2 = if (isNormalized) x2 * inputW else x2
                    val py2 = if (isNormalized) y2 * inputH else y2

                    val mappedRect = mapToOriginalImageRect(
                        left = px1,
                        top = py1,
                        right = px2,
                        bottom = py2,
                        srcImgW = srcImgW,
                        srcImgH = srcImgH,
                        inputW = inputW,
                        inputH = inputH
                    )
                    boxes.add(ParsedBox(classId, score, mappedRect))
                }
            }
        } else if (shape.size == 3) {
            // // Legacy [1, 4 + numClasses, numPredictions] layout
            val channels = shape[1].toInt()
            val numPredictions = shape[2].toInt()

            for (i in 0 until numPredictions) {
                var maxScore = 0f
                var classId = -1

                for (c in 4 until channels) {
                    val score = data[c * numPredictions + i]
                    if (score > maxScore) {
                        maxScore = score
                        classId = c - 4
                    }
                }

                if (maxScore > confThreshold && classId in AppConfig.cocoClasses.indices) {
                    val xCenter = data[0 * numPredictions + i]
                    val yCenter = data[1 * numPredictions + i]
                    val w = data[2 * numPredictions + i]
                    val h = data[3 * numPredictions + i]

                    val mappedRect = mapToOriginalImageRect(
                        left = xCenter - w / 2f,
                        top = yCenter - h / 2f,
                        right = xCenter + w / 2f,
                        bottom = yCenter + h / 2f,
                        srcImgW = srcImgW,
                        srcImgH = srcImgH,
                        inputW = inputW,
                        inputH = inputH
                    )
                    boxes.add(ParsedBox(classId, maxScore, mappedRect))
                }
            }
        } else {
            Log.w("YOLO_ET", "Unsupported output shape: ${shape.contentToString()}")
        }

        return boxes
    }

    private fun mapToOriginalImageRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        srcImgW: Float,
        srcImgH: Float,
        inputW: Float,
        inputH: Float
    ): RectF {
        val scale = minOf(inputW / srcImgW, inputH / srcImgH)
        val validW = srcImgW * scale
        val validH = srcImgH * scale

        val clampedLeft = left.coerceIn(0f, validW)
        val clampedTop = top.coerceIn(0f, validH)
        val clampedRight = right.coerceIn(0f, validW)
        val clampedBottom = bottom.coerceIn(0f, validH)

        return RectF(
            clampedLeft / scale,
            clampedTop / scale,
            clampedRight / scale,
            clampedBottom / scale
        )
    }

    private fun groupDetections(
        detections: List<ParsedBox>,
        currentLocale: String
    ): List<DetectionResult> {
        val groupedResults = mutableMapOf<String, MutableList<RectF>>()
        for (det in detections) {
            val labelObj = AppConfig.cocoClasses[det.classId]
            val displayName = if (currentLocale == "ko") labelObj.ko else labelObj.en
            groupedResults.getOrPut(displayName) { mutableListOf() }.add(det.rect)
        }

        var idCounter = 1
        return groupedResults.map { (name, rects) ->
            DetectionResult(
                id = idCounter++,
                name = name,
                count = rects.size.toString(),
                boundingBoxList = rects
            )
        }
    }

    private fun nms(
        boxes: List<ParsedBox>,
        iouThreshold: Float = AppConfig.INTERSECTION_OVER_UNION_THRESHOLD
    ): List<ParsedBox> {
        val sortedBoxes = boxes.sortedByDescending { it.maxScore }.toMutableList()
        val selectedBoxes = mutableListOf<ParsedBox>()

        while (sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.removeAt(0)
            selectedBoxes.add(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (first.classId == next.classId && calculateIoU(first.rect, next.rect) >= iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return selectedBoxes
    }

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