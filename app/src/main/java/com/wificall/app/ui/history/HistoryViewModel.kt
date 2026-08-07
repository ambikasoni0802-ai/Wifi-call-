package com.wificall.app.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wificall.app.data.model.CallHistory
import com.wificall.app.data.repository.CallRepository
import kotlinx.coroutines.launch

/**
 * HistoryViewModel.kt
 * Loads and exposes the user's call history list.
 */
class HistoryViewModel : ViewModel() {

    private val callRepository = CallRepository()

    private val _history = MutableLiveData<List<CallHistory>>()
    val history: LiveData<List<CallHistory>> = _history

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * Fetches call history for [uid] ordered newest-first.
     */
    fun loadHistory(uid: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = callRepository.getCallHistory(uid)
            result
                .onSuccess { list ->
                    _history.value = list
                    _error.value = null
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Failed to load history"
                }
            _isLoading.value = false
        }
    }
}
