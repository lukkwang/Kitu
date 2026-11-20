package com.aivisionapp.kitu.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.aivisionapp.kitu.config.AppConfig
import kotlin.math.min



fun padBottomRightSquare(
    source: Bitmap,
    targetSize_x: Int = AppConfig.MODEL_INPUT_SIZE_X,
    targetSize_y: Int = AppConfig.MODEL_INPUT_SIZE_Y,
    padColor: Int = Color.rgb(114, 114, 114)
): Bitmap {
    val scale = min(
        targetSize_x.toFloat() / source.width.toFloat(),
        targetSize_y.toFloat() / source.height.toFloat()
    )
    val scaledWidth = (source.width * scale).toInt()
    val scaledHeight = (source.height * scale).toInt()

    val output = Bitmap.createBitmap(targetSize_x, targetSize_y, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(padColor)

    val srcRect = Rect(0, 0, source.width, source.height)
    val dstRect = Rect(0, 0, scaledWidth, scaledHeight)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(source, srcRect, dstRect, paint)

    val padRight = targetSize_x - scaledWidth
    val padBottom = targetSize_y - scaledHeight

//    Log.d("Image resize and pad result",
//        "source: ${source.width}x${source.height} -> output: ${scaledWidth}x${scaledHeight}, " +
//                "padding(right:${padRight}px, bottom:${padBottom}px) -> ouput: ${output.width}x${output.height}"
//    )

    return output
}


// test function
//fun loadBitmapFromAssets(context: Context, fileName: String): Bitmap? {
//    val options = BitmapFactory.Options().apply {
//        inPreferredConfig = Bitmap.Config.ARGB_8888
//    }
//
//    return try {
//        context.assets.open(fileName).use { inputStream ->
//            BitmapFactory.decodeStream(inputStream, null, options)
//        }
//    } catch (e: IOException) {
//        e.printStackTrace()
//        null
//    }
//}


// for test
//fun saveBitmapToSdCard(
//    bitmap: Bitmap,
//    fileName: String = "image_${System.currentTimeMillis()}.jpg",
//    dirPath: String = "${Environment.getExternalStorageDirectory().absolutePath}/Download"  // /sdcard/Download
//): Boolean {
//    val dir = File(dirPath).apply { if (!exists()) mkdirs() }
//    val file = File(dir, fileName)
//
//    return try {
//        FileOutputStream(file).use { out ->
//            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
//        }
//        true
//    } catch (e: Exception) {
//        e.printStackTrace()
//        false
//    }
//}
