package ar.pharmatrace.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ar.pharmatrace.mobile.ui.PharmaTraceNav
import ar.pharmatrace.mobile.ui.theme.PharmaTraceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PharmaTraceApp).container
        setContent {
            PharmaTraceTheme {
                PharmaTraceNav(container)
            }
        }
    }
}
