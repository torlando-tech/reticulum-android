package tech.torlando.rns.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8BD0E0),
    secondary = Color(0xFF82B3C0),
    tertiary = Color(0xFF9ECAAF),
    background = Color(0xFF0F1A1E),
    surface = Color(0xFF0F1A1E),
    surfaceVariant = Color(0xFF1A2A30),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006878),
    secondary = Color(0xFF4A6267),
    tertiary = Color(0xFF526350),
    background = Color(0xFFF5FAFB),
    surface = Color(0xFFF5FAFB),
)

@Composable
fun ReticulumTransportTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
