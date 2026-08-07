package com.wificall.app.ui.profile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.wificall.app.R
import com.wificall.app.databinding.FragmentProfileBinding
import com.wificall.app.ui.home.HomeActivity
import com.wificall.app.utils.Extensions.snack
import com.wificall.app.utils.Extensions.visibleIf

/**
 * ProfileFragment.kt
 * Screen where users can:
 *  - View their 4-digit ID and email
 *  - Change their display name
 *  - Change their profile photo (picked from the gallery)
 *  - Sign out
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()
    private val currentUid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // ── Gallery picker launcher ───────────────────────────────────────────────
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            viewModel.uploadProfilePhoto(currentUid, uri)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()
        observeViewModel()
        setupClickListeners()
        viewModel.loadUser(currentUid)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MENU (Sign out)
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_profile, menu)
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_sign_out -> {
                        (requireActivity() as HomeActivity).signOut()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OBSERVERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user == null) return@observe
            binding.tvEmail.text = user.email
            binding.tvFourDigitId.text = "Your ID: ${user.fourDigitId}"
            binding.etDisplayName.setText(user.displayName)

            // Load profile photo
            if (user.photoUrl.isNotBlank()) {
                Glide.with(this)
                    .load(user.photoUrl)
                    .placeholder(R.drawable.ic_default_avatar)
                    .circleCrop()
                    .into(binding.ivProfilePhoto)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibleIf(loading)
            binding.btnSaveName.isEnabled = !loading
            binding.ivProfilePhoto.isClickable = !loading
        }

        viewModel.uploadProgress.observe(viewLifecycleOwner) { progress ->
            binding.uploadProgressBar.visibleIf(progress in 1..99)
            binding.uploadProgressBar.progress = progress
        }

        viewModel.updateResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess { binding.root.snack("Profile updated!") }
            result.onFailure { e -> binding.root.snack(e.message ?: "Update failed") }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CLICK LISTENERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupClickListeners() {
        // Save new display name
        binding.btnSaveName.setOnClickListener {
            viewModel.updateDisplayName(
                uid = currentUid,
                newName = binding.etDisplayName.text.toString()
            )
        }

        // Open gallery to pick a new profile photo
        binding.ivProfilePhoto.setOnClickListener { openGallery() }
        binding.btnChangePhoto.setOnClickListener { openGallery() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }
}
