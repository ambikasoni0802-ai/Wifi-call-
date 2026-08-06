package com.wificall.app.data.model

/**
 * User.kt
 * Data class that mirrors the Firestore "users" document structure.
 * All fields have defaults so Firestore's toObject() can deserialize
 * partial documents without throwing NullPointerException.
 */
data class User(
    /** Firebase Auth UID – also used as the Firestore document ID. */
    val uid: String = "",

    /** Email address used for sign-up / login. */
    val email: String = "",

    /** Human-readable display name (can be changed on profile page). */
    val displayName: String = "",

    /**
     * Unique 4-digit short ID (e.g. "4821") assigned once at account creation.
     * Stored as a String to preserve leading zeros if ever needed.
     */
    val fourDigitId: String = "",

    /** Firebase Storage download URL for the user's profile photo. Empty = no photo. */
    val photoUrl: String = "",

    /** Current FCM registration token used to send incoming call push notifications. */
    val fcmToken: String = "",

    /** True while the user has the app open / is in an active session. */
    val isOnline: Boolean = false,

    /** Epoch-millis timestamp of when the account was created. */
    val createdAt: Long = 0L
)
