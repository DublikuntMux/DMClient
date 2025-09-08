package com.dublikunt.dmclient.screen

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.dublikunt.dmclient.R
import kotlin.system.exitProcess

@Composable
fun LockScreen(onSuccess: () -> Unit, correctPin: String) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    val requiredPinLength = correctPin.length
    var attempts by remember { mutableIntStateOf(0) }
    val maxAttempts = 3
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val biometricTried = remember { mutableStateOf(false) }

    val vibrationService = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    LaunchedEffect(Unit) {
        if (!biometricTried.value) {
            biometricTried.value = true
            authenticateWithBiometrics(context, onSuccess = onSuccess, onError = {
                errorMessage = it
            })
        }
    }

    if (attempts >= maxAttempts) {
        LaunchedEffect(Unit) { exitProcess(0) }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Branding Icon",
                modifier = Modifier
                    .size(100.dp),
                contentScale = ContentScale.Fit
            )
            Text("Enter your PIN", style = MaterialTheme.typography.headlineSmall)
            Text(
                "You have ${maxAttempts - attempts} attempt(s) remaining",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row {
                repeat(requiredPinLength) { index ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .padding(4.dp)
                            .then(
                                if (index < pin.length)
                                    Modifier.background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                                else
                                    Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                        CircleShape
                                    )
                            )
                    )
                }
            }

            val numbers = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("b", "0", "d")
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                numbers.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        row.forEach { item ->
                            when (item) {
                                "d" -> MorphingButton(icon = Icons.AutoMirrored.Filled.ArrowBack) {
                                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                }

                                "b" -> MorphingButton(icon = Icons.Default.Lock) {
                                    authenticateWithBiometrics(context, onSuccess) {
                                        errorMessage = it
                                    }
                                }

                                else -> MorphingButton(icon = null, text = item) {
                                    if (pin.length < requiredPinLength) {
                                        pin += item
                                        if (pin.length == requiredPinLength) {
                                            if (pin == correctPin) {
                                                onSuccess()
                                            } else {
                                                pin = ""
                                                attempts++
                                                errorMessage =
                                                    "Incorrect PIN. ${maxAttempts - attempts} attempt(s) left."

                                                vibrationService.vibrate(
                                                    VibrationEffect.createOneShot(
                                                        300,
                                                        VibrationEffect.DEFAULT_AMPLITUDE
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun MorphingButton(
    icon: ImageVector?,
    text: String? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.size(72.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
        } else if (text != null) {
            Text(text, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

fun authenticateWithBiometrics(
    context: Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val fragment = context as FragmentActivity
    val executor = ContextCompat.getMainExecutor(context)
    val biometricPrompt = BiometricPrompt(
        fragment,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Authentication failed")
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock App")
        .setSubtitle("Authenticate using your fingerprint")
        .setNegativeButtonText("Use PIN")
        .build()

    biometricPrompt.authenticate(promptInfo)
}
