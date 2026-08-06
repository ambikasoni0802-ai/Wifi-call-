package com.wificall.app.ui.call

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.wificall.app.databinding.ActivityIncomingCallBinding
import com.wificall.app.utils.Constants

/**
 * IncomingCallActivity.kt
 * Full-screen "incoming call" screen launched when the device receives an
 * FCM push notification for a Wi-Fi call.
 *
 * Shows on the lock screen (declared in manifest with showOnLockScreen + turnScreenOn).
 * The user can Accept (→ CallActivity) or Reject (→ update Firestore + finish).
 */
class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding
    private val viewModel: CallViewModel by viewModels()

    private val callId   get() = intent.getStringExtra(Constants.EXTRA_CALL_ID) ?: ""
    private val callerName get() = intent.getStringExtra(Constants.EXTRA_CALLER_NAME) ?: "Unknown"
    private val callerDigitId get() = intent.getStringExtra(Constants.EXTRA_CALLER_DIGIT_ID) ?: "????"

    private var vibrator: Vibrator? = null

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupClickListeners()
        startVibration()
        scheduleAutoMissed()
        observeViewModel()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVibration()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SETUP
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupUI() {
        binding.tvCallerName.text = callerName
        binding.tvCallerDigitId.text = "ID: $callerDigitId"
    }

    private fun setupClickListeners() {
        // Accept – navigate to CallActivity in callee mode
        binding.btnAccept.setOnClickListener {
            stopVibration()
            val intent = Intent(this, CallActivity::class.java).apply {
                putExtra(Constants.EXTRA_IS_INCOMING, true)
                putExtra(Constants.EXTRA_CALL_ID, callId)
                putExtra(Constants.EXTRA_PEER_DIGIT_ID, callerDigitId)
                putExtra(Constants.EXTRA_PEER_NAME, callerName)
            }
            startActivity(intent)
            finish()
        }

        // Reject – update Firestore and close
        binding.btnReject.setOnClickListener {
            stopVibration()
            viewModel.rejectCall(callId)
            finish()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OBSERVERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        // If the caller hangs up before the callee answers, close the screen
        viewModel.callStatus.observe(this) { status ->
            if (status == Constants.CALL_STATUS_ENDED ||
                status == Constants.CALL_STATUS_MISSED) {
                stopVibration()
                finish()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VIBRATION RING
    // ─────────────────────────────────────────────────────────────────────────

    private fun startVibration() {
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        // Pattern: wait 0ms, vibrate 700ms, pause 500ms, repeat
        val pattern = longArrayOf(0, 700, 500)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUTO-MISS TIMER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * If neither button is pressed within [Constants.CALL_RING_TIMEOUT_SECONDS],
     * mark the call as missed and finish.
     */
    private fun scheduleAutoMissed() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                stopVibration()
                viewModel.rejectCall(callId)  // Uses "rejected" for simplicity; UI shows missed
                finish()
            }
        }, Constants.CALL_RING_TIMEOUT_SECONDS * 1000)
    }
}
