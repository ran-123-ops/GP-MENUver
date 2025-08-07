package com.example.voiceapp.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voiceapp.BuildConfig
import com.example.voiceapp.api.Message
import com.example.voiceapp.api.OpenAIClient
import kotlinx.coroutines.launch

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatViewModel : ViewModel() {

    private val _messages = MutableLiveData<List<ChatMessage>>()
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isApiKeyConfigured = MutableLiveData<Boolean>()
    val isApiKeyConfigured: LiveData<Boolean> = _isApiKeyConfigured

    private var openAIClient: OpenAIClient? = null

    init {
        _messages.value = emptyList()
        _isLoading.value = false
        initializeApiKey()
    }

    private fun initializeApiKey() {
        val apiKey = BuildConfig.OPENAI_API_KEY
        val baseUrl = BuildConfig.OPENAI_BASE_URL

        if (apiKey.isNotEmpty() && apiKey != "your_openai_api_key_here") {
            openAIClient = OpenAIClient(apiKey, baseUrl)
            _isApiKeyConfigured.value = true
        } else {
            _isApiKeyConfigured.value = false
            _error.value = "local.propertiesファイルでOPENAI_API_KEYを設定してください"
        }
    }

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        val client = openAIClient
        if (client == null) {
            _error.value = "APIキーが設定されていません"
            return
        }

        // ユーザーメッセージを追加
        val currentMessages = _messages.value?.toMutableList() ?: mutableListOf()
        currentMessages.add(ChatMessage(userMessage, true))
        _messages.value = currentMessages

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                // OpenAI APIに送信するメッセージリストを作成
                val apiMessages = currentMessages.map { chatMessage ->
                    Message(
                        role = if (chatMessage.isUser) "user" else "assistant",
                        content = chatMessage.content
                    )
                }

                val result = client.sendMessage(apiMessages)

                result.fold(
                    onSuccess = { response ->
                        val updatedMessages = _messages.value?.toMutableList() ?: mutableListOf()
                        updatedMessages.add(ChatMessage(response, false))
                        _messages.value = updatedMessages
                    },
                    onFailure = { exception ->
                        _error.value = "エラー: ${exception.message}"
                    }
                )
            } catch (e: Exception) {
                _error.value = "予期しないエラーが発生しました: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        _error.value = null
    }
}
