package com.wificall.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.wificall.app.R
import com.wificall.app.databinding.FragmentHomeBinding
import com.wificall.app.ui.call.CallActivity
import com.wificall.app.utils.Constants
import com.wificall.app.utils.Extensions.gone
import com.wificall.app.utils.Extensions.show
import com.wificall.app.utils.Extensions.snack
import com.wificall.app.utils.Extensions.visibleIf

/**
 * HomeFragment.kt
 * The main dialer screen shown after login.
 *
 * Displays:
 *  - User's own 4-digit ID (large, copyable)
 *  - Online/Offline network banner
 *  - 5 suggestion cards (valid users at random IDs)
 *  - Manual 4-digit input field + Call button
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!     // Safe non-null accessor

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var suggestionAdapter: SuggestionAdapter

    private val currentUid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSuggestionRecycler()
        observeViewModel()
        setupClickListeners()

        // Load data on first show
        viewModel.loadCurrentUser(currentUid)
    }

    override fun onResume() {
        super.onResume()
        // Refresh user data in case profile was just updated
        viewModel.loadCurrentUser(currentUid)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SETUP
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupSuggestionRecycler() {
        suggestionAdapter = SuggestionAdapter { user ->
            // Suggestion card tapped → start a call to that user
            startCall(
                peerDigitId = user.fourDigitId,
                peerName = user.displayName
            )
        }
        binding.rvSuggestions.adapter = suggestionAdapter
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OBSERVERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        // Current user profile
        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user == null) return@observe

            // Show the user's name and big 4-digit ID
            binding.tvUserName.text = user.displayName.ifBlank { "You" }
            binding.tvFourDigitId.text = user.fourDigitId

            // Profile photo (Glide handles placeholder and circle crop)
            if (user.photoUrl.isNotBlank()) {
                Glide.with(this)
                    .load(user.photoUrl)
                    .placeholder(R.drawable.ic_default_avatar)
                    .circleCrop()
                    .into(binding.ivProfilePhoto)
            }

            // Load suggestions excluding the user's own ID
            viewModel.loadSuggestions(user.fourDigitId)
        }

        // Suggestions
        viewModel.suggestions.observe(viewLifecycleOwner) { users ->
            suggestionAdapter.submitList(users)
            binding.tvNoSuggestions.visibleIf(users.isEmpty())
        }

        // Suggestions loading spinner
        viewModel.isLoadingSuggestions.observe(viewLifecycleOwner) { loading ->
            binding.progressSuggestions.visibleIf(loading)
        }

        // Network banner
        viewModel.isInternetAvailable.observe(viewLifecycleOwner) { connected ->
            if (connected) {
                binding.bannerNoInternet.gone()
                binding.btnCall.isEnabled = true
            } else {
                binding.bannerNoInternet.show()
                binding.btnCall.isEnabled = false
            }
        }

        // Peer lookup after manual ID entry
        viewModel.peerLookupResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess { user ->
                if (user == null) {
                    binding.root.snack("No user found with that ID")
                } else {
                    startCall(
                        peerDigitId = user.fourDigitId,
                        peerName = user.displayName
                    )
                }
            }
            result.onFailure { error ->
                binding.root.snack(error.message ?: "Lookup failed")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CLICK LISTENERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupClickListeners() {
        // Call button for manually entered ID
        binding.btnCall.setOnClickListener {
            val inputId = binding.etTargetId.text.toString().trim()
            viewModel.lookupPeer(inputId)
        }

        // Refresh suggestions button
        binding.btnRefreshSuggestions.setOnClickListener {
            val myId = viewModel.currentUser.value?.fourDigitId ?: return@setOnClickListener
            viewModel.loadSuggestions(myId)
        }

        // Copy own ID to clipboard on long-press
        binding.tvFourDigitId.setOnLongClickListener {
            val clipboard = requireContext()
                .getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("WiFiCall ID",
                binding.tvFourDigitId.text)
            clipboard.setPrimaryClip(clip)
            binding.root.snack("ID copied to clipboard")
            true
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NAVIGATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Starts [CallActivity] for an outgoing call to [peerDigitId].
     */
    private fun startCall(peerDigitId: String, peerName: String) {
        val intent = Intent(requireContext(), CallActivity::class.java).apply {
            putExtra(Constants.EXTRA_IS_INCOMING, false)
            putExtra(Constants.EXTRA_PEER_DIGIT_ID, peerDigitId)
            putExtra(Constants.EXTRA_PEER_NAME, peerName)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null  // Avoid memory leaks – clear binding reference when view is destroyed
    }
}
