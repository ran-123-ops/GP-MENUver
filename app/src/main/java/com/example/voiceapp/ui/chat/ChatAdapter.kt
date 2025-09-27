package com.example.voiceapp.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
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
        private val tvUserTimestamp: TextView = itemView.findViewById(R.id.tvUserTimestamp)
        private val tvAiTimestamp: TextView = itemView.findViewById(R.id.tvAiTimestamp)
        private val ivUserImage: ImageView = itemView.findViewById(R.id.ivUserImage)
        private val ivAiImage: ImageView = itemView.findViewById(R.id.ivAiImage)

        fun bind(message: ChatMessage) {
            val timeText = android.text.format.DateFormat.format("HH:mm", message.timestamp).toString()
            if (message.isUser) {
                cardUserMessage.visibility = View.VISIBLE
                cardAiMessage.visibility = View.GONE
                bindMessageContent(
                    textView = tvUserMessage,
                    imageView = ivUserImage,
                    message = message
                )
                tvUserTimestamp.text = timeText
                animateIn(cardUserMessage)
            } else {
                cardUserMessage.visibility = View.GONE
                cardAiMessage.visibility = View.VISIBLE
                bindMessageContent(
                    textView = tvAiMessage,
                    imageView = ivAiImage,
                    message = message
                )
                tvAiTimestamp.text = timeText
                animateIn(cardAiMessage)
            }
        }

        private fun bindMessageContent(textView: TextView, imageView: ImageView, message: ChatMessage) {
            val hasText = message.content.isNotBlank()
            textView.visibility = if (hasText) View.VISIBLE else View.GONE
            if (hasText) {
                textView.text = message.content
            } else {
                textView.text = ""
            }

            val attachment = message.image
            if (attachment != null) {
                imageView.visibility = View.VISIBLE
                val uri = attachment.uri
                if (uri != null) {
                    imageView.setImageURI(uri)
                } else {
                    val dataUrl = attachment.dataUrl
                    val base64Part = dataUrl.substringAfter("base64,", missingDelimiterValue = "")
                    if (base64Part.isNotBlank()) {
                        val bytes = runCatching { Base64.decode(base64Part, Base64.DEFAULT) }.getOrNull()
                        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                        } else {
                            imageView.visibility = View.GONE
                            imageView.setImageDrawable(null)
                        }
                    } else {
                        imageView.visibility = View.GONE
                        imageView.setImageDrawable(null)
                    }
                }
            } else {
                imageView.visibility = View.GONE
                imageView.setImageDrawable(null)
            }
        }

        private fun animateIn(target: View) {
            if (target.alpha == 1f) return
            target.alpha = 0f
            target.translationY = 12f
            target.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
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
