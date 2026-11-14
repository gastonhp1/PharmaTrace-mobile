package ar.pharmatrace.mobile.ui.screens

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import ar.pharmatrace.mobile.data.Session

@Composable
fun HomeScreen(session: Session, onOpenBox: () -> Unit, onCargo: (String) -> Unit, onLogout: () -> Unit) {
    var cargoId by rememberSaveable { mutableStateOf("") }
    ScreenScaffold(
        title = "PharmaTrace",
        actions = { IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Salir") } },
    ) { padding ->
        ScreenColumn(padding) {
            // Who is signed in, laid out as the credential of the person holding the phone.
            RecordHeader(
                title = session.user.username,
                tone = MaterialTheme.colorScheme.primary,
                facts = listOf(
                    "Organización" to orgName(session.user.mspId),
                    "Identidad en la red" to session.user.mspId,
                ),
            )

            Sheet {
                SectionTitle(
                    title = "Abrir una PharmaBox",
                    hint = "Apretá el botón de la caja y escaneá el QR que aparece en su pantalla. " +
                        "El servidor decide si podés abrirla según el estado del cargamento.",
                    icon = Icons.Filled.LockOpen,
                )
                PrimaryButton("Escanear el QR de la caja", onClick = onOpenBox)
            }

            Sheet {
                SectionTitle(
                    title = "Consultar un cargamento",
                    hint = "Custodia, lotes y cadena de frío.",
                    icon = Icons.Filled.LocalShipping,
                )
                OutlinedTextField(
                    value = cargoId,
                    onValueChange = { cargoId = it.trim() },
                    label = { Text("ID del cargamento") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (cargoId.isNotBlank()) onCargo(cargoId) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton("Ver el cargamento", onClick = { onCargo(cargoId) }, enabled = cargoId.isNotBlank())
            }
        }
    }
}
