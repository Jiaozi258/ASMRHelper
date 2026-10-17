package com.asmrhelper.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary

/**
 * 发现页占位。搜索 / 推荐 / 榜单 / 最近播放内容在 M2 音源层落地后填充。
 */
@Composable
fun DiscoverScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "发现",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "搜索 / 推荐 / 榜单（即将上线）",
                color = TextHint,
                fontSize = 14.sp
            )
        }
    }
}
