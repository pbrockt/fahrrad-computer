package com.pbrockt.fahrradcomputer.coach

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonSchemaLocalValidation
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaFallbacksParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import com.anthropic.models.beta.messages.StructuredMessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Erstellt den Trainingsplan ueber die Claude-API.
 *
 * - Modell: Claude Opus 5.5 mit Aufwand "medium" - ein Wochenplan braucht Sorgfalt,
 *   aber keine tiefe Recherche; das haelt die Kosten pro Plan im Cent-Bereich.
 * - Strukturierte Ausgabe: Die Antwort ist garantiert JSON in der Form von [TrainingPlan].
 * - Fallbacks "default": Lehnt der Sicherheitsfilter eine Anfrage faelschlich ab, versucht
 *   die API es automatisch mit einem passenden anderen Modell, statt einfach abzubrechen.
 */
object Coach {

    const val MODEL = "claude-opus-5-5"

    /** Preise je 1 Mio. Token fuer [MODEL] (Stand 2026-10), fuer die Kostenanzeige. */
    private const val USD_PER_M_INPUT = 4.0
    private const val USD_PER_M_OUTPUT = 20.0

    sealed interface Result {
        data class Ok(val plan: TrainingPlan, val costUsd: Double) : Result
        data class Error(val message: String) : Result
    }

    /** Request bauen - getrennt, damit Tests das Schema ohne Netzwerk pruefen koennen. */
    fun buildRequest(system: String, user: String): StructuredMessageCreateParams<TrainingPlan> =
        MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16_000L)
            .addBeta("server-side-fallback-2026-07-01")
            .fallbacks(BetaFallbacksParam.ofDefault())
            .system(system)
            .addUserMessage(user)
            .outputConfig(TrainingPlan::class.java, BetaOutputConfig.Effort.MEDIUM, JsonSchemaLocalValidation.YES)
            .build()

    suspend fun createPlan(apiKey: String, system: String, user: String): Result = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        try {
            val response = client.beta().messages().create(buildRequest(system, user))
            if (response.stopReason().orElse(null) == BetaStopReason.REFUSAL) {
                return@withContext Result.Error("Claude hat die Anfrage abgelehnt. Formuliere Ziel oder Hinweise bitte anders.")
            }
            val plan = response.content().firstNotNullOfOrNull { it.text().orElse(null)?.text() }
                ?: return@withContext Result.Error("Die Antwort enthielt keinen Plan.")
            val usage = response.usage()
            val cost = usage.inputTokens() * USD_PER_M_INPUT / 1e6 + usage.outputTokens() * USD_PER_M_OUTPUT / 1e6
            Result.Ok(plan, cost)
        } catch (e: Exception) {
            Result.Error(explain(e))
        } finally {
            client.close()
        }
    }

    /** Prueft den Key, ohne Kosten zu verursachen: Modell-Infos abrufen ist gratis. */
    suspend fun checkKey(apiKey: String): String? = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        try {
            client.models().retrieve(MODEL)
            null
        } catch (e: Exception) {
            explain(e)
        } finally {
            client.close()
        }
    }

    private fun explain(e: Exception): String = when (e) {
        is UnauthorizedException -> "Der API-Key ist ungültig. Bitte prüfen oder einen neuen anlegen."
        is PermissionDeniedException -> "Der API-Key hat keine Berechtigung für dieses Modell."
        is RateLimitException -> "Zu viele Anfragen gerade – bitte in einer Minute nochmal."
        // Ein leeres Guthaben meldet die API als Bad Request mit Hinweis im Text.
        is BadRequestException ->
            if (e.message.orEmpty().contains("credit", ignoreCase = true)) {
                "Dein Guthaben bei Anthropic ist aufgebraucht. In der Konsole unter Billing aufladen."
            } else {
                "Anfrage abgelehnt: ${e.message}"
            }
        is AnthropicServiceException -> "Fehler bei Anthropic (${e.statusCode()}): ${e.message}"
        is AnthropicIoException -> "Keine Verbindung zu Anthropic – Internet prüfen."
        else -> "Unerwarteter Fehler: ${e.message ?: e.javaClass.simpleName}"
    }
}
