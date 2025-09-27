package com.example.voiceapp.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voiceapp.BuildConfig
import com.example.voiceapp.api.ChatRequestMessage
import com.example.voiceapp.api.ImageUrl
import com.example.voiceapp.api.MessageContent
import com.example.voiceapp.api.OpenAIClient
import kotlinx.coroutines.launch

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val image: ImageAttachment? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ImageAttachment(
    val uri: android.net.Uri?,
    val dataUrl: String
)

class ChatViewModel(private val chatHistoryStorage: ChatHistoryStorage) : ViewModel() {

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
        _messages.value = chatHistoryStorage.loadMessages()
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

    fun sendMessage(userMessage: String?, image: ImageAttachment? = null, systemPrompt: String? = null) {
        if (userMessage.isNullOrBlank() && image == null) return

        val messageToAdd = ChatMessage(userMessage.orEmpty(), true, image)
        val client = openAIClient
        if (client == null) {
            _error.value = "APIキーが設定されていません"
            return
        }

        val currentMessages = _messages.value?.toMutableList() ?: mutableListOf()
        currentMessages.add(messageToAdd)
        updateMessages(currentMessages)

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                // OpenAI APIに送信するメッセージリストを作成
                val apiMessages = mutableListOf<ChatRequestMessage>()
                if (!systemPrompt.isNullOrBlank()) {
                    apiMessages.add(
                        ChatRequestMessage(
                            role = "system",
                            content = listOf(MessageContent(type = "text", text = systemPrompt))
                        )
                    )
                }
                apiMessages.addAll(currentMessages.map { chatMessage ->
                    chatMessage.toApiMessage()
                })

                val result = client.sendMessage(apiMessages)

                result.fold(
                    onSuccess = { response ->
                        val updatedMessages = _messages.value?.toMutableList() ?: mutableListOf()
                        updatedMessages.add(ChatMessage(response, false))
                        updateMessages(updatedMessages)
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
        updateMessages(emptyList())
        chatHistoryStorage.clear()
        _error.value = null
    }

    private fun ChatMessage.toApiMessage(): ChatRequestMessage {
        val contents = mutableListOf<MessageContent>()
        if (content.isNotBlank()) {
            contents.add(MessageContent(type = "text", text = content))
        }
        image?.let {
            contents.add(
                MessageContent(
                    type = "input_image",
                    imageUrl = ImageUrl(url = it.dataUrl)
                )
            )
        }
        if (contents.isEmpty()) {
            contents.add(MessageContent(type = "text", text = ""))
        }
        return ChatRequestMessage(
            role = if (isUser) "user" else "assistant",
            content = contents
        )
    }

    private fun updateMessages(messages: List<ChatMessage>) {
        _messages.value = messages
        chatHistoryStorage.saveMessages(messages)
    }
}
