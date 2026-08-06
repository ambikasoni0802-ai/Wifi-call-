package com.wificall.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.wificall.app.R
import com.wificall.app.ui.call.CallActivity
import com.wificall.app.utils.Constants
import org.webrtc.*

/**
 * WebRTCService.kt
 * Foreground service that manages the WebRTC PeerConnection lifecycle.
 *
 * Running as a foreground service allows the peer connection to stay alive
 * when the user leaves the call screen (e.g., checks a message), preventing
 * call drops from process death.
 *
 * Architecture note:
 *  - This service owns the PeerConnection and audio tracks.
 *  - CallViewModel owns the Firestore signaling (SDP, ICE exchange).
 *  - CallActivity bridges between them: it reads ViewModel LiveData to feed
 *    ICE candidates and SDP to this service, and reads service events to
 *    update the ViewModel.
 *
 * For a production app, use a bound service + AIDL or a shared singleton
 * to give the Activity direct method access. This simplified version
 * communicates via static callbacks for demo clarity.
 */
class WebRTCService : Service() {

    companion object {
        // ── Static callbacks – set by CallActivity ────────────────────────────
        // In production, prefer a proper service binding approach.

        /** Called when the local SDP offer/answer is ready. */
        var onLocalSdpReady: ((SessionDescription) -> Unit)? = null

        /** Called for each ICE candidate generated locally. */
        var onIceCandidateReady: ((IceCandidate) -> Unit)? = null

        /** Called when the peer connection state changes. */
        var onConnectionStateChanged: ((PeerConnection.PeerConnectionState) -> Unit)? = null

        // ── Shared PeerConnection (set after startService()) ──────────────────
        var peerConnection: PeerConnection? = null
    }

    // ── WebRTC objects ────────────────────────────────────────────────────────
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // ─────────────────────────────────────────────────────────────────────────
    // SERVICE LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        initWebRTC()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground() within 5 seconds of onStartCommand()
        startForeground(Constants.NOTIFICATION_ID_ONGOING_CALL, buildOngoingNotification())

        val isIncoming = intent?.getBooleanExtra(Constants.EXTRA_IS_INCOMING, false) ?: false
        val peerDigitId = intent?.getStringExtra(Constants.EXTRA_PEER_DIGIT_ID) ?: ""
        val callId = intent?.getStringExtra(Constants.EXTRA_CALL_ID)

        createPeerConnection()

        if (isIncoming) {
            // Callee: wait for the offer SDP to be set externally, then createAnswer
            // CallActivity observes viewModel.remoteSdpAnswer and calls setRemoteSdp()
        } else {
            // Caller: create the SDP offer
            createOffer()
        }

        return START_NOT_STICKY  // Don't restart after being killed
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePeerConnection()
    }

    override fun onBind(intent: Intent?): IBinder? = null  // Not a bound service

    // ─────────────────────────────────────────────────────────────────────────
    // WEBRTC INITIALISATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initialises the WebRTC engine and creates a PeerConnectionFactory.
     * Must be called once, ideally in [onCreate].
     */
    private fun initWebRTC() {
        // Bootstrap the WebRTC native libraries
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        // Configure audio-only (no video encoder/decoder needed)
        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()

        // Create the microphone audio source
        val audioConstraints = MediaConstraints()
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio_track_0", audioSource)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PEER CONNECTION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates the [PeerConnection] with a STUN server for NAT traversal.
     * ICE candidates and connection state changes are surfaced via the static
     * callbacks above.
     */
    private fun createPeerConnection() {
        // Configure the STUN server (helps devices behind NAT find each other)
        val iceServers = listOf(
            PeerConnection.IceServer.builder(Constants.STUN_SERVER).createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {

                // ── ICE candidate generated locally ──────────────────────────
                override fun onIceCandidate(candidate: IceCandidate) {
                    onIceCandidateReady?.invoke(candidate)
                }

                // ── Connection state changed ──────────────────────────────────
                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                    onConnectionStateChanged?.invoke(newState)
                }

                // ── Required overrides (no-op for audio-only) ─────────────────
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(
                    receiver: RtpReceiver?,
                    streams: Array<out MediaStream>?
                ) {}
            }
        )

        // Add the local audio track so it's included in the SDP offer/answer
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("local_stream"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SDP OFFER / ANSWER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates an SDP offer (caller side).
     * The offer is passed to [onLocalSdpReady] for upload to Firestore.
     */
    private fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SilentSdpObserver(), sdp)
                onLocalSdpReady?.invoke(sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    /**
     * Called externally (by CallActivity) once the remote offer SDP
     * is retrieved from Firestore. Creates the answer SDP.
     */
    fun setRemoteOfferAndCreateAnswer(offerSdp: String) {
        val offer = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                // Create the answer after setting the remote description
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                }
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) {
                        peerConnection?.setLocalDescription(SilentSdpObserver(), sdp)
                        onLocalSdpReady?.invoke(sdp)
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {}
                }, constraints)
            }
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        }, offer)
    }

    /**
     * Applies the callee's SDP answer (caller side) once received from Firestore.
     */
    fun setRemoteAnswer(answerSdp: String) {
        val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
