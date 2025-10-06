package com.example.voiceapp.ui.chat

import android.content.Context
import android.util.Log
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

    companion object {
        private const val TAG = "ChatViewModel"
    }

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

        // OpenAI公式APIのエンドポイントを使用
        val baseUrl = "https://api.openai.com/v1/"

        if (apiKey.isNotEmpty() && apiKey != "your_openai_api_key_here") {
            openAIClient = OpenAIClient(apiKey, baseUrl)
            _isApiKeyConfigured.value = true
        } else {
            _isApiKeyConfigured.value = false
            _error.value = "local.propertiesファイルまたはデバッグ画面でAPIキーを設定してください"
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
        
        // Web Search設定を取得
        val isWebSearchEnabled = com.example.voiceapp.ui.settings.SettingsFragment.isWebSearchEnabled(context)
        
        // Web Searchが有効な場合はGPT-4o Search Preview (gpt-4o-2024-11-20) を使用
        // 無効な場合は通常のgpt-4o-miniを使用
        val selectedModel = if (isWebSearchEnabled) "gpt-4o-2024-11-20" else "gpt-4o-mini"
        
        Log.d(TAG, "メッセージ送信: model=$selectedModel, webSearch=$isWebSearchEnabled")

        viewModelScope.launch {
            try {
                // OpenAI APIに送信するメッセージリストを作成
                val apiMessages = mutableListOf<ChatRequestMessage>()
                if (!systemPrompt.isNullOrBlank()) {
                    apiMessages.add(
                        ChatRequestMessage(
                            role = "system",
                            content = systemPrompt
                        )
                    )
                }
                apiMessages.addAll(currentMessages.map { it.toApiMessage() })

                val placeholderIndex = addAssistantPlaceholder()
                val builder = StringBuilder()

                Log.d(TAG, "ストリーミング開始...")
                
                val streamResult = client.streamMessage(
                    messages = apiMessages,
                    model = selectedModel
                ) { delta ->
                    builder.append(delta)
                    withContext(Dispatchers.Main) {
                        updateAssistantMessageContent(placeholderIndex, builder.toString(), persist = false)
                    }
                }

                streamResult.fold(
                    onSuccess = {
                        Log.d(TAG, "ストリーミング成功: 最終テキスト長=${builder.length}")
                        withContext(Dispatchers.Main) {
                            updateAssistantMessageContent(placeholderIndex, builder.toString(), persist = true)
                        }
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "ストリーミング失敗", exception)
                        withContext(Dispatchers.Main) {
                            removeAssistantMessage(placeholderIndex)
                            _error.value = "エラー: ${exception.message}"
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "予期しないエラー", e)
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
        // 画像がある場合はマルチモーダル形式、なければテキストのみ
        return if (image != null) {
            val contents = mutableListOf<MessageContent>()
            if (content.isNotBlank()) {
                contents.add(MessageContent(type = "text", text = content))
            }
            contents.add(
                MessageContent(
                    type = "image_url",
                    imageUrl = ImageUrl(url = image.dataUrl, detail = "auto")
                )
            )
            ChatRequestMessage(
                role = if (isUser) "user" else "assistant",
                content = contents
            )
        } else {
            // テキストのみの場合は文字列として送信
            ChatRequestMessage(
                role = if (isUser) "user" else "assistant",
                content = content.ifBlank { "" }
            )
        }
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
