package com.dublikunt.dmclient.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dublikunt.dmclient.data.repository.LibraryRepository
import com.dublikunt.dmclient.data.repository.ReadingStatus
import com.dublikunt.dmclient.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatusesViewModel @Inject constructor(private val repository: LibraryRepository) :
    ViewModel() {
    val statuses =
        repository.statuses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    fun create(name: String, color: Int) = action { repository.createStatus(name, color) }

    fun update(status: ReadingStatus) = action { repository.updateStatus(status) }

    fun delete(id: Int) = action {
        repository.deleteStatus(id)
        messages.send("Status deleted")
    }

    fun move(id: Int, offset: Int) {
        val ids = statuses.value?.map { it.id }?.toMutableList() ?: return
        val index = ids.indexOf(id)
        val target = index + offset
        if (index < 0 || target !in ids.indices) return
        ids[index] = ids[target]
        ids[target] = id
        action { repository.reorderStatuses(ids) }
    }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                messages.send(error.userMessage())
            }
        }
    }
}
