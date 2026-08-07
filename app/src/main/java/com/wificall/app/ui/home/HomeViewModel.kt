package com.wificall.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.wificall.app.data.model.User
import com.wificall.app.data.repository.UserRepository
import com.wificall.app.utils.NetworkUtils
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * HomeViewModel.kt
 * Drives the HomeFragment UI state:
 *  - Current user's profile (name, 4-digit ID, photo)
 *  - 5 random suggestion users
 *  - Network connectivity status
 *
 * Extends [AndroidViewModel] so we can safely access [Application] context
 * to monitor network connectivity without leaking an Activity context.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepository = UserRepository()

    // ── Current user ──────────────────────────────────────────────────────────

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    // ── Suggestions ───────────────────────────────────────────────────────────

    /** Loading flag for the suggestions refresh button. */
    private val _isLoadingSuggestions = MutableLiveData(false)
    val isLoadingSuggestions: LiveData<Boolean> = _isLoadingSuggestions

    private val _suggestions = MutableLiveData<List<User>>()
    val suggestions: LiveData<List<User>> = _suggestions

    // ── Peer lookup ───────────────────────────────────────────────────────────

    /** Result of looking up a manually-entered 4-digit ID. */
    private val _peerLookupResult = MutableLiveData<Result<User?>>()
    val peerLookupResult: LiveData<Result<User?>> = _peerLookupResult

    // ── Network ───────────────────────────────────────────────────────────────

    private val _isInternetAvailable = MutableLiveData(true)
    val isInternetAvailable: LiveData<Boolean> = _isInternetAvailable

    // ─────────────────────────────────────────────────────────────────────────
    // INIT – Start monitoring network and load data
    // ─────────────────────────────────────────────────────────────────────────

    init {
        // Observe network connectivity using a Flow so any change is reflected live
        NetworkUtils.observeNetworkState(application)
            .onEach { connected -> _isInternetAvailable.postValue(connected) }
            .launchIn(viewModelScope)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads the current user's profile from Firestore.
     * Called when the fragment first appears (and after profile edits).
     */
    fun loadCurrentUser(uid: String) {
        viewModelScope.launch {
            val result = userRepository.getUserById(uid)
            _currentUser.value = result.getOrNull()
        }
    }

    /**
     * Refreshes the 5 suggestion users shown on the home screen.
     * Only shows valid users (those with a real account in Firestore).
     */
    fun loadSuggestions(excludeId: String) {
        _isLoadingSuggestions.value = true
        viewModelScope.launch {
            val suggestions = userRepository.getValidSuggestions(excludeId)
            _suggestions.value = suggestions
            _isLoadingSuggestions.value = false
        }
    }

    /**
     * Looks up a user by their manually-entered 4-digit ID.
     * The result drives navigation to [CallActivity] (valid user)
     * or shows an error snackbar (unknown ID).
     */
    fun lookupPeer(fourDigitId: String) {
        if (fourDigitId.length != 4 || !fourDigitId.all { it.isDigit() }) {
            _peerLookupResult.value = Result.failure(Exception("Please enter a valid 4-digit ID"))
            return
        }
        viewModelScope.launch {
            val result = userRepository.getUserByFourDigitId(fourDigitId)
            _peerLookupResult.value = result
        }
    }
}
