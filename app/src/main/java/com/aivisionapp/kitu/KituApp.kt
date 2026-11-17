package com.aivisionapp.kitu

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aivisionapp.kitu.config.AppConfig
import com.aivisionapp.kitu.ui.CameraState
import com.aivisionapp.kitu.ui.CameraUiState
import com.aivisionapp.kitu.ui.KituViewModel
import com.aivisionapp.kitu.ui.rememberCameraState
import com.aivisionapp.kitu.ui.theme.KituTheme
import com.aivisionapp.kitu.utils.rememberCameraPermissionState
import kotlinx.coroutines.launch


@Composable
fun KituApp(
    kituViewModel: KituViewModel = viewModel(factory = KituViewModel.Factory)
) {
    val context = LocalContext.current

    val uiState by kituViewModel.uiState.collectAsState()

    KituAppLayout(
        uiState,
        onImageCapturing = { kituViewModel.setCapturingState() },
        onImageCaptured = { bitMap -> kituViewModel.setCapturedState(bitMap) },
        onResetClick = { kituViewModel.resetToPreview() },
        onRunDetection = { kituViewModel.runObjectDetection(context) },
        onErrorCameraPhotoCapture = { kituViewModel.setErrorState(null) },
        onErrorPopClose = { bitMap: Bitmap? ->
            if (bitMap != null)
                kituViewModel.setCapturedState(bitMap)
            else
                kituViewModel.resetToPreview()
        }
    )
}


@Composable
fun KituAppLayout(
    uiState: CameraUiState,
    onImageCapturing: () -> Unit,
    onImageCaptured: (Bitmap) -> Unit,
    onResetClick: () -> Unit,
    onRunDetection: () -> Unit,
    onErrorCameraPhotoCapture: () -> Unit,
    onErrorPopClose: (Bitmap?) -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraState = rememberCameraState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = modifier)
        {
            // camera
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)  // 4:3 ratio
                    .background(Color.DarkGray)
            ) {
                // check Permissions ans preview
                if (rememberCameraPermissionState() && !LocalInspectionMode.current) {  // perssion is true and preview is false
                    cameraArea(
                        uiState = uiState,
                        cameraState = cameraState,
                        modifier = modifier
                    )
                } else {
                    Text(
                        text = stringResource(R.string.permission_camera),
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // toolbar
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color.LightGray),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceAround,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                toolBarArea(
                    uiState,
                    cameraState = cameraState,
                    onImageCapturing = onImageCapturing,
                    onImageCaptured = onImageCaptured,
                    onResetClick = onResetClick,
                    onErrorCameraPhotoCapture = onErrorCameraPhotoCapture,
                    onRunDetection = onRunDetection,
                )
            }

            // grid
            gridArea(
                modifier = modifier
                    .fillMaxWidth()
                    .weight(1f)  // occpy all remain area
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        if (uiState is CameraUiState.Error) {
            AlertDialog(
                onDismissRequest = {
                    onErrorPopClose(uiState.bitmap)
                },
                text = {
                    Text(stringResource(
                        if (uiState.bitmap != null)
                                R.string.error_object_detect
                            else
                                R.string.error_camera_phpto_capture)
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        onErrorPopClose(uiState.bitmap)
                    }) {
                        Text(stringResource(R.string.btn_close))
                    }
                }
            )
        }
    }  // Box(modifier = modifier.fillMaxSize())
}  // fun KituAppLayout


