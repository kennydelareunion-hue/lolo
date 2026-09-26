package com.termux.devcenter.data.omniroute

import android.content.Context
import com.google.gson.JsonParser

enum class ModelTier { PRO, FREE, COMBO, HOPLITE }

/** Modèle affiché dans le sélecteur : OmniRoute (`/v1/models`) ou Hoplite. */
data class ModelInfo(
    val id: String,
    /** Fournisseur canonique (`owned_by`), ex. « kiro », ou « hoplite ». */
    val provider: String,
    /** Identifiant chez le fournisseur (`root`), ex. « claude-sonnet-4.5 ». */
    val upstreamId: String,
    val tier: ModelTier,
    /** Nom lisible quand la source en fournit un (Hoplite). */
    val name: String? = null
) {
    val isHoplite: Boolean get() = tier == ModelTier.HOPLITE
    val displayName: String get() = name ?: upstreamId.ifBlank { id.substringAfter('/') }

    companion object {
        const val HOPLITE_PREFIX = "hoplite:"
        const val HOPLITE_PROVIDER = "hoplite"
    }
}

/**
 * Données générées depuis le dépôt OmniRoute (`scripts/update_free_models.py` → assets/free_models.json) :
 * modèles gratuits et fournisseurs « sans clé » qu'OmniRoute annonce même sans compte connecté.
 */
class FreeModelCatalog(
    private val freeByProvider: Map<String, Set<String>>,
    val noAuthProviders: Set<String> = emptySet()
) {

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
            val root = JsonParser.parseString(json).asJsonObject
            val providers = root.getAsJsonObject("providers")
            return FreeModelCatalog(
                providers.entrySet().associate { (p, models) -> p to models.asJsonObject.keySet() },
                root.getAsJsonArray("noAuthProviders")?.map { it.asString }?.toSet().orEmpty()
            )
        }

        fun load(context: Context): FreeModelCatalog = cached ?: runCatching {
            context.assets.open("free_models.json").bufferedReader().use { parse(it.readText()) }
        }.getOrDefault(EMPTY).also { cached = it }
    }
}

object ModelSelection {
    /** Claude Sonnet 4.5 via Kiro, le modèle principal de l'utilisateur. */
    const val PREFERRED_MODEL = "kr/claude-sonnet-4.5"
    const val PREFERRED_PROVIDER = "kiro"

    fun pickDefault(models: List<ModelInfo>): ModelInfo? =
        models.firstOrNull { it.id == PREFERRED_MODEL }
            ?: models.firstOrNull { it.provider == PREFERRED_PROVIDER && it.upstreamId == "claude-sonnet-4.5" }
            ?: models.firstOrNull { !it.isHoplite && it.id.contains("claude", true) && it.id.contains("sonnet", true) }
            ?: models.firstOrNull { !it.isHoplite && it.id.contains("claude", true) }
            ?: models.firstOrNull()

    /**
     * Fournisseurs affichés tant que l'utilisateur n'a rien choisi : Kiro s'il est présent,
     * sinon tous sauf les fournisseurs sans clé et les combos automatiques.
     */
    fun defaultProviders(models: List<ModelInfo>, catalog: FreeModelCatalog): Set<String> {
        val providers = models.filterNot { it.isHoplite }.map { it.provider }.toSet()
        if (PREFERRED_PROVIDER in providers) return setOf(PREFERRED_PROVIDER)
        return providers.filterNot { it in catalog.noAuthProviders || it == "combo" }.toSet()
    }

    /** Modèles OmniRoute des fournisseurs choisis + tous les modèles Hoplite. */
    fun filterEnabled(models: List<ModelInfo>, enabledProviders: Set<String>): List<ModelInfo> =
        models.filter { it.isHoplite || it.provider in enabledProviders }

    /** Fournisseurs OmniRoute présents, avec leur nombre de modèles, triés par nom. */
    fun providerCounts(models: List<ModelInfo>): List<Pair<String, Int>> =
        models.filterNot { it.isHoplite }.groupingBy { it.provider }.eachCount().toSortedMap().toList()

    /** Regroupe par fournisseur, fournisseurs et modèles triés alphabétiquement. */
    fun groupByProvider(models: List<ModelInfo>): List<Pair<String, List<ModelInfo>>> =
        models.groupBy { it.provider }.toSortedMap().map { (p, list) -> p to list.sortedBy { it.displayName } }

    fun search(models: List<ModelInfo>, query: String): List<ModelInfo> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return models
        return models.filter { m ->
            terms.all { t ->
                m.id.lowercase().contains(t) || m.provider.lowercase().contains(t) || m.displayName.lowercase().contains(t)
            }
        }
    }
}
