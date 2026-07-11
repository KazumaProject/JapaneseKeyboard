package com.kazumaproject.markdownhelperkeyboard.ngram_rule.ui

import com.google.gson.Gson
import com.google.gson.JsonParser

object NgramRuleBackupCodec {
    private val gson = Gson()

    fun encode(backup: NgramRuleBackup): String = gson.toJson(backup)

    fun decode(json: String): NgramRuleBackup {
        val root = JsonParser.parseString(json).asJsonObject
        return when {
            root.has("version") -> {
                require(root.get("version").asInt == NgramRuleViewModel.NGRAM_BACKUP_VERSION) {
                    "Unsupported N-gram backup version"
                }
                gson.fromJson(root, NgramRuleBackup::class.java).also(::validateStructure)
            }

            root.has("twoNodeRules") || root.has("threeNodeRules") -> {
                val legacy = gson.fromJson(root, LegacyNgramRuleBackup::class.java)
                NgramRuleBackup(
                    rules = legacy.twoNodeRules.map {
                        NgramRuleBackupItem(listOf(it.prev, it.current), it.adjustment)
                    } + legacy.threeNodeRules.map {
                        NgramRuleBackupItem(listOf(it.first, it.second, it.third), it.adjustment)
                    },
                ).also(::validateStructure)
            }

            else -> error("Unknown N-gram backup format")
        }
    }

    private fun validateStructure(backup: NgramRuleBackup) {
        require(backup.version == NgramRuleViewModel.NGRAM_BACKUP_VERSION)
        backup.rules.forEachIndexed { index, rule ->
            require(rule.nodes.size in 2..5) { "Invalid node count at rule $index" }
            require(rule.adjustment in NgramRuleViewModel.ADJUSTMENT_MIN..NgramRuleViewModel.ADJUSTMENT_MAX) {
                "Invalid adjustment at rule $index"
            }
        }
    }
}
