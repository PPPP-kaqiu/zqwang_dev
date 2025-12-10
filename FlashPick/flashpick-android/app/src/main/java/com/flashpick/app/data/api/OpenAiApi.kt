package com.flashpick.app.data.api

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

// --- Request Models ---

data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    @SerializedName("max_tokens") val maxTokens: Int = 500
)

data class Message(
    val role: String,
    val content: List<ContentPart>
)

sealed class ContentPart {
    data class Text(val type: String = "text", val text: String) : ContentPart()
    data class ImageUrl(val type: String = "image_url", @SerializedName("image_url") val imageUrl: ImageUrlData) : ContentPart()
}

data class ImageUrlData(
    val url: String // data:image/jpeg;base64,...
)

// --- Response Models ---

data class ChatCompletionResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: MessageResponse
)

data class MessageResponse(
    val content: String
)

data class TranscriptionResponse(
    val text: String
)

interface OpenAiApi {
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse

    @Multipart
    @POST("audio/transcriptions")
    suspend fun transcribeAudio(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody
    ): TranscriptionResponse
}
