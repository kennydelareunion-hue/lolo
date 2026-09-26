package com.termux.devcenter.data.omniroute

import android.content.Context
import com.google.gson.JsonParser

enum class ModelTier { PRO, FREE, COMBO }

/** Modèle tel qu'annoncé par `/v1/models` d'OmniRoute, enrichi de sa catégorie. */
data class ModelInfo(
    val id: String,
    /** Fournisseur canonique (`owned_by`), ex. « kiro ». */
    val provider: String,
    /** Identifiant chez le fournisseur (`root`), ex. « claude-sonnet-4.5 ». */
    val upstreamId: String,
    val tier: ModelTier
) {
    val displayName: String get() = upstreamId.ifBlank { id.substringAfter('/') }
    val label: String get() = "$displayName · $provider"
}

/**
 * Liste des modèles gratuits, générée depuis le catalogue officiel d'OmniRoute
 * (`scripts/update_free_models.py` → assets/free_models.json).
 */
class FreeModelCatalog(private val freeByProvider: Map<String, Set<String>>) {

    fun isFree(provider: String, upstreamId: String, id: String): Boolean {
        if (id.endsWith(":free") || upstreamId.endsWith(":free")) return true
        val models = freeByProvider[provider] ?: return false
        return upstreamId in models || id.substringAfter('/') in models
    }

    companion object {
        val EMPTY = FreeModelCatalog(emptyMap())

        @Volatile
        private var cached: FreeModelCatalog? = null

        fun parse(json: String): FreeModelCatalog {
            val providers = JsonParser.parseString(json).asJsonObject.getAsJsonObject("providers")
            return FreeModelCatalog(providers.entrySet().associate { (p, models) -> p to models.asJsonObject.keySet() })
        }

        fun load(context: Context): FreeModelCatalog = cached ?: runCatching {
            context.assets.open("free_models.json").bufferedReader().use { parse(it.readText()) }
        }.getOrDefault(EMPTY).also { cached = it }
    }
}

object ModelSelection {
    /** Claude Sonnet 4.5 via Kiro, le modèle principal de l'utilisateur. */
    const val PREFERRED_MODEL = "kr/claude-sonnet-4.5"

    fun pickDefault(models: List<ModelInfo>): ModelInfo? =
        models.firstOrNull { it.id == PREFERRED_MODEL }
            ?: models.firstOrNull { it.provider == "kiro" && it.upstreamId == "claude-sonnet-4.5" }
            ?: models.firstOrNull { it.id.contains("claude", true) && it.id.contains("sonnet", true) }
            ?: models.firstOrNull { it.id.contains("claude", true) }
            ?: models.firstOrNull()

    /** Regroupe par fournisseur, fournisseurs et modèles triés alphabétiquement. */
    fun groupByProvider(models: List<ModelInfo>): List<Pair<String, List<ModelInfo>>> =
        models.groupBy { it.provider }.toSortedMap().map { (p, list) -> p to list.sortedBy { it.displayName } }

    fun search(models: List<ModelInfo>, query: String): List<ModelInfo> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return models
        return models.filter { m -> terms.all { t -> m.id.lowercase().contains(t) || m.provider.lowercase().contains(t) } }
    }
}
