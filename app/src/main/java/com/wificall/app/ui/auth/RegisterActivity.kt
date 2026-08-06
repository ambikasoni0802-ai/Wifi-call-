package com.wificall.app.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.wificall.app.databinding.ActivityRegisterBinding
import com.wificall.app.ui.home.HomeActivity
import com.wificall.app.utils.Extensions.snack
import com.wificall.app.utils.Extensions.visibleIf

/**
 * RegisterActivity.kt
 * Registration screen: collects name, email, password, confirm-password.
 * The ViewModel handles all logic; this file is pure UI wiring.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: RegisterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show back arrow in the toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        observeViewModel()
        setupClickListeners()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OBSERVERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        // Loading state – disable form while request is in flight
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibleIf(isLoading)
            binding.btnRegister.isEnabled = !isLoading
            binding.etName.isEnabled = !isLoading
            binding.etEmail.isEnabled = !isLoading
            binding.etPassword.isEnabled = !isLoading
            binding.etConfirmPassword.isEnabled = !isLoading
        }

        // Multi-step status message shown above the progress bar
        viewModel.statusMessage.observe(this) { message ->
            binding.tvStatus.text = message
            binding.tvStatus.visibleIf(message.isNotBlank())
        }

        // Final result
        viewModel.registerResult.observe(this) { result ->
            result.onSuccess {
                // Clear the back stack so the user can't navigate back to auth
                val intent = Intent(this, HomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            result.onFailure { error ->
                binding.root.snack(error.message ?: "Registration failed. Please try again.")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CLICK LISTENERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            viewModel.register(
                name = binding.etName.text.toString(),
                email = binding.etEmail.text.toString(),
                password = binding.etPassword.text.toString(),
                confirmPassword = binding.etConfirmPassword.text.toString()
            )
        }
    }
}
