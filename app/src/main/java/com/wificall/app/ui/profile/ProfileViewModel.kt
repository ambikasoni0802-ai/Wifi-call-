package com.wificall.app.ui.profile

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.storage.FirebaseStorage
import com.wificall.app.data.model.User
import com.wificall.app.data.repository.UserRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ProfileViewModel.kt
 * Drives the profile editing screen:
 *  - Loads the current user profile
 *  - Handles display name updates
 *  - Uploads a new profile photo to Firebase Storage and saves the URL
 */
class ProfileViewModel : ViewModel() {

    private val userRepository = UserRepository()
    private val storage = FirebaseStorage.getInstance()

    // ── Current user ──────────────────────────────────────────────────────────

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    // ── Loading / status ──────────────────────────────────────────────────────

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _updateResult = MutableLiveData<Result<Unit>>()
    val updateResult: LiveData<Result<Unit>> = _updateResult

    // ── Upload progress (0–100) ───────────────────────────────────────────────

    private val _uploadProgress = MutableLiveData<Int>()
    val uploadProgress: LiveData<Int> = _uploadProgress

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /** Loads the user profile from Firestore. */
    fun loadUser(uid: String) {
        viewModelScope.launch {
            val result = userRepository.getUserById(uid)
            _user.value = result.getOrNull()
        }
    }

    /**
     * Updates the display name for [uid].
     * Shows a Snackbar-compatible result in [updateResult].
     */
    fun updateDisplayName(uid: String, newName: String) {
        if (newName.isBlank()) {
            _updateResult.value = Result.failure(Exception("Name cannot be empty"))
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            val result = userRepository.updateDisplayName(uid, newName.trim())
            _updateResult.value = result
            if (result.isSuccess) {
                // Refresh the local copy
                val updated = _user.value?.copy(displayName = newName.trim())
                _user.value = updated
            }
            _isLoading.value = false
        }
    }

    /**
     * Uploads [imageUri] to Firebase Storage under users/{uid}/profile.jpg,
     * then saves the download URL to Firestore.
     *
     * Upload progress is emitted via [uploadProgress] so the UI can show
     * a progress bar.
     */
    fun uploadProfilePhoto(uid: String, imageUri: Uri) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val storageRef = storage.reference
                    .child("users/$uid/profile.jpg")

                // Upload and track progress
                val uploadTask = storageRef.putFile(imageUri)
                uploadTask.addOnProgressListener { snapshot ->
                    val pct = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount).toInt()
                    _uploadProgress.postValue(pct)
                }
                uploadTask.await()

                // Retrieve the public download URL
                val downloadUrl = storageRef.downloadUrl.await().toString()

                // Persist the URL in Firestore
                userRepository.updatePhotoUrl(uid, downloadUrl)
                    .onSuccess {
                        val updated = _user.value?.copy(photoUrl = downloadUrl)
                        _user.postValue(updated)
                        _updateResult.postValue(Result.success(Unit))
                    }
                    .onFailure { _updateResult.postValue(Result.failure(it)) }

            } catch (e: Exception) {
                _updateResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
