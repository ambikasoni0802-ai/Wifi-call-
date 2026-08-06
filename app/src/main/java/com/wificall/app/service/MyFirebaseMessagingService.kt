package com.wificall.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wificall.app.R
import com.wificall.app.data.repository.UserRepository
import com.wificall.app.ui.call.IncomingCallActivity
import com.wificall.app.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * MyFirebaseMessagingService.kt
 * Handles Firebase Cloud Messaging events:
 *  1. [onMessageReceived] – incoming call push notification from the caller's device
 *  2. [onNewToken]        – FCM token refresh; saves the new token to Firestore
 *
 * All incoming WiFiCall signaling is carried in FCM *data* messages
 * (not notification messages) so the app has full control over the UI,
 * even when the app is in the background.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    // A service-scoped coroutine scope for Firestore writes
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val userRepository = UserRepository()

    // ─────────────────────────────────────────────────────────────────────────
    // FCM TOKEN REFRESH
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called by FCM whenever the registration token is refreshed.
     * We must save the new token so other users can still reach this device.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        serviceScope.launch {
            userRepository.refreshAndSaveFcmToken(uid)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MESSAGE RECEIVED
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called when a data message is received (app foreground or background).
     *
     * Expected data payload for an incoming call:
     * ```
     * {
     *   "type":          "incoming_call",
     *   "callId":        "<firestoreCallDocId>",
     *   "callerName":    "Alice",
     *   "callerDigitId": "4821"
     * }
     * ```
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val type = data[Constants.FCM_KEY_TYPE] ?: return

        when (type) {
            Constants.FCM_TYPE_INCOMING_CALL -> {
                val callId       = data[Constants.FCM_KEY_CALL_ID]       ?: return
                val callerName   = data[Constants.FCM_KEY_CALLER_NAME]   ?: "Unknown"
                val callerDigitId = data[Constants.FCM_KEY_CALLER_DIGIT_ID] ?: "????"

                showIncomingCallNotification(callId, callerName, callerDigitId)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INCOMING CALL NOTIFICATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds and shows a full-screen / heads-up notification that launches
     * [IncomingCallActivity] when tapped or when the full-screen intent fires.
     *
     * On Android 10+ a full-screen intent is needed to show over the lock screen.
     */
    private fun showIncomingCallNotification(
        callId: String,
        callerName: String,
        callerDigitId: String
    ) {
        // Intent that opens IncomingCallActivity
        val incomingIntent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(Constants.EXTRA_CALL_ID, callId)
            putExtra(Constants.EXTRA_CALLER_NAME, callerName)
            putExtra(Constants.EXTRA_CALLER_DIGIT_ID, callerDigitId)
        }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, incomingIntent, pendingFlags
        )

        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_INCOMING_CALLS)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle("Incoming WiFi Call")
            .setContentText("$callerName (ID: $callerDigitId) is calling…")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)   // Show over lock screen
            .setContentIntent(fullScreenPendingI
