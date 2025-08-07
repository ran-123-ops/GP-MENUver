package com.example.voiceapp.api

import com.google.gson.annotations.SerializedName

// OpenAI APIのリクエスト・レスポンス用データクラス
data class ChatCompletionRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<Message>,
    @SerializedName("max_tokens") val maxTokens: Int = 1000,
    val temperature: Double = 0.7,
    val stream: Boolean = false
)

data class Message(
    val role: String, // "user", "assistant", "system"
    val content: String
)

data class ChatCompletionResponse(
    val id: String,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage?
)

data class Choice(
    val index: Int,
    val message: Message,
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
