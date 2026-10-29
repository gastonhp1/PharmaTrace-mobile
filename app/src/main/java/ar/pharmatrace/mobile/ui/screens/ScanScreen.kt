package ar.pharmatrace.mobile.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.pharmatrace.core.BoxProtocol
import ar.pharmatrace.mobile.ui.hasAll
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScanScreen(onQr: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasAll(arrayOf(Manifest.permission.CAMERA))) }
    var notABox by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    ScanContent(
        granted = granted,
        notABox = notABox,
        onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
        onBack = onBack,
    ) {
        QrScanner(
            onCode = { text ->
                // Anything that is not a PharmaBox QR (a medicine box, a URL) is ignored.
                val isBox = BoxProtocol.parseQr(text) != null
                if (isBox) onQr(text.trim()) else notABox = true
                isBox
            },
        )
    }
}

/** The screen around the camera; [camera] is the live preview (a placeholder in screenshot tests). */
@Composable
fun ScanContent(
    granted: Boolean,
    notABox: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
    camera: @Composable () -> Unit,
) {
    ScreenScaffold(title = "Escanear la caja", onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (granted) {
                camera()
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(240.dp)
                        .border(3.dp, Color.White, RoundedCornerShape(16.dp)),
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                ) {
                    Text(
                        if (notABox) "Ese código no es de una PharmaBox. Apuntá al QR de la pantalla de la caja."
                        else "Apuntá al QR que muestra la caja (vale 2 minutos).",
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Hace falta la cámara para leer el QR de la caja.", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onRequestPermission) { Text("Permitir cámara") }
                }
            }
        }
    }
}

/**
 * CameraX preview + ML Kit QR detection. [onCode] returns true when it took the code; after that
 * the scanner stops delivering codes, so navigation happens exactly once.
 */
@Composable
private fun QrScanner(onCode: (String) -> Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    val taken = remember { AtomicBoolean(false) }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).also { view ->
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { proxy -> analyze(proxy, scanner, taken, ctx, onCode) }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
    )
}

@OptIn(ExperimentalGetImage::class)
private fun analyze(
    proxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    taken: AtomicBoolean,
    context: android.content.Context,
    onCode: (String) -> Boolean,
) {
    val media = proxy.image
    if (media == null || taken.get()) {
        proxy.close()
        return
    }
    scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
        .addOnSuccessListener(ContextCompat.getMainExecutor(context)) { codes ->
            val text = codes.firstNotNullOfOrNull { it.rawValue } ?: return@addOnSuccessListener
            if (!taken.get() && onCode(text)) taken.set(true)
        }
        .addOnCompleteListener { proxy.close() }
}
