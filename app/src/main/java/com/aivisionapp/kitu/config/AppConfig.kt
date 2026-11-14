package com.aivisionapp.kitu.config

import androidx.compose.ui.graphics.Color
import com.aivisionapp.kitu.ui.YoloLabel

object AppConfig {
    // camera
    const val CAMERA_CAPTURE_SIZE_X = 1280
    const val CAMERA_CAPTURE_SIZE_Y = 960
    const val jpegQuality = 90

    // yolo
    const val MODEL_INPUT_SIZE_X = 640
    const val MODEL_INPUT_SIZE_Y = 640
    const val MODEL_FILE_NAME = "yolov8n.torchscript"

    // object detection box
    val BOUNDING_BOX_COLOR = Color.Red
    const val BOUNDING_BOX_STROKE_WIDTH = 3f

    // Confidence Threshold
    const val CONFIDENCE_THRESHOLD = 0.4f

    val cocoClasses = listOf(
        YoloLabel("person", "사람"), YoloLabel("bicycle", "자전거"), YoloLabel("car", "자동차"), YoloLabel("motorcycle", "오토바이"),
        YoloLabel("airplane", "비행기"), YoloLabel("bus", "버스"), YoloLabel("train", "기차"), YoloLabel("truck", "트럭"),
        YoloLabel("boat", "보트"), YoloLabel("traffic light", "신호등"), YoloLabel("fire hydrant", "소화전"), YoloLabel("stop sign", "정지 표지판"),
        YoloLabel("parking meter", "주차 요금기"), YoloLabel("bench", "벤치"), YoloLabel("bird", "새"), YoloLabel("cat", "고양이"),
        YoloLabel("dog", "개"), YoloLabel("horse", "말"), YoloLabel("sheep", "양"), YoloLabel("cow", "소"),
        YoloLabel("elephant", "코끼리"), YoloLabel("bear", "곰"), YoloLabel("zebra", "얼룩말"), YoloLabel("giraffe", "기린"),
        YoloLabel("backpack", "배낭"), YoloLabel("umbrella", "우산"), YoloLabel("handbag", "핸드백"), YoloLabel("tie", "넥타이"),
        YoloLabel("suitcase", "여행용 가방"), YoloLabel("frisbee", "프리스비"), YoloLabel("skis", "스키"), YoloLabel("snowboard", "스노보드"),
        YoloLabel("sports ball", "공"), YoloLabel("kite", "연"), YoloLabel("baseball bat", "야구 방망이"), YoloLabel("baseball glove", "야구 글러브"),
        YoloLabel("skateboard", "스케이트보드"), YoloLabel("surfboard", "서프보드"), YoloLabel("tennis racket", "테니스 라켓"), YoloLabel("bottle", "병"),
        YoloLabel("wine glass", "와인잔"), YoloLabel("cup", "컵"), YoloLabel("fork", "포크"), YoloLabel("knife", "나이프"),
        YoloLabel("spoon", "숟가락"), YoloLabel("bowl", "그릇"), YoloLabel("banana", "바나나"), YoloLabel("apple", "사과"),
        YoloLabel("sandwich", "샌드위치"), YoloLabel("orange", "오렌지"), YoloLabel("broccoli", "브로콜리"), YoloLabel("carrot", "당근"),
        YoloLabel("hot dog", "핫도그"), YoloLabel("pizza", "피자"), YoloLabel("donut", "도넛"), YoloLabel("cake", "케이크"),
        YoloLabel("chair", "의자"), YoloLabel("couch", "소파"), YoloLabel("potted plant", "화분"), YoloLabel("bed", "침대"),
        YoloLabel("dining table", "식탁"), YoloLabel("toilet", "변기"), YoloLabel("tv", "티비"), YoloLabel("laptop", "노트북"),
        YoloLabel("mouse", "마우스"), YoloLabel("remote", "리모컨"), YoloLabel("keyboard", "키보드"), YoloLabel("cell phone", "휴대폰"),
        YoloLabel("microwave", "전자레인지"), YoloLabel("oven", "오븐"), YoloLabel("toaster", "토스터"), YoloLabel("sink", "싱크대"),
        YoloLabel("refrigerator", "냉장고"), YoloLabel("book", "책"), YoloLabel("clock", "시계"), YoloLabel("vase", "꽃병"),
        YoloLabel("scissors", "가위"), YoloLabel("teddy bear", "테디베어"), YoloLabel("hair drier", "헤어드라이어"), YoloLabel("toothbrush", "칫솔")
    )
}