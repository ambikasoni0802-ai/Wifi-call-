package com.wificall.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.wificall.app.ui.auth.LoginActivity
import com.wificall.app.ui.home.HomeActivity

/**
 * MainActivity.kt
 * Splash / router activity – the first Activity the OS launches.
 *
 * Its only job is to check whether a user is already signed in:
 *  - Signed in  → go directly to [HomeActivity]
 *  - Not signed in → go to [LoginActivity]
 *
 * It never shows any UI of its own; it finishes immediately after routing.
 * The @style/Theme.WiFiCall.Splash (a windowBackground drawable) provides the
 * visual splash effect while this minimal code runs.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No setContentView – we never render a layout here

        val currentUser = FirebaseAuth.getInstance().currentUser

        val destination = if (currentUser != null) {
            // User is already authenticated – head straight to the app
            Intent(this, HomeActivity::class.java)
        } else {
            // No active session – ask them to log in
            Intent(this, LoginActivity::class.java)
        }

        startActivity(destination)
        finish() // Removes MainActivity from the back stack so Back doesn't return here
    }
}
