package com.nagarro.techmappoc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF001D36),
    
    secondary = Color(0xFF03A9F4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB3E5FC),
    onSecondaryContainer = Color(0xFF001F2A),
    
    tertiary = Color(0xFF4CAF50),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC8E6C9),
    onTertiaryContainer = Color(0xFF002106),
    
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFF410002),
    
    background = Color(0xFFFCFCFF),
    onBackground = Color(0xFF1A1C1E),
    
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF003256),
    primaryContainer = Color(0xFF00497A),
    onPrimaryContainer = Color(0xFFCAE6FF),
    
    secondary = Color(0xFF81D4FA),
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF004D62),
    onSecondaryContainer = Color(0xFFB3E5FC),
    
    tertiary = Color(0xFFA5D6A7),
    onTertiary = Color(0xFF003910),
    tertiaryContainer = Color(0xFF005319),
    onTertiaryContainer = Color(0xFFC8E6C9),
    
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F)
)

@Composable
fun PassportReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
