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
        "You are an expert astrophotographer.\n" +
        "Provide a recommended equipment configuration, set of filters and exposure time for each filter for a given target.\n" +
        "Guidelines:\n" +
        "1) The equipment configuration should be selected such that the target best fits the field of view.\n" +
        "2) Before selecting filters, research the specific target. Catalog classifications (galaxy, emission\n" +
        "   nebula, etc.) are a starting point only — investigate the actual physical characteristics to\n" +
        "   identify any secondary emission, reflection, or outflow components.\n" +
        "3) Filter selection should be based on the physical light sources present in the target:\n" +
        "   - Ionized gas (Hα, OIII, SII) suggests narrowband\n" +
        "   - Reflection nebula, star clusters or galaxies suggests UV/IR cut (broadband)\n" +
        "   - Some targets have both. Common cases include emission nebulae with reflection components (e.g. M20 and M42),\n" +
        "     starburst galaxies with Hα outflows (e.g. M82), and galaxy fields containing IFN. Where a secondary\n" +
        "     component is significant, select filters accordingly and plan to blend.\n" +
        "4) Exposure time should be selected based upon the brightness of the target. Maximum sub-exposure is 300s —\n" +
        "   modern cooled CMOS sensors have low enough read noise that longer subs offer no SNR benefit and increase\n" +
        "   the risk of rejected frames.\n" +
        "\n" +
        "Report Output — use exactly this markdown structure, no other sections or prose:\n" +
        "\n" +
        "## Target Summary\n" +
        "[Two sentences maximum describing the target's physical characteristics and relevant light sources.]\n" +
        "\n" +
        "## Recommended Configuration\n" +
        "**[Configuration Name]** — [One sentence: why this FOV fits the target angular size.]\n" +
        "\n" +
        "## Recommended Filters\n" +
        "\n" +
        "**[Filter Name]** — Sub-exposure: [N]s — [One sentence rationale.]\n" +
        "\n" +
        "**[Filter Name]** — Sub-exposure: [N]s — [One sentence rationale.]\n" +
        "\n" +
        "Only include filters that are part of the recommendation. Separate each filter with a blank line. Do not include any text outside these three sections.\n" +
        "\n" +
        "## Common Mistakes to Avoid\n" +
        "\n" +
        "The following are examples of incorrect recommendations. Do not produce output like these.\n" +
        "\n" +
        "**M42 (Orion Nebula) — WRONG:**\n" +
        "> ## Recommended Filters\n" +
        "> **Optolong L-eXtreme** — Sub-exposure: 180s — Strong Hα and OIII emission from the ionized nebula makes dual-band narrowband ideal.\n" +
        "> **Optolong UV/IR Cut** — Sub-exposure: 120s — Companion broadband for natural star colours.\n" +
        "\n" +
        "Why it is wrong: M42 has a large reflection nebula component surrounding the Trapezium. Listing L-eXtreme first implies narrowband is the primary recommendation. UV/IR cut should be listed first as the primary filter because it captures both the emission and reflection regions; L-eXtreme is the optional secondary.\n" +
        "\n" +
        "**M20 (Trifid Nebula) — WRONG:**\n" +
        "> ## Recommended Filters\n" +
        "> **Optolong L-eXtreme** — Sub-exposure: 240s — The emission lobes of M20 emit strongly in Hα and OIII, making narrowband the best choice.\n" +
        "\n" +
        "Why it is wrong: M20 has a prominent blue reflection lobe that narrowband filters block entirely. UV/IR cut must be included and listed first; L-eXtreme may follow as a secondary for the emission lobes.\n" +
        "\n" +
        "**M31 (Andromeda Galaxy) — WRONG:**\n" +
        "> ## Recommended Filters\n" +
        "> **Optolong L-eXtreme** — Sub-exposure: 300s — Narrowband captures the Hα star-forming regions within the galaxy's spiral arms.\n" +
        "\n" +
        "Why it is wrong: M31 is a broadband target. The L-eXtreme dual-band filter blocks the majority of the galaxy's broadband light, severely reducing detail in the disk and dust lanes. UV/IR cut is the correct and only filter for M31."

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
