package com.example.kitu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
    // close button
    IconButton(onClick = { /* 닫기 동작 */ }) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.close)
        )
    }

    // take photo button
    IconButton(onClick = { /* 촬영 동작 */ }) {
        Icon(
            imageVector = Icons.Default.PhotoCamera,
            contentDescription = stringResource(R.string.take_photo)
        )
    }

    // AI object detection button
    IconButton(onClick = { /* AI 객체인식 동작 */ }) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = stringResource(R.string.object_detection)
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
        KituAppLayout()
    }
}