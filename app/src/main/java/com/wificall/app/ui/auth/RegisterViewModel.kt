package com.wificall.app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.wificall.app.data.model.User
import com.wificall.app.data.repository.AuthRepository
import com.wificall.app.data.repository.UserRepository
import kotlinx.coroutines.launch

/**
 * RegisterViewModel.kt
 * Orchestrates the registration flow:
 *  1. Validate inputs
 *  2. Create Firebase Auth account
 *  3. Generate a unique 4-digit ID
 *  4. Write the user profile to Firestore
 *  5. Emit success so the Activity can navigate away
 */
class RegisterViewModel : ViewModel() {

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    // ── UI State ──────────────────────────────────────────────────────────────

    private val _registerResult = MutableLiveData<Result<FirebaseUser>>()
    val registerResult: LiveData<Result<FirebaseUser>> = _registerResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /**
     * Emits status messages during registration so the user sees progress:
     * "Creating account…", "Generating your ID…", "Setting up profile…"
     */
    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Full registration pipeline: Auth → unique ID → Firestore profile.
     * All steps run inside a single viewModelScope coroutine so errors at
     * any step surface cleanly through [_registerResult].
     */
    fun register(name: String, email: String, password: String, confirmPassword: String) {
        if (!validateInputs(name, email, password, confirmPassword)) return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Step 1: Create Firebase Auth account
                _statusMessage.value = "Creating account…"
                val authResult = authRepository.register(email.trim(), password)
                val firebaseUser = authResult.getOrElse {
                    _registerResult.value = Result.failure(it)
                    _isLoading.value = false
                    return@launch
                }

                // Step 2: Generate a unique 4-digit ID (retries on collision)
                _statusMessage.value = "Generating your unique ID…"
                val fourDigitId = userRepository.generateUniqueFourDigitId()

                // Step 3: Build and save the user profile in Firestore
                _statusMessage.value = "Setting up your profile…"
                val user = User(
                    uid = firebaseUser.uid,
                    email = email.trim(),
                    displayName = name.trim(),
                    fourDigitId = fourDigitId,
                    photoUrl = "",
                    fcmToken = "",
                    isOnline = true,
                    createdAt = System.currentTimeMillis()
                )
                userRepository.createUserProfile(user)

                // Step 4: Refresh FCM token now that the profile doc exists
                userRepository.refreshAndSaveFcmToken(firebaseUser.uid)

                _registerResult.value = Result.success(firebaseUser)
            } catch (e: Exception) {
                _registerResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateInputs(
        name: String, email: String,
        password: String, confirmPassword: String
    ): Boolean {
        return when {
            name.isBlank() -> {
                _registerResult.value = Result.failure(Exception("Name cannot be empty"))
                false
            }
            email.isBlank() -> {
                _registerResult.value = Result.failure(Exception("Email cannot be empty"))
                false
            }
            password.length < 6 -> {
                _registerResult.value = Result.failure(
                    Exception("Password must be at least 6 characters"))
                false
            }
            password != confirmPassword -> {
                _registerResult.value = Result.failure(Exception("Passwords do not match"))
                false
            }
            else -> true
        }
    }
}
