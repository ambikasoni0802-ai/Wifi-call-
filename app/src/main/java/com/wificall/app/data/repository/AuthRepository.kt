package com.wificall.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * AuthRepository.kt
 * Handles all Firebase Authentication operations: sign-up, login, logout.
 * Wraps Firebase Tasks into suspend functions so ViewModels can call them
 * inside coroutines and handle results with try/catch.
 *
 * This class is stateless – it only delegates to FirebaseAuth.
 * User profile data (name, 4-digit ID, etc.) is managed by [UserRepository].
 */
class AuthRepository {

    // Singleton FirebaseAuth instance
    private val auth = FirebaseAuth.getInstance()

    /**
     * Returns the currently signed-in Firebase user, or null if no one is logged in.
     * The ViewModel uses this to decide which screen to show on app launch.
     */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * Creates a new Firebase Auth account with [email] and [password].
     *
     * @return [FirebaseUser] on success (never null inside Result.success).
     * @throws Exception with a human-readable message on failure (weak password,
     *         email already in use, etc.).
     */
    suspend fun register(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("Registration failed: user is null")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs in an existing user with [email] and [password].
     *
     * @return [FirebaseUser] on success.
     * @throws Exception if credentials are wrong or the account doesn't exist.
     */
    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("Login failed: user is null")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs the current user out of Firebase Auth.
     * After this call, [currentUser] returns null.
     */
    fun logout() {
        auth.signOut()
    }

    /**
     * Sends a password-reset email to [email].
     * The user will receive a link to choose a new password.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
