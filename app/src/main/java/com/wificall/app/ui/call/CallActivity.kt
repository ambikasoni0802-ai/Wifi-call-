package com.wificall.app.ui.call

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.wificall.app.R
import com.wificall.app.databinding.ActivityCallBinding
import com.wificall.app.service.WebRTCService
import com.wificall.app.utils.Constants
import com.wificall.app.utils.Extensions.toCallDurationString
import com.wificall.app.utils.Extensions.visibleIf

/**
 * CallActivity.kt
 * Active call screen – handles both outgoing and incoming (callee side) calls.
 *
 * UI states:
 *  1. Ringing    – "Calling…" / "Incoming Call" shown, timer hidden
 *  2. Connected  – timer running, mute/speaker controls visible
 *  3. Ended      – brief "Call ended" shown, then finish()
 *
 * WebRTC peer connection is managed by [WebRTCService] (a foreground service
 * so the call survives screen-off). This Activity communicates with the service
 * via an Intent-based API and mirrors ViewModel state to the UI.
 */
class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private val viewModel: CallViewModel by viewModels()

    // ── Call parameters from Intent ───────────────────────────────────────────
    private val isIncoming get() = intent.getBooleanExtra(Constants.EXTRA_IS_INCOMING, false)
    private val peerDigitId get() = intent.getStringExtra(Constants.EXTRA_PEER_DIGIT_ID) ?: ""
    private val peerName get() = intent.getStringExtra(Constants.EXTRA_PEER_NAME) ?: "Unknown"
    private val callId get() = intent.getStringExtra(Constants.EXTRA_CALL_ID) ?: ""

    private val currentUid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // ── State ─────────────────────────────────────────────────────────────────
    private var isMuted = false
    private var isSpeakerOn = false

    // ── Mic permission launcher ───────────────────────────────────────────────
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startWebRtcService()
        else {
            binding.tvCallStatus.text = "Microphone permission denied"
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2000)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
        setupClickListeners()
        checkMicPermissionAndStart()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the WebRTC foreground service if the activity finishes
        stopService(Intent(this, WebRTCService::class.java))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SETUP
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupUI() {
        binding.tvPeerName.text = peerName
        binding.tvPeerDigitId.text = peerDigitId
        binding.tvCallStatus.text = if (isIncoming) "Connecting…" else "Calling…"
        binding.tvTimer.visibleIf(false)

        // Load peer avatar placeholder
        Glide.with(this)
            .load(R.drawable.ic_default_avatar)
            .circleCrop()
            .into(binding.ivPeerAvatar)
    }

    private fun checkMicPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startWebRtcService()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WebRTC SERVICE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Starts [WebRTCService] as a foreground service with call parameters.
     * The service creates the PeerConnection, gathers ICE candidates, and
     * posts updates back via broadcast / LiveData.
     */
    private fun startWebRtcService() {
        val serviceIntent = Intent(this, WebRTCService::class.java).apply {
            putExtra(Constants.EXTRA_IS_INCOMING, isIncoming)
            putExtra(Constants.EXTRA_PEER_DIGIT_ID, peerDigitId)
            putExtra(Constants.EXTRA_PEER_NAME, peerName)
            if (isIncoming) putExtra(Constants.EXTRA_CALL_ID, callId)
        }
        startForegroundService(serviceIntent)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OBSERVERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        // Call status changes (calling → accepted → ended)
        viewModel.callStatus.observe(this) { status ->
            when (status) {
                Constants.CALL_STATUS_CALLING -> {
                    binding.tvCallStatus.text = "Calling…"
                    binding.tvTimer.visibleIf(false)
                }
                Constants.CALL_STATUS_ACCEPTED -> {
                    binding.tvCallStatus.text = "Connected"
                    binding.tvTimer.visibleIf(true)
                    viewModel.startTimer()
                }
                Constants.CALL_STATUS_REJECTED -> {
                    binding.tvCallStatus.text = "Call declined"
                    finishWithDelay(1500)
                }
                Constants.CALL_STATUS_ENDED, Constants.CALL_STATUS_MISSED -> {
                    binding.tvCallStatus.text = "Call ended"
                    viewModel.stopTimer()
                    finishWithDelay(1500)
                }
            }
        }

        // Live call timer (mm:ss)
        viewModel.callDurationSeconds.observe(this) { seconds ->
            binding.tvTimer.text = seconds.toCallDurationString()
        }

        // Error messages
        viewModel.error.observe(this) { msg ->
            binding.tvCallStatus.text = msg
            finishWithDelay(2000)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CLICK LISTENERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupClickListeners() {
        // End call button (red button)
        binding.btnEndCall.setOnClickListener {
            viewModel.endCall(
                callId = viewModel.callId.value ?: "",
                myUid = currentUid,
                peerFourDigitId = peerDigitId,
                peerName = peerName,
                callType = if (isIncoming) "incoming" else "outgoing"
            )
            finish()
        }

        // Mute / unmute microphone
        binding.btnMute.setOnClickListener {
            isMuted = !isMuted
            binding.btnMute.setIconResource(
                if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_on
            )
            // TODO: apply mute to WebRTCService local audio track
        }

        // Toggle speaker / earpiece
        binding.btnSpeaker.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            binding.btnSpeaker.setIconResource(
                if (isSpeakerOn) R.drawable.ic_speaker_on else R.drawable.ic_speaker_off
            )
            // TODO: toggle AudioManager mode
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun finishWithDelay(delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, delayMs)
    }
}
