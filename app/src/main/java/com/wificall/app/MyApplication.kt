package com.wificall.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.wificall.app.data.repository.UserRepository
import com.wificall.app.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * MyApplication.kt
 * Application subclass – the entry point that runs before any Activity or Service.
 *
 * Responsibilities:
 *  1. Create notification channels (required on Android 8+).
 *  2. Mark the user as online on launch, offline on termination.
 *  3. Provide a global application-scoped coroutine scope for fire-and-forget tasks.
 */
class MyApplication : Application() {

    /**
     * Application-scoped coroutine scope.
     * Survives orientation changes. Use only for background tasks that should
     * outlive individual Activities (e.g. marking online status).
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val userRepository = UserRepository()

    override fun onCreate() {
        super.onCreate()

        // Create notification channels on first run (no-op on subsequent launches)
        createNotificationChannels()

        // Mark user as online if they are already signed in (e.g. app restart)
        setUserOnline(true)
    }

    override fun onTerminate() {
        super.onTerminate()
        // onTerminate() is NOT guaranteed to be called on real devices,
        // but we call it here for emulators and clean-exit scenarios.
        // The FCM service also marks offline when the token refreshes on loss.
        setUserOnline(false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NOTIFICATION CHANNELS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates the two notification channels used by WiFiCall:
     *  1. "incoming_calls" – high importance, shown on lock screen with sound
     *  2. "ongoing_call"   – low importance foreground service notification
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Incoming call channel – needs to be high importance so it pops up
        NotificationChannel(
            Constants.CHANNEL_INCOMING_CALLS,
            "Incoming Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for incoming WiFi calls"
            enableVibration(true)
            enableLights(true)
            manager.createNotificationChannel(this)
        }

        // Ongoing call – lower importance; just keeps the foreground service alive
        NotificationChannel(
            Constants.CHANNEL_ONGOING_CALL,
            "Ongoing Call",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while a call is in progress"
            manager.createNotificationChannel(this)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ONLINE PRESENCE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Updates the [isOnline] field in Firestore for the current user.
     * Fire-and-forget – uses the application scope so it isn't cancelled
     * when an Activity is destroyed.
     */
    fun setUserOnline(isOnline: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        applicationScope.launch {
            userRepository.setOnlineStatus(uid, isOnline)
        }
    }
}
