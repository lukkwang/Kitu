package com.aivisionapp.kitu.yolo

import android.graphics.RectF

// object detection result for grid
data class DetectionResult(
    val id: Int,
    val name: String,
    val count: String,
    val boundingBoxList: MutableList<RectF>
)