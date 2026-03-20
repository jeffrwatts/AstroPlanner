package com.islandskiesastro.astroplanner

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class AnthropicMessage(val role: String, val content: String)

@Serializable
private data class AnthropicRequest(
    val model: String,
    val max_tokens: Int,
    val system: String,
    val messages: List<AnthropicMessage>
)

@Serializable
private data class AnthropicContent(val type: String, val text: String = "")

@Serializable
private data class AnthropicResponse(val content: List<AnthropicContent>)

object AnthropicService {
    private const val SYSTEM_PROMPT =
        "You are an expert at capturing deep sky astrophotography images. " +
        "You will be given a target object, a list of telescope/camera configurations with the field of view of each, " +
        "available light filters, and basic site information including altitude and Bortle scale. " +
        "Research the object. Based on its apparent size, recommend the most suitable configuration from the provided list. " +
        "Based on the type and intensity of light emitted by the target, recommend a filter and sub-exposure time " +
        "from the available filters. Always include a specific sub-exposure time in seconds for every filter recommended. " +
        "Whenever you recommend a narrowband filter, always also recommend a UV/IR cut filter as a companion for " +
        "capturing natural star colours to blend with the narrowband data. " +
        "Only recommend equipment and filters from the lists provided. " +
        "Be concise — short sentences, no filler. Aim for 1–3 sentences per section, but go longer only if genuinely necessary."

    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000   // 2 min — generation can be slow
            connectTimeoutMillis = 15_000
            socketTimeoutMillis  = 120_000
        }
    }

    enum class Model(val id: String, val label: String) {
        HAIKU("claude-haiku-4-5-20251001", "Haiku (fast)"),
        SONNET("claude-sonnet-4-6", "Sonnet (better)"),
        OPUS("claude-opus-4-6", "Opus (best)")
    }

    suspend fun generatePlan(apiKey: String, prompt: String, model: Model = Model.HAIKU): Result<String> {
        return try {
            val response: AnthropicResponse = client.post("https://api.anthropic.com/v1/messages") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(
                    AnthropicRequest(
                        model      = model.id,
                        max_tokens = 1024,
                        system     = SYSTEM_PROMPT,
                        messages   = listOf(AnthropicMessage(role = "user", content = prompt))
                    )
                )
            }.body()
            val text = response.content.firstOrNull { it.type == "text" }?.text
                ?: return Result.failure(Exception("No text content in response"))
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
