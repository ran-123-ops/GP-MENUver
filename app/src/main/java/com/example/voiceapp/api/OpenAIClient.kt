package com.example.voiceapp.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class OpenAIClient(private val apiKey: String, private val baseUrl: String) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val service = retrofit.create(OpenAIService::class.java)

    suspend fun sendMessage(messages: List<Message>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = ChatCompletionRequest(
                model = "gpt-3.5-turbo",
                messages = messages,
                maxTokens = 1000,
                temperature = 0.7
            )

            val response = service.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (response.isSuccessful) {
                val chatResponse = response.body()
                val content = chatResponse?.choices?.firstOrNull()?.message?.content
                if (content != null) {
                    Result.success(content)
                } else {
                    Result.failure(Exception("レスポンスにコンテンツが含まれていません"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception("API エラー: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
