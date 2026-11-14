package ar.pharmatrace.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ORG_NAMES = mapOf(
    "LabMSP" to "Laboratorio",
    "DistMSP" to "Droguería",
    "FarmaMSP" to "Farmacia",
    "RegMSP" to "Regulador",
)

/** The organisation behind an MSP ID, as the web UI names it. Unknown MSPs are shown as they are. */
fun orgName(mspId: String): String = ORG_NAMES[mspId] ?: mspId

private val ButtonShape = RoundedCornerShape(4.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                        }
                    },
                    actions = { actions() },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        content = content,
    )
}

/** Scrollable column with the usual screen padding. */
@Composable
fun ScreenColumn(padding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** The PharmaTrace mark: a white pharmacy cross on the brand blue. */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    val blue = MaterialTheme.colorScheme.primary
    val cross = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier.size(size)) {
        val s = this.size.width
        drawRoundRect(color = blue, cornerRadius = CornerRadius(s * 0.19f, s * 0.19f))
        val arm = s * 0.19f
        val len = s * 0.62f
        drawRect(color = cross, topLeft = Offset((s - arm) / 2f, (s - len) / 2f), size = Size(arm, len))
        drawRect(color = cross, topLeft = Offset((s - len) / 2f, (s - arm) / 2f), size = Size(len, arm))
    }
}

/** A sheet of the reference book: white paper with a hairline border. */
@Composable
fun Sheet(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/** Title of a section inside a [Sheet], with an optional line saying what it is for. */
@Composable
fun SectionTitle(title: String, hint: String? = null, icon: ImageVector? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The label of a record, like the one printed on a medicine box: a band in the colour of its
 * state, the name in serif and the facts that identify it in label/value pairs, two per row.
 */
@Composable
fun RecordHeader(
    title: String,
    tone: Color,
    facts: List<Pair<String, String>> = emptyList(),
    chip: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(tone))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                if (chip != null) chip()
                facts.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (label, value) ->
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** A state as a small tag: outlined with a dot, or filled when it needs attention. */
@Composable
fun StatusChip(label: String, color: Color, filled: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (filled) color else Color.Transparent,
        border = BorderStroke(1.dp, color),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!filled) Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (filled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** A line of a record: label on the left, value on the right, ruled off from the line above. */
@Composable
fun LabeledRow(label: String, value: String, first: Boolean = false) {
    Column {
        if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

/** A message that needs to be read: tinted box with a border in its colour. */
@Composable
fun Notice(text: String, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

/**
 * Horizontal thermometer: the band is the range the policy allows and the mark is [value].
 * The scale leaves room on both sides so a reading out of range is still drawn.
 */
@Composable
fun TempGauge(value: Double, min: Double, max: Double, okColor: Color, badColor: Color, description: String) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val inRange = value >= min && value <= max
    val span = (max - min).coerceAtLeast(0.1)
    val lo = min - span * 0.75
    val hi = max + span * 0.75
    fun fraction(v: Double): Float = ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .semantics { contentDescription = description },
    ) {
        val barHeight = size.height * 0.5f
        val top = (size.height - barHeight) / 2f
        val radius = CornerRadius(barHeight / 2f, barHeight / 2f)
        drawRoundRect(color = track, topLeft = Offset(0f, top), size = Size(size.width, barHeight), cornerRadius = radius)
        val from = fraction(min) * size.width
        val to = fraction(max) * size.width
        drawRect(color = okColor.copy(alpha = 0.45f), topLeft = Offset(from, top), size = Size(to - from, barHeight))
        val markWidth = 4.dp.toPx()
        val x = (fraction(value) * size.width - markWidth / 2f).coerceIn(0f, size.width - markWidth)
        drawRoundRect(
            color = if (inRange) okColor else badColor,
            topLeft = Offset(x, 0f),
            size = Size(markWidth, size.height),
            cornerRadius = CornerRadius(markWidth / 2f, markWidth / 2f),
        )
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, shape = ButtonShape, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(text)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, shape = ButtonShape, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(text)
    }
}
