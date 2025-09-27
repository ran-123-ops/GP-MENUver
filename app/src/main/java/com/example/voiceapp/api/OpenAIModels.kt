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
    val content: List<MessageContent>
)

data class ChatResponseMessage(
    val role: String,
    val content: JsonElement
)

data class MessageContent(
    val type: String, // "text" | "input_image"
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    val url: String,
    val detail: String? = null
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
