package com.wificall.app.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wificall.app.databinding.ActivityLoginBinding
import com.wificall.app.ui.home.HomeActivity
import com.wificall.app.utils.Extensions.snack
import com.wificall.app.utils.Extensions.visibleIf

/**
 * LoginActivity.kt
 * Displays the Email + Password login form.
 *
 * View Binding is used throughout (no findViewById). The ViewModel holds
 * all business logic; this file only wires UI events → ViewModel and
 * ViewModel results → UI updates.
 */
class LoginActivity : AppCompatActivity() {

    // View Binding – gives type-safe access to every view in activity_login.xml
    private lateinit var binding: ActivityLoginBinding

    // Delegates ViewModel creation to the Activity's ViewModelStore
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeViewModel()
        setupClickListeners()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OBSERVERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Subscribes to LiveData from the ViewModel.
     * All UI updates happen here – keeping them out of click handlers
     * ensures the UI can be restored correctly after a config change.
     */
    private fun observeViewModel() {
        // Show/hide the loading spinner while the login request is in flight
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibleIf(isLoading)
            binding.btnLogin.isEnabled = !isLoading
            binding.btnRegister.isEnabled = !isLoading
        }

        // Handle the login result
        viewModel.loginResult.observe(this) { result ->
            result.onSuccess {
                // Successful login → navigate to the main app shell
                startActivity(Intent(this, HomeActivity::class.java))
                finish() // Don't allow Back to return to the login screen
            }
            result.onFailure { error ->
                binding.root.snack(error.message ?: "Login failed. Please try again.")
            }
        }

        // Handle password reset result
        viewModel.resetResult.observe(this) { result ->
            result.onSuccess {
                binding.root.snack("Password reset email sent. Check your inbox.")
            }
            result.onFailure { error ->
                binding.root.snack(error.message ?: "Failed to send reset email.")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CLICK LISTENERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupClickListeners() {
        // Login button – reads fields and delegates to ViewModel
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            viewModel.login(email, password)
        }

        // Navigate to the registration screen
        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // "Forgot password?" – shows a dialog asking for the user's email
        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DIALOGS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shows a Material dialog with a single email input field.
     * On confirm, delegates to the ViewModel to send the reset email.
     */
    private fun showForgotPasswordDialog() {
        // Inflate a simple single-field layout for the dialog
        val emailInput = com.google.android.material.textfield.TextInputEditText(this)
        emailInput.hint = "Enter your email"

        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Password")
            .setMessage("We'll send a reset link to your email address.")
            .setView(emailInput)
            .setPositiveButton("Send") { _, _ ->
                viewModel.sendPasswordReset(emailInput.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
