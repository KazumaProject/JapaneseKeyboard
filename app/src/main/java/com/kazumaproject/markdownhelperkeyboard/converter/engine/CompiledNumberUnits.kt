package com.kazumaproject.markdownhelperkeyboard.converter.engine

/** Built once per settings snapshot. Input lookup visits matching suffixes, not every rule. */
internal class CompiledNumberUnits(units: List<CustomNumberUnit>) {
    companion object { val EMPTY = CompiledNumberUnits(emptyList()) }
    private class UnitRules(val ordinal: Int, val unit: CustomNumberUnit) {
        val specialValues = unit.specialReadings.map { it.value }.distinct().sorted().toLongArray()
        val blockedBases = unit.specialReadings.filter { it.mode == SpecialNumberReadingMode.COMPOSE }
            .map { it.baseReading }.distinct()
    }
    private data class Rule(val owner: UnitRules, val ordinal: Int, val special: SpecialNumberReading)
    private class Matches {
        val exact = mutableListOf<Rule>()
        val composed = mutableListOf<Rule>()
        var ordinary = false
    }

    val startCharacters: Set<Char>
    val endCharacters: Set<Char>
    val allValid: Boolean
    private val enabled: List<UnitRules>
    private val byId: Map<String, List<UnitRules>>
    private val specialReadings: SuffixIndex<Rule>
    private val ordinary: SuffixIndex<UnitRules>

    init {
        // Invalid direct-constructor units keep the old behavior: no generated candidates.
        val validUnits = units.filter { it.isValid() }
        allValid = validUnits.size == units.size
        enabled = validUnits.withIndex().filter { it.value.enabled }.map { UnitRules(it.index, it.value) }
        byId = enabled.groupBy { it.unit.id }
        startCharacters = enabled.flatMap { it.unit.specialReadings.map { rule -> rule.reading.first() } }.toSet()
        endCharacters = enabled.flatMap { listOf(it.unit.reading.last()) + it.unit.specialReadings.map { rule -> rule.reading.last() } }.toSet()
        val rules = enabled.flatMap { owner -> owner.unit.specialReadings.mapIndexed { i, s -> Rule(owner, i, s) } }
        specialReadings = SuffixIndex(rules.map { it.special.reading to it })
        ordinary = SuffixIndex(enabled.map { it.unit.reading to it })
    }

    fun permits(unit: CustomNumberUnit): Boolean = byId[unit.id]?.any { it.unit == unit } == true

    fun match(input: String): List<Pair<CustomNumberUnit, List<Long>>> {
        if (enabled.isEmpty()) return emptyList()
        val matches = java.util.TreeMap<Int, Pair<UnitRules, Matches>>()
        fun bucket(owner: UnitRules): Matches = matches.getOrPut(owner.ordinal) { owner to Matches() }.second
        specialReadings.forEachMatch(input) { rule ->
            if (rule.special.reading.length == input.length) bucket(rule.owner).exact.add(rule)
            else if (rule.special.mode == SpecialNumberReadingMode.COMPOSE) bucket(rule.owner).composed.add(rule)
        }
        ordinary.forEachMatch(input) { bucket(it).ordinary = true }
        return matches.values.mapNotNull { (owner, match) ->
            val values = when {
                match.exact.isNotEmpty() -> match.exact.map { it.special.value }
                else -> match.composed.sortedBy { it.ordinal }.mapNotNull { rule ->
                    val special = rule.special
                    // Validation and parsing of baseReading have already happened at construction.
                    ValidatedNumber.parseReading(input.dropLast(special.reading.length) + special.baseReading)?.value
                }.ifEmpty {
                    if (!match.ordinary) emptyList() else {
                        val stem = input.dropLast(owner.unit.reading.length)
                        if (owner.blockedBases.any(stem::endsWith)) emptyList() else
                            listOfNotNull(ValidatedNumber.parseReading(stem)?.value?.takeIf { owner.specialValues.binarySearch(it) < 0 })
                    }
                }
            }.distinct()
            if (values.isEmpty()) null else owner.unit to values
        }
    }

    /** One substring lookup per registered suffix length; at most 255, usually only a few. */
    private class SuffixIndex<T>(entries: List<Pair<String, T>>) {
        private val byLength = entries.groupBy { it.first.length }.mapValues { (_, values) ->
            values.groupBy({ it.first }, { it.second })
        }
        fun forEachMatch(input: String, action: (T) -> Unit) {
            for ((length, suffixes) in byLength) {
                if (length <= input.length) suffixes[input.substring(input.length - length)]?.forEach(action)
            }
        }
    }
}
