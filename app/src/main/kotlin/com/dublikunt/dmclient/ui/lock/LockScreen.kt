package com.dublikunt.dmclient.ui.lock

import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardTab
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.graphics.shapes.Morph
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dublikunt.dmclient.R
import com.dublikunt.dmclient.data.lock.LockState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val MaxKeySize = 80.dp
private val MinKeySize = 56.dp
private val KeyRowSpacing = 12.dp
private val KeyColumnSpacing = 24.dp
private val ScreenPadding = 24.dp
private val MaxPortraitHeight = 760.dp
private val DotSize = 14.dp
private val DotSlot = 28.dp
private const val MinKeyHighlightMillis = 150L

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val DotEntryShapes =
    listOf(
        MaterialShapes.Diamond,
        MaterialShapes.Triangle,
        MaterialShapes.Pentagon,
        MaterialShapes.Cookie4Sided,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Gem,
        MaterialShapes.Sunny,
    )

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
    val lockedOut = remainingSeconds > 0
    val enabled = !busy && !lockedOut
    val keypadAlpha by
    animateFloatAsState(
        if (lockedOut) 0.38f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "Keypad alpha",
    )
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
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val error = lockedOut || state.failedAttempts > 0
                val message =
                    when {
                        lockedOut -> "Too many attempts. Try again in $remainingSeconds s"
                        state.failedAttempts > 0 -> "Wrong PIN"
                        else -> biometricMessage ?: "Enter PIN"
                    }
                val header =
                    @Composable {
                        LockHeader(message, error, input.length) { shake.value }
                    }
                val keypad =
                    @Composable { keySize: Dp ->
                        Keypad(
                            keySize = keySize,
                            input = input,
                            enabled = enabled,
                            busy = busy,
                            onDigit = viewModel::digit,
                            onBackspace = viewModel::backspace,
                            onClear = viewModel::clear,
                            onSubmit = viewModel::submit,
                            modifier = Modifier.alpha(keypadAlpha),
                        )
                    }
                val biometricButton =
                    @Composable {
                        BiometricButton(
                            biometricsAvailable && prompt != null,
                            enabled,
                            ::authenticate,
                        )
                    }
                if (maxWidth > maxHeight && maxWidth >= 600.dp) {
                    // Landscape: header beside a keypad sized to fit the screen height.
                    val keySize =
                        ((maxHeight - ScreenPadding * 2 - KeyRowSpacing * 3) / 4)
                            .coerceIn(MinKeySize, MaxKeySize)
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = ScreenPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = ScreenPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            header()
                            biometricButton()
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            keypad(keySize)
                        }
                    }
                } else {
                    // Portrait: one column, capped in height so tall tablets keep it compact.
                    val contentHeight = minOf(maxHeight, MaxPortraitHeight)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = maxHeight)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            Modifier
                                .heightIn(min = contentHeight)
                                .padding(horizontal = 16.dp, vertical = ScreenPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            header()
                            Spacer(Modifier.weight(1f))
                            keypad(MaxKeySize)
                            biometricButton()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LockHeader(message: String, error: Boolean, length: Int, shake: () -> Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = MaterialShapes.Cookie12Sided.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.logo), "DMClient", Modifier.size(72.dp))
            }
        }
        Text(
            message,
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleMedium,
            color =
                if (error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        PinDots(
            length,
            Modifier
                .padding(vertical = 32.dp)
                .graphicsLayer { translationX = shake() }
                .semantics { contentDescription = "$length digits entered" },
        )
    }
}