@Composable
fun cameraArea(
    uiState: CameraUiState,
    cameraState: CameraState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    LaunchedEffect(lifecycleOwner, uiState) {
        if (uiState is CameraUiState.Preview) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = CameraXPreview.Builder().build().also {
                    it.setSurfaceProvider { request ->
                        surfaceRequest = request
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    cameraState.imageCapture
                )
            } catch (exc: Exception) {
                Log.e("Camera", "Camera binding fail", exc)
            }
        }
    }

    when (uiState) {
        is CameraUiState.Preview -> {
            surfaceRequest?.let { request ->
                CameraXViewfinder(
                    surfaceRequest = request,
                    modifier = modifier
                )
            }
        }
        is CameraUiState.Capturing -> {
            val flashAlpha by animateFloatAsState(
                targetValue = if (uiState == CameraUiState.Capturing) 1f else 0f,
                animationSpec = tween(durationMillis = 150),
                label = "ShutterFlash"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(flashAlpha)
                    .background(Color.White)
                    //.background(Color.White.copy(alpha = 0.8f))
            )
        }
        is CameraUiState.Captured,
        is CameraUiState.Error,
        is CameraUiState.Detecting,
        is CameraUiState.Detected -> {
            val bitmap = when (uiState) {
                is CameraUiState.Captured -> uiState.bitmap
                is CameraUiState.Error -> uiState.bitmap
                is CameraUiState.Detecting -> uiState.bitmap
                is CameraUiState.Detected -> uiState.bitmap
                else -> null
            }

            if (bitmap != null) {  // CameraUiState.Captured or CameraUiState.Detected
                Box(modifier = modifier.fillMaxSize()) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.btn_take_photo),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    if (uiState is CameraUiState.Detected) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            uiState.detections.forEach { detection ->
                                val boxs = detection.boundingBoxList
                                boxs.forEach { box ->
                                    drawRect(
                                        color = AppConfig.BOUNDING_BOX_COLOR,
                                        topLeft = Offset(box.left, box.top),
                                        size = Size(box.width(), box.height()),
                                        style = Stroke(width = AppConfig.BOUNDING_BOX_STROKE_WIDTH)  // line width
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (uiState is CameraUiState.Detecting) {  // circular pregress
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }  // when (uiState)
}  // fun cameraArea(


@Composable
fun toolBarArea(
    uiState: CameraUiState,
    cameraState: CameraState,
    onImageCapturing: () -> Unit,
    onImageCaptured: (Bitmap) -> Unit,
    onResetClick: () -> Unit,
    onErrorCameraPhotoCapture: () -> Unit,
    onRunDetection: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // close button
    IconButton(
        onClick = onResetClick,
        enabled = uiState is CameraUiState.Captured || uiState is CameraUiState.Detected
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.btn_close)
        )
    }

    // capture button
    IconButton(
        onClick = {
            onImageCapturing()   // cover the preview by solid color until capture is completee
            coroutineScope.launch {
                try {
                    val bitmap = cameraState.takeCapture()
                    onImageCaptured(bitmap)
                } catch (e: Exception) {
                    onErrorCameraPhotoCapture()
                }
            }
        },
        enabled = uiState is CameraUiState.Preview
    ) {
        Icon(
            imageVector = Icons.Default.PhotoCamera,
            contentDescription = stringResource(R.string.btn_take_photo)
        )
    }

    // AI object detection button
    IconButton(
        onClick = onRunDetection,
        enabled = uiState is CameraUiState.Captured
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = stringResource(R.string.btn_object_detection)
        )
    }
}


@Composable
fun gridArea(modifier: Modifier = Modifier)
{
    val headers = listOf(
        stringResource(id = R.string.grid_header_object) to 8f,
        stringResource(id = R.string.grid_header_count) to 2f
    )

    // temp
    val records = listOf(
        listOf("데이터 A-1", "A-2"),
        listOf("데이터 B-1", "B-2"),
        listOf("데이터 C-1", "C-2"),
    )

    val columnAlignment = listOf(
        Alignment.CenterStart,
        Alignment.CenterEnd
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .background(Color.LightGray),
        //.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.DarkGray)
                .padding(vertical = 1.dp, horizontal = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            headers.forEachIndexed {i, (headerTitle, headerWeight) ->
                Box(
                    modifier = Modifier
                        .weight(headerWeight)
                        //.height(40.dp)
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = headerTitle
                        //color = Color.White,
                        //fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // body
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(1.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {

            records.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    rowItems.forEachIndexed { i, cellText ->
                        val cellWeight = headers.get(i).second

                        Box(
                            modifier = Modifier
                                .weight(cellWeight)
                                .background(Color.White)
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            contentAlignment = columnAlignment.get(i)
                        ) {
                            Text(text = cellText)
                        }
                    }
                }
            }

        }
    }  // Column
}  // fun gridArea(modifier: Modifier = Modifier)


@Preview(showBackground = true)
@Composable
fun KituAppLayoutPreview() {
    KituTheme {
        KituAppLayout(
            CameraUiState.Preview,
            onImageCapturing = {},
            onImageCaptured = {},
            onResetClick = {},
            onRunDetection = {},
            onErrorCameraPhotoCapture = {},
            onErrorPopClose = {}
        )
    }
}