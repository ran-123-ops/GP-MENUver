package com.example.voiceapp.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.voiceapp.R

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardUserMessage: CardView = itemView.findViewById(R.id.cardUserMessage)
        private val cardAiMessage: CardView = itemView.findViewById(R.id.cardAiMessage)
        private val tvUserMessage: TextView = itemView.findViewById(R.id.tvUserMessage)
        private val tvAiMessage: TextView = itemView.findViewById(R.id.tvAiMessage)

        fun bind(message: ChatMessage) {
            if (message.isUser) {
                cardUserMessage.visibility = View.VISIBLE
                cardAiMessage.visibility = View.GONE
                tvUserMessage.text = message.content
            } else {
                cardUserMessage.visibility = View.GONE
                cardAiMessage.visibility = View.VISIBLE
                tvAiMessage.text = message.content
            }
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.timestamp == newItem.timestamp
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
