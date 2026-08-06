package com.wificall.app.data.model

/**
 * CallHistory.kt
 * Represents one entry in the user's call history sub-collection in Firestore.
 * Stored under: users/{uid}/callHistory/{callId}
 */
data class CallHistory(
    /** Firestore document ID (same as the call document ID in /calls). */
    val callId: String = "",

    /** Display name of the other party on the call. */
    val peerName: String = "",

    /** 4-digit short ID of the other party. */
    val peerFourDigitId: String = "",

    /**
     * Direction of the call from this user's perspective:
     *  - "outgoing" – this user placed the call
     *  - "incoming" – this user received the call
     *  - "missed"   – incoming call that was never answered
     */
    val callType: String = "",

    /** How long the call lasted, in seconds. 0 for missed calls. */
    val durationSeconds: Long = 0L,

    /** Epoch-millis timestamp of when the call started (or was attempted). */
    val callDate: Long = 0L
)
