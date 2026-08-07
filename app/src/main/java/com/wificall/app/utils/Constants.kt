package com.wificall.app.utils

/**
 * Constants.kt
 * Central place for all magic strings and numbers used across the app.
 * Using an object (singleton) so values are accessible without instantiation.
 */
object Constants {

    // ── Firestore collection names ────────────────────────────────────────────
    /** Top-level collection that stores every user's profile document. */
    const val COLLECTION_USERS = "users"

    /**
     * Collection that maps every active 4-digit ID → userId.
     * Used to check uniqueness and look up users by their short ID.
     */
    const val COLLECTION_DIGIT_IDS = "digitIds"

    /** Sub-collection inside each user document for call history records. */
    const val COLLECTION_CALL_HISTORY = "callHistory"

    /**
     * Top-level collection used as a WebRTC signaling channel.
     * Each document represents an active or pending call session.
     */
    const val COLLECTION_CALLS = "calls"

    // ── Firestore field names (User document) ────────────────────────────────
    const val FIELD_UID = "uid"
    const val FIELD_EMAIL = "email"
    const val FIELD_DISPLAY_NAME = "displayName"
    const val FIELD_FOUR_DIGIT_ID = "fourDigitId"
    const val FIELD_PHOTO_URL = "photoUrl"
    const val FIELD_FCM_TOKEN = "fcmToken"
    const val FIELD_IS_ONLINE = "isOnline"
    const val FIELD_CREATED_AT = "createdAt"

    // ── Firestore field names (Call document) ────────────────────────────────
    const val FIELD_CALLER_ID = "callerId"
    const val FIELD_CALLER_NAME = "callerName"
    const val FIELD_CALLEE_ID = "calleeId"
    const val FIELD_CALL_STATUS = "callStatus"
    const val FIELD_OFFER_SDP = "offerSdp"
    const val FIELD_ANSWER_SDP = "answerSdp"
    const val FIELD_TIMESTAMP = "timestamp"

    // ── Call status values ────────────────────────────────────────────────────
    const val CALL_STATUS_CALLING = "calling"   // Caller is ringing the callee
    const val CALL_STATUS_ACCEPTED = "accepted" // Callee accepted
    const val CALL_STATUS_REJECTED = "rejected" // Callee rejected
    const val CALL_STATUS_ENDED = "ended"       // Either party hung up
    const val CALL_STATUS_MISSED = "missed"     // Callee didn't answer in time

    // ── Call history field names ──────────────────────────────────────────────
    const val FIELD_PEER_NAME = "peerName"
    const val FIELD_PEER_FOUR_DIGIT_ID = "peerFourDigitId"
    const val FIELD_CALL_TYPE = "callType"      // "outgoing" | "incoming" | "missed"
    const val FIELD_DURATION_SECONDS = "durationSeconds"
    const val FIELD_CALL_DATE = "callDate"

    // ── FCM payload keys (sent from MyFirebaseMessagingService) ──────────────
    const val FCM_KEY_TYPE = "type"
    const val FCM_TYPE_INCOMING_CALL = "incoming_call"
    const val FCM_KEY_CALL_ID = "callId"
    const val FCM_KEY_CALLER_NAME = "callerName"
    const val FCM_KEY_CALLER_DIGIT_ID = "callerDigitId"

    // ── Intent extra keys ─────────────────────────────────────────────────────
    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_CALLER_NAME = "extra_caller_name"
    const val EXTRA_CALLER_DIGIT_ID = "extra_caller_digit_id"
    const val EXTRA_IS_INCOMING = "extra_is_incoming"
    const val EXTRA_PEER_DIGIT_ID = "extra_peer_digit_id"
    const val EXTRA_PEER_NAME = "extra_peer_name"

    // ── Notification channel IDs ──────────────────────────────────────────────
    const val CHANNEL_INCOMING_CALLS = "incoming_calls"
    const val CHANNEL_ONGOING_CALL = "ongoing_call"

    // ── Notification IDs ─────────────────────────────────────────────────────
    const val NOTIFICATION_ID_INCOMING_CALL = 1001
    const val NOTIFICATION_ID_ONGOING_CALL = 1002

    // ── ID generation ─────────────────────────────────────────────────────────
    /** Inclusive range for the 4-digit short ID (1000–9999). */
    const val FOUR_DIGIT_ID_MIN = 1000
    const val FOUR_DIGIT_ID_MAX = 9999

    /** How many random IDs to show as suggestions on the home screen. */
    const val SUGGESTION_COUNT = 5

    // ── WebRTC / signaling ────────────────────────────────────────────────────
    /** ICE candidate sub-collection path inside a call document. */
    const val COLLECTION_ICE_CANDIDATES = "iceCandidates"

    /** Public STUN server (Google) – used to discover public IP/port. */
    const val STUN_SERVER = "stun:stun.l.google.com:19302"

    // ── Timeouts ──────────────────────────────────────────────────────────────
    /** Seconds before an unanswered outgoing call is marked missed. */
    const val CALL_RING_TIMEOUT_SECONDS = 30L
}
