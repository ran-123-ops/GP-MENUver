package com.example.voiceapp.ui.chat

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voiceapp.BuildConfig
import com.example.voiceapp.api.ChatRequestMessage
import com.example.voiceapp.api.ImageUrl
import com.example.voiceapp.api.MessageContent
import com.example.voiceapp.api.OpenAIClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

class ChatViewModel(
    private val chatHistoryStorage: ChatHistoryStorage,
    private val context: Context
) : ViewModel() {

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
        val prefs = context.getSharedPreferences("voiceapp_settings", Context.MODE_PRIVATE)
        val customApiKey = prefs.getString("custom_api_key", "")?.trim()
        
        // カスタムAPIキーがあればそれを使用、なければビルド設定値
        val apiKey = if (!customApiKey.isNullOrEmpty()) {
            customApiKey
        } else {
            BuildConfig.OPENAI_API_KEY
        }
        
        val baseUrl = getBaseUrl()

        if (apiKey.isNotEmpty() && apiKey != "your_openai_api_key_here") {
            openAIClient = OpenAIClient(apiKey, baseUrl)
            _isApiKeyConfigured.value = true
        } else {
            _isApiKeyConfigured.value = false
            _error.value = "local.propertiesファイルまたはデバッグ画面でAPIキーを設定してください"
        }
    }

    private fun getBaseUrl(): String {
        val prefs = context.getSharedPreferences("voiceapp_settings", Context.MODE_PRIVATE)
        val customIp = prefs.getString("custom_server_ip", "")?.trim()
        val customPort = prefs.getString("custom_server_port", "")?.trim()

        // カスタムサーバーが設定されている場合
        if (!customIp.isNullOrEmpty()) {
            val port = if (!customPort.isNullOrEmpty()) ":$customPort" else ""
            val protocol = if (customPort == "443") "https" else "http"
            // URLが既に/v1/で終わっていない場合のみ追加
            val baseUrl = "$protocol://$customIp$port"
            return if (baseUrl.endsWith("/v1") || baseUrl.endsWith("/v1/")) {
                if (baseUrl.endsWith("/v1")) "$baseUrl/" else baseUrl
            } else {
                "$baseUrl/v1/"
            }
        }

        // デフォルトのOpenAI URL
        return BuildConfig.OPENAI_BASE_URL
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

                val placeholderIndex = addAssistantPlaceholder()
                val builder = StringBuilder()

                val streamResult = client.streamMessage(apiMessages) { delta ->
                    builder.append(delta)
                    withContext(Dispatchers.Main) {
                        updateAssistantMessageContent(placeholderIndex, builder.toString(), persist = false)
                    }
                }

                streamResult.fold(
                    onSuccess = {
                        withContext(Dispatchers.Main) {
                            updateAssistantMessageContent(placeholderIndex, builder.toString(), persist = true)
                        }
                    },
                    onFailure = { exception ->
                        withContext(Dispatchers.Main) {
                            removeAssistantMessage(placeholderIndex)
                            _error.value = "エラー: ${exception.message}"
                        }
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

    private fun addAssistantPlaceholder(): Int {
        val list = _messages.value?.toMutableList() ?: mutableListOf()
        val placeholder = ChatMessage(content = "", isUser = false)
        list.add(placeholder)
        _messages.value = list
        return list.lastIndex
    }

    private fun updateAssistantMessageContent(messageIndex: Int, content: String, persist: Boolean) {
        val current = _messages.value?.toMutableList() ?: mutableListOf()
        if (persist && content.isBlank()) {
            if (messageIndex in current.indices) {
                current.removeAt(messageIndex)
                updateMessages(current)
            }
            return
        }
        if (messageIndex !in current.indices) {
            if (persist) {
                updateMessages(current + ChatMessage(content, false))
            } else {
                _messages.value = current + ChatMessage(content, false)
            }
            return
        }

        val existing = current[messageIndex]
        current[messageIndex] = existing.copy(content = content)

        if (persist) {
            updateMessages(current)
        } else {
            _messages.value = current
        }
    }

    private fun removeAssistantMessage(messageIndex: Int) {
        val current = _messages.value?.toMutableList() ?: return
        if (messageIndex !in current.indices) return
        current.removeAt(messageIndex)
        _messages.value = current
    }
}
