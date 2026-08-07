package com.wificall.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.wificall.app.R
import com.wificall.app.data.model.User
import com.wificall.app.databinding.ItemSuggestionBinding

/**
 * SuggestionAdapter.kt
 * RecyclerView adapter for the 5 random suggestion cards on the home screen.
 *
 * Uses [ListAdapter] + [DiffUtil] for efficient list updates without full
 * redraws – only changed items are animated.
 *
 * @param onCallClick Called with the tapped [User] when the user initiates a call.
 */
class SuggestionAdapter(
    private val onCallClick: (User) -> Unit
) : ListAdapter<User, SuggestionAdapter.SuggestionViewHolder>(DiffCallback) {

    // ─────────────────────────────────────────────────────────────────────────
    // VIEWHOLDER
    // ─────────────────────────────────────────────────────────────────────────

    inner class SuggestionViewHolder(
        private val binding: ItemSuggestionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Binds a [User] to the card view:
         *  - Avatar image (or placeholder if no photo)
         *  - Display name
         *  - 4-digit ID chip
         *  - Online indicator dot
         */
        fun bind(user: User) {
            binding.tvName.text = user.displayName.ifBlank { "User" }
            binding.tvDigitId.text = user.fourDigitId

            // Online indicator: green dot = online, grey dot = offline
            val indicatorRes = if (user.isOnline)
                R.drawable.ic_online_indicator
            else
                R.drawable.ic_offline_indicator
            binding.ivOnlineIndicator.setImageResource(indicatorRes)

            // Load profile photo or fallback avatar
            if (user.photoUrl.isNotBlank()) {
                Glide.with(binding.root.context)
                    .load(user.photoUrl)
                    .placeholder(R.drawable.ic_default_avatar)
                    .circleCrop()
                    .into(binding.ivAvatar)
            } else {
                binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
            }

            // Tapping anywhere on the card triggers a call
            binding.root.setOnClickListener { onCallClick(user) }
            binding.btnCall.setOnClickListener { onCallClick(user) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADAPTER OVERRIDES
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val binding = ItemSuggestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DIFF CALLBACK – determines which items changed so ListAdapter can animate
    // ─────────────────────────────────────────────────────────────────────────

    companion object DiffCallback : DiffUtil.ItemCallback<User>() {
        /** Two items are the "same" list entry if they share a uid. */
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean =
            oldItem.uid == newItem.uid

        /** Two items have the same content if all displayed fields match. */
        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean =
            oldItem == newItem
    }
}
