package com.kazumaproject.markdownhelperkeyboard.converter.english.phrase

/** Keeps catalog entries searchable without using an ordinary word to guess a name. */
internal class EnglishCompletionPolicy(
    private val prefix: String,
    private val grammarRoles: GrammarRoles,
) {
    private val plainPrefix = prefix.all { it in 'a'..'z' }
    private val prefixRoles by lazy(LazyThreadSafetyMode.NONE) {
        if (plainPrefix) grammarRoles.lookup(prefix, prefix) else emptySet()
    }
    private val isOrdinaryWord by lazy(LazyThreadSafetyMode.NONE) { prefixRoles.any(::isOrdinaryWordRole) }

    fun allowsWord(reading: String, surface: String, cost: Int): Boolean {
        if (!plainPrefix) return true
        val symbol = surface.indexOfFirst(::isIdentifierCharacter)
        if (symbol >= 0) {
            // A low dictionary cost does not supply the missing identifier syntax.
            // Keep compact aliases once the typed letters reach past the separator.
            return !isOrdinaryWord && symbol < prefix.length &&
                (reading == prefix || surface.regionMatches(0, prefix, 0, symbol, ignoreCase = true))
        }
        return reading == prefix || !isOrdinaryWord ||
            !isCatalogName(surface, personalOnly = cost < CATALOG_COST)
    }

    fun completionCost(reading: String, surface: String, cost: Int): Int {
        if (cost >= CATALOG_COST || !plainPrefix || reading == prefix) return cost
        // Wait until the first component is typed before applying the boost
        // for stylized spellings such as GitHub or OpenAI.
        for (index in 1 until surface.length) {
            if (surface[index].isUpperCase() && surface[index - 1].isLowerCase()) {
                return if (index > prefix.length) CATALOG_COST else cost
            }
        }
        return cost
    }

    // Specific terms can start a registered phrase such as Android Studio.
    // For an everyday word, wait for more input before suggesting catalog names.
    fun allowsPhrase(candidate: DictionaryCandidate): Boolean =
        candidate.cost < CATALOG_COST || !isOrdinaryWord ||
            ("domain_daily" !in prefixRoles && candidate.reading.startsWith("$prefix ")) ||
            !isCatalogName(candidate.surface)

    private fun isCatalogName(surface: String, personalOnly: Boolean = false): Boolean {
        val capitalized = surface.any(Char::isUpperCase)
        if (personalOnly && !capitalized) return false
        val key = lookupKey(surface)
        val roles = grammarRoles.lookup(surface, key)
        if (personalOnly) {
            // The base word list also gives some personal names lower costs
            // than the catalog default. A month can have a given-name tag too.
            return "domain_daily" !in roles && roles.any { it in personalNameTopics }
        }
        return roles.any { it == "noun_proper" || it.startsWith("name_") || it.startsWith("identifier_") } &&
            (capitalized || roles.none(::isOrdinaryWordRole))
    }

    private companion object {
        // Catalog names in the bundled dictionary generally start at this cost.
        // Keep explicitly prioritized spellings such as OpenAI and AutoCAD.
        const val CATALOG_COST = 6000

        fun isIdentifierCharacter(character: Char): Boolean =
            !character.isLetter() && !character.isWhitespace() && character !in "'’-" &&
                Character.getType(character) != Character.NON_SPACING_MARK.toInt()

        fun isOrdinaryWordRole(role: String): Boolean =
            isFunctionWordRole(role) || role in ordinaryRoles ||
                role.startsWith("noun_count_") || role.startsWith("verb_")

        val personalNameTopics = setOf("topic_names_given", "topic_names_middle", "topic_names_surname")

        val ordinaryRoles = setOf(
            "noun_uncountable", "adjective", "adverb", "interjection", "contraction",
        )
    }
}
