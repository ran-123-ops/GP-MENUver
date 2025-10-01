package com.example.voiceapp.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
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

    suspend fun sendMessage(messages: List<ChatRequestMessage>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = ChatCompletionRequest(messages = messages)

            val response = service.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (response.isSuccessful) {
                val chatResponse = response.body()
                val contentElement = chatResponse
                    ?.choices
                    ?.firstOrNull()
                    ?.message
                    ?.content

                val content = extractContentText(contentElement)

                if (!content.isNullOrBlank()) {
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

    suspend fun streamMessage(
        messages: List<ChatRequestMessage>,
        model: String = "gpt-4o-mini",
        onDelta: suspend (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = ChatCompletionRequest(
                model = model,
                messages = messages,
                stream = true
            )
            val response = service.createChatCompletionStream(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                return@withContext Result.failure(Exception("API ストリームエラー: ${response.code()} - $errorBody"))
            }

            val body: ResponseBody = response.body()
                ?: return@withContext Result.failure(Exception("ストリームレスポンスの取得に失敗しました"))

            body.use { responseBody ->
                val source = responseBody.source()
                while (true) {
                    if (source.exhausted()) break
                    val line = source.readUtf8Line() ?: continue
                    if (line.isBlank()) continue
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    if (payload.isBlank()) continue

                    val jsonElement = runCatching { JsonParser.parseString(payload) }.getOrNull()
                    val jsonObject = jsonElement?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    val choices = jsonObject.getAsJsonArray("choices") ?: continue
                    if (choices.size() == 0) continue
                    val choiceObject = choices[0].takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    val delta = choiceObject.getAsJsonObject("delta") ?: continue
                    val contentElement = delta.get("content") ?: continue

                    val text = extractContentText(contentElement)
                    if (!text.isNullOrBlank()) {
                        onDelta(text)
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractContentText(content: JsonElement?): String? {
        if (content == null || content.isJsonNull) return null

        return when {
            content.isJsonPrimitive && content.asJsonPrimitive.isString ->
                content.asString.takeIf { it.isNotBlank() }

            content.isJsonArray -> {
                content.asJsonArray
                    .mapNotNull { extractContentText(it) }
                    .filter { it.isNotBlank() }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = "\n")
            }

            content.isJsonObject -> extractTextFromContentObject(content.asJsonObject)

            else -> null
        }
    }

    private fun extractTextFromContentObject(obj: JsonObject): String? {
        val type = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString
        val textValue = obj.get("text")?.takeIf { it.isJsonPrimitive }?.asString
        if (!textValue.isNullOrBlank()) {
            return textValue
        }

        val valueField = obj.get("value")?.takeIf { it.isJsonPrimitive }?.asString
        if (!valueField.isNullOrBlank()) {
            return valueField
        }

        val nestedContent = obj.get("content")
        return if (nestedContent != null && !nestedContent.isJsonNull) {
            extractContentText(nestedContent)
        } else {
            when (type) {
                "output_text" -> obj.get("output_text")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
                else -> null
            }
        }
    }
}
