package com.kazumaproject.markdownhelperkeyboard.converter.english.phrase

/** Keeps catalog entries searchable without using an ordinary word to guess a name. */
internal class EnglishCompletionPolicy(
    private val prefix: String,
    private val grammarRoles: GrammarRoles,
) {
    private val prefixRoles by lazy(LazyThreadSafetyMode.NONE) {
        if (prefix.length >= 2 && prefix.all { it in 'a'..'z' }) grammarRoles.lookup(prefix, prefix)
        else emptySet()
    }
    private val isOrdinaryWord by lazy(LazyThreadSafetyMode.NONE) { prefixRoles.any(::isOrdinaryWordRole) }

    fun allowsWord(reading: String, surface: String, cost: Int): Boolean =
        reading == prefix || cost < CATALOG_COST || !isOrdinaryWord ||
            (surface.none { it.isDigit() || it in "./_+:" } && !isCatalogName(surface))

    // Specific terms can start a registered phrase such as Android Studio.
    // For an everyday word, wait for more input before suggesting catalog names.
    fun allowsPhrase(candidate: DictionaryCandidate): Boolean =
        candidate.cost < CATALOG_COST || !isOrdinaryWord ||
            ("domain_daily" !in prefixRoles && candidate.reading.startsWith("$prefix ")) ||
            !isCatalogName(candidate.surface)

    private fun isCatalogName(surface: String): Boolean {
        val key = lookupKey(surface)
        val roles = grammarRoles.lookup(surface, key)
        return roles.any { it == "noun_proper" || it.startsWith("name_") || it.startsWith("identifier_") } &&
            (surface.any(Char::isUpperCase) || roles.none(::isOrdinaryWordRole))
    }

    private companion object {
        // Catalog names in the bundled dictionary generally start at this cost.
        // Keep explicitly prioritized spellings such as OpenAI and AutoCAD.
        const val CATALOG_COST = 6000

        fun isOrdinaryWordRole(role: String): Boolean =
            isFunctionWordRole(role) || role in ordinaryRoles ||
                role.startsWith("noun_count_") || role.startsWith("verb_")

        val ordinaryRoles = setOf(
            "noun_uncountable", "adjective", "adverb", "interjection", "contraction",
        )
    }
}
