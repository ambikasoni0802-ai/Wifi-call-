package com.wificall.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.wificall.app.data.model.User
import com.wificall.app.utils.Constants
import kotlinx.coroutines.tasks.await

/**
 * UserRepository.kt
 * Manages all Firestore operations for user profiles and 4-digit ID assignment.
 *
 * Firestore structure:
 *  /users/{uid}                      → User document
 *  /digitIds/{fourDigitId}           → { "uid": "..." } (uniqueness index)
 */
class UserRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection(Constants.COLLECTION_USERS)
    private val digitIdsCollection = firestore.collection(Constants.COLLECTION_DIGIT_IDS)

    // ─────────────────────────────────────────────────────────────────────────
    // 4-DIGIT ID GENERATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a random 4-digit ID and ensures it's unique across all users.
     * Uses a Firestore transaction to atomically check and reserve the ID so
     * two simultaneous registrations can never receive the same ID.
     *
     * @return A unique 4-digit string (e.g. "4821").
     */
    suspend fun generateUniqueFourDigitId(): String {
        var candidateId: String
        var attempts = 0
        val maxAttempts = 50  // Safety valve – 9000 possible IDs, so collisions are rare

        do {
            if (attempts++ > maxAttempts) {
                throw Exception("Could not generate a unique ID after $maxAttempts attempts")
            }
            // Pick a random number in [1000, 9999]
            val raw = (Constants.FOUR_DIGIT_ID_MIN..Constants.FOUR_DIGIT_ID_MAX).random()
            candidateId = raw.toString()
        } while (!tryReserveId(candidateId))

        return candidateId
    }

    /**
     * Atomically checks whether [id] is available and, if so, reserves it
     * with a placeholder value (uid = "pending"). The caller [createUserProfile]
     * must update this placeholder with the real uid after the profile is written.
     *
     * @return true if the ID was successfully reserved, false if it was already taken.
     */
    private suspend fun tryReserveId(id: String): Boolean {
        return try {
            val docRef = digitIdsCollection.document(id)
            firestore.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.exists()) {
                    // Already taken – signal failure
                    throw Exception("ID_TAKEN")
                }
                // Reserve the slot with a placeholder
                tx.set(docRef, mapOf("uid" to "pending"))
            }.await()
            true
        } catch (e: Exception) {
            // Transaction failed because ID was taken, or a genuine Firestore error
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // USER PROFILE CREATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new user document in Firestore and finalises the digitIds entry.
     * Called once, right after Firebase Auth registration.
     *
     * @param user A fully-populated [User] object (uid, email, displayName, fourDigitId, …).
     */
    suspend fun createUserProfile(user: User) {
        // Write the full user document
        usersCollection.document(user.uid).set(user).await()

        // Update the placeholder in /digitIds to point to the real uid
        digitIdsCollection.document(user.fourDigitId)
            .set(mapOf("uid" to user.uid), SetOptions.merge())
            .await()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // USER PROFILE READS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches the [User] document for [uid] from Firestore.
     * @return [User] on success, or failure with an appropriate error message.
     */
    suspend fun getUserById(uid: String): Result<User> {
        return try {
            val snap = usersCollection.document(uid).get().await()
            val user = snap.toObject(User::class.java)
                ?: return Result.failure(Exception("User not found"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Looks up a user by their 4-digit short ID.
     * First resolves the uid from /digitIds, then fetches the user profile.
     *
     * @return [User] if a matching valid user exists, null if the ID has no user.
     */
    suspend fun getUserByFourDigitId(fourDigitId: String): Result<User?> {
        return try {
            // Step 1: resolve uid from the digitIds index
            val idDoc = digitI
