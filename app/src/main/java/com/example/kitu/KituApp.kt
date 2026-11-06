package com.example.kitu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kitu.ui.theme.KituTheme
import kotlin.time.Duration.Companion.seconds

@Composable
fun KituApp()
{
    KituAppLayout()
}


@Composable
fun KituAppLayout(modifier: Modifier = Modifier)
{
    Column(modifier = modifier)
    {
        // camera
        Box(
            modifier = modifier
                        .fillMaxWidth()
                        .weight(0.5f)
                        .background(Color.DarkGray)
        ) {
            cameraArea()
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
            toolBarArea(modifier)
        }

        // grid
        gridArea(modifier = modifier
                                .fillMaxWidth()
                                .weight(0.5f)
                                .padding(horizontal = 16.dp, vertical = 12.dp))
    }
}


@Composable
fun cameraArea()
{

}


@Composable
fun toolBarArea(modifier: Modifier = Modifier)
{
    // 1. 닫기 버튼
    IconButton(onClick = { /* 닫기 동작 */ }) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "닫기"
        )
    }

    // 2. 사진 촬영 버튼
    IconButton(onClick = { /* 촬영 동작 */ }) {
        Icon(
            imageVector = Icons.Default.PhotoCamera,
            contentDescription = "사진 촬영"
        )
    }

    // 3. AI 객체인식 실행 버튼
    IconButton(onClick = { /* AI 객체인식 동작 */ }) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "AI 객체인식"
        )
    }
}


@Composable
fun gridArea(modifier: Modifier = Modifier)
{
    val headers = listOf(
                    stringResource(id = R.string.header_object) to 8f,
                    stringResource(id = R.string.header_count) to 2f
    )

    // temp
    val records = listOf(
        listOf("데이터 A-1", "A-2"),
        listOf("데이터 B-1", "B-2"),
        listOf("데이터 C-1", "C-2")
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .background(Color.LightGray),
            //.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp) // 헤더와 본문 사이 여백을 없앰 (기본값이 0이므로 생략 가능)
    ) {
        // header
        Row(
            modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.DarkGray)
                            .padding(vertical = 2.dp, horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
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
                .padding(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {

            records.forEach { rowItems ->
                // 각 행(Row)을 생성
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp) // 셀 사이 세로선 간격
                ) {
                    rowItems.forEachIndexed { index, cellText ->
                        val cellWeight = headers.get(index).second

                        Box(
                            modifier = Modifier
                                .weight(cellWeight)
                                .background(Color.White) // 셀 배경색
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
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
        KituAppLayout()
    }
}