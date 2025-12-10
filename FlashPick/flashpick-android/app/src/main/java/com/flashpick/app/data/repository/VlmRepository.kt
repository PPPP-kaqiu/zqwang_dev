package com.flashpick.app.data.repository

import android.util.Base64
import com.flashpick.app.data.api.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.lang.reflect.Type

class ContentPartDeserializer : JsonDeserializer<ContentPart> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ContentPart {
        val jsonObject = json.asJsonObject
        return when (jsonObject.get("type").asString) {
            "text" -> ContentPart.Text(text = jsonObject.get("text").asString)
            "image_url" -> {
                val urlData = jsonObject.get("image_url").asJsonObject
                ContentPart.ImageUrl(imageUrl = ImageUrlData(url = urlData.get("url").asString))
            }
            else -> throw IllegalArgumentException("Unknown type")
        }
    }
}

object VlmRepository {
    private const val BASE_URL = "https://ark.cn-beijing.volces.com/api/v3/" 
    private var API_KEY = "1337868c-2baf-4aa1-a684-c3a885ba802c"
    private var MODEL_ENDPOINT_ID = "doubao-seed-1-6-vision-250815"
    private var ASR_ENDPOINT_ID = "" 

    fun setApiKey(key: String) { API_KEY = key }
    fun setModelEndpoint(endpointId: String) { MODEL_ENDPOINT_ID = endpointId }
    fun setAsrEndpoint(endpointId: String) { ASR_ENDPOINT_ID = endpointId }
    fun hasApiKey(): Boolean = API_KEY.isNotEmpty() && MODEL_ENDPOINT_ID.isNotEmpty()

    private val gson: Gson = GsonBuilder().create()

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val api = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(OpenAiApi::class.java)

    data class AnalysisResult(
        val app_name: String,
        val title: String,
        val summary: String,
        val keywords: List<String>,
        val entities: List<String>
    )

    suspend fun analyzeImage(imageFiles: List<File>, audioTranscript: String? = null): AnalysisResult {
        if (!hasApiKey()) throw IllegalStateException("API Key not set")
        if (imageFiles.isEmpty()) throw IllegalArgumentException("No images provided")

        val contentParts = mutableListOf<ContentPart>()
        
        var prompt = """
            分析这一组手机应用截图（它们是连续视频的关键帧）。
            请综合画面内容，判断内容属性（是“娱乐休闲”还是“知识学术”），并以严格有效的 JSON 格式输出，包含以下键：
            - "app_name": 根据界面元素识别应用名称（如“抖音”、“微信”、“小红书”、“Arxiv”、“VSCode”等）。如果不确定，请使用“未知应用”。
            - "title": 一个简短、吸引人的标题（最多 15 个字），概括整个过程。
            - "summary": 内容总结。请根据内容类型采取不同策略：
                * 如果是【娱乐/生活/购物】类（如短视频、聊天、商品浏览）：保持简短（50字以内），描述用户在做什么。
                * 如果是【知识/学术/技术/工作】类（如论文、代码、长文章、图表）：请提供详细的深度解读（150字以内），提取核心论点、技术原理、代码功能或关键数据，帮助用户快速回忆知识点。
            - "keywords": 提取 3-5 个关键标签（如 "SwiftUI", "食谱", "旅行攻略"）。
            - "entities": 提取画面中的关键实体名称（如书名、电影名、地名、代码库名）。
        """.trimIndent()

        if (!audioTranscript.isNullOrEmpty()) {
            prompt += "\n此外，用户在录制时说了以下语音笔记，请将其作为**最重要的上下文**来生成标题和总结，这代表了用户的核心意图：\n\"$audioTranscript\""
        }

        contentParts.add(ContentPart.Text(text = prompt))

        imageFiles.forEach { file ->
            val base64Image = encodeImage(file)
            contentParts.add(ContentPart.ImageUrl(imageUrl = ImageUrlData("data:image/jpeg;base64,$base64Image")))
        }

        val request = ChatCompletionRequest(
            model = MODEL_ENDPOINT_ID,
            messages = listOf(
                Message(role = "user", content = contentParts)
            )
        )

        val response = api.chatCompletion("Bearer $API_KEY", request)
        val content = response.choices.first().message.content

        val jsonString = if (content is String) {
            val start = content.indexOf("{")
            val end = content.lastIndexOf("}")
            if (start != -1 && end != -1) {
                content.substring(start, end + 1)
            } else {
                content
            }
        } else {
            gson.toJson(content)
        }

        return try {
            gson.fromJson(jsonString, AnalysisResult::class.java)
        } catch (e: Exception) {
            AnalysisResult("未知应用", "AI 解析失败", content.toString(), emptyList(), emptyList())
        }
    }

    suspend fun chatWithText(text: String): String {
        if (!hasApiKey()) return "API Key Missing"

        val request = ChatCompletionRequest(
            model = MODEL_ENDPOINT_ID,
            messages = listOf(
                Message(role = "user", content = listOf(ContentPart.Text(text = text)))
            )
        )

        return try {
            val response = api.chatCompletion("Bearer $API_KEY", request)
            val content = response.choices.firstOrNull()?.message?.content
            if (content is String) content else gson.toJson(content)
        } catch (e: Exception) {
            e.printStackTrace()
            "AI 回答失败: ${e.message}"
        }
    }

    suspend fun chatWithImage(imageFiles: List<File>, question: String): String {
        if (!hasApiKey()) return "API Key Missing"
        if (imageFiles.isEmpty()) return "No Image"

        val contentParts = mutableListOf<ContentPart>()
        
        contentParts.add(ContentPart.Text(text = "用户问题：$question\n请结合图片回答。"))

        imageFiles.forEach { file ->
            val base64Image = encodeImage(file)
            contentParts.add(ContentPart.ImageUrl(imageUrl = ImageUrlData("data:image/jpeg;base64,$base64Image")))
        }

        val request = ChatCompletionRequest(
            model = MODEL_ENDPOINT_ID,
            messages = listOf(
                Message(role = "user", content = contentParts)
            )
        )

        return try {
            val response = api.chatCompletion("Bearer $API_KEY", request)
            val content = response.choices.firstOrNull()?.message?.content
            if (content is String) content else gson.toJson(content)
        } catch (e: Exception) {
            e.printStackTrace()
            "AI 回答失败: ${e.message}"
        }
    }

    fun transcribeAudio(audioFile: File): String? {
        // Deprecated
        return null
    }

    private fun encodeImage(file: File): String {
        return Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }
}
