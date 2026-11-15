package ar.pharmatrace.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.pharmatrace.mobile.R

// Same identity as the web UI (PharmaTrace-UI v2): a drug reference book. White paper on a cool
// grey ground, night-blue ink, "vademécum blue" as the brand colour. Green is the pharmacy cross
// (compliant), red is kept for compromised or denied, amber for what is still being watched.
private val Blue = Color(0xFF1D4E89)
private val BlueLight = Color(0xFF8DB9F2)

// Source Serif 4 for names and titles, IBM Plex Sans for everything else. Both are bundled
// (res/font, latin subset, SIL OFL: see licenses/), so nothing is downloaded at runtime.
val Serif = FontFamily(Font(R.font.source_serif_semibold, FontWeight.SemiBold))
val Sans = FontFamily(
    Font(R.font.plex_sans_regular, FontWeight.Normal),
    Font(R.font.plex_sans_medium, FontWeight.Medium),
    Font(R.font.plex_sans_semibold, FontWeight.SemiBold),
)

/** Status colours that keep their meaning (and enough contrast) in light and dark. */
@Composable
fun statusOk(): Color = if (isSystemInDarkTheme()) Color(0xFF63C794) else Color(0xFF1B7F4E)

@Composable
fun statusWarn(): Color = if (isSystemInDarkTheme()) Color(0xFFE5B45A) else Color(0xFF9A6200)

@Composable
fun statusBad(): Color = if (isSystemInDarkTheme()) Color(0xFFF28B82) else Color(0xFFB3261E)

private val Light = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8F6),
    onPrimaryContainer = Color(0xFF0B2545),
    secondary = Color(0xFF1B7F4E),
    onSecondary = Color.White,
    background = Color(0xFFF1F4F8),
    onBackground = Color(0xFF16283A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16283A),
    surfaceVariant = Color(0xFFE6ECF2),
    onSurfaceVariant = Color(0xFF4C6076),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F4F8),
    surfaceContainerHighest = Color(0xFFE6ECF2),
    outline = Color(0xFF8497AB),
    outlineVariant = Color(0xFFD3DCE6),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF5C1410),
)

private val Dark = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color(0xFF0B2545),
    primaryContainer = Color(0xFF1D3F6B),
    onPrimaryContainer = Color(0xFFDCE8F6),
    secondary = Color(0xFF63C794),
    onSecondary = Color(0xFF06351F),
    background = Color(0xFF0D151E),
    onBackground = Color(0xFFE6EDF5),
    surface = Color(0xFF15202C),
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Color(0xFF1D2B3A),
    onSurfaceVariant = Color(0xFF9DB0C4),
    surfaceContainerLowest = Color(0xFF0D151E),
    surfaceContainerLow = Color(0xFF15202C),
    surfaceContainer = Color(0xFF15202C),
    surfaceContainerHigh = Color(0xFF1D2B3A),
    surfaceContainerHighest = Color(0xFF24364A),
    outline = Color(0xFF6F8399),
    outlineVariant = Color(0xFF2A3A4B),
    error = Color(0xFFF28B82),
    onError = Color(0xFF5C1410),
    errorContainer = Color(0xFF5C1410),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val Base = Typography()
private val AppTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Sans),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Sans),
    bodySmall = Base.bodySmall.copy(fontFamily = Sans),
    labelLarge = Base.labelLarge.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
)

// Printed-label corners rather than the default pills.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
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
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}
