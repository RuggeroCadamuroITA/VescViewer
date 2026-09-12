package com.ruggerocadamuro.myapplication.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import android.content.Intent
import com.ruggerocadamuro.myapplication.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ruggerocadamuro.myapplication.ServiceLocator
import com.ruggerocadamuro.myapplication.data.security.AuthRepository

@Composable
fun AuthScreen(
    setupMode: Boolean,
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    val auth = ServiceLocator.authManager
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canUseBiometric = remember(context) {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
    var biometricRequested by rememberSaveable(setupMode) { mutableStateOf(false) }

    fun authenticateBiometric() {
        val activity = context as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    auth.unlock()
                    onAuthenticated()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    error = errString.toString()
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Sblocca VescViewer")
                .setSubtitle("Usa la biometria o il PIN dell'app")
                .setNegativeButtonText("Usa PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
        )
    }

    LaunchedEffect(setupMode, canUseBiometric) {
        if (!setupMode && canUseBiometric && !biometricRequested) {
            biometricRequested = true
            authenticateBiometric()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            if (setupMode) "Proteggi VescViewer" else "VescViewer bloccato",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            if (setupMode) "Crea un PIN di almeno 6 cifre. Potrai usare la biometria per gli accessi successivi."
            else "Sblocca l'app con la biometria o con il PIN.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(12); error = null },
            label = { Text(if (setupMode) "Nuovo PIN" else "PIN") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        )
        if (setupMode) {
            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it.filter(Char::isDigit).take(12); error = null },
                label = { Text("Conferma PIN") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        Button(
            onClick = {
                if (saving) return@Button
                if (setupMode) {
                    if (pin.length < AuthRepository.MIN_PIN_LENGTH ||
                        !pin.all(Char::isDigit) || pin != confirmation
                    ) {
                        error = "Il PIN deve avere almeno 6 cifre e coincidere con la conferma."
                    } else {
                        showRestartDialog = true
                    }
                } else {
                    saving = true
                    scope.launch {
                        val success = withContext(Dispatchers.Default) { auth.verify(pin) }
                        saving = false
                        if (success) onAuthenticated() else error = "PIN non valido."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
        ) {
            Text(if (saving) "Attendi…" else if (setupMode) "Salva PIN" else "Sblocca")
        }
        if (!setupMode && canUseBiometric) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                Button(onClick = ::authenticateBiometric) { Text("Usa biometria") }
            }
        }
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { if (!saving) showRestartDialog = false },
            title = { Text("Riavvio necessario") },
            text = {
                Text("Il PIN verrà salvato in modo sicuro. Dopo il salvataggio VescViewer si riavvierà e ti chiederà l'impronta digitale o il PIN per accedere.")
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        showRestartDialog = false
                        saving = true
                        scope.launch {
                            val success = withContext(Dispatchers.Default) {
                                auth.createPin(pin, confirmation)
                            }
                            if (success) {
                                val activity = context as? FragmentActivity
                                if (activity != null) {
                                    activity.startActivity(
                                        Intent(activity, MainActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        }
                                    )
                                    activity.finish()
                                } else {
                                    saving = false
                                    onAuthenticated()
                                }
                            } else {
                                saving = false
                                error = "Impossibile salvare il PIN. Riprova."
                            }
                        }
                    }
                ) { Text("Salva e riavvia") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) { Text("Annulla") }
            }
        )
    }
}
