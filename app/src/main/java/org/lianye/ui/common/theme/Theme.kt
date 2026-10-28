package org.lianye.ui.common.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = LianyeInk, onPrimary = LianyeWarmBackground,
    primaryContainer = Color(0xFFEFEBE4), onPrimaryContainer = LianyeInk,
    secondary = Color(0xFFA54023), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7E4D9), onSecondaryContainer = Color(0xFF743018),
    tertiary = LianyeGreen,
    background = LianyeWarmBackground, onBackground = LianyeInk,
    surface = Color.White, onSurface = LianyeInk,
    surfaceVariant = Color(0xFFF0EDE7), onSurfaceVariant = Color(0xFF6C6D68),
    surfaceDim = Color(0xFFE8E4DD), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = LianyeWarmBackground,
    surfaceContainer = Color(0xFFF0EDE7), surfaceContainerHigh = Color(0xFFEBE7E0),
    surfaceContainerHighest = Color(0xFFE5E1D9),
    inverseSurface = LianyeInk, inverseOnSurface = LianyeWarmBackground, inversePrimary = LianyeWarmBackground,
    outline = Color(0xFF8C8E87), outlineVariant = Color(0xFFE0DDD6),
    error = LianyeRed, errorContainer = Color(0xFFFFE7E3), onErrorContainer = Color(0xFF70211E)
)

private val DarkColorScheme = darkColorScheme(
    primary = LianyeWarmBackground, onPrimary = LianyeDarkBackground,
    primaryContainer = Color(0xFF303B35), onPrimaryContainer = LianyeWarmBackground,
    secondary = BrandPalette.DarkAccent, onSecondary = Color(0xFF4D2618),
    secondaryContainer = Color(0xFF573225), onSecondaryContainer = Color(0xFFFFD9C8),
    tertiary = Color(0xFF9FC9AC),
    background = LianyeDarkBackground, onBackground = LianyeWarmBackground,
    surface = LianyeSurface, onSurface = LianyeWarmBackground,
    surfaceVariant = Color(0xFF303B35), onSurfaceVariant = Color(0xFFADB7B0),
    surfaceDim = LianyeDarkBackground, surfaceBright = Color(0xFF3A443E),
    surfaceContainerLowest = Color(0xFF141A17), surfaceContainerLow = Color(0xFF232C27),
    surfaceContainer = LianyeSurface, surfaceContainerHigh = Color(0xFF303B35),
    surfaceContainerHighest = Color(0xFF39443D),
    inverseSurface = LianyeWarmBackground, inverseOnSurface = LianyeInk, inversePrimary = LianyeInk,
    outline = Color(0xFF859389), outlineVariant = Color(0xFF45514B),
    error = Color(0xFFFFB4AA), errorContainer = Color(0xFF5B211F), onErrorContainer = Color(0xFFFFDAD5)
)

@Composable
@Suppress("UNUSED_PARAMETER")
fun LianyeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(18.dp),
            extraLarge = RoundedCornerShape(20.dp)
        ),
        content = content
    )
}
