package ar.pharmatrace.mobile.ui.theme

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

// Pharmacy teal, with status colors that read the same in light and dark.
private val Teal = Color(0xFF0E5E55)
private val TealLight = Color(0xFF7FD6C8)

val StatusOk = Color(0xFF2E7D32)
val StatusWarn = Color(0xFFB26A00)
val StatusBad = Color(0xFFC62828)

private val Light = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDE7),
    onPrimaryContainer = Color(0xFF00201C),
    background = Color(0xFFF7F9F8),
    surface = Color(0xFFF7F9F8),
)

private val Dark = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFFCDEDE7),
)

@Composable
fun PharmaTraceTheme(dynamic: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
