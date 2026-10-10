package com.dublikunt.dmclient.data.lock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dublikunt.dmclient.data.settings.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockManagerTest {
    private class Store(initial: Preferences) : DataStore<Preferences> {
        val ready = CompletableDeferred<Unit>()
        val values = MutableStateFlow(initial)
        private val lock = Mutex()
        override val data: Flow<Preferences> = flow { ready.await(); emitAll(values) }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            ready.await()
            return lock.withLock { transform(values.value).also { values.value = it } }
        }
    }

    @Test
    fun `initial read cannot unlock and migrates legacy PIN in place`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val legacy = stringPreferencesKey("pin_code")
            val store = Store(mutablePreferencesOf(legacy to "1234"))
            val manager = AppLockManager(store, SettingsRepository(store, scope), scope)
            assertEquals(LockState.Loading, manager.state.value)
            assertFalse(manager.verify("1234"))
            manager.unlockWithBiometric()
            assertEquals(LockState.Loading, manager.state.value)
            val presence = async { manager.isPinSet.first() }
            assertFalse(presence.isCompleted)
            store.ready.complete(Unit)
            withTimeout(10_000) { manager.state.first { it is LockState.Locked } }
            assertTrue(withTimeout(10_000) { presence.await() })
            assertNull(store.values.value[legacy])
            assertNotNull(store.values.value[stringPreferencesKey("pin_hash")])
            assertNotNull(store.values.value[stringPreferencesKey("pin_salt")])
            assertTrue(manager.verify("1234"))
            assertEquals(LockState.Unlocked, manager.state.value)
            manager.removePin()
            assertFalse(manager.isPinSet.first())
            assertNull(store.values.value[stringPreferencesKey("pin_hash")])
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `no PIN unlocks only after storage read`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = Store(mutablePreferencesOf())
            val manager = AppLockManager(store, SettingsRepository(store, scope), scope)
            assertEquals(LockState.Loading, manager.state.value)
            store.ready.complete(Unit)
            withTimeout(10_000) { manager.state.first { it == LockState.Unlocked } }
            assertFalse(manager.isPinSet.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `failed attempts survive restart and successful PIN clears them`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val failures = intPreferencesKey("pin_failed_attempts")
            val store = Store(mutablePreferencesOf(stringPreferencesKey("pin_code") to "1234"))
            val manager = AppLockManager(store, SettingsRepository(store, scope), scope)
            store.ready.complete(Unit)
            withTimeout(10_000) { manager.state.first { it is LockState.Locked } }
            repeat(4) { assertFalse(manager.verify("4321")) }
            assertEquals(4, store.values.value[failures])
            scope.cancel()

            val restoredStore = Store(store.values.value)
            val restored = AppLockManager(
                restoredStore,
                SettingsRepository(restoredStore, restartedScope),
                restartedScope
            )
            restoredStore.ready.complete(Unit)
            val state = withTimeout(10_000) { restored.state.first { it is LockState.Locked } }
            assertEquals(4, (state as LockState.Locked).failedAttempts)
            assertTrue(restored.verify("1234"))
            assertNull(restoredStore.values.value[failures])
        } finally {
            scope.cancel(); restartedScope.cancel()
        }
    }
}
