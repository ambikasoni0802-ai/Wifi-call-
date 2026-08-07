package com.wificall.app.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wificall.app.R
import com.wificall.app.data.model.CallHistory
import com.wificall.app.databinding.ItemCallHistoryBinding
import com.wificall.app.utils.Extensions.toReadableDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : ListAdapter<CallHistory, HistoryAdapter.HistoryViewHolder>(DiffCallback) {

    inner class HistoryViewHolder(
        private val binding: ItemCallHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault())

        fun bind(item: CallHistory) {
            binding.tvPeerName.text = item.peerName.ifBlank { "Unknown" }
            binding.tvPeerDigitId.text = item.peerFourDigitId
            binding.tvDate.text = dateFormat.format(Date(item.callDate))
            binding.tvDuration.text = if (item.callType == "missed") "Missed"
                                      else item.durationSeconds.toReadableDuration()

            val (iconRes, tintRes) = when (item.callType) {
                "outgoing" -> Pair(R.drawable.ic_call_made, R.color.call_outgoing)
                "missed"   -> Pair(R.drawable.ic_call_missed, R.color.call_missed)
                else       -> Pair(R.drawable.ic_call_received, R.color.call_incoming)
            }
            binding.ivCallType.setImageResource(iconRes)
            binding.ivCallType.setColorFilter(
                binding.root.context.getColor(tintRes)
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemCallHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CallHistory>() {
        override fun areItemsTheSame(old: CallHistory, new: CallHistory) = old.callId == new.callId
        override fun areContentsTheSame(old: CallHistory, new: CallHistory) = old == new
    }
}
