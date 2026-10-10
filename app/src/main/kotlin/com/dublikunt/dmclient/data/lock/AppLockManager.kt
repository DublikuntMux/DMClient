package com.dublikunt.dmclient.data.lock

import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dublikunt.dmclient.data.settings.SettingsRepository
import com.dublikunt.dmclient.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LockState {
    data object Loading : LockState
    data class Locked(val failedAttempts: Int = 0, val cooldownUntil: Long? = null) : LockState
    data object Unlocked : LockState
}

@Singleton
class AppLockManager @Inject constructor(
    private val store: DataStore<Preferences>,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private val monitor = Any()
    private val mutableState = MutableStateFlow<LockState>(LockState.Loading)
    private val credential = MutableStateFlow<PinCredential?>(null)
    private val attempts = PinAttempts(SystemClock::elapsedRealtime)
    private var initialized = false
    private var backgroundAt: Long? = null

    /** Loading until storage is read; locked state includes elapsed-realtime cooldown deadline. */
    val state: StateFlow<LockState> = mutableState.asStateFlow()
    /** Emits PIN presence after storage initialization, without exposing any credential. */
    val isPinSet: Flow<Boolean> = state.filter { it != LockState.Loading }.map { credential.value != null }.distinctUntilChanged()

    init {
        scope.launch(Dispatchers.IO) {
            store.edit { prefs ->
                prefs[legacyPin]?.takeIf { it.isNotEmpty() }?.let { pin ->
                    if (prefs[pinHash] == null) {
                        val hashed = PinHasher.hash(pin)
                        prefs[pinHash] = hashed.hash
                        prefs[pinSalt] = hashed.salt
                    }
                }
                prefs.remove(legacyPin)
            }
            store.data.collect { prefs ->
                val saved = prefs[pinHash]?.let { hash -> PinCredential(hash, prefs[pinSalt].orEmpty()) }
                synchronized(monitor) {
                    if (!initialized || credential.value != saved) {
                        credential.value = saved
                        attempts.reset()
                        mutableState.value = if (saved == null) LockState.Unlocked else LockState.Locked()
                        initialized = true
                    }
                }
            }
        }
    }

    /** Checks a PIN, enforces five-attempt lockout, and unlocks only on success. */
    fun verify(pin: String): Boolean = synchronized(monitor) {
        if (!initialized || !attempts.canAttempt()) return false
        val saved = credential.value ?: return false
        if (PinHasher.verify(pin, saved)) {
            attempts.reset()
            mutableState.value = LockState.Unlocked
            true
        } else {
            attempts.failed()
            mutableState.value = LockState.Locked(attempts.failedAttempts, attempts.cooldownUntil)
            attempts.cooldownUntil?.let { deadline ->
                scope.launch {
                    delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                    synchronized(monitor) {
                        if (mutableState.value is LockState.Locked && attempts.canAttempt()) {
                            mutableState.value = LockState.Locked()
                        }
                    }
                }
            }
            false
        }
    }

    /** Unlocks after the UI has received successful biometric authentication. */
    fun unlockWithBiometric() = synchronized(monitor) {
        if (initialized) { attempts.reset(); mutableState.value = LockState.Unlocked }
    }

    /** Starts measuring time away from the foreground. */
    fun onAppBackground() = synchronized(monitor) {
        backgroundAt = SystemClock.elapsedRealtime()
        if (initialized && credential.value != null && settings.settings.value.lockTimeout.milliseconds == 0L) {
            mutableState.value = LockState.Locked(attempts.failedAttempts, attempts.cooldownUntil)
        }
    }

    /** Locks when the configured background timeout expired; OnRestart retains this session. */
    fun onAppForeground() = synchronized(monitor) {
        val since = backgroundAt
        val timeout = settings.settings.value.lockTimeout.milliseconds
        if (initialized && credential.value != null && since != null && timeout != null && SystemClock.elapsedRealtime() - since >= timeout) {
            attempts.canAttempt()
            mutableState.value = LockState.Locked(attempts.failedAttempts, attempts.cooldownUntil)
        }
        backgroundAt = null
    }

    /** Persists a salted PBKDF2 hash for a 4–15 digit PIN; the current session stays unlocked. */
    suspend fun setPin(pin: String) = withContext(Dispatchers.IO) {
        require(pin.length in 4..15 && pin.all { it in '0'..'9' }) { "PIN must contain 4–15 digits" }
        val hashed = PinHasher.hash(pin)
        store.edit { it[pinHash] = hashed.hash; it[pinSalt] = hashed.salt; it.remove(legacyPin) }
        synchronized(monitor) {
            credential.value = hashed
            attempts.reset()
            initialized = true
            mutableState.value = LockState.Unlocked
        }
    }

    /** Removes stored credentials and unlocks the app. */
    suspend fun removePin() {
        store.edit { it.remove(pinHash); it.remove(pinSalt); it.remove(legacyPin) }
        synchronized(monitor) {
            credential.value = null
            attempts.reset()
            initialized = true
            mutableState.value = LockState.Unlocked
        }
    }

    override fun onStart(owner: LifecycleOwner) { onAppForeground() }
    override fun onStop(owner: LifecycleOwner) { onAppBackground() }

    private companion object {
        val legacyPin = stringPreferencesKey("pin_code")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
    }
}
