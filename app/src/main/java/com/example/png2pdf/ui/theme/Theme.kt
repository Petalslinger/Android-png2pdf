package com.example.png2pdf.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Blue = Color(0xFF1F6FEB)
private val BlueLight = Color(0xFF9CC4FF)
private val Slate = Color(0xFF44546F)

private val LightScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E6FF),
    onPrimaryContainer = Color(0xFF00254C),
    secondary = Slate,
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
)

private val DarkScheme = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color(0xFF00305F),
    primaryContainer = Color(0xFF004784),
    onPrimaryContainer = Color(0xFFD8E6FF),
    secondary = Color(0xFFBFC9DA),
    background = Color(0xFF111418),
    surface = Color(0xFF181C20),
)

@Composable
fun Png2PdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Android 12+ 跟随壁纸取色 */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(colorScheme = colors, content = content)
}
