package com.asmrhelper.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Apple 风：标题用 SemiBold + 收紧字距（tracking-tight），正文保持正常字重。
private fun baseTypography(): Typography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
)

/**
 * 全局字型：`customFont` 非空时把该字体铺到全部字型上（含 Material3 默认的
 * headlineSmall / bodySmall / labelSmall 等未在 base 里显式覆盖的样式），
 * 实现「自定义文字类型」全局生效；null 表示跟随系统。
 */
fun appTypography(customFont: FontFamily? = null): Typography {
    val base = baseTypography()
    if (customFont == null) return base
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = customFont),
        displayMedium = base.displayMedium.copy(fontFamily = customFont),
        displaySmall = base.displaySmall.copy(fontFamily = customFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = customFont),
        headlineMedium = base.headlineMedium.copy(fontFamily = customFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = customFont),
        titleLarge = base.titleLarge.copy(fontFamily = customFont),
        titleMedium = base.titleMedium.copy(fontFamily = customFont),
        titleSmall = base.titleSmall.copy(fontFamily = customFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = customFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = customFont),
        bodySmall = base.bodySmall.copy(fontFamily = customFont),
        labelLarge = base.labelLarge.copy(fontFamily = customFont),
        labelMedium = base.labelMedium.copy(fontFamily = customFont),
        labelSmall = base.labelSmall.copy(fontFamily = customFont)
    )
}

/** 兼容旧引用：无自定义字体时的默认字型。 */
val AppTypography: Typography = appTypography()
