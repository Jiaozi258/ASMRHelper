package com.asmrhelper.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 语义化配色集合。
 * 浅色 = Apple 风格（#f5f5f7 背景 + 白色卡片 + 近黑文字），深色 = 原沉浸式深色。
 * 通过 [LocalAppColors] 注入，随「浅色/深色」主题动态切换。
 */
@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textHint: Color,
    val controlWhite: Color,
    val errorRed: Color,
    val successGreen: Color,
)

// ── Apple 浅色 ──────────────────────────────────────────
val LightAppColors = AppColors(
    background = Color(0xFFF5F5F7),     // Apple 灰
    surface = Color(0xFFFFFFFF),        // 白卡片
    surfaceVariant = Color(0xFFE8E8ED), // 分割线 / 次级面
    textPrimary = Color(0xFF1D1D1F),    // 近黑
    textSecondary = Color(0xFF6E6E73),  // Apple 灰字
    textHint = Color(0xFF86868B),
    controlWhite = Color(0xFFFFFFFF),
    errorRed = Color(0xFFFF3B30),       // Apple 红
    successGreen = Color(0xFF34C759),   // Apple 绿
)

// ── 深色沉浸（保留原观感） ──────────────────────────────
val DarkAppColors = AppColors(
    background = Color(0xFF0D0D0D),
    surface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFF252540),
    textPrimary = Color(0xFFF5F5F5),
    textSecondary = Color(0xFFB0B0B0),
    textHint = Color(0xFF6A6A6A),
    controlWhite = Color(0xFFFFFFFF),
    errorRed = Color(0xFFCF6679),
    successGreen = Color(0xFF4CAF50),
)

/** 当前语义配色（浅/深），由 ASMRHelperTheme 注入。 */
val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

// Apple 蓝 —— 新的默认强调色
val AppleBlue = Color(0xFF0071E3)
val AppleBlueVariant = Color(0xFF0077ED)
// 保留旧引用（纯 Color，仍可直接使用）
val AccentPurple = Color(0xFFBB86FC)
val AccentPurpleVariant = Color(0xFF9C64E8)

/** 动态强调色 — 由 ThemePreset 通过 CompositionLocalProvider 注入 */
val LocalAccentColor = staticCompositionLocalOf { AppleBlue }

// ── 兼容旧引用：语义色改为 @Composable getter，随浅/深主题动态切换 ──
// 这样各界面文件的既有 import / 用法无需改动，即可自动适配主题。
val DarkBackground: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.background
val DarkSurface: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
val DarkSurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceVariant
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textSecondary
val TextHint: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textHint
val ControlWhite: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.controlWhite
val ErrorRed: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.errorRed
val SuccessGreen: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.successGreen
