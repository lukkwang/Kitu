package com.aivisionapp.kitu.ui

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
import com.aivisionapp.kitu.config.AppConfig

class CameraState(val context: Context)
{
    // camera capture image size is same as YOLO input image size
    private val targetWidth = AppConfig.CAMERA_CAPTURE_SIZE_X
    private val targetHeight = AppConfig.CAMERA_CAPTURE_SIZE_Y

    val resolutionSelector: ResolutionSelector = ResolutionSelector.Builder()
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(targetWidth, targetHeight),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            )
        )
        .build()

    val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setJpegQuality(AppConfig.jpegQuality)
        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
        .setResolutionSelector(resolutionSelector)
        .build()

    fun takeCapture(
        onImageCaptured: (Bitmap) -> Unit,
        onErrorCameraPhotoCapture: () -> Unit
    ) {
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
                    onErrorCameraPhotoCapture()

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