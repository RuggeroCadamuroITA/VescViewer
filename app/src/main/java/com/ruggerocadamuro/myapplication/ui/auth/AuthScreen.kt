package com.ruggerocadamuro.myapplication.ui.auth

import android.content.Intent
import android.annotation.SuppressLint
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.ruggerocadamuro.myapplication.MainActivity
import com.ruggerocadamuro.myapplication.ServiceLocator
import com.ruggerocadamuro.myapplication.data.security.AuthRepository
import com.ruggerocadamuro.myapplication.ui.components.GlassButton
import com.ruggerocadamuro.myapplication.ui.components.GlassSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PIN_REVEAL_DURATION_MS = 1_500L

private object LastCharacterVisibleTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val masked = buildString(text.length) {
            repeat((text.length - 1).coerceAtLeast(0)) { append('•') }
            if (text.isNotEmpty()) append(text.last())
        }
        return TransformedText(AnnotatedString(masked), OffsetMapping.Identity)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun AuthScreen(
    setupMode: Boolean,
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    val auth = ServiceLocator.authManager
    val scope = rememberCoroutineScope()
    val pinBringIntoView = remember { BringIntoViewRequester() }
    val confirmationBringIntoView = remember { BringIntoViewRequester() }
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var pinRevealsLastCharacter by remember { mutableStateOf(false) }
    var confirmationRevealsLastCharacter by remember { mutableStateOf(false) }
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
                .setTitle(context.getString(com.ruggerocadamuro.myapplication.R.string.auth_biometric_title))
                .setSubtitle(context.getString(com.ruggerocadamuro.myapplication.R.string.auth_biometric_subtitle))
                .setNegativeButtonText(context.getString(com.ruggerocadamuro.myapplication.R.string.auth_use_pin))
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (setupMode) Icons.Filled.Security else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                text = if (setupMode) stringResource(com.ruggerocadamuro.myapplication.R.string.auth_setup_title) else stringResource(com.ruggerocadamuro.myapplication.R.string.auth_locked_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                text = if (setupMode) {
                    stringResource(com.ruggerocadamuro.myapplication.R.string.auth_setup_desc)
                } else {
                    stringResource(com.ruggerocadamuro.myapplication.R.string.auth_locked_desc)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                shape = RoundedCornerShape(24.dp),
                glowColor = MaterialTheme.colorScheme.primary
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { input ->
                            pin = input.filter(Char::isDigit).take(12)
                            error = null
                            pinRevealsLastCharacter = pin.isNotEmpty()
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        label = { Text(if (setupMode) stringResource(com.ruggerocadamuro.myapplication.R.string.auth_new_pin) else stringResource(com.ruggerocadamuro.myapplication.R.string.auth_pin)) },
                        singleLine = true,
                        visualTransformation = if (pinRevealsLastCharacter) {
                            LastCharacterVisibleTransformation
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(pinBringIntoView)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    scope.launch {
                                        delay(180)
                                        pinBringIntoView.bringIntoView()
                                    }
                                }
                            }
                    )
                    LaunchedEffect(pin) {
                        if (pin.isNotEmpty()) {
                            pinRevealsLastCharacter = true
                            delay(PIN_REVEAL_DURATION_MS)
                            pinRevealsLastCharacter = false
                        }
                    }

                    LaunchedEffect(confirmation) {
                        if (confirmation.isNotEmpty()) {
                            confirmationRevealsLastCharacter = true
                            delay(PIN_REVEAL_DURATION_MS)
                            confirmationRevealsLastCharacter = false
                        }
                    }

                    AnimatedVisibility(
                        visible = setupMode,
                        enter = fadeIn() + slideInVertically { it / 3 },
                        exit = fadeOut() + slideOutVertically { it / 3 }
                    ) {
                        OutlinedTextField(
                            value = confirmation,
                            onValueChange = { input ->
                                confirmation = input.filter(Char::isDigit).take(12)
                                error = null
                                confirmationRevealsLastCharacter = confirmation.isNotEmpty()
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            label = { Text(stringResource(com.ruggerocadamuro.myapplication.R.string.auth_confirm_pin)) },
                            singleLine = true,
                            visualTransformation = if (confirmationRevealsLastCharacter) {
                                LastCharacterVisibleTransformation
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(confirmationBringIntoView)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        scope.launch {
                                            delay(180)
                                            confirmationBringIntoView.bringIntoView()
                                        }
                                    }
                                }
                        )
                    }

                    error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    GlassButton(
                        onClick = {
                            if (saving) return@GlassButton
                            if (setupMode) {
                                if (pin.length < AuthRepository.MIN_PIN_LENGTH || pin != confirmation) {
                                    error = context.getString(com.ruggerocadamuro.myapplication.R.string.auth_pin_mismatch)
                                } else {
                                    showRestartDialog = true
                                }
                            } else {
                                saving = true
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) { auth.verify(pin) }
                                    saving = false
                                    if (result.success) onAuthenticated() else error = if (result.retryAfterMs > 0L) {
                                        context.getString(
                                            com.ruggerocadamuro.myapplication.R.string.auth_retry_later,
                                            (result.retryAfterMs / 1000L).coerceAtLeast(1L)
                                        )
                                    } else {
                                        context.getString(com.ruggerocadamuro.myapplication.R.string.auth_invalid_pin)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving
                    ) {
                        Text(
                            text = if (saving) stringResource(com.ruggerocadamuro.myapplication.R.string.auth_verifying) else if (setupMode) stringResource(com.ruggerocadamuro.myapplication.R.string.auth_save_pin) else stringResource(com.ruggerocadamuro.myapplication.R.string.auth_unlock),
                            color = if (!saving) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!setupMode && canUseBiometric) {
                TextButton(
                    onClick = ::authenticateBiometric,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(com.ruggerocadamuro.myapplication.R.string.auth_use_biometric),
                        modifier = Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { if (!saving) showRestartDialog = false },
            title = { Text(stringResource(com.ruggerocadamuro.myapplication.R.string.auth_restart_title)) },
            text = {
                Text(
                    stringResource(com.ruggerocadamuro.myapplication.R.string.auth_restart_desc)
                )
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
                                error = context.getString(com.ruggerocadamuro.myapplication.R.string.auth_save_error)
                            }
                        }
                    }
                ) {
                    Text(stringResource(com.ruggerocadamuro.myapplication.R.string.auth_save_restart))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text(stringResource(com.ruggerocadamuro.myapplication.R.string.auth_cancel))
                }
            }
        )
    }
}
