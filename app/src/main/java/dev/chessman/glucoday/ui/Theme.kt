package dev.chessman.glucoday.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chessman.glucoday.domain.AppTheme

private val Dark = darkColorScheme(
    primary = Color(0xFF91DBC0), onPrimary = Color(0xFF00382B), primaryContainer = Color(0xFF174B3D), onPrimaryContainer = Color(0xFFC5F4E0),
    secondary = Color(0xFFE8CC9A), onSecondary = Color(0xFF3C2F17), secondaryContainer = Color(0xFF463B28),
    background = Color(0xFF101816), onBackground = Color(0xFFE6EDE8),
    surface = Color(0xFF101816), onSurface = Color(0xFFE6EDE8), surfaceVariant = Color(0xFF263B33), onSurfaceVariant = Color(0xFFB4C9BD),
    surfaceContainer = Color(0xFF19241F), surfaceContainerLow = Color(0xFF17211D), surfaceContainerHigh = Color(0xFF223028),
    outline = Color(0xFF789084), outlineVariant = Color(0xFF33483D),
)
private val Light = lightColorScheme(
    primary = Color(0xFF24694F), onPrimary = Color.White, primaryContainer = Color(0xFFC3EFDA), onPrimaryContainer = Color(0xFF123A2D),
    secondary = Color(0xFF72572A), background = Color(0xFFF5F8F2), onBackground = Color(0xFF16231C),
    surface = Color(0xFFF5F8F2), onSurface = Color(0xFF16231C), surfaceVariant = Color(0xFFE0E9DE), onSurfaceVariant = Color(0xFF4A6050),
    surfaceContainerLow = Color(0xFFEDF2E9), surfaceContainer = Color(0xFFE8EFE4), surfaceContainerHigh = Color(0xFFE1EBDD),
    outline = Color(0xFF6E8272), outlineVariant = Color(0xFFC2D1C1),
)

@Composable fun GlucoTheme(theme: AppTheme = AppTheme.DARK, content: @Composable () -> Unit) {
    val dark = theme == AppTheme.DARK || (theme == AppTheme.SYSTEM && isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
            headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
}
