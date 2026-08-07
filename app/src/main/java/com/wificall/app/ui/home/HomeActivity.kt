package com.wificall.app.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.auth.FirebaseAuth
import com.wificall.app.MyApplication
import com.wificall.app.R
import com.wificall.app.databinding.ActivityHomeBinding
import com.wificall.app.ui.auth.LoginActivity

/**
 * HomeActivity.kt
 * The main shell activity that hosts the bottom navigation bar and
 * the NavHostFragment. It owns the navigation graph; individual
 * screens (Home, History, Profile) are Fragments inside this shell.
 *
 * Responsibility split:
 *  - HomeActivity  → navigation wiring, auth guard, bottom bar
 *  - HomeFragment  → call dialer, ID display, suggestions
 *  - ProfileFragment → name/photo editing
 *  - HistoryFragment → list of past calls
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Guard: if somehow the user reaches here without being signed in, bounce to login
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupNavigation()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NAVIGATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Wires the BottomNavigationView to the NavController from the NavHostFragment.
     * The nav_graph.xml defines all destinations and their fragment classes.
     */
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Links bottom nav item selections to fragment destinations
        binding.bottomNavigation.setupWithNavController(navController)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE – online presence
    // ─────────────────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        // Mark user as online whenever the app comes to foreground
        (application as MyApplication).setUserOnline(true)
    }

    override fun onPause() {
        super.onPause()
        // Mark offline when the app goes to background
        (application as MyApplication).setUserOnline(false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SIGN OUT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Signs the user out and navigates back to [LoginActivity].
     * Called from [ProfileFragment] via the toolbar menu.
     */
    fun signOut() {
        (application as MyApplication).setUserOnline(false)
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
