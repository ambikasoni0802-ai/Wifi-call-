package com.wificall.app.ui.call

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wificall.app.data.model.CallHistory
import com.wificall.app.data.repository.CallRepository
import com.wificall.app.data.repository.UserRepository
import com.wificall.app.utils.Constants
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * CallViewModel.kt
 * Manages call state for both outgoing (CallActivity) and incoming (IncomingCallActivity) calls.
 *
 * Responsibilities:
 *  - Signal the callee via Firestore (create call doc with SDP offer)
 *  - Watch for callee responses (status changes, SDP answer)
 *  - Relay ICE candidates between peers
 *  - Drive the call timer LiveData
 *  - Save call history when the call ends
 */
class CallViewModel : ViewModel() {

    private val callRepository = CallRepository()
    private val userRepository = UserRepository()

    // ── Call identity ─────────────────────────────────────────────────────────

    /** Firestore document ID for this call session. */
    private val _callId = MutableLiveData<String>()
    val callId: LiveData<String> = _callId

    // ── Call status ───────────────────────────────────────────────────────────

    /**
     * Mirrors the Firestore `callStatus` field:
     * "calling" | "accepted" | "rejected" | "ended" | "missed"
     */
    private val _callStatus = MutableLiveData<String>()
    val callStatus: LiveData<String> = _callStatus

    // ── SDP answer (for the caller side) ─────────────────────────────────────

    /** The remote SDP answer received from the callee via Firestore. */
    private val _remoteSdpAnswer = MutableLiveData<String>()
    val remoteSdpAnswer: LiveData<String> = _remoteSdpAnswer

    // ── ICE candidates ────────────────────────────────────────────────────────

    /** Emits each ICE candidate map as it arrives from the remote peer. */
    private val _remoteIceCandidate = MutableLiveData<Map<String, Any>>()
    val remoteIceCandidate: LiveData<Map<String, Any>> = _remoteIceCandidate

    // ── Call timer ────────────────────────────────────────────────────────────

    /** Elapsed call seconds emitted every second while connected. */
    private val _callDurationSeconds = MutableLiveData(0L)
    val callDurationSeconds: LiveData<Long> = _callDurationSeconds

    private var callTimer: CountDownTimer? = null
    private var callStartEpoch: Long = 0L

    // ── Error ─────────────────────────────────────────────────────────────────

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    // ─────────────────────────────────────────────────────────────────────────
    // OUTGOING CALL – CALLER SIDE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates the Firestore call document with the SDP offer.
     * Starts observing the document for the callee's answer or rejection.
     *
     * Called by [CallActivity] after WebRTC has created an SDP offer.
     */
    fun initiateCall(
        callerUid: String,
        callerName: String,
        calleeDigitId: String,
        offerSdp: String
    ) {
        viewModelScope.launch {
            try {
                val id = callRepository.createCallDocument(
                    callerUid, callerName, calleeDigitId, offerSdp
                )
                _callId.value = id
                observeCallDocument(id, callerUid)
            } catch (e: Exception) {
                _error.value = "Failed to start call: ${e.message}"
            }
        }
    }

    /**
     * Streams Firestore updates for the call document so the caller can react
     * to status changes (accepted, rejected, ended) and receive the SDP answer.
     */
    private fun observeCallDocument(callId: String, myUid: String) {
        callRepository.observeCallDocument(callId)
            .onEach { data ->
                val status = data[Constants.FIELD_CALL_STATUS] as? String ?: return@onEach
                _callStatus.value = status

                // If callee accepted, extract the SDP answer
                if (status == Constants.CALL_STATUS_ACCEPTED) {
                    val answer = data[Constants.FIELD_ANSWER_SDP] as? String ?: ""
                    if (answer.isNotBlank()) {
                        _remoteSdpAnswer.value = answer
                    }
                }
            }
            .launchIn(viewModelScope)

        // Also observe ICE candidates from the callee
        callRepository.observeIceCandidates(callId, remoteSenderId = callId + "_callee")
            .onEach { candidate -> _remoteIceCandidate.value = candidate }
            .launchIn(viewModelScope)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INCOMING CALL – CALLEE SIDE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Accepts the incoming call: writes the SDP answer and flips status.
     * Called by [IncomingCallActivity] / [CallActivity] (callee mode) after
     * WebRTC has created its answer SDP.
     */
    fun acceptCall(callId: String, answerSdp: String, myUid: String) {
        viewModelScope.launch {
            try {
                _callId.value = callId
                callRepository.acceptCall(callId, answerSdp)

                // Watch for caller's ICE candidates
                callRepository.observeIceCandidates(callId,
                    remoteSenderId = callId + "_caller")
                    .onEach { candidate -> _remoteIceCandidate.value = candidate }
                    .launch
