package com.example.voiceapp.ui.chat

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ChatHistoryStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<PersistedMessage>>() {}.type

    fun saveMessages(messages: List<ChatMessage>) {
        val persisted = messages.map { message ->
            PersistedMessage(
                content = message.content,
                isUser = message.isUser,
                imageUri = null,
                imageDataUrl = message.image?.dataUrl,
                timestamp = message.timestamp
            )
        }
        val json = gson.toJson(persisted, listType)
        prefs.edit { putString(KEY_MESSAGES, json) }
    }

    fun loadMessages(): List<ChatMessage> {
        val json = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        return runCatching {
            val persisted: List<PersistedMessage> = gson.fromJson(json, listType)
            persisted.mapNotNull { it.toChatMessage() }
        }.getOrDefault(emptyList())
    }

    fun clear() {
        prefs.edit { remove(KEY_MESSAGES) }
    }

    private data class PersistedMessage(
        val content: String,
        val isUser: Boolean,
        val imageUri: String?,
        val imageDataUrl: String?,
        val timestamp: Long
    ) {
        fun toChatMessage(): ChatMessage {
            val attachment = if (!imageDataUrl.isNullOrBlank()) {
                ImageAttachment(
                    uri = null,
                    dataUrl = imageDataUrl
                )
            } else {
                null
            }
            return ChatMessage(
                content = content,
                isUser = isUser,
                image = attachment,
                timestamp = timestamp
            )
        }
    }

    companion object {
        private const val PREF_NAME = "chat_history_prefs"
        private const val KEY_MESSAGES = "messages"
    }
}
