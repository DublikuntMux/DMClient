package com.dublikunt.dmclient.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dublikunt.dmclient.data.lock.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(private val manager: AppLockManager) : ViewModel() {
    private val mutableInput = MutableStateFlow("")
    val input = mutableInput.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()

    fun digit(value: Int) {
        if (!mutableBusy.value && mutableInput.value.length < 15)
            mutableInput.value += value.toString()
    }

    fun backspace() {
        if (!mutableBusy.value) mutableInput.value = mutableInput.value.dropLast(1)
    }

    fun clear() {
        if (!mutableBusy.value) mutableInput.value = ""
    }

    fun submit() {
        val pin = mutableInput.value
        if (mutableBusy.value || pin.length < 4) return
        mutableBusy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                manager.verify(pin)
            } finally {
                mutableInput.value = ""
                mutableBusy.value = false
            }
        }
    }

    fun biometricSucceeded() {
        mutableInput.value = ""
        viewModelScope.launch(Dispatchers.IO) { manager.unlockWithBiometric() }
    }
}
