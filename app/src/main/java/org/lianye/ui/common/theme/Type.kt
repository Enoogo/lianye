package org.lianye.ui.common.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun text(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp
)

val Typography = Typography(
    displayLarge = text(40, 48, FontWeight.Medium),
    displayMedium = text(36, 44, FontWeight.Medium),
    displaySmall = text(32, 40, FontWeight.Medium),
    headlineLarge = text(28, 38, FontWeight.Medium),
    headlineMedium = text(24, 33, FontWeight.Medium),
    headlineSmall = text(22, 30, FontWeight.Medium),
    titleLarge = text(20, 28, FontWeight.Medium),
    titleMedium = text(18, 26, FontWeight.Medium),
    titleSmall = text(14, 22, FontWeight.Medium),
    bodyLarge = text(15, 23),
    bodyMedium = text(14, 21),
    bodySmall = text(12, 18),
    labelLarge = text(14, 20, FontWeight.Medium),
    labelMedium = text(13, 19, FontWeight.Medium),
    labelSmall = text(12, 18, FontWeight.Medium)
)
