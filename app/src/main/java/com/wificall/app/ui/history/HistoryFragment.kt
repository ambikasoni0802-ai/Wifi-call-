package com.wificall.app.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.firebase.auth.FirebaseAuth
import com.wificall.app.databinding.FragmentHistoryBinding
import com.wificall.app.utils.Extensions.gone
import com.wificall.app.utils.Extensions.show
import com.wificall.app.utils.Extensions.visibleIf

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter

    private val currentUid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecycler()
        observeViewModel()
        viewModel.loadHistory(currentUid)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecycler() {
        adapter = HistoryAdapter()
        binding.rvHistory.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibleIf(loading)
        }

        viewModel.history.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            if (list.isEmpty()) {
                binding.tvEmpty.show()
                binding.rvHistory.gone()
            } else {
                binding.tvEmpty.gone()
                binding.rvHistory.show()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            if (errorMsg != null) {
                binding.tvEmpty.text = errorMsg
                binding.tvEmpty.show()
            }
        }
    }
}
