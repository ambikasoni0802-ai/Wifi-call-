package com.wificall.app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.wificall.app.data.repository.AuthRepository
import com.wificall.app.data.repository.UserRepository
import kotlinx.coroutines.launch

/**
 * LoginViewModel.kt
 * Holds UI state for [LoginActivity] and orchestrates the login flow.
 *
 * MVVM role: ViewModel is the middle layer between the View (Activity)
 * and the Model (Repositories). It survives orientation changes.
 */
class LoginViewModel : ViewModel() {

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    // ── UI State LiveData ─────────────────────────────────────────────────────

    /** Emits the signed-in FirebaseUser on successful login. */
    private val _loginResult = MutableLiveData<Result<FirebaseUser>>()
    val loginResult: LiveData<Result<FirebaseUser>> = _loginResult

    /** True while a login network request is in flight. */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** Emits a one-shot event after password-reset email is sent. */
    private val _resetResult = MutableLiveData<Result<Unit>>()
    val resetResult: LiveData<Result<Unit>> = _resetResult

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Validates inputs and calls [AuthRepository.login].
     * On success, refreshes the FCM token in Firestore so this device
     * can receive incoming call notifications.
     */
    fun login(email: String, password: String) {
        if (!validateInputs(email, password)) return

        _isLoading.value = true
        viewModelScope.launch {
            val result = authRepository.login(email.trim(), password)
            // If login succeeded, refresh the FCM token for push notifications
            result.getOrNull()?.let { firebaseUser ->
                userRepository.refreshAndSaveFcmToken(firebaseUser.uid)
                userRepository.setOnlineStatus(firebaseUser.uid, true)
            }
            _loginResult.value = result
            _isLoading.value = false
        }
    }

    /**
     * Sends a password reset email to [email].
     */
    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _resetResult.value = Result.failure(Exception("Please enter your email address"))
            return
        }
        viewModelScope.launch {
            _resetResult.value = authRepository.sendPasswordReset(email.trim())
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Returns true if [email] and [password] pass basic format checks.
     * Sets [_loginResult] with a failure so the UI can show the error message.
     */
    private fun validateInputs(email: String, password: String): Boolean {
        return when {
            email.isBlank() -> {
                _loginResult.value = Result.failure(Exception("Email cannot be empty"))
                false
            }
            password.isBlank() -> {
                _loginResult.value = Result.failure(Exception("Password cannot be empty"))
                false
            }
            password.length < 6 -> {
                _loginResult.value = Result.failure(Exception("Password must be at least 6 characters"))
                false
            }
            else -> true
        }
    }
}
