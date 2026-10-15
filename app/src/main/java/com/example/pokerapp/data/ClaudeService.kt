package com.example.pokerapp.data

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thrown when a Claude call can't produce usable text; [message] is user-facing. */
class ClaudeUnavailable(message: String) : Exception(message)

/**
 * Thin wrapper over the Anthropic Java SDK. Every caller has an offline fallback, so this
 * only has to say "here's the text" or "why not" — it never decides what to do on failure.
 */
class ClaudeService(private val settings: SettingsRepository) {
    private var cached: Pair<String, AnthropicClient>? = null

    @Synchronized
    private fun client(apiKey: String): AnthropicClient {
        cached?.let { (k, c) -> if (k == apiKey) return c }
        cached?.second?.close()
        return AnthropicOkHttpClient.builder().apiKey(apiKey).build().also { cached = apiKey to it }
    }

    /**
     * One Messages API call. [history] is prior (user, assistant) turns for follow-ups.
     * Throws [ClaudeUnavailable] when no key is set, the request fails, or the model declines.
     */
    suspend fun complete(
        system: String,
        user: String,
        history: List<Pair<String, String>> = emptyList(),
        maxTokens: Long = 16_000,
        effort: OutputConfig.Effort = OutputConfig.Effort.MEDIUM,
    ): String = withContext(Dispatchers.IO) {
        val s = settings.current()
        if (!s.claudeEnabled) throw ClaudeUnavailable("No API key set")

        val params = MessageCreateParams.builder()
            .model(s.model)
            .maxTokens(maxTokens)
            .system(system)
            .outputConfig(OutputConfig.builder().effort(effort).build())
            .apply {
                for ((q, a) in history) { addUserMessage(q); addAssistantMessage(a) }
                addUserMessage(user)
            }
            .build()

        val msg = try {
            client(s.apiKey).messages().create(params)
        } catch (e: UnauthorizedException) {
            throw ClaudeUnavailable("API key was rejected")
        } catch (e: PermissionDeniedException) {
            throw ClaudeUnavailable("API key lacks access to ${s.model}")
        } catch (e: NotFoundException) {
            throw ClaudeUnavailable("Model ${s.model} not found")
        } catch (e: RateLimitException) {
            throw ClaudeUnavailable("Rate limited — try again shortly")
        } catch (e: AnthropicServiceException) {
            throw ClaudeUnavailable("Claude API error ${e.statusCode()}")
        } catch (e: AnthropicIoException) {
            throw ClaudeUnavailable("No connection to Claude")
        }

        if (msg.stopReason().orElse(null) == StopReason.REFUSAL) throw ClaudeUnavailable("Claude declined this request")
        val text = msg.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("").trim()
        if (text.isEmpty()) throw ClaudeUnavailable("Claude returned no text")
        text
    }
}
