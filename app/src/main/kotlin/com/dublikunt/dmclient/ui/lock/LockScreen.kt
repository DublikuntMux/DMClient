package com.dublikunt.dmclient.ui.lock

import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dublikunt.dmclient.R
import com.dublikunt.dmclient.data.lock.LockState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LockScreen(state: LockState.Locked) {
    val viewModel: LockViewModel = hiltViewModel()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
    val biometricsAvailable =
        remember(context) {
            BiometricManager.from(context).canAuthenticate(authenticators) ==
                    BiometricManager.BIOMETRIC_SUCCESS
        }
    var biometricMessage by remember { mutableStateOf<String?>(null) }
    var remainingSeconds by remember { mutableLongStateOf(0) }
    val shake = remember { Animatable(0f) }
    val prompt =
        remember(activity, viewModel) {
            activity?.let {
                BiometricPrompt(
                    it,
                    ContextCompat.getMainExecutor(context),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult
                        ) {
                            viewModel.biometricSucceeded()
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            if (
                                errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                errorCode != BiometricPrompt.ERROR_CANCELED
                            )
                                biometricMessage = errString.toString()
                        }

                        override fun onAuthenticationFailed() {
                            biometricMessage =
                                "Biometric not recognized. Try again or enter your PIN."
                        }
                    },
                )
            }
        }

    fun authenticate() {
        biometricMessage = null
        prompt?.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock DMClient")
                .setSubtitle("Use biometrics or enter your PIN")
                .setAllowedAuthenticators(authenticators)
                .setNegativeButtonText("Use PIN")
                .build()
        )
    }
    BackHandler { activity?.moveTaskToBack(true) }
    DisposableEffect(prompt) { onDispose { prompt?.cancelAuthentication() } }
    LaunchedEffect(Unit) {
        viewModel.clear()
        if (biometricsAvailable && state.cooldownUntil == null) authenticate()
    }
    LaunchedEffect(state.cooldownUntil) {
        val deadline = state.cooldownUntil
        do {
            remainingSeconds =
                if (deadline == null) 0
                else ((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999) / 1_000
            if (remainingSeconds > 0) delay(200)
        } while (remainingSeconds > 0)
    }
    LaunchedEffect(state.failedAttempts) {
        if (state.failedAttempts > 0) {
            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            listOf(-12f, 12f, -8f, 8f, 0f).forEach { shake.animateTo(it, tween(60)) }
        }
    }
    val enabled =
        !busy &&
                (state.cooldownUntil == null || state.cooldownUntil <= SystemClock.elapsedRealtime())
    Dialog(
        onDismissRequest = { activity?.moveTaskToBack(true) },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .widthIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Surface(
                        shape = MaterialShapes.Cookie12Sided.toShape(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(96.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painterResource(R.drawable.logo),
                                "DMClient",
                                Modifier.size(72.dp),
                            )
                        }
                    }
                    Text("Enter PIN", style = MaterialTheme.typography.headlineSmall)
                    Row(
                        Modifier
                            .graphicsLayer { translationX = shake.value }
                            .semantics { contentDescription = "${input.length} digits entered" },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(maxOf(4, input.length)) { index ->
                            val filled = index < input.length
                            val scale by
                            animateFloatAsState(
                                if (filled) 1.2f else 1f,
                                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                                label = "PIN dot",
                            )
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .scale(scale)
                                    .then(
                                        if (filled)
                                            Modifier.background(
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape,
                                            )
                                        else
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.outline,
                                                CircleShape,
                                            )
                                    )
                            )
                        }
                    }
                    Text(
                        if (remainingSeconds > 0)
                            "Too many attempts. Try again in $remainingSeconds s"
                        else if (state.failedAttempts > 0) "Wrong PIN"
                        else biometricMessage ?: "Enter your PIN to unlock",
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (remainingSeconds > 0 || state.failedAttempts > 0)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        for (row in 0..2) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            for (column in 1..3) {
                                val digit = row * 3 + column
                                KeypadButton(enabled, { viewModel.digit(digit) }) {
                                    Text(
                                        digit.toString(),
                                        style = MaterialTheme.typography.headlineSmall,
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (biometricsAvailable && prompt != null)
                                KeypadButton(enabled, ::authenticate) {
                                    Icon(Icons.Rounded.Fingerprint, "Unlock with biometrics")
                                }
                            else Spacer(Modifier.size(72.dp))
                            KeypadButton(enabled, { viewModel.digit(0) }) {
                                Text("0", style = MaterialTheme.typography.headlineSmall)
                            }
                            KeypadButton(
                                enabled && input.isNotEmpty(),
                                if (input.length >= 4) viewModel::submit else viewModel::backspace,
                            ) {
                                if (input.length >= 4) Icon(Icons.Rounded.Check, "Submit PIN")
                                else Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete digit")
                            }
                        }
                        if (input.length >= 4)
                            TextButton(onClick = viewModel::backspace, enabled = enabled) {
                                Icon(Icons.AutoMirrored.Rounded.Backspace, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Delete digit")
                            }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KeypadButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(72.dp),
        shapes = ButtonDefaults.shapes(),
        contentPadding = PaddingValues(0.dp),
        content = content,
    )
}
