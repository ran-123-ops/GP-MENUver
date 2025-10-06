package com.example.voiceapp.api

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

// OpenAI APIのリクエスト・レスポンス用データクラス
data class ChatCompletionRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<ChatRequestMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 1000,
    val temperature: Double = 0.7,
    val stream: Boolean = false
)

data class ChatRequestMessage(
    val role: String, // "user", "assistant", "system"
    val content: Any // String または List<MessageContent>
)

data class ChatResponseMessage(
    val role: String,
    val content: JsonElement
)

data class MessageContent(
    val type: String, // "text" | "image_url"
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    val url: String,
    val detail: String? = "auto"
)

data class ChatCompletionResponse(
    val id: String,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage?
)

data class Choice(
    val index: Int,
    val message: ChatResponseMessage,
    @SerializedName("finish_reason") val finishReason: String?
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)

data class ErrorResponse(
    val error: ErrorDetail
)

data class ErrorDetail(
    val message: String,
    val type: String,
    val code: String?
)

// TTS API用データクラス
data class TTSRequest(
    val model: String = "tts-1",
    val input: String,
    val voice: String = "alloy",
    val speed: Double = 1.0,
    @SerializedName("response_format") val responseFormat: String = "mp3"
)