@Composable
private fun Keypad(
    keySize: Dp,
    input: String,
    enabled: Boolean,
    busy: Boolean,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    fun press(action: () -> Unit) = {
        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        action()
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KeyRowSpacing)) {
        for (row in 0..2) Row(horizontalArrangement = Arrangement.spacedBy(KeyColumnSpacing)) {
            for (column in 1..3) {
                val digit = row * 3 + column
                DigitKey(digit, keySize, enabled, press { onDigit(digit) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KeyColumnSpacing)) {
            PinKey(
                size = keySize,
                onClick = press(onBackspace),
                onLongClick = press(onClear),
                enabled = enabled && input.isNotEmpty(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete digit")
            }
            DigitKey(0, keySize, enabled, press { onDigit(0) })
            PinKey(
                size = keySize,
                onClick = press(onSubmit),
                enabled = enabled && input.length >= 4,
                highlighted = busy,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardTab, "Unlock")
            }
        }
    }
}

@Composable
private fun BiometricButton(visible: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.padding(top = 24.dp).height(40.dp), contentAlignment = Alignment.Center) {
        if (visible)
            FilledTonalButton(onClick = onClick, enabled = enabled) {
                Icon(Icons.Rounded.Fingerprint, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use biometrics")
            }
    }
}

@Composable
private fun PinDots(length: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier.fillMaxWidth().height(DotSize * 2),
        contentAlignment = Alignment.Center,
    ) {
        val slot = minOf(DotSlot, maxWidth / maxOf(length, 1))
        val color = MaterialTheme.colorScheme.onSurface
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(LockViewModel.MAX_PIN_LENGTH) { index ->
                AnimatedVisibility(
                    visible = index < length,
                    enter =
                        expandHorizontally(
                            spring(stiffness = Spring.StiffnessMedium),
                            Alignment.CenterHorizontally,
                            clip = false,
                        ),
                    exit =
                        shrinkHorizontally(
                            spring(stiffness = Spring.StiffnessMedium),
                            Alignment.CenterHorizontally,
                            clip = false,
                        ) + scaleOut() + fadeOut(),
                ) {
                    PinDot(color, Modifier.size(slot, DotSize * 2), minOf(DotSize, slot / 2))
                }
            }
        }
    }
}

/** A PIN dot that pops in as a random expressive shape and settles into a circle. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PinDot(color: Color, modifier: Modifier, dotSize: Dp) {
    val morph = remember { Morph(DotEntryShapes.random(), MaterialShapes.Circle) }
    val progress = remember { Animatable(0f) }
    val scale = remember { Animatable(0.4f) }
    val path = remember { Path() }
    LaunchedEffect(Unit) {
        scale.animateTo(1.8f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
        launch { scale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
        progress.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
    }
    Spacer(
        modifier.drawBehind {
            val diameter = dotSize.toPx() * scale.value
            path.rewind()
            morph.toPath(progress.value, path)
            translate(center.x - diameter / 2, center.y - diameter / 2) {
                scale(diameter, diameter, Offset.Zero) { drawPath(path, color) }
            }
        }
    )
}

@Composable
private fun DigitKey(digit: Int, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    PinKey(
        size = size,
        onClick = onClick,
        enabled = enabled,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            digit.toString(),
            fontSize = (size.value * 0.4f).sp,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

/** Keypad key that morphs from a circle into a tinted rounded square while pressed. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PinKey(
    size: Dp,
    onClick: () -> Unit,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    onLongClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        var pressedAt = 0L
        interactionSource.interactions.collectLatest {
            when (it) {
                is PressInteraction.Press -> {
                    pressedAt = SystemClock.uptimeMillis()
                    pressed = true
                }

                is PressInteraction.Release,
                is PressInteraction.Cancel -> {
                    // Keep quick taps visible long enough for the morph to read.
                    delay(MinKeyHighlightMillis - (SystemClock.uptimeMillis() - pressedAt))
                    pressed = false
                }
            }
        }
    }
    val active = pressed || highlighted
    val corner by
    animateDpAsState(
        if (active) size * 0.3f else size / 2,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "Key corner",
    )
    val container by
    animateColorAsState(
        if (active) MaterialTheme.colorScheme.primaryContainer else containerColor,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "Key container",
    )
    val contentTint by
    animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimaryContainer else contentColor,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "Key content",
    )
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(container)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentTint, content = content)
    }
}
