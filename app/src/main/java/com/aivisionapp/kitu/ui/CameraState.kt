package com.aivisionapp.kitu.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class CameraState(val context: Context)
{
    val activityManager = context.getSystemService(ActivityManager::class.java)

    val totalRamGB = if (activityManager != null) {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        memoryInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
    } else {
        0.0
    }

    val isLowEndDevice = totalRamGB <= 2.0

    val resolutionSelector = if (isLowEndDevice) {
        ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()
    } else {
        null
    }

    val imageCapture: ImageCapture = run {
        val isLowEndDevice = totalRamGB <= 2.0  // low spec device is 2GB RAM

        val builder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(if (isLowEndDevice) 70 else 90)  // JPEG compress rate(default 100 -> 90~70)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)

        if (isLowEndDevice) {
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()
            builder.setResolutionSelector(resolutionSelector)
        }

        builder.build()
    }

    fun takeCapture(onImageCaptured: (Bitmap) -> Unit) {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {

                    val originalBitmap = image.toBitmap()

                    // camera sensor rotate angle
                    val rotationDegrees = image.imageInfo.rotationDegrees

                    val rotatedBitmap = if (rotationDegrees != 0) {
                        val matrix = Matrix().apply {
                            postRotate(rotationDegrees.toFloat())
                        }
                        Bitmap.createBitmap(
                            originalBitmap,
                            0, 0,
                            originalBitmap.width,
                            originalBitmap.height,
                            matrix,
                            true
                        ).also {
                            if (it != originalBitmap)
                                originalBitmap.recycle()
                        }
                    } else {
                        originalBitmap
                    }

                    image.close()
                    onImageCaptured(rotatedBitmap)
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e("Camera", "Photo capture failed", exception)
                }
            }
        )
    }  // fun takeCapture(onImageCaptured: (Bitmap) -> Unit)
}  // class CameraState(val context: Context)


@Composable
fun rememberCameraState(): CameraState
{
    val context = LocalContext.current

    return remember(context) { CameraState(context) }
}