package ar.pharmatrace.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.pharmatrace.mobile.ui.LoginState
import ar.pharmatrace.mobile.ui.LoginViewModel

@Composable
fun LoginScreen(vm: LoginViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    LoginContent(s, onChange = vm::update, onLogin = vm::login)
}

@Composable
fun LoginContent(s: LoginState, onChange: ((LoginState) -> LoginState) -> Unit, onLogin: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandMark(48.dp)
                Column {
                    Text("PharmaTrace", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Vademécum de trazabilidad",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Ingresá con el usuario de tu organización.", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = s.apiUrl,
                onValueChange = { v -> onChange { it.copy(apiUrl = v) } },
                label = { Text("Servidor") },
                placeholder = { Text("https://api.pharmatrace.example") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.username,
                onValueChange = { v -> onChange { it.copy(username = v) } },
                label = { Text("Usuario") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.password,
                onValueChange = { v -> onChange { it.copy(password = v) } },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onLogin() }),
                modifier = Modifier.fillMaxWidth(),
            )
            s.error?.let { Notice(it, MaterialTheme.colorScheme.error) }
            Button(
                onClick = onLogin,
                enabled = !s.busy && s.username.isNotBlank() && s.password.isNotEmpty(),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (s.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Ingresar")
            }
        }
    }
}
