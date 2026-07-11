package com.kazumaproject.markdownhelperkeyboard.ngram_rule.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NgramRuleBackupCodecTest {
    @Test
    fun versionTwo_roundTripsTwoThroughFiveNodeRules() {
        val backup = NgramRuleBackup(
            rules = (2..5).map { count ->
                NgramRuleBackupItem(
                    nodes = List(count) { index -> NodeFeatureInput(word = "word-$count-$index") },
                    adjustment = -count * 100,
                )
            },
        )

        assertEquals(backup, NgramRuleBackupCodec.decode(NgramRuleBackupCodec.encode(backup)))
    }

    @Test
    fun legacyVersionOne_convertsTwoAndThreeNodeRules() {
        val json = """
            {
              "twoNodeRules": [{
                "prev": {"word":"布"},
                "current": {"word":"で"},
                "adjustment": -300
              }],
              "threeNodeRules": [{
                "first": {"word":"布"},
                "second": {"word":"で"},
                "third": {"word":"拭く"},
                "adjustment": -1200
              }]
            }
        """.trimIndent()

        val decoded = NgramRuleBackupCodec.decode(json)

        assertEquals(listOf(2, 3), decoded.rules.map { it.nodes.size })
        assertEquals(listOf(-300, -1200), decoded.rules.map { it.adjustment })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownVersion() {
        NgramRuleBackupCodec.decode("""{"version":99,"rules":[]}""")
    }
}
