package com.wificall.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.wificall.app.data.model.CallHistory
import com.wificall.app.utils.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * CallRepository.kt
 * Manages WebRTC signaling documents in Firestore and stores call history.
 *
 * Signaling flow overview:
 *  1. Caller creates /calls/{callId} with offerSdp + status = "calling"
 *  2. Callee watches for their calleeId, reads offerSdp, writes answerSdp
 *     and flips status to "accepted"
 *  3. Both sides exchange ICE candidates under /calls/{callId}/iceCandidates
 *  4. On hang-up, status is set to "ended"
 *
 * Firestore structure:
 *  /calls/{callId}
 *      callerId, callerName, calleeId, callStatus, offerSdp, answerSdp, timestamp
 *      /iceCandidates/{docId}
 *          sdp, sdpMid, sdpMLineIndex, senderId
 */
class CallRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val callsCollection = firestore.collection(Constants.COLLECTION_CALLS)
    private val usersCollection = firestore.collection(Constants.COLLECTION_USERS)

    // ─────────────────────────────────────────────────────────────────────────
    // OUTGOING CALL – SIGNALING
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new call document as the caller side.
     * The SDP offer is created by WebRTCService and passed in here.
     *
     * @return The auto-generated Firestore call document ID.
     */
    suspend fun createCallDocument(
        callerUid: String,
        callerName: String,
        calleeDigitId: String,
        offerSdp: String
    ): String {
        val callRef = callsCollection.document() // Auto-generate ID
        val data = hashMapOf(
            Constants.FIELD_CALLER_ID to callerUid,
            Constants.FIELD_CALLER_NAME to callerName,
            Constants.FIELD_CALLEE_ID to calleeDigitId,
            Constants.FIELD_CALL_STATUS to Constants.CALL_STATUS_CALLING,
            Constants.FIELD_OFFER_SDP to offerSdp,
            Constants.FIELD_ANSWER_SDP to "",
            Constants.FIELD_TIMESTAMP to System.currentTimeMillis()
        )
        callRef.set(data).await()
        return callRef.id
    }

    /**
     * Streams updates to a call document identified by [callId].
     * Used by the caller to watch for the callee's answer SDP and status changes.
     */
    fun observeCallDocument(callId: String): Flow<Map<String, Any?>> = callbackFlow {
        val listener: ListenerRegistration = callsCollection.document(callId)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.data?.let { trySend(it) }
            }
        awaitClose { listener.remove() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INCOMING CALL – SIGNALING
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Writes the callee's SDP answer to the call document and changes status
     * to "accepted", letting the caller know the call was picked up.
     */
    suspend fun acceptCall(callId: String, answerSdp: String) {
        callsCollection.document(callId).update(
            mapOf(
                Constants.FIELD_ANSWER_SDP to answerSdp,
                Constants.FIELD_CALL_STATUS to Constants.CALL_STATUS_ACCEPTED
            )
        ).await()
    }

    /**
     * Sets call status to "rejected" so the caller's UI updates immediately.
     */
    suspend fun rejectCall(callId: String) {
        callsCollection.document(callId)
            .update(Constants.FIELD_CALL_STATUS, Constants.CALL_STATUS_REJECTED)
            .await()
    }

    /**
     * Sets call status to "ended" (either party can call this on hang-up).
     */
    suspend fun endCall(callId: String) {
        try {
            callsCollection.document(callId)
                .update(Constants.FIELD_CALL_STATUS, Constants.CALL_STATUS_ENDED)
                .await()
        } catch (_: Exception) { /* Document may already be gone */ }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ICE CANDIDATES – WebRTC peer discovery
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Uploads one ICE candidate to the signaling sub-collection.
     * The remote peer will read this to establish the P2P path.
     */
    suspend fun sendIceCandidate(
        callId: String,
        senderId: String,
        sdp: String,
        sdpMid: String?,
        sdpMLineIndex: Int
    ) {
        try {
            callsCollection.document(callId)
                .collection(Constants.COLLECTION_ICE_CANDIDATES)
                .add(
                    hashMapOf(
                        "sdp" to sdp,
                        "sdpMid" to (sdpMid ?: ""),
                        "sdpMLineIndex" to sdpMLineIndex,
                        "senderId" to senderId
                    )
                ).await()
        } catch (_: Exception) { /* Non-fatal; ICE will retry via other candidates */ }
    }

    /**
     * Streams ICE candidates sent by [remoteSenderId] so the local peer
     * can process them via WebRTC's [PeerConnection.addIceCandidate].
     */
    fun observeIceCandidates(callId: String, remoteSenderId: String):
            Flow<Map<String, Any>> = callbackFlow {
        val listener: ListenerRegistration = callsCollection.document(callId)
            .collection(Constants.COLLECTION_ICE_CANDIDATES)
            .whereEqualTo("senderId", remoteSenderId)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    @Suppress("UNCHECKED_CAST")
                    trySend(change.document.data as Map<String, Any>)
                }
            }
        awaitClose { listener.remove() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CALL HISTORY
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Appends a [CallHistory] record to the user's sub-collection.
     * Called when a call ends (for both caller and callee).
     */
    suspend fun saveCallHistory(uid: String, history: CallHistory) {
        try {
            usersCollection.document(uid)
                .collection(Constants.COLLECTION_CALL_HISTORY)
                .document(history.callId)
                .set(history)
                .await()
        } catch (_: Exception) { /* History is best-effort */ }
    }

    /**
     * Fetches the most recent call history entries for [uid], ordered newest first.
     */
    suspend fun getCallHistory(uid: String): Result<List<CallHistory>> {
        return try {
            val snapshot = usersCollection.document(uid)
                .collection(Constants.COLLECTION_CALL_HISTORY)
                .orderBy(Constants.FIELD_CALL_DATE,
                    com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .await()
            val history = snapshot.toObjects(CallHistory::class.java)
            Result.success(history)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
